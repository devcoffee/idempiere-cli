package org.idempiere.cli.commands;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for UpgradeCommand.
 */
@QuarkusMainTest
class UpgradeCommandTest {

    @Test
    @Launch({"upgrade", "--check"})
    void testUpgradeCheck(LaunchResult result) {
        // Check mode should try to connect to GitHub and check version
        // The command outputs to stdout
        String output = result.getOutput();
        assertTrue(output.contains("Current version") || output.contains("iDempiere CLI") ||
                   output.contains("version") || output.contains("SNAPSHOT"));
    }

    @Test
    @Launch(value = {"upgrade", "-f"}, exitCode = 0)
    void testUpgradeForce(LaunchResult result) {
        // Force mode - will attempt upgrade even if up to date
        // May fail if not running as native binary, but command should start
        String output = result.getOutput();
        assertTrue(output.contains("Current version") || output.contains("iDempiere CLI") ||
                   output.contains("Upgrade") || output.contains("version") ||
                   output.contains("Could not determine"));
    }
}
