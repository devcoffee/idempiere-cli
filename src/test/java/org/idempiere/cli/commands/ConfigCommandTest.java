package org.idempiere.cli.commands;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.idempiere.cli.model.CliConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusMainTest
class ConfigCommandTest {

    @Test
    @Launch({"config", "--help"})
    void testConfigHelp(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("show"));
        assertTrue(output.contains("get"));
        assertTrue(output.contains("set"));
    }

    @Test
    @Launch({"config", "show"})
    void testConfigShow(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("ai:"));
        assertTrue(output.contains("provider:"));
        assertTrue(output.contains("skills:"));
    }

    @Test
    @Launch({"config", "get", "ai.provider"})
    void testConfigGetAiProvider(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput().trim();
        assertEquals("none", output);
    }

    @Test
    @Launch({"config", "get", "ai.fallback"})
    void testConfigGetAiFallback(LaunchResult result) {
        assertEquals(0, result.exitCode());
        String output = result.getOutput().trim();
        assertEquals("templates", output);
    }

    @Test
    @Launch(value = {"config", "get", "nonexistent.key"}, exitCode = 1)
    void testConfigGetUnknownKey(LaunchResult result) {
        assertEquals(1, result.exitCode());
        String errorOutput = result.getErrorOutput();
        assertTrue(errorOutput.contains("Unknown config key"));
    }

    @Test
    void testGetConfigValueUnit() {
        CliConfig config = new CliConfig();
        config.getAi().setProvider("anthropic");
        config.getAi().setModel("claude-sonnet-4-20250514");
        config.getDefaults().setVendor("Test Corp");

        assertEquals("anthropic", ConfigCommand.getConfigValue(config, "ai.provider"));
        assertEquals("claude-sonnet-4-20250514", ConfigCommand.getConfigValue(config, "ai.model"));
        assertEquals("Test Corp", ConfigCommand.getConfigValue(config, "defaults.vendor"));
        assertNull(ConfigCommand.getConfigValue(config, "unknown.key"));
    }

    @Test
    void testSetConfigValueUnit() {
        CliConfig config = new CliConfig();

        assertTrue(ConfigCommand.setConfigValue(config, "ai.provider", "google"));
        assertEquals("google", config.getAi().getProvider());

        assertTrue(ConfigCommand.setConfigValue(config, "ai.model", "gemini-2.5-flash"));
        assertEquals("gemini-2.5-flash", config.getAi().getModel());

        assertTrue(ConfigCommand.setConfigValue(config, "defaults.vendor", "My Corp"));
        assertEquals("My Corp", config.getDefaults().getVendor());

        assertFalse(ConfigCommand.setConfigValue(config, "unknown.key", "value"));
    }
}
