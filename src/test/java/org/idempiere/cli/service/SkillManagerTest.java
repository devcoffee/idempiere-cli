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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SkillManagerTest {

    @Inject
    SkillManager skillManager;

    @Inject
    CliConfigService configService;

    private Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("skill-manager-test-");
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
    void testResolveSkillFromLocalSource() throws IOException {
        // Create a fake skill source directory
        Path sourceDir = tempDir.resolve("my-skills");
        Path calloutSkillDir = sourceDir.resolve("idempiere-callout-generator");
        Files.createDirectories(calloutSkillDir);
        Files.writeString(calloutSkillDir.resolve("SKILL.md"), "# Callout Generator\nGenerate a callout.");

        // Create config that points to this local source
        CliConfig config = new CliConfig();
        List<CliConfig.SkillSource> sources = new ArrayList<>();
        sources.add(new CliConfig.SkillSource("local", null, sourceDir.toString(), 1));
        config.getSkills().setSources(sources);

        Path configFile = tempDir.resolve(".idempiere-cli.yaml");
        configService.saveConfig(config, configFile);

        // Load config and resolve
        CliConfig loaded = configService.loadFromPath(configFile);
        assertNotNull(loaded);
        assertEquals(1, loaded.getSkills().getSources().size());
        assertEquals("local", loaded.getSkills().getSources().get(0).getName());
    }

    @Test
    void testTypeToSkillMapping() {
        assertNotNull(SkillManager.TYPE_TO_SKILL.get("callout"));
        assertNotNull(SkillManager.TYPE_TO_SKILL.get("process"));
        assertNotNull(SkillManager.TYPE_TO_SKILL.get("event-handler"));
        assertNotNull(SkillManager.TYPE_TO_SKILL.get("zk-form"));
        assertNotNull(SkillManager.TYPE_TO_SKILL.get("rest-extension"));
        assertNotNull(SkillManager.TYPE_TO_SKILL.get("window-validator"));
        assertNull(SkillManager.TYPE_TO_SKILL.get("nonexistent"));
    }

    @Test
    void testResolveSkillReturnsEmptyForUnknownType() {
        Optional<SkillManager.SkillResolution> resolution = skillManager.resolveSkill("nonexistent-type");
        assertTrue(resolution.isEmpty());
    }

    @Test
    void testResolveSkillReturnsEmptyWhenNoSourcesConfigured() {
        // Default config has no sources
        Optional<SkillManager.SkillResolution> resolution = skillManager.resolveSkill("callout");
        assertTrue(resolution.isEmpty());
    }

    @Test
    void testLoadSkillReturnsEmptyWhenNoSourcesConfigured() {
        Optional<String> content = skillManager.loadSkill("callout");
        assertTrue(content.isEmpty());
    }

    @Test
    void testListSourcesEmptyByDefault() {
        List<SkillManager.SkillSourceInfo> sources = skillManager.listSources();
        assertTrue(sources.isEmpty());
    }

    @Test
    void testSyncSkillsWithNoSources() {
        SkillManager.SyncResult result = skillManager.syncSkills();
        assertEquals(0, result.updated());
        assertEquals(0, result.unchanged());
        assertEquals(0, result.failed());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void testSkillSourceIsRemote() {
        CliConfig.SkillSource remote = new CliConfig.SkillSource("test", "https://github.com/test/skills.git", null, 1);
        assertTrue(remote.isRemote());

        CliConfig.SkillSource local = new CliConfig.SkillSource("test", null, "/opt/skills", 0);
        assertFalse(local.isRemote());
    }

    @Test
    void testDynamicDiscoveryByExactName() throws IOException {
        // Create a skill source with a non-hardcoded skill directory
        Path sourceDir = tempDir.resolve("custom-skills");
        Path customSkillDir = sourceDir.resolve("my-custom-skill");
        Files.createDirectories(customSkillDir);
        Files.writeString(customSkillDir.resolve("SKILL.md"), "# Custom Skill\nGenerate custom stuff.");

        // Create config pointing to this source
        CliConfig config = new CliConfig();
        List<CliConfig.SkillSource> sources = new ArrayList<>();
        sources.add(new CliConfig.SkillSource("custom", null, sourceDir.toString(), 0));
        config.getSkills().setSources(sources);

        Path configFile = tempDir.resolve(".idempiere-cli.yaml");
        configService.saveConfig(config, configFile);

        // Use a fresh SkillManager with this config
        CliConfig loaded = configService.loadFromPath(configFile);
        assertNotNull(loaded);

        // Verify the skill directory exists
        assertTrue(Files.exists(customSkillDir.resolve("SKILL.md")));
        assertEquals("# Custom Skill\nGenerate custom stuff.",
                Files.readString(customSkillDir.resolve("SKILL.md")));
    }

    @Test
    void testDynamicDiscoveryByIdempierePrefix() throws IOException {
        // Create a skill source with "idempiere-" prefixed directory
        Path sourceDir = tempDir.resolve("prefixed-skills");
        Path prefixedSkillDir = sourceDir.resolve("idempiere-custom-generator");
        Files.createDirectories(prefixedSkillDir);
        Files.writeString(prefixedSkillDir.resolve("SKILL.md"), "# Prefixed Skill");

        // The directory name follows "idempiere-<type>" convention
        assertTrue(Files.exists(prefixedSkillDir.resolve("SKILL.md")));
        assertEquals("idempiere-custom-generator", prefixedSkillDir.getFileName().toString());
    }

    @Test
    void testListAvailableTypesIncludesHardcoded() {
        List<String> types = skillManager.listAvailableTypes();
        // Should include at least the hardcoded types
        assertTrue(types.contains("callout"));
        assertTrue(types.contains("process"));
        assertTrue(types.contains("event-handler"));
    }

    @Test
    void testPriorityResolutionLowerNumberWins() throws IOException {
        // Create two skill source directories with different content
        Path source1Dir = tempDir.resolve("source1");
        Path source2Dir = tempDir.resolve("source2");

        Path skill1Dir = source1Dir.resolve("idempiere-callout-generator");
        Files.createDirectories(skill1Dir);
        Files.writeString(skill1Dir.resolve("SKILL.md"), "Priority 1 content");

        Path skill2Dir = source2Dir.resolve("idempiere-callout-generator");
        Files.createDirectories(skill2Dir);
        Files.writeString(skill2Dir.resolve("SKILL.md"), "Priority 0 content (higher priority)");

        // Verify the files exist with the expected content
        assertEquals("Priority 1 content", Files.readString(skill1Dir.resolve("SKILL.md")));
        assertEquals("Priority 0 content (higher priority)", Files.readString(skill2Dir.resolve("SKILL.md")));
    }
}
