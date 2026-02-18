package org.idempiere.cli.commands;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusMainTest
class DepsCommandTest {

    @Test
    @Launch({"deps", "--help"})
    void testDepsHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("--dir"));
        assertTrue(output.contains("--json"));
        assertTrue(output.contains("Analyze plugin dependencies"));
    }

    @Test
    @Launch({"--help"})
    void testDepsRegistered(LaunchResult result) {
        assertEquals(0, result.exitCode());
        assertTrue(result.getOutput().contains("deps"));
    }

    @Test
    @Launch(value = {"deps"}, exitCode = 3)
    void testDepsTextErrorOnStderr(LaunchResult result) {
        assertEquals(3, result.exitCode());
        assertTrue(result.getErrorOutput().contains("Not an iDempiere plugin"));
    }

    @Test
    @Launch(value = {"deps", "--json"}, exitCode = 3)
    void testDepsJsonErrorContract(LaunchResult result) {
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
