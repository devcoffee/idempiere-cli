package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.idempiere.cli.service.check.CheckResult;
import org.idempiere.cli.service.check.EnvironmentCheck;
import org.idempiere.cli.service.check.PluginCheck;
import org.idempiere.cli.service.check.PostgresCheck;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates development environment and plugin structure.
 *
 * <p>Uses CDI-discovered {@link EnvironmentCheck} and {@link PluginCheck}
 * implementations for individual checks.
 */
@ApplicationScoped
public class DoctorService {

    /** Links a check result with its originating check for fix suggestions. */
    public record CheckEntry(EnvironmentCheck check, CheckResult result) {}

    /** Structured result of an environment check run. */
    public record EnvironmentResult(
            List<CheckEntry> entries,
            long passed,
            long warnings,
            long failed
    ) {}

    /** Structured result of a plugin check run. */
    public record PluginCheckResult(
            Path pluginDir,
            List<CheckResult> results,
            long passed,
            long warnings,
            long failed
    ) {}

    @Inject
    ProcessRunner processRunner;

    @Inject
    PostgresCheck postgresCheck;

    @Inject
    Instance<EnvironmentCheck> environmentChecks;

    @Inject
    Instance<PluginCheck> pluginChecks;

    /**
     * Runs environment checks and returns structured data without printing.
     */
    public EnvironmentResult checkEnvironmentData() {
        List<CheckEntry> entries = new ArrayList<>();

        for (EnvironmentCheck check : environmentChecks) {
            if (!check.isApplicable()) continue;
            CheckResult result = check.check();
            entries.add(new CheckEntry(check, result));
        }

        List<CheckResult> results = entries.stream().map(CheckEntry::result).toList();
        return new EnvironmentResult(
                entries,
                results.stream().filter(CheckResult::isOk).count(),
                results.stream().filter(CheckResult::isWarn).count(),
                results.stream().filter(CheckResult::isFail).count()
        );
    }

    /**
     * Runs plugin checks and returns structured data without printing.
     */
    public PluginCheckResult checkPluginData(Path pluginDir) {
        if (!Files.exists(pluginDir)) {
            return new PluginCheckResult(pluginDir, List.of(), 0, 0, 0);
        }

        List<CheckResult> results = new ArrayList<>();
        for (PluginCheck check : pluginChecks) {
            results.add(check.check(pluginDir));
        }

        return new PluginCheckResult(
                pluginDir,
                results,
                results.stream().filter(CheckResult::isOk).count(),
                results.stream().filter(CheckResult::isWarn).count(),
                results.stream().filter(CheckResult::isFail).count()
        );
    }

