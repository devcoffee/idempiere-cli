package org.idempiere.cli.commands;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusMainTest
class PackageCommandTest {

    @Test
    @Launch({"package", "--help"})
    void testPackageHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("--dir"));
        assertTrue(output.contains("--format"));
        assertTrue(output.contains("--output"));
        assertTrue(output.contains("Package plugin"));
    }

    @Test
    @Launch({"--help"})
    void testPackageRegistered(LaunchResult result) {
        assertEquals(0, result.exitCode());
        assertTrue(result.getOutput().contains("package"));
    }
}
