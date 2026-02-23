package org.idempiere.cli.commands;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract test: keeps core command surface stable according to docs/reference/core-contract.md.
 */
@QuarkusMainTest
@Tag("core-contract")
class CoreContractCommandSurfaceTest {

    @Test
    @Launch({"--help"})
    void testMainHelpContainsCoreContractCommands(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();

        // Environment/setup
        assertTrue(output.contains("doctor"));
        assertTrue(output.contains("setup-dev-env"));

        // Scaffolding
        assertTrue(output.contains("init"));
        assertTrue(output.contains("add"));

        // Quality/analysis
        assertTrue(output.contains("validate"));
        assertTrue(output.contains("deps"));
        assertTrue(output.contains("diff-schema"));
        assertTrue(output.contains("info"));

        // Delivery
        assertTrue(output.contains("build"));
        assertTrue(output.contains("deploy"));
        assertTrue(output.contains("package"));
        assertTrue(output.contains("migrate"));
        assertTrue(output.contains("dist"));

        // Utilities
        assertTrue(output.contains("config"));
        assertTrue(output.contains("import-workspace"));
        assertTrue(output.contains("generate-completion"));
        assertTrue(output.contains("upgrade"));
    }
}
