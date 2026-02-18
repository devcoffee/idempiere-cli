package org.idempiere.cli.commands;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusMainTest
class CommandExitCodeContractTest {

    @Test
    @Launch(value = {"package"}, exitCode = 3)
    void testPackageReturnsStateErrorOutsidePlugin(LaunchResult result) {
        assertEquals(3, result.exitCode());
        assertTrue(result.getErrorOutput().contains("Not an iDempiere plugin"));
    }

    @Test
    @Launch(value = {"diff-schema", "--table=C_Order"}, exitCode = 3)
    void testDiffSchemaReturnsStateErrorOutsidePlugin(LaunchResult result) {
        assertEquals(3, result.exitCode());
        assertTrue(result.getErrorOutput().contains("Not an iDempiere plugin"));
    }

    @Test
    @Launch(value = {"migrate", "--from=12", "--to=13"}, exitCode = 3)
    void testMigrateReturnsStateErrorOutsidePlugin(LaunchResult result) {
        assertEquals(3, result.exitCode());
        assertTrue(result.getErrorOutput().contains("Not an iDempiere plugin"));
    }

    @Test
    @Launch(value = {
            "import-workspace",
            "--dir=/nonexistent/plugin",
            "--eclipse-dir=/tmp",
            "--workspace=/tmp"
    }, exitCode = 3)
    void testImportWorkspaceReturnsStateErrorForMissingPluginDir(LaunchResult result) {
        assertEquals(3, result.exitCode());
        assertTrue(result.getErrorOutput().contains("Plugin directory does not exist"));
    }
}
