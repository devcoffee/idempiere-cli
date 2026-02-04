package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Validates development environment and plugin structure.
 */
@ApplicationScoped
public class DoctorService {

    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    // Use ASCII on Windows to avoid encoding issues
    private static final String CHECK = IS_WINDOWS ? "[OK]" : "\u2714";
    private static final String CROSS = IS_WINDOWS ? "[FAIL]" : "\u2718";
    private static final String WARN = IS_WINDOWS ? "[WARN]" : "\u26A0";

    // Pre-compiled patterns for version detection (avoids re-compilation on each call)
    private static final Pattern REQUIRE_BUNDLE_PATTERN = Pattern.compile("(?:Require-Bundle|Fragment-Host):\\s*(.+)", Pattern.DOTALL);
    private static final Pattern JAVA_VERSION_PATTERN = Pattern.compile("version \"(\\d+)");
    private static final Pattern MAVEN_VERSION_PATTERN = Pattern.compile("Apache Maven (\\S+)");
    private static final Pattern GIT_VERSION_PATTERN = Pattern.compile("git version (\\S+)");
    private static final Pattern DOCKER_VERSION_PATTERN = Pattern.compile("Docker version (\\S+)");
    private static final Pattern PSQL_VERSION_PATTERN = Pattern.compile("psql \\(PostgreSQL\\) (\\S+)");

    @Inject
    ProcessRunner processRunner;

    public void checkEnvironment(boolean fix) {
        System.out.println();
        System.out.println("iDempiere CLI - Environment Check");
        System.out.println("==================================");
        System.out.println();

        List<CheckResult> results = new ArrayList<>();

        results.add(checkJava());
        results.add(checkJar());
        results.add(checkMaven());
        results.add(checkGit());
        results.add(checkPostgres());
        results.add(checkGreadlink());
        results.add(checkDocker());

        System.out.println();
        System.out.println("----------------------------------");

        long passed = results.stream().filter(r -> r.status == Status.OK).count();
        long warnings = results.stream().filter(r -> r.status == Status.WARN).count();
        long failed = results.stream().filter(r -> r.status == Status.FAIL).count();

        System.out.printf("Results: %d passed, %d warnings, %d failed%n", passed, warnings, failed);

        if (fix && failed > 0) {
            runAutoFix(results);
        } else if (failed > 0) {
            printFixSuggestions(results);
        } else if (passed == results.size()) {
            System.out.println();
            System.out.println("All checks passed! Your environment is ready.");
            System.out.println("Run 'idempiere setup-dev-env' to bootstrap your development environment.");
        }

        System.out.println();
    }

    public void checkPlugin(Path pluginDir) {
        System.out.println();
        System.out.println("iDempiere CLI - Plugin Validation");
        System.out.println("==================================");
        System.out.println();

        if (!Files.exists(pluginDir)) {
            System.err.println("  Error: Directory '" + pluginDir + "' does not exist.");
            return;
        }

        List<CheckResult> results = new ArrayList<>();

        results.add(checkManifest(pluginDir));
        results.add(checkBuildProperties(pluginDir));
        results.add(checkPomXml(pluginDir));
        results.add(checkOsgiInf(pluginDir));
        results.add(checkImportsVsRequireBundle(pluginDir));

        System.out.println();
        System.out.println("----------------------------------");

        long passed = results.stream().filter(r -> r.status == Status.OK).count();
        long warnings = results.stream().filter(r -> r.status == Status.WARN).count();
        long failed = results.stream().filter(r -> r.status == Status.FAIL).count();

        System.out.printf("Results: %d passed, %d warnings, %d failed%n", passed, warnings, failed);
        System.out.println();
    }

