package org.idempiere.cli.commands;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusMainTest
class ValidateCommandTest {

    @Test
    @Launch({"validate", "--help"})
    void testValidateHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        assertTrue(result.getOutput().contains("Validate plugin structure"));
        assertTrue(result.getOutput().contains("--strict"));
        assertTrue(result.getOutput().contains("--json"));
        assertTrue(result.getOutput().contains("--quiet"));
    }

    @Test
    @Launch(value = {"validate", "/nonexistent/path"}, exitCode = 1)
    void testValidateNonexistentPath(LaunchResult result) {
        assertEquals(1, result.exitCode());
        assertTrue(result.getOutput().contains("does not exist"));
    }

    @Test
    @Launch(value = {"validate", "--json", "/nonexistent/path"}, exitCode = 1)
    void testValidateJsonOutput(LaunchResult result) {
        assertEquals(1, result.exitCode());
        assertTrue(result.getOutput().contains("\"valid\""));
        assertTrue(result.getOutput().contains("\"errors\""));
    }
}