    /**
     * Runs automatic fix for failed environment checks.
     * Installs missing tools using the appropriate package manager.
     *
     * @param entries the check entries to evaluate
     * @param optionalFilter set of lowercase tool names to fix as optional, or null to skip optional fixes
     * @param javaVersion the Java version identifier (SDKMAN id on macOS/Linux, winget id on Windows)
     */
    public void runAutoFix(List<CheckEntry> entries, Set<String> optionalFilter, String javaVersion) {
        String os = System.getProperty("os.name", "").toLowerCase();

        System.out.println();
        System.out.println("Attempting automatic fix...");
        System.out.println();

        // Collect packages to install from FixSuggestion (use Set to avoid duplicates)
        Set<String> sdkmanPackages = new LinkedHashSet<>();
        Set<String> brewPackages = new LinkedHashSet<>();
        Set<String> brewCasks = new LinkedHashSet<>();
        Set<String> aptPackages = new LinkedHashSet<>();
        Set<String> dnfPackages = new LinkedHashSet<>();
        Set<String> pacmanPackages = new LinkedHashSet<>();
        Set<String> wingetPackages = new LinkedHashSet<>();

        for (CheckEntry entry : entries) {
            CheckResult result = entry.result();
            boolean shouldFix = result.isFail()
                    || (optionalFilter != null && result.isWarn()
                        && optionalFilter.contains(result.tool().toLowerCase()));
            if (!shouldFix) continue;

            EnvironmentCheck.FixSuggestion fix = entry.check().getFixSuggestion(os);
            if (fix == null) continue;

            if (fix.sdkmanPackage() != null) sdkmanPackages.add(fix.sdkmanPackage());
            if (fix.brewPackage() != null) brewPackages.add(fix.brewPackage());
            if (fix.brewCask() != null) brewCasks.add(fix.brewCask());
            if (fix.aptPackage() != null) aptPackages.add(fix.aptPackage());
            if (fix.dnfPackage() != null) dnfPackages.add(fix.dnfPackage());
            if (fix.pacmanPackage() != null) pacmanPackages.add(fix.pacmanPackage());
            if (fix.wingetPackage() != null) wingetPackages.add(fix.wingetPackage());
        }

        // Override default Java version with user-specified --java value
        if (javaVersion != null) {
            if (sdkmanPackages.removeIf(pkg -> pkg.startsWith("java "))) {
                sdkmanPackages.add("java " + javaVersion);
            }
            if (wingetPackages.removeIf(pkg -> pkg.contains("Temurin") && pkg.contains("JDK"))) {
                wingetPackages.add(javaVersion);
            }
        }

        // Use SDKMAN for Java/Maven on non-Windows systems (no fallback to system packages)
        if (!os.contains("win") && !sdkmanPackages.isEmpty()) {
            // Always remove Java/Maven from system packages - SDKMAN is the only way
            removeJavaMavenPackages(brewPackages);
            removeJavaMavenPackages(aptPackages);
            removeJavaMavenPackages(dnfPackages);
            removeJavaMavenPackages(pacmanPackages);

            if (!runAutoFixSdkman(sdkmanPackages)) {
                System.out.println();
                System.out.println("ERROR: Failed to install Java/Maven via SDKMAN.");
                System.out.println("Please install SDKMAN manually:");
                System.out.println("  curl -s \"https://get.sdkman.io\" | bash");
                System.out.println("  source ~/.sdkman/bin/sdkman-init.sh");
                String javaId = javaVersion != null ? javaVersion : "21-tem";
                System.out.println("  sdk install java " + javaId);
            }
        }

        // Report tools that were requested but have no auto-install package for this OS
        reportManualInstalls(entries, optionalFilter, os);

        boolean fixDocker = optionalFilter != null && optionalFilter.contains("docker");
        if (os.contains("mac")) {
            runAutoFixMac(brewPackages, brewCasks, entries, fixDocker);
        } else if (os.contains("linux") || os.contains("nix")) {
            runAutoFixLinux(aptPackages, dnfPackages, pacmanPackages, entries, fixDocker);
        } else if (os.contains("win")) {
            runAutoFixWindows(wingetPackages, entries, fixDocker);
        } else {
            System.out.println("Auto-fix is not supported on this platform.");
            System.out.println("Please install the missing tools manually.");
        }
    }

    private void runAutoFixMac(Set<String> brewPackages, Set<String> brewCasks,
                               List<CheckEntry> entries, boolean fixDocker) {
        if (!processRunner.isAvailable("brew")) {
            System.out.println("Homebrew not found. Install it first:");
            System.out.println("  /bin/bash -c \"$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\"");
            return;
        }

        int exitCode = 0;

        if (!brewPackages.isEmpty()) {
            System.out.println("Installing packages with Homebrew...");
            List<String> command = new ArrayList<>();
            command.add("brew");
            command.add("install");
            command.addAll(brewPackages);
            // Use no timeout for package installations which can take a long time
            exitCode = processRunner.runLiveNoTimeout(command.toArray(new String[0]));
        }

        for (String cask : brewCasks) {
            System.out.println("Installing " + cask + " with Homebrew Cask...");
            // Use no timeout for package installations which can take a long time
            int caskExit = processRunner.runLiveNoTimeout("brew", "install", "--cask", cask);
            if (caskExit != 0) exitCode = caskExit;
        }

        // Handle Docker daemon if stopped
        CheckEntry dockerEntry = entries.stream()
                .filter(e -> e.result().tool().equals("Docker"))
                .findFirst().orElse(null);
        if (fixDocker && dockerEntry != null && dockerEntry.result().message() != null
                && dockerEntry.result().message().contains("daemon is not running")) {
            System.out.println("Starting Docker Desktop...");
            processRunner.runLive("open", "-a", "Docker");
        }

        System.out.println();
        if (exitCode == 0) {
            System.out.println("Installation complete. Run 'idempiere-cli doctor' to verify.");
        } else {
            System.out.println("Some packages may have failed. Check output above.");
        }
    }

