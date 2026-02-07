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
        assertTrue(output.contains("--skip-build"));
        assertTrue(output.contains("--skip-db"));
        assertTrue(output.contains("--skip-workspace"));
        assertTrue(output.contains("--include-rest"));
        assertTrue(output.contains("--non-interactive"));
        // Oracle Docker options
        assertTrue(output.contains("--oracle-docker-container"));
        assertTrue(output.contains("--oracle-docker-image"));
        assertTrue(output.contains("--oracle-docker-home"));
        assertTrue(output.contains("--dry-run"));
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
    @Launch({"setup-dev-env", "--db=oracle", "--with-docker", "--docker-postgres-name=custom-pg", "--dry-run"})
    void testOracleWithDockerWarnsAboutPostgresOptions(LaunchResult result) {
        // Oracle + Docker is now supported, but PostgreSQL options should be warned
        String output = result.getErrorOutput();
        assertTrue(output.contains("--docker-postgres-name is ignored"));
    }

    @Test
    @Launch({"setup-dev-env", "--db=postgresql", "--with-docker", "--oracle-docker-container=custom-oracle", "--dry-run"})
    void testPostgresWithDockerWarnsAboutOracleOptions(LaunchResult result) {
        // PostgreSQL + Docker should warn about Oracle options
        String output = result.getErrorOutput();
        assertTrue(output.contains("--oracle-docker-container is ignored"));
    }

    @Test
    @Launch({"setup-dev-env", "--skip-db", "--with-docker", "--dry-run"})
    void testSkipDbWarnsAboutDockerOption(LaunchResult result) {
        // Should warn but not fail validation
        String output = result.getErrorOutput();
        assertTrue(output.contains("--with-docker is ignored when --skip-db is set"));
    }

    @Test
    @Launch({"setup-dev-env", "--skip-workspace", "--install-copilot", "--skip-db", "--dry-run"})
    void testSkipWorkspaceWarnsAboutCopilot(LaunchResult result) {
        String output = result.getErrorOutput();
        assertTrue(output.contains("--install-copilot is ignored when --skip-workspace is set"));
    }
}
