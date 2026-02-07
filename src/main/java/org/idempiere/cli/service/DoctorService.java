package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.idempiere.cli.service.check.CheckResult;
import org.idempiere.cli.service.check.CheckResult.Status;
import org.idempiere.cli.service.check.EnvironmentCheck;
import org.idempiere.cli.service.check.PluginCheck;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Validates development environment and plugin structure.
 *
 * <p>Uses CDI-discovered {@link EnvironmentCheck} and {@link PluginCheck}
 * implementations for individual checks.
 */
@ApplicationScoped
public class DoctorService {

    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    // Use ASCII on Windows to avoid encoding issues
    private static final String CHECK_MARK = IS_WINDOWS ? "[OK]" : "\u2714";
    private static final String CROSS = IS_WINDOWS ? "[FAIL]" : "\u2718";
    private static final String WARN_MARK = IS_WINDOWS ? "[WARN]" : "\u26A0";

    @Inject
    ProcessRunner processRunner;

    @Inject
    Instance<EnvironmentCheck> environmentChecks;

    @Inject
    Instance<PluginCheck> pluginChecks;

    public void checkEnvironment(boolean fix, boolean fixOptional) {
        System.out.println();
        System.out.println("iDempiere CLI - Environment Check");
        System.out.println("==================================");
        System.out.println();

        List<CheckResult> results = new ArrayList<>();

        // Run all registered environment checks
        for (EnvironmentCheck check : environmentChecks) {
            if (!check.isApplicable()) {
                continue;  // Silently skip non-applicable checks
            }
            CheckResult result = check.check();
            printResult(result);
            results.add(result);
        }

        System.out.println();
        System.out.println("----------------------------------");

        long passed = results.stream().filter(CheckResult::isOk).count();
        long warnings = results.stream().filter(CheckResult::isWarn).count();
        long failed = results.stream().filter(CheckResult::isFail).count();

        System.out.printf("Results: %d passed, %d warnings, %d failed%n", passed, warnings, failed);

        boolean dockerNotOk = results.stream().anyMatch(r -> r.tool().equals("Docker") && !r.isOk());
        boolean postgresOutdated = results.stream().anyMatch(r -> r.tool().equals("PostgreSQL") && r.isWarn());
        boolean hasFixableIssues = failed > 0 || (fixOptional && dockerNotOk);

        if (fix && hasFixableIssues) {
            runAutoFix(results, fixOptional);
        } else if (failed > 0 || dockerNotOk || postgresOutdated) {
            printFixSuggestions(results);
        }

        if (failed == 0) {
            System.out.println();
            if (warnings > 0) {
                System.out.println("Environment is functional but has warnings. Consider addressing them.");
            } else {
                System.out.println("All checks passed! Your environment is ready.");
            }
            System.out.println("Run 'idempiere-cli setup-dev-env' to bootstrap your development environment.");
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

        // Run all registered plugin checks
        for (PluginCheck check : pluginChecks) {
            CheckResult result = check.check(pluginDir);
            printResult(result);
            results.add(result);
        }

        System.out.println();
        System.out.println("----------------------------------");

        long passed = results.stream().filter(CheckResult::isOk).count();
        long warnings = results.stream().filter(CheckResult::isWarn).count();
        long failed = results.stream().filter(CheckResult::isFail).count();

        System.out.printf("Results: %d passed, %d warnings, %d failed%n", passed, warnings, failed);
        System.out.println();
    }
    private boolean isDockerDesktopInstalled() {
        String programFiles = System.getenv("ProgramFiles");
        if (programFiles == null) programFiles = "C:\\Program Files";
        return Files.exists(Path.of(programFiles, "Docker", "Docker", "Docker Desktop.exe"));
    }

    private void printResult(CheckResult result) {
        String icon = switch (result.status()) {
            case OK -> CHECK_MARK;
            case WARN -> WARN_MARK;
            case FAIL -> CROSS;
        };
        System.out.printf("  %s  %-15s %s%n", icon, result.tool(), result.message());
    }

    private void printFixSuggestions(List<CheckResult> results) {
        String os = System.getProperty("os.name", "").toLowerCase();

        // Get failed results with their messages
        CheckResult javaResult = results.stream().filter(r -> r.tool().equals("Java") && r.status() == Status.FAIL).findFirst().orElse(null);
        CheckResult jarResult = results.stream().filter(r -> r.tool().equals("jar") && r.status() == Status.FAIL).findFirst().orElse(null);
        CheckResult mavenResult = results.stream().filter(r -> r.tool().equals("Maven") && r.status() == Status.FAIL).findFirst().orElse(null);
        CheckResult gitResult = results.stream().filter(r -> r.tool().equals("Git") && r.status() == Status.FAIL).findFirst().orElse(null);
        CheckResult postgresResult = results.stream().filter(r -> r.tool().equals("PostgreSQL") && r.status() == Status.FAIL).findFirst().orElse(null);
        CheckResult postgresWarnResult = results.stream().filter(r -> r.tool().equals("PostgreSQL") && r.status() == Status.WARN).findFirst().orElse(null);
        CheckResult greadlinkResult = results.stream().filter(r -> r.tool().equals("greadlink") && r.status() == Status.FAIL).findFirst().orElse(null);
        boolean dockerMissing = results.stream().anyMatch(r -> r.tool().equals("Docker") && r.status() != Status.OK);

        boolean javaFailed = javaResult != null;
        boolean jarFailed = jarResult != null;
        boolean mavenFailed = mavenResult != null;
        boolean gitFailed = gitResult != null;
        boolean postgresFailed = postgresResult != null;
        boolean postgresOutdated = postgresWarnResult != null;
        boolean greadlinkFailed = greadlinkResult != null;

        boolean hasCriticalFailures = javaFailed || jarFailed || mavenFailed || gitFailed || postgresFailed || greadlinkFailed;

        if (!hasCriticalFailures && !dockerMissing && !postgresOutdated) {
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
                if (javaFailed || jarFailed) brewPackages.add("openjdk@21");
                if (mavenFailed) brewPackages.add("maven");
                if (gitFailed) brewPackages.add("git");
                if (postgresFailed) brewPackages.add("postgresql@16");
                if (greadlinkFailed) brewPackages.add("coreutils");

                if (!brewPackages.isEmpty()) {
                    System.out.println("  Install with Homebrew:");
                    System.out.println();
                    System.out.println("    brew install " + String.join(" ", brewPackages));
                    System.out.println();
                    System.out.println("  Or run: idempiere-cli doctor --fix");
                    System.out.println();
                }
            } else if (os.contains("linux")) {
                List<String> aptPackages = new ArrayList<>();
                if (javaFailed || jarFailed) aptPackages.add("openjdk-21-jdk");
                if (mavenFailed) aptPackages.add("maven");
                if (gitFailed) aptPackages.add("git");
                if (postgresFailed) aptPackages.add("postgresql-client-16");

                if (!aptPackages.isEmpty()) {
                    System.out.println("  Install with apt (Debian/Ubuntu):");
                    System.out.println();
                    System.out.println("    sudo apt install " + String.join(" ", aptPackages));
                    System.out.println();
                    System.out.println("  Or run: idempiere-cli doctor --fix");
                    System.out.println("  (auto-detects apt, dnf, yum, pacman, zypper)");
                    System.out.println();
                }
            } else if (os.contains("win")) {
                // Windows: separate tools into "need install" vs "need PATH"
                List<String> wingetPackages = new ArrayList<>();
                List<String[]> pathIssues = new ArrayList<>(); // {tool name, path to add}

                // Check Java
                if (javaFailed || jarFailed) {
                    if (javaResult != null && isInstalledButNotInPath(javaResult)) {
                        String path = detectWingetToolPath("EclipseAdoptium.Temurin");
                        if (path != null) pathIssues.add(new String[]{"Java", path});
                    } else {
                        wingetPackages.add("EclipseAdoptium.Temurin.21.JDK");
                    }
                }

                // Check Git
                if (gitFailed) {
                    if (isInstalledButNotInPath(gitResult)) {
                        String path = detectWingetToolPath("Git.Git");
                        if (path != null) pathIssues.add(new String[]{"Git", path});
                    } else {
                        wingetPackages.add("Git.Git");
                    }
                }

                // Check PostgreSQL
                if (postgresFailed) {
                    if (isInstalledButNotInPath(postgresResult)) {
                        String path = detectWingetToolPath("PostgreSQL.PostgreSQL");
                        if (path != null) pathIssues.add(new String[]{"PostgreSQL", path});
                    } else {
                        wingetPackages.add("PostgreSQL.PostgreSQL.17");
                    }
                }

                // Check Maven
                boolean mavenNeedsInstall = false;
                if (mavenFailed) {
                    if (isInstalledButNotInPath(mavenResult)) {
                        String programFiles = System.getenv("ProgramFiles");
                        if (programFiles == null) programFiles = "C:\\Program Files";
                        Path mavenBin = Path.of(programFiles, "apache-maven-" + MAVEN_VERSION, "bin");
                        if (Files.exists(mavenBin)) {
                            pathIssues.add(new String[]{"Maven", mavenBin.toString()});
                        } else {
                            mavenNeedsInstall = true;
                        }
                    } else {
                        mavenNeedsInstall = true;
                    }
                }

                // Show PATH instructions first (tools already installed)
                if (!pathIssues.isEmpty()) {
                    System.out.println("  Tools installed but not in PATH:");
                    System.out.println();
                    StringBuilder allPaths = new StringBuilder();
                    for (String[] issue : pathIssues) {
                        System.out.println("    " + issue[0] + ": " + issue[1]);
                        if (allPaths.length() > 0) allPaths.append(";");
                        allPaths.append(issue[1]);
                    }
                    System.out.println();
                    System.out.println("  Option 1: Restart your terminal");
                    System.out.println();
                    System.out.println("  Option 2: Add to PATH (run as Administrator in PowerShell):");
                    System.out.println();
                    System.out.println("    [Environment]::SetEnvironmentVariable(\"Path\",");
                    System.out.println("      [Environment]::GetEnvironmentVariable(\"Path\", \"Machine\") + \";\" +");
                    System.out.println("      \"" + allPaths + "\", \"Machine\")");
                    System.out.println();
                }

                // Show install instructions (tools not installed)
                if (!wingetPackages.isEmpty() || mavenNeedsInstall) {
                    if (!pathIssues.isEmpty()) {
                        System.out.println("  Tools that need to be installed:");
                        System.out.println();
                    }
                    if (!wingetPackages.isEmpty()) {
                        System.out.println("  Install with winget:");
                        System.out.println();
                        for (String pkg : wingetPackages) {
                            System.out.println("    winget install --id " + pkg + " --source winget");
                        }
                        System.out.println();
                    }
                    if (mavenNeedsInstall) {
                        System.out.println("  Maven (manual download):");
                        System.out.println("    https://maven.apache.org/download.cgi");
                        System.out.println();
                    }
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

            System.out.println("After fixing, run 'idempiere-cli doctor' again.");
        }

        // PostgreSQL upgrade suggestion (when outdated but functional)
        if (postgresOutdated) {
            System.out.println();
            System.out.println("Recommendation: Upgrade PostgreSQL client to version 16");
            System.out.println();
            if (os.contains("mac")) {
                System.out.println("  brew install postgresql@16");
                System.out.println("  brew link postgresql@16 --force");
            } else if (os.contains("linux")) {
                System.out.println("  # Debian/Ubuntu:");
                System.out.println("  sudo apt install postgresql-client-16");
                System.out.println();
                System.out.println("  # Fedora/RHEL:");
                System.out.println("  sudo dnf install postgresql16");
            } else if (os.contains("win")) {
                System.out.println("  winget install --id PostgreSQL.PostgreSQL.16 --source winget");
            }
            System.out.println();
            System.out.println("  This ensures client/server version compatibility.");
        }

        if (dockerMissing) {
            System.out.println();
            CheckResult dockerResult = results.stream().filter(r -> r.tool().equals("Docker")).findFirst().orElse(null);
            boolean daemonNotRunning = dockerResult != null && dockerResult.message() != null
                    && dockerResult.message().contains("daemon is not running");

            if (daemonNotRunning) {
                System.out.println("Docker is installed but the daemon is not running.");
                if (os.contains("win")) {
                    if (isDockerDesktopInstalled()) {
                        System.out.println("  Start Docker Desktop from the Start menu, or run:");
                        System.out.println("    & \"C:\\Program Files\\Docker\\Docker\\Docker Desktop.exe\"");
                    } else {
                        System.out.println("  Docker Desktop not found. Install it from:");
                        System.out.println("    https://www.docker.com/products/docker-desktop");
                        System.out.println("  Or: winget install --id Docker.DockerDesktop --source winget");
                    }
                } else if (os.contains("mac")) {
                    System.out.println("  Start Docker Desktop, or run:");
                    System.out.println("    open -a Docker");
                } else {
                    System.out.println("  Start Docker with:");
                    System.out.println("    sudo systemctl start docker");
                }
            } else {
                System.out.println("Optional: Docker (for containerized PostgreSQL)");
                if (os.contains("mac")) {
                    System.out.println("  Install manually:  brew install --cask docker");
                } else if (os.contains("linux")) {
                    System.out.println("  Install manually:  sudo apt install docker.io");
                } else if (os.contains("win")) {
                    System.out.println("  Install manually:  winget install --id Docker.DockerDesktop --source winget");
                } else {
                    System.out.println("  Install manually:  https://www.docker.com/products/docker-desktop");
                }
                System.out.println("  Or run:            idempiere-cli doctor --fix-optional");
            }
            System.out.println();
            System.out.println("  With Docker, use: idempiere-cli setup-dev-env --with-docker");
        }
    }

    /**
     * Check if a tool is installed but not in PATH based on the check result message.
     */
    private boolean isInstalledButNotInPath(CheckResult result) {
        if (result == null || result.message() == null) return false;
        String msg = result.message().toLowerCase();
        return msg.contains("not in path") || msg.contains("installed but");
    }

    private void runAutoFix(List<CheckResult> results, boolean fixOptional) {
        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("win")) {
            runAutoFixWindows(results, fixOptional);
        } else if (os.contains("mac")) {
            runAutoFixMac(results, fixOptional);
        } else if (os.contains("linux") || os.contains("nix")) {
            runAutoFixLinux(results, fixOptional);
        } else {
            System.out.println();
            System.out.println("Auto-fix is not supported on this platform.");
            System.out.println("Please install the missing tools manually.");
        }
    }

    private void runAutoFixWindows(List<CheckResult> results, boolean fixOptional) {
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

        // Docker (optional - only with --fix-optional)
        CheckResult dockerResult = results.stream().filter(r -> r.tool().equals("Docker")).findFirst().orElse(null);
        boolean dockerNotInstalled = fixOptional && dockerResult != null && dockerResult.status() != Status.OK
                && dockerResult.message() != null && !dockerResult.message().contains("daemon is not running");
        boolean dockerDaemonStopped = fixOptional && dockerResult != null && dockerResult.message() != null
                && dockerResult.message().contains("daemon is not running");

        // Track tools that need PATH adjustment
        List<String[]> pathIssues = new ArrayList<>(); // {tool name, path to add}

        // Packages to install via winget (Maven not available on winget)
        List<String[]> packagesToInstall = new ArrayList<>();
        if (javaFailed || jarFailed) packagesToInstall.add(new String[]{"EclipseAdoptium.Temurin.21.JDK", "Java 21 (Temurin)"});
        if (gitFailed) packagesToInstall.add(new String[]{"Git.Git", "Git"});
        if (postgresFailed) packagesToInstall.add(new String[]{"PostgreSQL.PostgreSQL.17", "PostgreSQL 17"});
        if (dockerNotInstalled) packagesToInstall.add(new String[]{"Docker.DockerDesktop", "Docker Desktop"});

        boolean hasWingetPackages = !packagesToInstall.isEmpty();
        boolean anythingInstalled = false;

        if (hasWingetPackages) {
            System.out.println();
            System.out.println("Installing packages with winget...");
            System.out.println();

            for (String[] pkg : packagesToInstall) {
                // Check if already installed via winget
                if (isWingetPackageInstalled(pkg[0])) {
                    System.out.println(pkg[1] + " is already installed but not in PATH.");
                    anythingInstalled = true;
                    // Detect the path for each tool
                    String pathToAdd = detectWingetToolPath(pkg[0]);
                    if (pathToAdd != null) {
                        pathIssues.add(new String[]{pkg[1], pathToAdd});
                    }
                    continue;
                }

                System.out.println("Installing " + pkg[1] + "...");
                int exitCode = processRunner.runLive("winget", "install", "--id", pkg[0], "--source", "winget", "--accept-source-agreements", "--accept-package-agreements");
                if (exitCode == 0) {
                    anythingInstalled = true;
                } else {
                    System.out.println("  Warning: " + pkg[1] + " installation may have failed.");
                }
            }
        }

        // Maven: download and install automatically
        if (mavenFailed) {
            System.out.println();
            boolean mavenInstalled = installMavenWindows();
            if (mavenInstalled) {
                anythingInstalled = true;
                // Check if Maven needs PATH
                String programFiles = System.getenv("ProgramFiles");
                if (programFiles == null) programFiles = "C:\\Program Files";
                Path mavenBin = Path.of(programFiles, "apache-maven-" + MAVEN_VERSION, "bin");
                if (Files.exists(mavenBin) && !processRunner.isAvailable("mvn")) {
                    pathIssues.add(new String[]{"Maven", mavenBin.toString()});
                }
            }
        }

        // Docker daemon stopped: try to start it
        if (dockerDaemonStopped && isDockerDesktopInstalled()) {
            System.out.println();
            System.out.println("Starting Docker Desktop...");
            int dockerStart = processRunner.runLive("cmd", "/c", "start", "",
                    "C:\\Program Files\\Docker\\Docker\\Docker Desktop.exe");
            if (dockerStart == 0) {
                System.out.println("  Docker Desktop is starting. It may need a moment to be ready.");
                anythingInstalled = true;
            } else {
                System.out.println("  Could not start Docker Desktop. Start it manually from the Start menu.");
            }
        }

        if (!hasWingetPackages && !mavenFailed && !dockerDaemonStopped) {
            System.out.println();
            System.out.println("Nothing to fix!");
            return;
        }

        System.out.println();
        if (anythingInstalled) {
            System.out.println("Installation complete.");

            if (!pathIssues.isEmpty()) {
                System.out.println();
                System.out.println("IMPORTANT: The following tools need to be added to PATH:");
                System.out.println();

                // Collect all paths for the combined command
                StringBuilder allPaths = new StringBuilder();
                for (String[] issue : pathIssues) {
                    System.out.println("  " + issue[0] + ": " + issue[1]);
                    if (allPaths.length() > 0) allPaths.append(";");
                    allPaths.append(issue[1]);
                }

                System.out.println();
                System.out.println("Option 1: Restart your terminal (may work if installer updated PATH)");
                System.out.println();
                System.out.println("Option 2: Add to PATH manually (run as Administrator in PowerShell):");
                System.out.println();
                System.out.println("  [Environment]::SetEnvironmentVariable(\"Path\",");
                System.out.println("    [Environment]::GetEnvironmentVariable(\"Path\", \"Machine\") + \";\" +");
                System.out.println("    \"" + allPaths + "\", \"Machine\")");
                System.out.println();
                System.out.println("Then run 'idempiere-cli doctor' to verify.");
            } else {
                System.out.println("Restart your terminal and run 'idempiere-cli doctor' to verify.");
            }
        }
    }

    /**
     * Detect the bin path for a winget-installed tool.
     */
    private String detectWingetToolPath(String packageId) {
        String programFiles = System.getenv("ProgramFiles");
        if (programFiles == null) programFiles = "C:\\Program Files";

        if (packageId.contains("Temurin")) {
            // Java Temurin installs to Program Files\Eclipse Adoptium\jdk-VERSION
            Path adoptiumDir = Path.of(programFiles, "Eclipse Adoptium");
            if (Files.exists(adoptiumDir)) {
                try (var dirs = Files.list(adoptiumDir)) {
                    return dirs.filter(Files::isDirectory)
                            .filter(p -> p.getFileName().toString().startsWith("jdk-"))
                            .findFirst()
                            .map(p -> p.resolve("bin").toString())
                            .orElse(null);
                } catch (IOException e) {
                    return null;
                }
            }
        } else if (packageId.contains("PostgreSQL")) {
            // PostgreSQL installs to Program Files\PostgreSQL\VERSION\bin
            Path pgDir = Path.of(programFiles, "PostgreSQL");
            if (Files.exists(pgDir)) {
                try (var dirs = Files.list(pgDir)) {
                    return dirs.filter(Files::isDirectory)
                            .filter(p -> p.getFileName().toString().matches("\\d+"))
                            .findFirst()
                            .map(p -> p.resolve("bin").toString())
                            .orElse(null);
                } catch (IOException e) {
                    return null;
                }
            }
        } else if (packageId.contains("Git")) {
            // Git installs to Program Files\Git\cmd
            Path gitPath = Path.of(programFiles, "Git", "cmd");
            if (Files.exists(gitPath)) {
                return gitPath.toString();
            }
        }
        return null;
    }

    private boolean isWingetPackageInstalled(String packageId) {
        ProcessRunner.RunResult result = processRunner.run("winget", "list", "--id", packageId, "--source", "winget");
        // winget list returns 0 if found, and output contains the package name
        return result.exitCode() == 0 && result.output().contains(packageId);
    }

    private static final String MAVEN_VERSION = "3.9.12";
    private static final String MAVEN_URL = "https://dlcdn.apache.org/maven/maven-3/" + MAVEN_VERSION + "/binaries/apache-maven-" + MAVEN_VERSION + "-bin.zip";

    private boolean installMavenWindows() {
        System.out.println("Installing Maven " + MAVEN_VERSION + "...");

        String programFiles = System.getenv("ProgramFiles");
        if (programFiles == null) {
            programFiles = "C:\\Program Files";
        }
        Path mavenDir = Path.of(programFiles, "Maven");
        Path mavenExtracted = Path.of(programFiles, "apache-maven-" + MAVEN_VERSION);

        // Check if already installed
        if (Files.exists(mavenExtracted.resolve("bin").resolve("mvn.cmd"))) {
            System.out.println("  Maven is already installed at: " + mavenExtracted);
            addMavenToPathInstructions(mavenExtracted);
            return true;
        }

        try {
            // Download Maven ZIP
            System.out.println("  Downloading from: " + MAVEN_URL);
            Path tempZip = Files.createTempFile("maven-", ".zip");

            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MAVEN_URL))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                System.out.println("  Failed to download Maven. HTTP status: " + response.statusCode());
                showMavenManualInstructions();
                return false;
            }

