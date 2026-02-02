package org.idempiere.cli.commands;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusMainTest
class MigrateCommandTest {

    @Test
    @Launch({"migrate", "--help"})
    void testMigrateHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("--from"));
        assertTrue(output.contains("--to"));
        assertTrue(output.contains("--dir"));
        assertTrue(output.contains("Migrate a plugin"));
    }

    @Test
    @Launch({"--help"})
    void testMigrateRegistered(LaunchResult result) {
        assertEquals(0, result.exitCode());
        assertTrue(result.getOutput().contains("migrate"));
    }
}
