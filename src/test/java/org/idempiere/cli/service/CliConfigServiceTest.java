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
import java.util.Comparator;

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
        assertEquals(12, config.getDefaults().getIdempiereVersion()); // default
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
}