    private void runAutoFixLinux(Set<String> aptPackages, Set<String> dnfPackages,
                                 Set<String> pacmanPackages, List<CheckEntry> entries, boolean fixDocker) {
        String pkgManager = null;
        Set<String> packages = null;

        if (processRunner.isAvailable("apt") && !aptPackages.isEmpty()) {
            pkgManager = "apt";
            packages = aptPackages;
        } else if (processRunner.isAvailable("dnf") && !dnfPackages.isEmpty()) {
            pkgManager = "dnf";
            packages = dnfPackages;
        } else if (processRunner.isAvailable("pacman") && !pacmanPackages.isEmpty()) {
            pkgManager = "pacman";
            packages = pacmanPackages;
        }

        if (pkgManager == null || packages == null || packages.isEmpty()) {
            System.out.println("No packages to install or package manager not detected.");
            return;
        }

        boolean isRoot = isRunningAsRoot();
        List<String> command = new ArrayList<>();

        if (!isRoot) command.add("sudo");
        command.add(pkgManager);

        switch (pkgManager) {
            case "apt" -> { command.add("install"); command.add("-y"); }
            case "dnf" -> { command.add("install"); command.add("-y"); }
            case "pacman" -> { command.add("-S"); command.add("--noconfirm"); }
        }
        command.addAll(packages);

        System.out.println("Installing packages with " + pkgManager + "...");
        // Use no timeout for package installations which can take a long time
        int exitCode = processRunner.runLiveNoTimeout(command.toArray(new String[0]));

        // Handle Docker daemon
        CheckEntry dockerEntry = entries.stream()
                .filter(e -> e.result().tool().equals("Docker"))
                .findFirst().orElse(null);
        if (fixDocker && dockerEntry != null && !dockerEntry.result().isOk()) {
            System.out.println("Starting Docker daemon...");
            List<String> startCmd = new ArrayList<>();
            if (!isRoot) startCmd.add("sudo");
            startCmd.add("systemctl");
            startCmd.add("start");
            startCmd.add("docker");
            processRunner.runLive(startCmd.toArray(new String[0]));
        }

        System.out.println();
        if (exitCode == 0) {
            System.out.println("Installation complete. Run 'idempiere-cli doctor' to verify.");
        } else {
            System.out.println("Some packages may have failed. Check output above.");
        }
    }

    private void runAutoFixWindows(Set<String> wingetPackages, List<CheckEntry> entries, boolean fixDocker) {
        if (!wingetPackages.isEmpty()) {
            if (!processRunner.isAvailable("winget")) {
                System.out.println("winget not found. Install from: https://aka.ms/getwinget");
                return;
            }

            System.out.println("Installing packages with winget...");
            List<String> failedPackages = new ArrayList<>();
            for (String pkg : wingetPackages) {
                System.out.println("  Installing " + pkg + "...");
                // Use no timeout for package installations which can take a long time
                int pkgExitCode = installWithWinget(pkg);
                if (pkgExitCode != 0) {
                    failedPackages.add(pkg);
                }
            }

            // Check if psql was installed but not in PATH, and fix it
            addPsqlToPathIfNeeded();

            System.out.println();
            if (failedPackages.isEmpty()) {
                System.out.println("Installation complete. Restart your terminal and run 'idempiere-cli doctor' to verify.");
            } else {
                System.out.println("Some packages failed to install: " + String.join(", ", failedPackages));
                System.out.println("Check output above and install manually if needed.");
            }
        }
    }

    private int installWithWinget(String pkg) {
        List<String> command = new ArrayList<>();
        command.add("winget");
        command.add("install");
        command.add("--accept-package-agreements");
        command.add("--accept-source-agreements");
        command.add("--source");
        command.add("winget");

        if (pkg.contains(".")) {
            command.add("--id");
            command.add(pkg);
            command.add("--exact");
        } else {
            // Keep compatibility for search terms (e.g. custom --java value on Windows).
            command.add(pkg);
        }

        return processRunner.runLiveNoTimeout(command.toArray(new String[0]));
    }

