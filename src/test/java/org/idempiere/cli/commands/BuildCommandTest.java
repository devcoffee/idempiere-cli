package org.idempiere.cli.commands;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for BuildCommand.
 */
@QuarkusMainTest
class BuildCommandTest {

    @Test
    @Launch({"build", "--help"})
    void testBuildHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("Build"));
        assertTrue(output.contains("--clean"));
        assertTrue(output.contains("--idempiere-home"));
        assertTrue(output.contains("--skip-tests"));
    }

    @Test
    @Launch(value = {"build"}, exitCode = 1)
    void testBuildWithoutPlugin(LaunchResult result) {
        // Should fail/warn because we're not in a plugin directory
        // Maven not found is also acceptable
        assertEquals(1, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("Error") || output.contains("not") ||
                   output.contains("found") || output.contains("Maven") ||
                   output.isEmpty());
    }
}
