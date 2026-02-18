package org.idempiere.cli.commands;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for DeployCommand.
 */
@QuarkusMainTest
class DeployCommandTest {

    @Test
    @Launch({"deploy", "--help"})
    void testDeployHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("Deploy"));
        assertTrue(output.contains("--target"));
        assertTrue(output.contains("--hot"));
        assertTrue(output.contains("--osgi-host"));
        assertTrue(output.contains("--osgi-port"));
    }

    @Test
    @Launch(value = {"deploy", "--target=/tmp"}, exitCode = 3)
    void testDeployWithMissingPlugin(LaunchResult result) {
        assertEquals(3, result.exitCode());
        assertTrue(result.getErrorOutput().contains("Not an iDempiere plugin"));
    }
}
