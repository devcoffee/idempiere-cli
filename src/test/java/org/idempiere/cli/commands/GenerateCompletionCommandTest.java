package org.idempiere.cli.commands;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for GenerateCompletionCommand.
 */
@QuarkusMainTest
class GenerateCompletionCommandTest {

    @Test
    @Launch({"generate-completion"})
    void testGenerateCompletion(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        // Should generate bash completion script (AutoComplete.bash)
        assertTrue(output.contains("_idempiere") || output.contains("complete") || output.contains("COMPREPLY"));
    }

    @Test
    @Launch({"generate-completion", "-n", "mycli"})
    void testGenerateCompletionWithCustomName(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        // Should use the custom command name
        assertTrue(output.contains("mycli") || output.contains("_mycli"));
    }
}
