package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.idempiere.cli.service.check.CheckResult;
import org.idempiere.cli.service.check.CheckResult.Status;
import org.idempiere.cli.service.check.EnvironmentCheck;
import org.idempiere.cli.service.check.PluginCheck;
import org.idempiere.cli.util.CliOutput;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    /** Links a check result with its originating check for fix suggestions. */
    record CheckEntry(EnvironmentCheck check, CheckResult result) {}

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

        List<CheckEntry> entries = new ArrayList<>();

        // Run all registered environment checks
        for (EnvironmentCheck check : environmentChecks) {
            if (!check.isApplicable()) {
                continue;  // Silently skip non-applicable checks
            }
            CheckResult result = check.check();
            printResult(result);
            entries.add(new CheckEntry(check, result));
        }

        System.out.println();
        System.out.println("----------------------------------");

        List<CheckResult> results = entries.stream().map(CheckEntry::result).toList();
        long passed = results.stream().filter(CheckResult::isOk).count();
        long warnings = results.stream().filter(CheckResult::isWarn).count();
        long failed = results.stream().filter(CheckResult::isFail).count();

        System.out.printf("Results: %d passed, %d warnings, %d failed%n", passed, warnings, failed);

        boolean dockerNotOk = results.stream().anyMatch(r -> r.tool().equals("Docker") && !r.isOk());
        boolean postgresOutdated = results.stream().anyMatch(r -> r.tool().equals("PostgreSQL") && r.isWarn());
        boolean hasFixableIssues = failed > 0 || (fixOptional && dockerNotOk);

        if (fix && hasFixableIssues) {
            runAutoFix(entries, fixOptional);
        } else if (failed > 0 || dockerNotOk || postgresOutdated) {
            printFixSuggestions(entries);
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
        String line = String.format("%-15s %s", result.tool(), result.message());
        String output = switch (result.status()) {
            case OK -> CliOutput.ok(line);
            case WARN -> CliOutput.warn(line);
            case FAIL -> CliOutput.fail(line);
        };
        System.out.println("  " + output);
    }

    private void printFixSuggestions(List<CheckEntry> entries) {
        String os = System.getProperty("os.name", "").toLowerCase();

        List<CheckEntry> failed = entries.stream()
                .filter(e -> e.result().isFail())
                .toList();

        List<CheckEntry> warned = entries.stream()
                .filter(e -> e.result().isWarn())
                .toList();

        if (failed.isEmpty() && warned.isEmpty()) return;

        System.out.println();
        System.out.println("Fix Suggestions");
        System.out.println("---------------");

        // Collect packages by package manager from FixSuggestion (use Set to avoid duplicates)
        Set<String> sdkmanPackages = new LinkedHashSet<>();
        Set<String> brewPackages = new LinkedHashSet<>();
        Set<String> brewCasks = new LinkedHashSet<>();
        Set<String> aptPackages = new LinkedHashSet<>();
        Set<String> dnfPackages = new LinkedHashSet<>();
        Set<String> pacmanPackages = new LinkedHashSet<>();
        Set<String> wingetPackages = new LinkedHashSet<>();
        Set<String> manualUrls = new LinkedHashSet<>();

        for (CheckEntry entry : failed) {
            EnvironmentCheck.FixSuggestion fix = entry.check().getFixSuggestion(os);
            if (fix == null) continue;

            if (fix.sdkmanPackage() != null) sdkmanPackages.add(fix.sdkmanPackage());
            if (fix.brewPackage() != null) brewPackages.add(fix.brewPackage());
            if (fix.brewCask() != null) brewCasks.add(fix.brewCask());
            if (fix.aptPackage() != null) aptPackages.add(fix.aptPackage());
            if (fix.dnfPackage() != null) dnfPackages.add(fix.dnfPackage());
            if (fix.pacmanPackage() != null) pacmanPackages.add(fix.pacmanPackage());
            if (fix.wingetPackage() != null) wingetPackages.add(fix.wingetPackage());
            if (fix.manualUrl() != null) manualUrls.add(entry.check().toolName() + ": " + fix.manualUrl());
        }

        // Show what --fix will do (SDKMAN for Java/Maven on non-Windows, system packages for the rest)
        if (!os.contains("win") && !sdkmanPackages.isEmpty()) {
            // Remove Java/Maven from system packages since SDKMAN handles them
            removeJavaMavenPackages(brewPackages);
            removeJavaMavenPackages(aptPackages);
            removeJavaMavenPackages(dnfPackages);
            removeJavaMavenPackages(pacmanPackages);

            System.out.println();
            System.out.println("  " + CliOutput.tip("--fix will install via SDKMAN (recommended for Java/Maven):"));
            for (String pkg : sdkmanPackages) {
                System.out.println("    sdk install " + pkg);
            }
        }

        // Show remaining system packages (excluding Java/Maven which SDKMAN handles)
        if (os.contains("mac") && (!brewPackages.isEmpty() || !brewCasks.isEmpty())) {
            System.out.println();
            System.out.println("  --fix will install via Homebrew:");
            if (!brewPackages.isEmpty()) {
                System.out.println("    brew install " + String.join(" ", brewPackages));
            }
            for (String cask : brewCasks) {
                System.out.println("    brew install --cask " + cask);
            }
        } else if (os.contains("linux") && !aptPackages.isEmpty()) {
            System.out.println();
            System.out.println("  --fix will install via apt:");
            System.out.println("    sudo apt install " + String.join(" ", aptPackages));
        } else if (os.contains("win") && !wingetPackages.isEmpty()) {
            System.out.println();
            System.out.println("  --fix will install via winget:");
            for (String pkg : wingetPackages) {
                System.out.println("    winget install " + pkg);
            }
        }

        System.out.println();
        System.out.println("  Run: idempiere-cli doctor --fix");

        // Handle Docker specifically (optional tool with special messages)
        CheckEntry dockerEntry = entries.stream()
                .filter(e -> e.result().tool().equals("Docker") && !e.result().isOk())
                .findFirst().orElse(null);

        if (dockerEntry != null) {
            System.out.println();
            boolean daemonNotRunning = dockerEntry.result().message() != null
                    && dockerEntry.result().message().contains("daemon is not running");

            if (daemonNotRunning) {
                System.out.println("Docker is installed but the daemon is not running.");
                if (os.contains("mac")) {
                    System.out.println("  Start Docker Desktop: open -a Docker");
                } else if (os.contains("linux")) {
                    System.out.println("  Start Docker: sudo systemctl start docker");
                } else if (os.contains("win")) {
                    System.out.println("  Start Docker Desktop from the Start menu");
                }
            } else {
                System.out.println("Optional: Docker (for containerized PostgreSQL)");
                System.out.println("  Run: idempiere-cli doctor --fix-optional");
            }
            System.out.println();
            System.out.println("  With Docker, use: idempiere-cli setup-dev-env --with-docker");
        }

        System.out.println();
    }

    /**
     * Check if a tool is installed but not in PATH based on the check result message.
     */
    private void runAutoFix(List<CheckEntry> entries, boolean fixOptional) {
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
            boolean shouldFix = result.isFail() || (fixOptional && result.isWarn());
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
                System.out.println("  sdk install java 21-tem");
            }
        }

        if (os.contains("mac")) {
            runAutoFixMac(brewPackages, brewCasks, entries, fixOptional);
        } else if (os.contains("linux") || os.contains("nix")) {
            runAutoFixLinux(aptPackages, dnfPackages, pacmanPackages, entries, fixOptional);
        } else if (os.contains("win")) {
            runAutoFixWindows(wingetPackages, entries, fixOptional);
        } else {
            System.out.println("Auto-fix is not supported on this platform.");
            System.out.println("Please install the missing tools manually.");
        }
    }

    private void runAutoFixMac(Set<String> brewPackages, Set<String> brewCasks,
                               List<CheckEntry> entries, boolean fixOptional) {
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
        if (fixOptional && dockerEntry != null && dockerEntry.result().message() != null
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
                                 Set<String> pacmanPackages, List<CheckEntry> entries, boolean fixOptional) {
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
        if (fixOptional && dockerEntry != null && !dockerEntry.result().isOk()) {
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

    private void runAutoFixWindows(Set<String> wingetPackages, List<CheckEntry> entries, boolean fixOptional) {
        if (!processRunner.isAvailable("winget")) {
            System.out.println("winget not found. Install from: https://aka.ms/getwinget");
            return;
        }

        if (wingetPackages.isEmpty()) {
            System.out.println("Nothing to install via winget.");
            return;
        }

        System.out.println("Installing packages with winget...");
        for (String pkg : wingetPackages) {
            System.out.println("  Installing " + pkg + "...");
            // Use no timeout for package installations which can take a long time
            processRunner.runLiveNoTimeout("winget", "install", "--accept-package-agreements", pkg);
        }

        System.out.println();
        System.out.println("Installation complete. Restart your terminal and run 'idempiere-cli doctor' to verify.");
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
            System.out.println("Note: Run 'source ~/.sdkman/bin/sdkman-init.sh' or restart your terminal.");
        }

        return allSuccess;
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