            Files.copy(response.body(), tempZip, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("  Downloaded successfully.");

            // Extract ZIP to Program Files
            System.out.println("  Extracting to: " + programFiles);
            extractZip(tempZip, Path.of(programFiles));
            Files.deleteIfExists(tempZip);

            if (!Files.exists(mavenExtracted.resolve("bin").resolve("mvn.cmd"))) {
                System.out.println("  Extraction failed - Maven not found at expected location.");
                showMavenManualInstructions();
                return false;
            }

            System.out.println("  Maven installed successfully!");
            addMavenToPathInstructions(mavenExtracted);
            return true;

        } catch (IOException | InterruptedException e) {
            System.out.println("  Failed to install Maven: " + e.getMessage());
            showMavenManualInstructions();
            return false;
        }
    }

    private void extractZip(Path zipFile, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = destDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private void addMavenToPathInstructions(Path mavenPath) {
        Path binPath = mavenPath.resolve("bin");
        System.out.println();
        System.out.println("  Add Maven to your PATH by running (as Administrator):");
        System.out.println("    setx /M PATH \"%PATH%;" + binPath + "\"");
        System.out.println();
        System.out.println("  Or manually add to System Environment Variables:");
        System.out.println("    " + binPath);
    }

    private void showMavenManualInstructions() {
        System.out.println();
        System.out.println("  Install Maven manually:");
        System.out.println("    1. Download from: https://maven.apache.org/download.cgi");
        System.out.println("    2. Extract ZIP to C:\\Program Files\\");
        System.out.println("    3. Add the bin folder to your PATH");
    }

    private void runAutoFixLinux(List<CheckResult> results, boolean fixOptional) {
        // Detect package manager
        String pkgManager = null;
        String installCmd = null;
        String sudoPrefix = "sudo ";

        if (processRunner.isAvailable("apt")) {
            pkgManager = "apt";
            installCmd = "apt install -y";
        } else if (processRunner.isAvailable("dnf")) {
            pkgManager = "dnf";
            installCmd = "dnf install -y";
        } else if (processRunner.isAvailable("yum")) {
            pkgManager = "yum";
            installCmd = "yum install -y";
        } else if (processRunner.isAvailable("pacman")) {
            pkgManager = "pacman";
            installCmd = "pacman -S --noconfirm";
        } else if (processRunner.isAvailable("zypper")) {
            pkgManager = "zypper";
            installCmd = "zypper install -y";
        }

        if (pkgManager == null) {
            System.out.println();
            System.out.println("Could not detect package manager (apt, dnf, yum, pacman, zypper).");
            System.out.println("Please install the missing tools manually.");
            return;
        }

        boolean javaFailed = results.stream().anyMatch(r -> r.tool().equals("Java") && r.status() == Status.FAIL);
        boolean jarFailed = results.stream().anyMatch(r -> r.tool().equals("jar") && r.status() == Status.FAIL);
        boolean mavenFailed = results.stream().anyMatch(r -> r.tool().equals("Maven") && r.status() == Status.FAIL);
        boolean gitFailed = results.stream().anyMatch(r -> r.tool().equals("Git") && r.status() == Status.FAIL);
        boolean postgresFailed = results.stream().anyMatch(r -> r.tool().equals("PostgreSQL") && r.status() == Status.FAIL);

        // Docker (optional - only with --fix-optional)
        CheckResult dockerResult = results.stream().filter(r -> r.tool().equals("Docker")).findFirst().orElse(null);
        boolean dockerNotInstalled = fixOptional && dockerResult != null && dockerResult.status() != Status.OK
                && dockerResult.message() != null && !dockerResult.message().contains("daemon is not running");
        boolean dockerDaemonStopped = fixOptional && dockerResult != null && dockerResult.message() != null
                && dockerResult.message().contains("daemon is not running");

        List<String> packagesToInstall = new ArrayList<>();

        // Package names vary by distribution
        switch (pkgManager) {
            case "apt" -> {
                if (javaFailed || jarFailed) packagesToInstall.add("openjdk-21-jdk");
                if (mavenFailed) packagesToInstall.add("maven");
                if (gitFailed) packagesToInstall.add("git");
                if (postgresFailed) packagesToInstall.add("postgresql-client-16");
                if (dockerNotInstalled) packagesToInstall.add("docker.io");
            }
            case "dnf", "yum" -> {
                if (javaFailed || jarFailed) packagesToInstall.add("java-21-openjdk-devel");
                if (mavenFailed) packagesToInstall.add("maven");
                if (gitFailed) packagesToInstall.add("git");
                if (postgresFailed) packagesToInstall.add("postgresql16");
                if (dockerNotInstalled) packagesToInstall.add("docker");
            }
            case "pacman" -> {
                if (javaFailed || jarFailed) packagesToInstall.add("jdk-openjdk");
                if (mavenFailed) packagesToInstall.add("maven");
                if (gitFailed) packagesToInstall.add("git");
                if (postgresFailed) packagesToInstall.add("postgresql-libs");
                if (dockerNotInstalled) packagesToInstall.add("docker");
            }
            case "zypper" -> {
                if (javaFailed || jarFailed) packagesToInstall.add("java-21-openjdk-devel");
                if (mavenFailed) packagesToInstall.add("maven");
                if (gitFailed) packagesToInstall.add("git");
                if (postgresFailed) packagesToInstall.add("postgresql16");
                if (dockerNotInstalled) packagesToInstall.add("docker");
            }
        }

        if (packagesToInstall.isEmpty() && !dockerDaemonStopped) {
            System.out.println();
            System.out.println("Nothing to fix!");
            return;
        }

        System.out.println();
        System.out.println("Installing missing packages with " + pkgManager + "...");
        System.out.println();

        boolean isRoot = isRunningAsRoot();

        // For apt, run update first (needed in Docker containers)
        if ("apt".equals(pkgManager)) {
            System.out.println("Updating package lists...");
            List<String> updateCmd = new ArrayList<>();
            if (!isRoot) {
                updateCmd.add("sudo");
            }
            updateCmd.add("apt-get");
            updateCmd.add("update");
            processRunner.runLive(updateCmd.toArray(new String[0]));
            System.out.println();
        }

        List<String> command = new ArrayList<>();
        // Skip sudo if already running as root
        if (!isRoot) {
            command.add("sudo");
        }
        command.add(pkgManager);

        // Add install subcommand and flags
        switch (pkgManager) {
            case "apt" -> { command.add("install"); command.add("-y"); }
            case "dnf", "yum" -> { command.add("install"); command.add("-y"); }
            case "pacman" -> { command.add("-S"); command.add("--noconfirm"); }
            case "zypper" -> { command.add("install"); command.add("-y"); }
        }

        command.addAll(packagesToInstall);

        int exitCode = processRunner.runLive(command.toArray(new String[0]));

        if (exitCode == 0) {
            System.out.println();
            System.out.println("Installation complete. Run 'idempiere-cli doctor' to verify.");
        } else {
            System.out.println();
            System.out.println("Some packages may have failed to install. Check output above.");
        }

        // Start Docker daemon if it was installed or just stopped
        if (dockerNotInstalled || dockerDaemonStopped) {
            System.out.println();
            System.out.println("Starting Docker daemon...");
            List<String> startCmd = new ArrayList<>();
            if (!isRunningAsRoot()) startCmd.add("sudo");
            startCmd.add("systemctl");
            startCmd.add("start");
            startCmd.add("docker");
            processRunner.runLive(startCmd.toArray(new String[0]));
        }
    }

    private void runAutoFixMac(List<CheckResult> results, boolean fixOptional) {
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

        // Docker (optional - only with --fix-optional)
        CheckResult dockerResult = results.stream().filter(r -> r.tool().equals("Docker")).findFirst().orElse(null);
        boolean dockerNotInstalled = fixOptional && dockerResult != null && dockerResult.status() != Status.OK
                && dockerResult.message() != null && !dockerResult.message().contains("daemon is not running");
        boolean dockerDaemonStopped = fixOptional && dockerResult != null && dockerResult.message() != null
                && dockerResult.message().contains("daemon is not running");

        if (javaFailed || jarFailed) packagesToInstall.add("openjdk@21");
        if (mavenFailed) packagesToInstall.add("maven");
        if (gitFailed) packagesToInstall.add("git");
        if (postgresFailed) packagesToInstall.add("postgresql@16");
        if (greadlinkFailed) packagesToInstall.add("coreutils");

        // Docker Desktop is installed as a cask (separate from regular packages)
        List<String> casksToInstall = new ArrayList<>();
        if (dockerNotInstalled) casksToInstall.add("docker");

        if (packagesToInstall.isEmpty() && casksToInstall.isEmpty() && !dockerDaemonStopped) {
            System.out.println();
            System.out.println("Nothing to fix!");
            return;
        }

        int exitCode = 0;

        if (!packagesToInstall.isEmpty()) {
            System.out.println();
            System.out.println("Installing missing packages with Homebrew...");
            System.out.println();

            List<String> command = new ArrayList<>();
            command.add("brew");
            command.add("install");
            command.addAll(packagesToInstall);

            exitCode = processRunner.runLive(command.toArray(new String[0]));
        }

        if (!casksToInstall.isEmpty()) {
            System.out.println();
            System.out.println("Installing Docker Desktop with Homebrew...");
            System.out.println();

            int caskExit = processRunner.runLive("brew", "install", "--cask", "docker");
            if (caskExit != 0) exitCode = caskExit;
        }

        if (dockerDaemonStopped) {
            System.out.println();
            System.out.println("Starting Docker Desktop...");
            processRunner.runLive("open", "-a", "Docker");
            System.out.println("  Docker Desktop is starting. It may need a moment to be ready.");
        }

        if (exitCode == 0) {
            System.out.println();
            System.out.println("Installation complete. Run 'idempiere-cli doctor' to verify.");
        } else {
            System.out.println();
            System.out.println("Some packages may have failed to install. Check output above.");
        }
    }

    /**
     * Check if running as root (UID 0) on Unix-like systems.
     * Used to skip sudo when already running as root (e.g., in Docker containers).
     */
    private boolean isRunningAsRoot() {
        // Check USER environment variable first (works in most shells)
        String user = System.getenv("USER");
        if ("root".equals(user)) {
            return true;
        }

        // Check UID via id command as fallback
        ProcessRunner.RunResult result = processRunner.run("id", "-u");
        if (result.exitCode() == 0) {
            return "0".equals(result.output().trim());
        }

        return false;
    }
}
