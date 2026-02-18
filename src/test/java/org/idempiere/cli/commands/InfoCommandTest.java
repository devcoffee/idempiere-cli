package org.idempiere.cli.commands;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for InfoCommand.
 */
@QuarkusMainTest
class InfoCommandTest {

    @Test
    @Launch({"info", "--help"})
    void testInfoHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("plugin") || output.contains("metadata") || output.contains("info"));
        assertTrue(output.contains("--json"));
    }

    @Test
    @Launch(value = {"info"}, exitCode = 3)
    void testInfoWithoutPlugin(LaunchResult result) {
        assertEquals(3, result.exitCode());
        String errorOutput = result.getErrorOutput();
        assertTrue(errorOutput.contains("Error"));
        assertTrue(errorOutput.contains("Not an iDempiere plugin"));
    }

    @Test
    @Launch(value = {"info", "--json"}, exitCode = 3)
    void testInfoJsonErrorContract(LaunchResult result) {
        assertEquals(3, result.exitCode());
        String output = result.getOutput();
        String errorOutput = result.getErrorOutput();
        assertTrue(output.contains("\"error\""));
        assertTrue(output.contains("\"code\""));
        assertTrue(output.contains("\"message\""));
        assertTrue(output.contains("NOT_PLUGIN"));
        assertTrue(errorOutput.isBlank());
    }
}
