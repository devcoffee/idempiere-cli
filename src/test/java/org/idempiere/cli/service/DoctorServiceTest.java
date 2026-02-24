package org.idempiere.cli.service;

import org.idempiere.cli.service.DoctorService.CheckEntry;
import org.idempiere.cli.service.check.CheckResult;
import org.idempiere.cli.service.check.EnvironmentCheck;
import org.idempiere.cli.service.check.PostgresCheck;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoctorServiceTest {

    private DoctorService doctorService;
    private StubProcessRunner processRunner;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private String originalOsName;

    @BeforeEach
    void setup() {
        originalOsName = System.getProperty("os.name");
        System.setOut(new PrintStream(outContent));
        processRunner = new StubProcessRunner();
        doctorService = new DoctorService();
        doctorService.processRunner = processRunner;
        doctorService.postgresCheck = new PostgresCheck();
    }

    @AfterEach
    void cleanup() {
        System.setOut(originalOut);
        System.setProperty("os.name", originalOsName);
    }

    @Test
    void testFixWithAllToolsAlreadyInstalled() {
        System.setProperty("os.name", "Linux");
        List<CheckEntry> entries = List.of(
                entry("Git", CheckResult.Status.OK, "git 2.43.0")
        );

        doctorService.runAutoFix(entries, null, null);

        String output = outContent.toString();
        assertFalse(output.contains("Installing packages"));
    }

    @Test
    void testFixLinuxWithoutSudo() {
        System.setProperty("os.name", "Linux");
        processRunner.availableCommands = Set.of("apt");

        List<CheckEntry> entries = List.of(
                failEntry("Git", "Not found",
                        EnvironmentCheck.FixSuggestion.builder().apt("git").build())
        );

        doctorService.runAutoFix(entries, null, null);

        String output = outContent.toString();
        assertTrue(output.contains("Root privileges required but 'sudo' not available"),
                "Should warn about missing sudo");
        assertTrue(output.contains("apt install -y git"),
                "Should print manual command");
    }

    @Test
    void testFixLinuxNoPackageManager() {
        System.setProperty("os.name", "Linux");
        processRunner.availableCommands = Set.of();

        List<CheckEntry> entries = List.of(
                failEntry("Git", "Not found",
                        EnvironmentCheck.FixSuggestion.builder().apt("git").build())
        );

        doctorService.runAutoFix(entries, null, null);

        String output = outContent.toString();
        assertTrue(output.contains("No packages to install or package manager not detected"),
                "Should report no package manager");
    }

    @Test
    void testFixLinuxNoPackageManagerStillHandlesDockerDaemon() {
        System.setProperty("os.name", "Linux");
        processRunner.availableCommands = Set.of();

        List<CheckEntry> entries = List.of(
                warnEntry("Docker", "Version 28.2.2, installed, but daemon is not running",
                        EnvironmentCheck.FixSuggestion.builder().apt("docker.io").build())
        );

        doctorService.runAutoFix(entries, Set.of("docker"), null);

        String output = outContent.toString();
        assertTrue(output.contains("No packages to install or package manager not detected"),
                "Should report no package manager");
        assertTrue(output.contains("Start Docker manually as root"),
                "Should still print docker daemon guidance");
    }

    @Test
    void testFixLinuxWithZypper() {
        System.setProperty("os.name", "Linux");
        processRunner.availableCommands = Set.of("zypper", "sudo");
        processRunner.runLiveExitCode = 0;

        List<CheckEntry> entries = List.of(
                failEntry("Git", "Not found",
                        EnvironmentCheck.FixSuggestion.builder().zypper("git").build())
        );

        doctorService.runAutoFix(entries, null, null);

        String output = outContent.toString();
        assertTrue(output.contains("Installing packages with zypper"),
                "Should use zypper when it's the only available manager");
    }

    @Test
    void testFixMacWithoutBrew() {
        System.setProperty("os.name", "Mac OS X");
        processRunner.availableCommands = Set.of();

        List<CheckEntry> entries = List.of(
                failEntry("Git", "Not found",
                        EnvironmentCheck.FixSuggestion.builder().brew("git").build())
        );

        doctorService.runAutoFix(entries, null, null);

        String output = outContent.toString();
        assertTrue(output.contains("Homebrew not found"),
                "Should report Homebrew not found");
    }

    @Test
    void testFixWindowsWithoutWinget() {
        System.setProperty("os.name", "Windows 10");
        processRunner.availableCommands = Set.of();

        List<CheckEntry> entries = List.of(
                failEntry("Git", "Not found",
                        EnvironmentCheck.FixSuggestion.builder().winget("Git.Git").build())
        );

        doctorService.runAutoFix(entries, null, null);

        String output = outContent.toString();
        assertTrue(output.contains("winget not found"),
                "Should report winget not found");
    }

    @Test
    void testFixWindowsWithoutWingetStillHandlesDockerDaemon() {
        System.setProperty("os.name", "Windows 10");
        processRunner.availableCommands = Set.of();

        List<CheckEntry> entries = List.of(
                warnEntry("Docker", "Version 28.2.2, installed, but daemon is not running",
                        EnvironmentCheck.FixSuggestion.builder().winget("Docker.DockerDesktop").build())
        );

        doctorService.runAutoFix(entries, Set.of("docker"), null);

        String output = outContent.toString();
        assertTrue(output.contains("winget not found"),
                "Should report winget missing");
        assertTrue(output.contains("Starting Docker Desktop"),
                "Should attempt to start Docker Desktop even without winget");
    }

    // --- Helpers ---

    private CheckEntry entry(String tool, CheckResult.Status status, String message) {
        return new CheckEntry(
                new StubEnvironmentCheck(tool, status == CheckResult.Status.FAIL, null),
                new CheckResult(tool, status, message)
        );
    }

    private CheckEntry failEntry(String tool, String message, EnvironmentCheck.FixSuggestion fix) {
        return new CheckEntry(
                new StubEnvironmentCheck(tool, true, fix),
                new CheckResult(tool, CheckResult.Status.FAIL, message)
        );
    }

    private CheckEntry warnEntry(String tool, String message, EnvironmentCheck.FixSuggestion fix) {
        return new CheckEntry(
                new StubEnvironmentCheck(tool, false, fix),
                new CheckResult(tool, CheckResult.Status.WARN, message)
        );
    }

    private static class StubEnvironmentCheck implements EnvironmentCheck {
        private final String name;
        private final boolean required;
        private final FixSuggestion fix;

        StubEnvironmentCheck(String name, boolean required, FixSuggestion fix) {
            this.name = name;
            this.required = required;
            this.fix = fix;
        }

        @Override
        public String toolName() { return name; }

        @Override
        public boolean isRequired() { return required; }

        @Override
        public CheckResult check() {
            return new CheckResult(name, CheckResult.Status.OK, "stubbed");
        }

        @Override
        public FixSuggestion getFixSuggestion(String os) { return fix; }
    }

    private static class StubProcessRunner extends ProcessRunner {
        Set<String> availableCommands = Set.of();
        int runLiveExitCode = 0;

        @Override
        public boolean isAvailable(String command) {
            return availableCommands.contains(command);
        }

        @Override
        public int runLiveNoTimeout(String... command) {
            return runLiveExitCode;
        }

        @Override
        public int runLive(String... command) {
            return runLiveExitCode;
        }

        @Override
        public RunResult run(String... command) {
            // For isRunningAsRoot() → id -u
            if (command.length >= 2 && "id".equals(command[0]) && "-u".equals(command[1])) {
                return new RunResult(0, "1000"); // not root
            }
            return new RunResult(0, "");
        }
    }
}