    /**
     * On Windows, check if psql.exe exists at a known location but is not in PATH.
     * If so, add the bin directory to the user PATH via PowerShell.
     */
    private void addPsqlToPathIfNeeded() {
        // Skip if psql is already in PATH
        if (processRunner.isAvailable("psql")) {
            return;
        }

        String psqlPath = postgresCheck.findPsqlOnWindows();
        if (psqlPath == null) {
            return;
        }

        String binDir = Path.of(psqlPath).getParent().toString();
        System.out.println("  Adding " + binDir + " to user PATH...");

        // Use PowerShell to append to user PATH only (avoids setx 1024-char limit)
        String psCommand = String.format(
                "$p = [Environment]::GetEnvironmentVariable('Path','User');" +
                "if ($p -notlike '*%s*') {" +
                "[Environment]::SetEnvironmentVariable('Path', $p + ';%s', 'User');" +
                "Write-Host 'Added to PATH'" +
                "} else { Write-Host 'Already in PATH' }",
                binDir.replace("\\", "\\\\"), binDir);

        processRunner.runLive("powershell", "-NoProfile", "-Command", psCommand);
    }

    /**
     * Install packages using SDKMAN.
     * @return true if SDKMAN was used successfully, false if SDKMAN is not available
     */
    private boolean runAutoFixSdkman(Set<String> sdkmanPackages) {
        if (sdkmanPackages.isEmpty()) {
            return false;
        }

        Path sdkmanDir = getSdkmanDir();
        Path sdkmanInit = sdkmanDir.resolve("bin/sdkman-init.sh");

        if (!Files.exists(sdkmanInit)) {
            System.out.println("SDKMAN not found. Installing SDKMAN first...");
            System.out.println();

            // Install prerequisites (zip/unzip) if missing
            if (!installSdkmanPrerequisites()) {
                System.out.println("Failed to install SDKMAN prerequisites (zip, unzip, curl).");
                return false;
            }

            // Install SDKMAN (no timeout for network downloads)
            int exitCode = processRunner.runLiveNoTimeout("bash", "-c",
                    "curl -s \"https://get.sdkman.io\" | bash");

            if (exitCode != 0) {
                System.out.println("Failed to install SDKMAN.");
                return false;
            }

            // Verify installation
            if (!Files.exists(sdkmanInit)) {
                System.out.println("SDKMAN installation completed but init script not found.");
                return false;
            }

            System.out.println();
            System.out.println("SDKMAN installed successfully!");
            System.out.println();
        }

        System.out.println("Installing packages with SDKMAN...");

        boolean allSuccess = true;
        for (String pkg : sdkmanPackages) {
            System.out.println("  Installing " + pkg + "...");

            // Run sdk install within a bash shell that sources SDKMAN (no timeout for downloads)
            String command = String.format(
                    "source \"%s\" && sdk install %s <<< 'Y'",
                    sdkmanInit, pkg);

            int exitCode = processRunner.runLiveNoTimeout("bash", "-c", command);
            if (exitCode != 0) {
                System.out.println("  Warning: Failed to install " + pkg);
                allSuccess = false;
            }
        }

        if (allSuccess) {
            System.out.println();
            System.out.println("SDKMAN packages installed successfully!");
            System.out.println();

            // Verify installed tools by running them through SDKMAN-sourced bash
            verifySdkmanInstalls(sdkmanInit, sdkmanPackages);

            System.out.println();
            System.out.println("Note: Run 'source ~/.sdkman/bin/sdkman-init.sh' or restart your terminal.");
        }

        return allSuccess;
    }

    /**
     * Verify SDKMAN-installed tools by running version commands through a SDKMAN-sourced bash shell.
     * This provides immediate feedback that the tools were installed correctly.
     */
    private void verifySdkmanInstalls(Path sdkmanInit, Set<String> packages) {
        System.out.println("  Verifying installations:");
        for (String pkg : packages) {
            // Extract tool name: "java 21-tem" -> "java", "maven" -> "mvn"
            String tool = pkg.split("\\s+")[0];
            String versionCmd = tool.equals("maven") ? "mvn -version" : tool + " -version";

            String command = String.format(
                    "source \"%s\" && %s 2>&1 | head -1",
                    sdkmanInit, versionCmd);

            ProcessRunner.RunResult result = processRunner.run("bash", "-c", command);
            if (result.isSuccess() && result.output() != null && !result.output().isBlank()) {
                System.out.println("    " + result.output().trim());
            }
        }
    }

