package org.idempiere.cli.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.idempiere.cli.model.CliConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class CliConfigServiceTest {

    @Inject
    CliConfigService configService;

    private Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("cli-config-test-");
    }

    @AfterEach
    void cleanup() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.delete(path); } catch (IOException ignored) {}
                    });
        }
    }

    @Test
    void testLoadEmptyConfig() {
        CliConfig config = configService.loadConfig();
        assertNotNull(config);
        assertNotNull(config.getDefaults());
    }

    @Test
    void testLoadConfigFromPath() throws IOException {
        Path configFile = tempDir.resolve(".idempiere-cli.yaml");
        Files.writeString(configFile, """
            defaults:
              vendor: "Test Company"
              idempiereVersion: 11
            """);

        CliConfig config = configService.loadFromPath(configFile);

        assertNotNull(config);
        assertEquals("Test Company", config.getDefaults().getVendor());
        assertEquals(11, config.getDefaults().getIdempiereVersion());
    }

    @Test
    void testLoadConfigFromPathWithYmlExtension() throws IOException {
        Path configFile = tempDir.resolve(".idempiere-cli.yml");
        Files.writeString(configFile, """
            defaults:
              vendor: "YML Vendor"
            """);

        CliConfig config = configService.loadFromPath(configFile);

        assertNotNull(config);
        assertEquals("YML Vendor", config.getDefaults().getVendor());
    }

    @Test
    void testLoadConfigFromNonExistentPath() {
        Path nonExistent = tempDir.resolve("does-not-exist.yaml");
        CliConfig config = configService.loadFromPath(nonExistent);
        assertNull(config);
    }

    @Test
    void testLoadConfigFromNullPath() {
        CliConfig config = configService.loadFromPath(null);
        assertNull(config);
    }

    @Test
    void testLoadConfigWithEmptyFile() throws IOException {
        Path configFile = tempDir.resolve(".idempiere-cli.yaml");
        Files.writeString(configFile, "");

        CliConfig config = configService.loadFromPath(configFile);

        assertNotNull(config);
        assertNotNull(config.getDefaults());
    }

    @Test
    void testConfigMerge() {
        CliConfig global = new CliConfig();
        global.getDefaults().setVendor("Global Vendor");
        global.getDefaults().setIdempiereVersion(11);

        CliConfig project = new CliConfig();
        project.getDefaults().setVendor("Project Vendor");
        // idempiereVersion not set in project (null)

        global.mergeFrom(project);

        assertEquals("Project Vendor", global.getDefaults().getVendor());
        assertEquals(11, global.getDefaults().getIdempiereVersion()); // unchanged because not set in project
    }

    @Test
    void testConfigMergeEmptyValues() {
        CliConfig global = new CliConfig();
        global.getDefaults().setVendor("Global Vendor");
        global.getDefaults().setIdempiereVersion(11);

        CliConfig project = new CliConfig();
        project.getDefaults().setVendor(""); // empty string should not override

        global.mergeFrom(project);

        assertEquals("Global Vendor", global.getDefaults().getVendor()); // unchanged
    }

    @Test
    void testLoadConfigWithOnlyVendor() throws IOException {
        Path configFile = tempDir.resolve(".idempiere-cli.yaml");
        Files.writeString(configFile, """
            defaults:
              vendor: "Only Vendor"
            """);

        CliConfig config = configService.loadFromPath(configFile);

        assertNotNull(config);
        assertEquals("Only Vendor", config.getDefaults().getVendor());
        assertEquals(13, config.getDefaults().getIdempiereVersion()); // default
    }

    @Test
    void testLoadConfigWithExplicitPath() throws IOException {
        Path configFile = tempDir.resolve("custom-config.yaml");
        Files.writeString(configFile, """
            defaults:
              vendor: "Explicit Config"
              idempiereVersion: 13
            """);

        CliConfig config = configService.loadConfig(configFile);

        assertNotNull(config);
        assertEquals("Explicit Config", config.getDefaults().getVendor());
        assertEquals(13, config.getDefaults().getIdempiereVersion());
    }

    @Test
    void testGetEnvVarName() {
        assertEquals("IDEMPIERE_CLI_CONFIG", configService.getEnvVarName());
    }

    @Test
    void testLoadConfigWithTemplatesPath() throws IOException {
        Path configFile = tempDir.resolve(".idempiere-cli.yaml");
        Files.writeString(configFile, """
            templates:
              path: ~/.idempiere-cli/templates
            """);

        CliConfig config = configService.loadFromPath(configFile);

        assertNotNull(config);
        assertNotNull(config.getTemplates());
        assertEquals("~/.idempiere-cli/templates", config.getTemplates().getPath());
        assertTrue(config.getTemplates().hasPath());
    }

    @Test
    void testLoadConfigWithTemplatesAndDefaults() throws IOException {
        Path configFile = tempDir.resolve(".idempiere-cli.yaml");
        Files.writeString(configFile, """
            defaults:
              vendor: "My Company"
              idempiereVersion: 12
            templates:
              path: /custom/templates
            """);

        CliConfig config = configService.loadFromPath(configFile);

        assertNotNull(config);
        assertEquals("My Company", config.getDefaults().getVendor());
        assertEquals(12, config.getDefaults().getIdempiereVersion());
        assertEquals("/custom/templates", config.getTemplates().getPath());
    }

    @Test
    void testTemplatesMerge() {
        CliConfig global = new CliConfig();
        global.getTemplates().setPath("~/.idempiere-cli/templates");

        CliConfig project = new CliConfig();
        project.getTemplates().setPath("./my-templates");

        global.mergeFrom(project);

        assertEquals("./my-templates", global.getTemplates().getPath());
    }

    @Test
    void testTemplatesMergeDoesNotOverrideWithNull() {
        CliConfig global = new CliConfig();
        global.getTemplates().setPath("~/.idempiere-cli/templates");

        CliConfig project = new CliConfig();
        // templates.path not set in project

        global.mergeFrom(project);

        assertEquals("~/.idempiere-cli/templates", global.getTemplates().getPath());
    }

    // ========== AI Config Tests ==========

    @Test
    void testDefaultAiConfig() {
        CliConfig config = new CliConfig();
        assertNotNull(config.getAi());
        assertEquals("none", config.getAi().getProvider());
        assertEquals("templates", config.getAi().getFallback());
        assertFalse(config.getAi().isEnabled());
        assertNull(config.getAi().getApiKeyEnv());
        assertNull(config.getAi().getModel());
    }

    @Test
    void testLoadAiConfigFromYaml() throws IOException {
        Path configFile = tempDir.resolve(".idempiere-cli.yaml");
        Files.writeString(configFile, """
            ai:
              provider: anthropic
              apiKeyEnv: ANTHROPIC_API_KEY
              model: claude-sonnet-4-20250514
              fallback: error
            """);

        CliConfig config = configService.loadFromPath(configFile);

        assertNotNull(config);
        assertEquals("anthropic", config.getAi().getProvider());
        assertEquals("ANTHROPIC_API_KEY", config.getAi().getApiKeyEnv());
        assertEquals("claude-sonnet-4-20250514", config.getAi().getModel());
        assertEquals("error", config.getAi().getFallback());
        assertTrue(config.getAi().isEnabled());
    }

    @Test
    void testAiConfigMerge() {
        CliConfig global = new CliConfig();
        global.getAi().setProvider("anthropic");
        global.getAi().setApiKeyEnv("ANTHROPIC_API_KEY");
        global.getAi().setModel("claude-sonnet-4-20250514");

        CliConfig project = new CliConfig();
        project.getAi().setModel("claude-opus-4-20250514");
        // provider not set in project

        global.mergeFrom(project);

        assertEquals("anthropic", global.getAi().getProvider());
        assertEquals("claude-opus-4-20250514", global.getAi().getModel());
    }

    @Test
    void testAiConfigProviderNone() {
        CliConfig config = new CliConfig();
        config.getAi().setProvider("none");
        assertFalse(config.getAi().isEnabled());
    }

    // ========== Skills Config Tests ==========

    @Test
    void testDefaultSkillsConfig() {
        CliConfig config = new CliConfig();
        assertNotNull(config.getSkills());
        assertTrue(config.getSkills().getSources().isEmpty());
        assertEquals("~/.idempiere-cli/skills", config.getSkills().getCacheDir());
        assertEquals("7d", config.getSkills().getUpdateInterval());
    }

    @Test
    void testLoadSkillsConfigFromYaml() throws IOException {
        Path configFile = tempDir.resolve(".idempiere-cli.yaml");
        Files.writeString(configFile, """
            skills:
              sources:
                - name: official
                  url: https://github.com/hengsin/idempiere-skills.git
                  priority: 1
                - name: local
                  path: /opt/mycompany/skills
                  priority: 0
              cacheDir: /tmp/skills-cache
              updateInterval: 1d
            """);

        CliConfig config = configService.loadFromPath(configFile);

        assertNotNull(config);
        assertEquals(2, config.getSkills().getSources().size());

        CliConfig.SkillSource official = config.getSkills().getSources().get(0);
        assertEquals("official", official.getName());
        assertEquals("https://github.com/hengsin/idempiere-skills.git", official.getUrl());
        assertEquals(1, official.getPriority());
        assertTrue(official.isRemote());

        CliConfig.SkillSource local = config.getSkills().getSources().get(1);
        assertEquals("local", local.getName());
        assertEquals("/opt/mycompany/skills", local.getPath());
        assertEquals(0, local.getPriority());
        assertFalse(local.isRemote());

        assertEquals("/tmp/skills-cache", config.getSkills().getCacheDir());
        assertEquals("1d", config.getSkills().getUpdateInterval());
    }

    @Test
    void testSkillsConfigMerge() {
        CliConfig global = new CliConfig();
        global.getSkills().setCacheDir("~/.cache/skills");

        CliConfig project = new CliConfig();
        List<CliConfig.SkillSource> sources = new ArrayList<>();
        sources.add(new CliConfig.SkillSource("custom", "https://example.com/skills.git", null, 1));
        project.getSkills().setSources(sources);

        global.mergeFrom(project);

        assertEquals(1, global.getSkills().getSources().size());
        assertEquals("custom", global.getSkills().getSources().get(0).getName());
        assertEquals("~/.cache/skills", global.getSkills().getCacheDir()); // unchanged
    }

    @Test
    void testLoadFullConfigWithAllSections() throws IOException {
        Path configFile = tempDir.resolve(".idempiere-cli.yaml");
        Files.writeString(configFile, """
            defaults:
              vendor: "ACME Corp"
              idempiereVersion: 12
            templates:
              path: /custom/templates
            ai:
              provider: google
              apiKeyEnv: GOOGLE_API_KEY
              model: gemini-2.5-flash
            skills:
              sources:
                - name: official
                  url: https://github.com/hengsin/idempiere-skills.git
                  priority: 1
            """);

        CliConfig config = configService.loadFromPath(configFile);

        assertNotNull(config);
        assertEquals("ACME Corp", config.getDefaults().getVendor());
        assertEquals(12, config.getDefaults().getIdempiereVersion());
        assertEquals("/custom/templates", config.getTemplates().getPath());
        assertEquals("google", config.getAi().getProvider());
        assertEquals("GOOGLE_API_KEY", config.getAi().getApiKeyEnv());
        assertEquals(1, config.getSkills().getSources().size());
    }

    // ========== Save Config Tests ==========

    @Test
    void testSaveAndReloadConfig() throws IOException {
        Path configFile = tempDir.resolve(".idempiere-cli.yaml");

        CliConfig config = new CliConfig();
        config.getDefaults().setVendor("Save Test");
        config.getAi().setProvider("anthropic");
        config.getAi().setApiKeyEnv("MY_KEY");

        configService.saveConfig(config, configFile);

        assertTrue(Files.exists(configFile));

        CliConfig reloaded = configService.loadFromPath(configFile);
        assertNotNull(reloaded);
        assertEquals("Save Test", reloaded.getDefaults().getVendor());
        assertEquals("anthropic", reloaded.getAi().getProvider());
        assertEquals("MY_KEY", reloaded.getAi().getApiKeyEnv());
    }
}
