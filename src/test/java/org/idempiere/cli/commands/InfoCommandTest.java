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
    @Launch(value = {"info"}, exitCode = 1)
    void testInfoWithoutPlugin(LaunchResult result) {
        // Should report error because we're not in a plugin directory
        assertEquals(1, result.exitCode());
        // The output could be on stdout or stderr
        String output = result.getOutput();
        assertTrue(output.contains("Error") || output.contains("not") ||
                   output.contains("MANIFEST") || output.isEmpty());
    }

    @Test
    @Launch(value = {"info", "--json"}, exitCode = 1)
    void testInfoJsonErrorContract(LaunchResult result) {
        assertEquals(1, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("\"error\""));
        assertTrue(output.contains("\"code\""));
        assertTrue(output.contains("\"message\""));
        assertTrue(output.contains("NOT_PLUGIN"));
    }
}
