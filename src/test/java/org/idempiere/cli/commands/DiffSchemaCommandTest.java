package org.idempiere.cli.commands;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusMainTest
class DiffSchemaCommandTest {

    @Test
    @Launch({"diff-schema", "--help"})
    void testDiffSchemaHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("--table"));
        assertTrue(output.contains("--dir"));
        assertTrue(output.contains("--db-host"));
        assertTrue(output.contains("Compare model"));
    }

    @Test
    @Launch({"--help"})
    void testDiffSchemaRegistered(LaunchResult result) {
        assertEquals(0, result.exitCode());
        assertTrue(result.getOutput().contains("diff-schema"));
    }
}
