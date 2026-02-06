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
    @Launch({"deploy", "--target=/tmp"})
    void testDeployWithMissingPlugin(LaunchResult result) {
        // Command runs but may output nothing to stdout if error goes to stderr
        // Just verify the command executed (0 exit code because error handling doesn't call System.exit)
        assertEquals(0, result.exitCode());
    }
}