    private CheckResult checkManifest(Path pluginDir) {
        Path manifest = pluginDir.resolve("META-INF/MANIFEST.MF");
        if (!Files.exists(manifest)) {
            printResult(Status.FAIL, "MANIFEST.MF", "Not found at META-INF/MANIFEST.MF");
            return new CheckResult("MANIFEST.MF", Status.FAIL);
        }
        try {
            String content = Files.readString(manifest);
            List<String> missing = new ArrayList<>();
            if (!content.contains("Bundle-SymbolicName")) missing.add("Bundle-SymbolicName");
            if (!content.contains("Bundle-Version")) missing.add("Bundle-Version");
            if (!content.contains("Bundle-RequiredExecutionEnvironment")) missing.add("Bundle-RequiredExecutionEnvironment");
            if (!content.contains("Require-Bundle") && !content.contains("Fragment-Host")) missing.add("Require-Bundle or Fragment-Host");

            if (missing.isEmpty()) {
                printResult(Status.OK, "MANIFEST.MF", "All required headers present");
                return new CheckResult("MANIFEST.MF", Status.OK);
            } else {
                printResult(Status.FAIL, "MANIFEST.MF", "Missing: " + String.join(", ", missing));
                return new CheckResult("MANIFEST.MF", Status.FAIL);
            }
        } catch (IOException e) {
            printResult(Status.FAIL, "MANIFEST.MF", "Error reading: " + e.getMessage());
            return new CheckResult("MANIFEST.MF", Status.FAIL);
        }
    }

    private CheckResult checkBuildProperties(Path pluginDir) {
        Path buildProps = pluginDir.resolve("build.properties");
        if (!Files.exists(buildProps)) {
            printResult(Status.FAIL, "build.properties", "Not found");
            return new CheckResult("build.properties", Status.FAIL);
        }
        try {
            String content = Files.readString(buildProps);
            List<String> missing = new ArrayList<>();
            if (!content.contains("source..")) missing.add("source..");
            if (!content.contains("output..")) missing.add("output..");
            if (!content.contains("bin.includes")) missing.add("bin.includes");

            if (missing.isEmpty()) {
                printResult(Status.OK, "build.properties", "All required entries present");
                return new CheckResult("build.properties", Status.OK);
            } else {
                printResult(Status.WARN, "build.properties", "Missing: " + String.join(", ", missing));
                return new CheckResult("build.properties", Status.WARN);
            }
        } catch (IOException e) {
            printResult(Status.FAIL, "build.properties", "Error reading: " + e.getMessage());
            return new CheckResult("build.properties", Status.FAIL);
        }
    }

    private CheckResult checkPomXml(Path pluginDir) {
        Path pom = pluginDir.resolve("pom.xml");
        if (!Files.exists(pom)) {
            printResult(Status.FAIL, "pom.xml", "Not found");
            return new CheckResult("pom.xml", Status.FAIL);
        }
        try {
            String content = Files.readString(pom);
            List<String> issues = new ArrayList<>();
            if (!content.contains("tycho-maven-plugin")) issues.add("tycho-maven-plugin not found");
            if (!content.contains("<packaging>bundle</packaging>")) issues.add("packaging is not 'bundle'");

            if (issues.isEmpty()) {
                printResult(Status.OK, "pom.xml", "Tycho plugin and bundle packaging present");
                return new CheckResult("pom.xml", Status.OK);
            } else {
                printResult(Status.FAIL, "pom.xml", String.join(", ", issues));
                return new CheckResult("pom.xml", Status.FAIL);
            }
        } catch (IOException e) {
            printResult(Status.FAIL, "pom.xml", "Error reading: " + e.getMessage());
            return new CheckResult("pom.xml", Status.FAIL);
        }
    }

    private CheckResult checkOsgiInf(Path pluginDir) {
        Path osgiInf = pluginDir.resolve("OSGI-INF");
        if (Files.exists(osgiInf) && Files.isDirectory(osgiInf)) {
            printResult(Status.OK, "OSGI-INF", "Directory exists");
            return new CheckResult("OSGI-INF", Status.OK);
        }
        printResult(Status.WARN, "OSGI-INF", "Directory not found");
        return new CheckResult("OSGI-INF", Status.WARN);
    }

