package org.idempiere.cli.commands;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusMainTest
class DistCommandTest {

    @Test
    @Launch({"dist", "--help"})
    void testDistHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("--source-dir"));
        assertTrue(output.contains("--skip-build"));
        assertTrue(output.contains("--version-label"));
        assertTrue(output.contains("--output"));
        assertTrue(output.contains("--clean"));
    }

    @Test
    @Launch({"--help"})
    void testDistRegistered(LaunchResult result) {
        assertEquals(0, result.exitCode());
        assertTrue(result.getOutput().contains("dist"));
    }

    @Test
    @Launch(value = {"dist", "--source-dir=nonexistent"}, exitCode = 3)
    void testDistNoSource(LaunchResult result) {
        assertEquals(3, result.exitCode());
        String output = result.getErrorOutput();
        assertTrue(output.contains("Not an iDempiere source directory"));
    }
}
