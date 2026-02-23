package org.idempiere.cli.commands;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(output.contains("source"));
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
        assertTrue(output.contains("No matching skill directory found"));
    }

    @Test
    @Launch({"skills", "source", "--help"})
    void testSkillsSourceHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("add"));
        assertTrue(output.contains("remove"));
        assertTrue(output.contains("list"));
    }

    @Test
    @Launch({"skills", "source", "list"})
    void testSkillsSourceList(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("No skill sources configured"));
    }

    @Test
    @Launch({"--help"})
    void testMainHelpHidesSkillsButIncludesConfig(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertFalse(output.contains("skills"));
        assertTrue(output.contains("config"));
    }
}
