package org.idempiere.cli.commands;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusMainTest
class SkillsCommandTest {

    @Test
    @Launch({"skills", "--help"})
    void testSkillsHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("list"));
        assertTrue(output.contains("sync"));
        assertTrue(output.contains("which"));
    }

    @Test
    @Launch({"skills", "list"})
    void testSkillsList(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("No skill sources configured"));
    }

    @Test
    @Launch({"skills", "sync"})
    void testSkillsSync(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("Sync complete"));
    }

    @Test
    @Launch({"skills", "which", "callout"})
    void testSkillsWhich(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("No skill found for: callout"));
    }

    @Test
    @Launch({"skills", "which", "nonexistent"})
    void testSkillsWhichUnknownType(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("Unknown component type"));
    }

    @Test
    @Launch({"--help"})
    void testMainHelpIncludesSkillsAndConfig(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("skills"));
        assertTrue(output.contains("config"));
    }
}
