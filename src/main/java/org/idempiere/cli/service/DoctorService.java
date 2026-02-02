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

@ApplicationScoped
public class DoctorService {

    private static final String CHECK = "\u2714";
    private static final String CROSS = "\u2718";
    private static final String WARN = "\u26A0";

    @Inject
    ProcessRunner processRunner;

    public void checkEnvironment(boolean fix) {
        System.out.println();
        System.out.println("iDempiere CLI - Environment Check");
        System.out.println("==================================");
        System.out.println();

        List<CheckResult> results = new ArrayList<>();

        results.add(checkJava());
        results.add(checkMaven());
        results.add(checkGit());
        results.add(checkDocker());
        results.add(checkPostgres());

        System.out.println();
        System.out.println("----------------------------------");

        long passed = results.stream().filter(r -> r.status == Status.OK).count();
        long warnings = results.stream().filter(r -> r.status == Status.WARN).count();
        long failed = results.stream().filter(r -> r.status == Status.FAIL).count();

        System.out.printf("Results: %d passed, %d warnings, %d failed%n", passed, warnings, failed);

        if (failed > 0 && fix) {
            System.out.println();
            System.out.println("Auto-fix is not yet implemented. Please install missing tools manually.");
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
            Matcher m = Pattern.compile("(?:Require-Bundle|Fragment-Host):\\s*(.+)", Pattern.DOTALL).matcher(manifestContent);
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

        Pattern pattern = Pattern.compile("version \"(\\d+)");
        Matcher matcher = pattern.matcher(result.output());
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

        Pattern pattern = Pattern.compile("Apache Maven (\\S+)");
        Matcher matcher = pattern.matcher(result.output());
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

        Pattern pattern = Pattern.compile("git version (\\S+)");
        Matcher matcher = pattern.matcher(result.output());
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

        Pattern pattern = Pattern.compile("Docker version (\\S+)");
        Matcher matcher = pattern.matcher(result.output());
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
            printResult(Status.WARN, "PostgreSQL", "psql client not found (optional)");
            return new CheckResult("PostgreSQL", Status.WARN);
        }

        Pattern pattern = Pattern.compile("psql \\(PostgreSQL\\) (\\S+)");
        Matcher matcher = pattern.matcher(result.output());
        if (matcher.find()) {
            printResult(Status.OK, "PostgreSQL", "psql version " + matcher.group(1) + " detected");
            return new CheckResult("PostgreSQL", Status.OK);
        }

        printResult(Status.OK, "PostgreSQL", "psql found");
        return new CheckResult("PostgreSQL", Status.OK);
    }

    private void printResult(Status status, String tool, String message) {
        String icon = switch (status) {
            case OK -> CHECK;
            case WARN -> WARN;
            case FAIL -> CROSS;
        };
        System.out.printf("  %s  %-15s %s%n", icon, tool, message);
    }

    private enum Status {
        OK, WARN, FAIL
    }

    private record CheckResult(String tool, Status status) {
    }
}