    /**
     * Install SDKMAN prerequisites (zip, unzip, curl).
     * @return true if prerequisites are available or were installed successfully
     */
    private boolean installSdkmanPrerequisites() {
        boolean hasZip = processRunner.isAvailable("zip");
        boolean hasUnzip = processRunner.isAvailable("unzip");
        boolean hasCurl = processRunner.isAvailable("curl");

        if (hasZip && hasUnzip && hasCurl) {
            return true;
        }

        List<String> missing = new ArrayList<>();
        if (!hasZip) missing.add("zip");
        if (!hasUnzip) missing.add("unzip");
        if (!hasCurl) missing.add("curl");

        System.out.println("Installing SDKMAN prerequisites: " + String.join(", ", missing));

        boolean isRoot = isRunningAsRoot();
        List<String> command = new ArrayList<>();

        if (processRunner.isAvailable("apt")) {
            if (!isRoot) command.add("sudo");
            command.add("apt");
            command.add("install");
            command.add("-y");
            command.addAll(missing);
        } else if (processRunner.isAvailable("dnf")) {
            if (!isRoot) command.add("sudo");
            command.add("dnf");
            command.add("install");
            command.add("-y");
            command.addAll(missing);
        } else if (processRunner.isAvailable("pacman")) {
            if (!isRoot) command.add("sudo");
            command.add("pacman");
            command.add("-S");
            command.add("--noconfirm");
            command.addAll(missing);
        } else if (processRunner.isAvailable("brew")) {
            command.add("brew");
            command.add("install");
            command.addAll(missing);
        } else {
            System.out.println("No package manager found to install prerequisites.");
            return false;
        }

        // Use no timeout for package installations which can take a long time
        int exitCode = processRunner.runLiveNoTimeout(command.toArray(new String[0]));
        return exitCode == 0;
    }

    /**
     * Get the SDKMAN directory path.
     */
    private Path getSdkmanDir() {
        String sdkmanDir = System.getenv("SDKMAN_DIR");
        if (sdkmanDir != null && !sdkmanDir.isEmpty()) {
            return Path.of(sdkmanDir);
        }
        return Path.of(System.getProperty("user.home"), ".sdkman");
    }

    /**
     * Reports tools that were requested for auto-fix but have no installable package on the current OS.
     */
    private void reportManualInstalls(List<CheckEntry> entries, Set<String> optionalFilter, String os) {
        for (CheckEntry entry : entries) {
            CheckResult result = entry.result();
            boolean wasRequested = result.isFail()
                    || (optionalFilter != null && result.isWarn()
                        && optionalFilter.contains(result.tool().toLowerCase()));
            if (!wasRequested) continue;

            EnvironmentCheck.FixSuggestion fix = entry.check().getFixSuggestion(os);
            if (fix == null) continue;

            boolean canAutoInstall;
            if (os.contains("win")) {
                canAutoInstall = fix.wingetPackage() != null;
            } else if (os.contains("mac")) {
                canAutoInstall = fix.brewPackage() != null || fix.brewCask() != null
                        || fix.sdkmanPackage() != null;
            } else {
                canAutoInstall = fix.aptPackage() != null || fix.dnfPackage() != null
                        || fix.pacmanPackage() != null || fix.sdkmanPackage() != null;
            }

            if (!canAutoInstall) {
                System.out.println("  " + result.tool() + ": No auto-install available on this platform.");
                if (fix.manualUrl() != null) {
                    System.out.println("    Install manually: " + fix.manualUrl());
                }
                System.out.println();
            }
        }
    }

    /**
     * Remove Java and Maven related packages from the set.
     * These are handled by SDKMAN instead.
     */
    private void removeJavaMavenPackages(Set<String> packages) {
        packages.removeIf(pkg ->
                pkg.contains("java") ||
                pkg.contains("jdk") ||
                pkg.contains("openjdk") ||
                pkg.contains("maven") ||
                pkg.contains("temurin"));
    }

    /**
     * Check if running as root (UID 0) on Unix-like systems.
     */
    private boolean isRunningAsRoot() {
        String user = System.getenv("USER");
        if ("root".equals(user)) {
            return true;
        }
        ProcessRunner.RunResult result = processRunner.run("id", "-u");
        return result.exitCode() == 0 && "0".equals(result.output().trim());
    }
}