    private CheckResult checkImportsVsRequireBundle(Path pluginDir) {
        Path manifest = pluginDir.resolve("META-INF/MANIFEST.MF");
        if (!Files.exists(manifest)) {
            printResult(Status.WARN, "Dependencies", "Cannot check — no MANIFEST.MF");
            return new CheckResult("Dependencies", Status.WARN);
        }

        try {
            String manifestContent = Files.readString(manifest);
            Set<String> declaredBundles = new HashSet<>();
            Matcher m = REQUIRE_BUNDLE_PATTERN.matcher(manifestContent);
            if (m.find()) {
                String bundleStr = m.group(1).split("\\n(?!\\s)")[0];
                for (String part : bundleStr.split(",")) {
                    String bundle = part.trim().split(";")[0].trim();
                    if (!bundle.isEmpty()) declaredBundles.add(bundle);
                }
            }

            // Scan java imports
            Path srcDir = pluginDir.resolve("src");
            if (!Files.exists(srcDir)) {
                printResult(Status.WARN, "Dependencies", "No src/ directory found");
                return new CheckResult("Dependencies", Status.WARN);
            }

            Set<String> externalPackages = new HashSet<>();
            try (Stream<Path> walk = Files.walk(srcDir)) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(javaFile -> {
                    try {
                        Files.readAllLines(javaFile).stream()
                                .filter(line -> line.startsWith("import "))
                                .map(line -> line.replace("import ", "").replace(";", "").trim())
                                .filter(imp -> !imp.startsWith("java.") && !imp.startsWith("javax.") && !imp.startsWith("jakarta."))
                                .forEach(externalPackages::add);
                    } catch (IOException ignored) {
                    }
                });
            }

            // Check if imports are covered by declared bundles
            Set<String> uncoveredPrefixes = new HashSet<>();
            for (String imp : externalPackages) {
                if (imp.startsWith("org.compiere.") || imp.startsWith("org.adempiere.") || imp.startsWith("org.idempiere.")) {
                    if (!declaredBundles.contains("org.adempiere.base") && !declaredBundles.contains("org.idempiere.rest.api")) {
                        uncoveredPrefixes.add("org.adempiere.base");
                    }
                } else if (imp.startsWith("org.adempiere.webui.")) {
                    if (!declaredBundles.contains("org.adempiere.ui.zk")) {
                        uncoveredPrefixes.add("org.adempiere.ui.zk");
                    }
                }
            }

            if (uncoveredPrefixes.isEmpty()) {
                printResult(Status.OK, "Dependencies", "Imports match declared bundles");
                return new CheckResult("Dependencies", Status.OK);
            } else {
                printResult(Status.WARN, "Dependencies", "Missing bundles: " + String.join(", ", uncoveredPrefixes));
                return new CheckResult("Dependencies", Status.WARN);
            }
        } catch (IOException e) {
            printResult(Status.WARN, "Dependencies", "Error checking: " + e.getMessage());
            return new CheckResult("Dependencies", Status.WARN);
        }
    }

    private CheckResult checkJava() {
        ProcessRunner.RunResult result = processRunner.run("java", "-version");
        if (result.exitCode() < 0 || result.output() == null) {
            printResult(Status.FAIL, "Java", "Not found");
            return new CheckResult("Java", Status.FAIL);
        }

        Matcher matcher = JAVA_VERSION_PATTERN.matcher(result.output());
        if (matcher.find()) {
            int majorVersion = Integer.parseInt(matcher.group(1));
            if (majorVersion >= 17) {
                printResult(Status.OK, "Java", "Version " + majorVersion + " detected");
                return new CheckResult("Java", Status.OK);
            } else {
                printResult(Status.FAIL, "Java", "Version " + majorVersion + " found, but 17+ is required");
                return new CheckResult("Java", Status.FAIL);
            }
        }

        printResult(Status.WARN, "Java", "Found but could not determine version");
        return new CheckResult("Java", Status.WARN);
    }

    private CheckResult checkMaven() {
        ProcessRunner.RunResult result = processRunner.run("mvn", "-version");
        if (result.exitCode() < 0 || result.output() == null) {
            printResult(Status.FAIL, "Maven", "Not found");
            return new CheckResult("Maven", Status.FAIL);
        }

        Matcher matcher = MAVEN_VERSION_PATTERN.matcher(result.output());
        if (matcher.find()) {
            printResult(Status.OK, "Maven", "Version " + matcher.group(1) + " detected");
            return new CheckResult("Maven", Status.OK);
        }

        printResult(Status.OK, "Maven", "Found");
        return new CheckResult("Maven", Status.OK);
    }

    private CheckResult checkGit() {
        ProcessRunner.RunResult result = processRunner.run("git", "--version");
        if (result.exitCode() < 0 || result.output() == null) {
            printResult(Status.FAIL, "Git", "Not found");
            return new CheckResult("Git", Status.FAIL);
        }

        Matcher matcher = GIT_VERSION_PATTERN.matcher(result.output());
        if (matcher.find()) {
            printResult(Status.OK, "Git", "Version " + matcher.group(1) + " detected");
            return new CheckResult("Git", Status.OK);
        }

        printResult(Status.OK, "Git", "Found");
        return new CheckResult("Git", Status.OK);
    }

    private CheckResult checkDocker() {
        ProcessRunner.RunResult result = processRunner.run("docker", "--version");
        if (result.exitCode() < 0 || result.output() == null) {
            printResult(Status.WARN, "Docker", "Not found (optional)");
            return new CheckResult("Docker", Status.WARN);
        }

        Matcher matcher = DOCKER_VERSION_PATTERN.matcher(result.output());
        if (matcher.find()) {
            printResult(Status.OK, "Docker", "Version " + matcher.group(1) + " detected");
            return new CheckResult("Docker", Status.OK);
        }

        printResult(Status.OK, "Docker", "Found");
        return new CheckResult("Docker", Status.OK);
    }

    private CheckResult checkPostgres() {
        ProcessRunner.RunResult result = processRunner.run("psql", "--version");
        if (result.exitCode() < 0 || result.output() == null) {
            printResult(Status.FAIL, "PostgreSQL", "psql client not found (required for database import)");
            return new CheckResult("PostgreSQL", Status.FAIL);
        }

        Matcher matcher = PSQL_VERSION_PATTERN.matcher(result.output());
        if (matcher.find()) {
            printResult(Status.OK, "PostgreSQL", "psql version " + matcher.group(1) + " detected");
            return new CheckResult("PostgreSQL", Status.OK);
        }

        printResult(Status.OK, "PostgreSQL", "psql found");
        return new CheckResult("PostgreSQL", Status.OK);
    }

    private CheckResult checkJar() {
        ProcessRunner.RunResult result = processRunner.run("jar", "--version");
        if (result.exitCode() < 0 || result.output() == null) {
            printResult(Status.FAIL, "jar", "Not found (required for database seed extraction)");
            return new CheckResult("jar", Status.FAIL);
        }
        printResult(Status.OK, "jar", "Found (part of JDK)");
        return new CheckResult("jar", Status.OK);
    }

    private CheckResult checkGreadlink() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("mac")) {
            // greadlink is only needed on macOS
            return new CheckResult("greadlink", Status.OK);
        }

        ProcessRunner.RunResult result = processRunner.run("greadlink", "--version");
        if (result.exitCode() < 0 || result.output() == null) {
            printResult(Status.FAIL, "greadlink", "Not found (required on macOS for database import)");
            return new CheckResult("greadlink", Status.FAIL);
        }
        printResult(Status.OK, "greadlink", "Found (coreutils)");
        return new CheckResult("greadlink", Status.OK);
    }

    private void printResult(Status status, String tool, String message) {
        String icon = switch (status) {
            case OK -> CHECK;
            case WARN -> WARN;
            case FAIL -> CROSS;
        };
        System.out.printf("  %s  %-15s %s%n", icon, tool, message);
    }

    private void printFixSuggestions(List<CheckResult> results) {
        String os = System.getProperty("os.name", "").toLowerCase();

        boolean javaFailed = results.stream().anyMatch(r -> r.tool().equals("Java") && r.status() == Status.FAIL);
        boolean jarFailed = results.stream().anyMatch(r -> r.tool().equals("jar") && r.status() == Status.FAIL);
        boolean mavenFailed = results.stream().anyMatch(r -> r.tool().equals("Maven") && r.status() == Status.FAIL);
        boolean gitFailed = results.stream().anyMatch(r -> r.tool().equals("Git") && r.status() == Status.FAIL);
        boolean postgresFailed = results.stream().anyMatch(r -> r.tool().equals("PostgreSQL") && r.status() == Status.FAIL);
        boolean greadlinkFailed = results.stream().anyMatch(r -> r.tool().equals("greadlink") && r.status() == Status.FAIL);
        boolean dockerMissing = results.stream().anyMatch(r -> r.tool().equals("Docker") && r.status() != Status.OK);

        boolean hasCriticalFailures = javaFailed || jarFailed || mavenFailed || gitFailed || postgresFailed || greadlinkFailed;

        if (!hasCriticalFailures && !dockerMissing) {
            System.out.println();
            System.out.println("All checks passed! Your environment is ready.");
            System.out.println("Run 'idempiere setup-dev-env' to bootstrap your development environment.");
            return;
        }

        System.out.println();
        System.out.println("Fix Suggestions");
        System.out.println("---------------");

        if (hasCriticalFailures) {
            System.out.println();
            System.out.println("The following tools are REQUIRED:");
            System.out.println();

            // Build combined install command for macOS
            if (os.contains("mac")) {
                List<String> brewPackages = new ArrayList<>();
                if (javaFailed || jarFailed) brewPackages.add("openjdk@17");
                if (mavenFailed) brewPackages.add("maven");
                if (gitFailed) brewPackages.add("git");
                if (postgresFailed) brewPackages.add("postgresql");
                if (greadlinkFailed) brewPackages.add("coreutils");

                if (!brewPackages.isEmpty()) {
                    System.out.println("  Install with Homebrew:");
                    System.out.println();
                    System.out.println("    brew install " + String.join(" ", brewPackages));
                    System.out.println();
                    System.out.println("  Or run: idempiere doctor --fix");
                    System.out.println();
                }
            } else if (os.contains("linux")) {
                List<String> aptPackages = new ArrayList<>();
                if (javaFailed || jarFailed) aptPackages.add("openjdk-17-jdk");
                if (mavenFailed) aptPackages.add("maven");
                if (gitFailed) aptPackages.add("git");
                if (postgresFailed) aptPackages.add("postgresql-client");

                if (!aptPackages.isEmpty()) {
                    System.out.println("  Install with apt (Debian/Ubuntu):");
                    System.out.println();
                    System.out.println("    sudo apt install " + String.join(" ", aptPackages));
                    System.out.println();
                }
            } else if (os.contains("win")) {
                // Windows with winget
                List<String> wingetPackages = new ArrayList<>();
                if (javaFailed || jarFailed) wingetPackages.add("EclipseAdoptium.Temurin.21.JDK");
                if (mavenFailed) wingetPackages.add("Apache.Maven");
                if (gitFailed) wingetPackages.add("Git.Git");
                if (postgresFailed) wingetPackages.add("PostgreSQL.PostgreSQL");

                if (!wingetPackages.isEmpty()) {
                    System.out.println("  Install with winget:");
                    System.out.println();
                    for (String pkg : wingetPackages) {
                        System.out.println("    winget install --id " + pkg);
                    }
                    System.out.println();
                    System.out.println("  Or run: idempiere-cli doctor --fix");
                    System.out.println();
                }
            } else {
                // Other OS - show URLs
                if (javaFailed || jarFailed) {
                    System.out.println("  Java 21+: https://adoptium.net/");
                }
                if (mavenFailed) {
                    System.out.println("  Maven: https://maven.apache.org/download.cgi");
                }
                if (gitFailed) {
                    System.out.println("  Git: https://git-scm.com/downloads");
                }
                if (postgresFailed) {
                    System.out.println("  PostgreSQL: https://www.postgresql.org/download/");
                }
                System.out.println();
            }

            System.out.println("After installing, run 'idempiere doctor' again.");
        }

        if (dockerMissing) {
            System.out.println();
            System.out.println("Optional: Docker (for containerized PostgreSQL)");
            if (os.contains("mac")) {
                System.out.println("    brew install --cask docker");
            } else if (os.contains("linux")) {
                System.out.println("    sudo apt install docker.io");
            } else {
                System.out.println("    https://www.docker.com/products/docker-desktop");
            }
            System.out.println();
            System.out.println("  With Docker, use: idempiere setup-dev-env --with-docker");
        }
    }

    private void runAutoFix(List<CheckResult> results) {
        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("win")) {
            runAutoFixWindows(results);
        } else if (os.contains("mac")) {
            runAutoFixMac(results);
        } else {
            System.out.println();
            System.out.println("Auto-fix is currently only supported on macOS (Homebrew) and Windows (winget).");
            System.out.println("Please install the missing tools manually.");
        }
    }

    private void runAutoFixWindows(List<CheckResult> results) {
        // Check if winget is available
        if (!processRunner.isAvailable("winget")) {
            System.out.println();
            System.out.println("winget not found. Please install Windows Package Manager or install tools manually.");
            System.out.println("winget is included in Windows 10/11. Update Windows or install from:");
            System.out.println("  https://aka.ms/getwinget");
            return;
        }

        boolean javaFailed = results.stream().anyMatch(r -> r.tool().equals("Java") && r.status() == Status.FAIL);
        boolean jarFailed = results.stream().anyMatch(r -> r.tool().equals("jar") && r.status() == Status.FAIL);
        boolean mavenFailed = results.stream().anyMatch(r -> r.tool().equals("Maven") && r.status() == Status.FAIL);
        boolean gitFailed = results.stream().anyMatch(r -> r.tool().equals("Git") && r.status() == Status.FAIL);
        boolean postgresFailed = results.stream().anyMatch(r -> r.tool().equals("PostgreSQL") && r.status() == Status.FAIL);

        List<String[]> packagesToInstall = new ArrayList<>();
        if (javaFailed || jarFailed) packagesToInstall.add(new String[]{"EclipseAdoptium.Temurin.21.JDK", "Java 21 (Temurin)"});
        if (mavenFailed) packagesToInstall.add(new String[]{"Apache.Maven", "Maven"});
        if (gitFailed) packagesToInstall.add(new String[]{"Git.Git", "Git"});
        if (postgresFailed) packagesToInstall.add(new String[]{"PostgreSQL.PostgreSQL", "PostgreSQL"});

        if (packagesToInstall.isEmpty()) {
            System.out.println();
            System.out.println("Nothing to fix!");
            return;
        }

        System.out.println();
        System.out.println("Installing missing packages with winget...");
        System.out.println();

        boolean allSucceeded = true;
        for (String[] pkg : packagesToInstall) {
            System.out.println("Installing " + pkg[1] + "...");
            int exitCode = processRunner.runLive("winget", "install", "--id", pkg[0], "--accept-source-agreements", "--accept-package-agreements");
            if (exitCode != 0) {
                System.out.println("  Warning: " + pkg[1] + " installation may have failed.");
                allSucceeded = false;
            }
        }

        System.out.println();
        if (allSucceeded) {
            System.out.println("Installation complete. Restart your terminal and run 'idempiere-cli doctor' to verify.");
        } else {
            System.out.println("Some packages may have failed. Check output above and install manually if needed.");
        }
        System.out.println();
        System.out.println("Note: You may need to restart your terminal for PATH changes to take effect.");
    }

    private void runAutoFixMac(List<CheckResult> results) {
        // Check if Homebrew is available
        if (!processRunner.isAvailable("brew")) {
            System.out.println();
            System.out.println("Homebrew not found. Install it first:");
            System.out.println("  /bin/bash -c \"$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\"");
            return;
        }

        List<String> packagesToInstall = new ArrayList<>();

        boolean javaFailed = results.stream().anyMatch(r -> r.tool().equals("Java") && r.status() == Status.FAIL);
        boolean jarFailed = results.stream().anyMatch(r -> r.tool().equals("jar") && r.status() == Status.FAIL);
        boolean mavenFailed = results.stream().anyMatch(r -> r.tool().equals("Maven") && r.status() == Status.FAIL);
        boolean gitFailed = results.stream().anyMatch(r -> r.tool().equals("Git") && r.status() == Status.FAIL);
        boolean postgresFailed = results.stream().anyMatch(r -> r.tool().equals("PostgreSQL") && r.status() == Status.FAIL);
        boolean greadlinkFailed = results.stream().anyMatch(r -> r.tool().equals("greadlink") && r.status() == Status.FAIL);

        if (javaFailed || jarFailed) packagesToInstall.add("openjdk@21");
        if (mavenFailed) packagesToInstall.add("maven");
        if (gitFailed) packagesToInstall.add("git");
        if (postgresFailed) packagesToInstall.add("postgresql");
        if (greadlinkFailed) packagesToInstall.add("coreutils");

        if (packagesToInstall.isEmpty()) {
            System.out.println();
            System.out.println("Nothing to fix!");
            return;
        }

        System.out.println();
        System.out.println("Installing missing packages with Homebrew...");
        System.out.println();

        List<String> command = new ArrayList<>();
        command.add("brew");
        command.add("install");
        command.addAll(packagesToInstall);

        int exitCode = processRunner.runLive(command.toArray(new String[0]));

        if (exitCode == 0) {
            System.out.println();
            System.out.println("Installation complete. Run 'idempiere-cli doctor' to verify.");
        } else {
            System.out.println();
            System.out.println("Some packages may have failed to install. Check output above.");
        }
    }

    private enum Status {
        OK, WARN, FAIL
    }

    private record CheckResult(String tool, Status status) {
    }
}
