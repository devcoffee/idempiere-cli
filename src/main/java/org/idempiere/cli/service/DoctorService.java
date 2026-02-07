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

    /** Links a check result with its originating check for fix suggestions. */
    record CheckEntry(EnvironmentCheck check, CheckResult result) {}

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
        String icon = switch (result.status()) {
            case OK -> CHECK_MARK;
            case WARN -> WARN_MARK;
            case FAIL -> CROSS;
        };
        System.out.printf("  %s  %-15s %s%n", icon, result.tool(), result.message());
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

        // Collect packages by package manager from FixSuggestion
        List<String> sdkmanPackages = new ArrayList<>();
        List<String> brewPackages = new ArrayList<>();
        List<String> brewCasks = new ArrayList<>();
        List<String> aptPackages = new ArrayList<>();
        List<String> dnfPackages = new ArrayList<>();
        List<String> pacmanPackages = new ArrayList<>();
        List<String> wingetPackages = new ArrayList<>();
        List<String> manualUrls = new ArrayList<>();

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

        // SDKMAN! educational message for Java/Maven (non-Windows only)
        if (!sdkmanPackages.isEmpty() && !os.contains("win")) {
            System.out.println();
            String lightbulb = IS_WINDOWS ? "[TIP]" : "\uD83D\uDCA1";
            System.out.println("  " + lightbulb + " Recommended: SDKMAN!");
            System.out.println("     SDKMAN manages Java/Maven versions and can auto-switch per project.");
            System.out.println();
            System.out.println("     Install SDKMAN:");
            System.out.println("       curl -s \"https://get.sdkman.io\" | bash");
            System.out.println("       source ~/.sdkman/bin/sdkman-init.sh");
            System.out.println();
            System.out.println("     Then install tools:");
            for (String pkg : sdkmanPackages) {
                System.out.println("       sdk install " + pkg);
            }
            System.out.println();
            System.out.println("     Enable auto-switching (recommended):");
            System.out.println("       echo \"sdkman_auto_env=true\" >> ~/.sdkman/etc/config");
            System.out.println();
            System.out.println("     With auto-switching, SDKMAN reads .sdkmanrc files in projects");
            System.out.println("     and automatically uses the correct Java/Maven version.");
            System.out.println();
            System.out.println("     Learn more: https://sdkman.io/usage");
        }

        if (os.contains("mac") && (!brewPackages.isEmpty() || !brewCasks.isEmpty())) {
            System.out.println();
            System.out.println("  Install with Homebrew:");
            if (!brewPackages.isEmpty()) {
                System.out.println("    brew install " + String.join(" ", brewPackages));
            }
            for (String cask : brewCasks) {
                System.out.println("    brew install --cask " + cask);
            }
            System.out.println();
            System.out.println("  Or run: idempiere-cli doctor --fix");
        } else if (os.contains("linux")) {
            if (!aptPackages.isEmpty()) {
                System.out.println();
                System.out.println("  Debian/Ubuntu:");
                System.out.println("    sudo apt install " + String.join(" ", aptPackages));
            }
            if (!dnfPackages.isEmpty()) {
                System.out.println();
                System.out.println("  Fedora/RHEL:");
                System.out.println("    sudo dnf install " + String.join(" ", dnfPackages));
            }
            if (!pacmanPackages.isEmpty()) {
                System.out.println();
                System.out.println("  Arch:");
                System.out.println("    sudo pacman -S " + String.join(" ", pacmanPackages));
            }
            System.out.println();
            System.out.println("  Or run: idempiere-cli doctor --fix");
        } else if (os.contains("win") && !wingetPackages.isEmpty()) {
            System.out.println();
            System.out.println("  Install with winget:");
            for (String pkg : wingetPackages) {
                System.out.println("    winget install " + pkg);
            }
            System.out.println();
            System.out.println("  Or run: idempiere-cli doctor --fix");
        }

        if (!manualUrls.isEmpty()) {
            System.out.println();
            System.out.println("  Manual install:");
            for (String url : manualUrls) {
                System.out.println("    " + url);
            }
        }

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

        // Collect packages to install from FixSuggestion
        List<String> brewPackages = new ArrayList<>();
        List<String> brewCasks = new ArrayList<>();
        List<String> aptPackages = new ArrayList<>();
        List<String> dnfPackages = new ArrayList<>();
        List<String> pacmanPackages = new ArrayList<>();
        List<String> wingetPackages = new ArrayList<>();

        for (CheckEntry entry : entries) {
            CheckResult result = entry.result();
            boolean shouldFix = result.isFail() || (fixOptional && result.isWarn());
            if (!shouldFix) continue;

            EnvironmentCheck.FixSuggestion fix = entry.check().getFixSuggestion(os);
            if (fix == null) continue;

            if (fix.brewPackage() != null) brewPackages.add(fix.brewPackage());
            if (fix.brewCask() != null) brewCasks.add(fix.brewCask());
            if (fix.aptPackage() != null) aptPackages.add(fix.aptPackage());
            if (fix.dnfPackage() != null) dnfPackages.add(fix.dnfPackage());
            if (fix.pacmanPackage() != null) pacmanPackages.add(fix.pacmanPackage());
            if (fix.wingetPackage() != null) wingetPackages.add(fix.wingetPackage());
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

    private void runAutoFixMac(List<String> brewPackages, List<String> brewCasks,
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
            exitCode = processRunner.runLive(command.toArray(new String[0]));
        }

        for (String cask : brewCasks) {
            System.out.println("Installing " + cask + " with Homebrew Cask...");
            int caskExit = processRunner.runLive("brew", "install", "--cask", cask);
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

    private void runAutoFixLinux(List<String> aptPackages, List<String> dnfPackages,
                                 List<String> pacmanPackages, List<CheckEntry> entries, boolean fixOptional) {
        String pkgManager = null;
        List<String> packages = null;

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
        int exitCode = processRunner.runLive(command.toArray(new String[0]));

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

    private void runAutoFixWindows(List<String> wingetPackages, List<CheckEntry> entries, boolean fixOptional) {
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
            processRunner.runLive("winget", "install", "--accept-package-agreements", pkg);
        }

        System.out.println();
        System.out.println("Installation complete. Restart your terminal and run 'idempiere-cli doctor' to verify.");
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
