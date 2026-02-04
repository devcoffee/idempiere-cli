package org.idempiere.cli.commands;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusMainTest
class SetupDevEnvCommandTest {

    @Test
    @Launch({"setup-dev-env", "--help"})
    void testSetupDevEnvHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("--db"));
        assertTrue(output.contains("--with-docker"));
        assertTrue(output.contains("--branch"));
        assertTrue(output.contains("--source-dir"));
        assertTrue(output.contains("--eclipse-dir"));
        assertTrue(output.contains("--skip-db"));
        assertTrue(output.contains("--skip-workspace"));
        assertTrue(output.contains("--include-rest"));
        assertTrue(output.contains("--non-interactive"));
    }

    @Test
    @Launch({"--help"})
    void testMainHelpIncludesSetupDevEnv(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("setup-dev-env"));
        assertTrue(output.contains("doctor"));
        assertTrue(output.contains("init"));
        assertTrue(output.contains("add"));
    }

    @Test
    @Launch(value = {"setup-dev-env", "--db=oracle", "--with-docker", "--non-interactive"}, exitCode = 1)
    void testOracleWithDockerFails(LaunchResult result) {
        String output = result.getErrorOutput();
        assertTrue(output.contains("--db=oracle is not compatible with --with-docker"));
    }

    @Test
    @Launch({"setup-dev-env", "--skip-db", "--with-docker", "--skip-workspace", "--non-interactive"})
    void testSkipDbWarnsAboutDockerOption(LaunchResult result) {
        // Should warn but not fail validation (will fail later due to headless/missing source)
        String output = result.getErrorOutput();
        assertTrue(output.contains("--with-docker is ignored when --skip-db is set"));
    }

    @Test
    @Launch({"setup-dev-env", "--skip-workspace", "--install-copilot", "--skip-db", "--non-interactive"})
    void testSkipWorkspaceWarnsAboutCopilot(LaunchResult result) {
        String output = result.getErrorOutput();
        assertTrue(output.contains("--install-copilot is ignored when --skip-workspace is set"));
    }
}
