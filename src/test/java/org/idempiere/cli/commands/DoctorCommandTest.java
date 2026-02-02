package org.idempiere.cli.commands;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusMainTest
class DoctorCommandTest {

    @Test
    @Launch({"doctor"})
    void testDoctorCommand(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("Environment Check"));
        assertTrue(output.contains("Java"));
        assertTrue(output.contains("Maven"));
        assertTrue(output.contains("Git"));
    }

    @Test
    @Launch({"doctor", "--fix"})
    void testDoctorCommandWithFix(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("Environment Check"));
    }

    @Test
    @Launch({"--help"})
    void testHelpOption(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("idempiere-cli"));
        assertTrue(output.contains("doctor"));
        assertTrue(output.contains("init"));
        assertTrue(output.contains("add"));
    }

    @Test
    @Launch({"doctor", "--help"})
    void testDoctorHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("Check required tools"));
        assertTrue(output.contains("--fix"));
        assertTrue(output.contains("--dir"));
    }
}
