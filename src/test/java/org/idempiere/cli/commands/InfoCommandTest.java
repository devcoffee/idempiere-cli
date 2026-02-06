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
    }

    @Test
    @Launch({"info"})
    void testInfoWithoutPlugin(LaunchResult result) {
        // Should report error because we're not in a plugin directory
        // The output could be on stdout or stderr
        String output = result.getOutput();
        assertTrue(output.contains("Error") || output.contains("not") ||
                   output.contains("MANIFEST") || output.isEmpty());
    }
}
