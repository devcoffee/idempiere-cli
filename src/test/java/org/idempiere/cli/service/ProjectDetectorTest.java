package org.idempiere.cli.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProjectDetector.
 */
@QuarkusTest
class ProjectDetectorTest {

    @Inject
    ProjectDetector projectDetector;

    private Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("project-detector-test-");
    }

    @AfterEach
    void cleanup() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException ignored) {}
                    });
        }
    }

    @Test
    void testIsIdempierePluginWithManifest() throws IOException {
        Files.createDirectories(tempDir.resolve("META-INF"));
        Files.writeString(tempDir.resolve("META-INF/MANIFEST.MF"), "Bundle-SymbolicName: org.test\n");

        assertTrue(projectDetector.isIdempierePlugin(tempDir));
    }

    @Test
    void testIsIdempierePluginWithoutManifest() {
        assertFalse(projectDetector.isIdempierePlugin(tempDir));
    }

    @Test
    void testDetectPluginIdWithValidManifest() throws IOException {
        Files.createDirectories(tempDir.resolve("META-INF"));
        Files.writeString(tempDir.resolve("META-INF/MANIFEST.MF"),
                "Manifest-Version: 1.0\n" +
                "Bundle-SymbolicName: org.idempiere.test.plugin\n" +
                "Bundle-Version: 1.0.0.qualifier\n");

        Optional<String> pluginId = projectDetector.detectPluginId(tempDir);
        assertTrue(pluginId.isPresent());
        assertEquals("org.idempiere.test.plugin", pluginId.get());
    }

    @Test
    void testDetectPluginIdWithSingleton() throws IOException {
        // MANIFEST.MF can have ";singleton:=true" after the bundle name
        Files.createDirectories(tempDir.resolve("META-INF"));
        Files.writeString(tempDir.resolve("META-INF/MANIFEST.MF"),
                "Bundle-SymbolicName: org.idempiere.singleton;singleton:=true\n");

        Optional<String> pluginId = projectDetector.detectPluginId(tempDir);
        assertTrue(pluginId.isPresent());
        assertEquals("org.idempiere.singleton", pluginId.get());
    }

    @Test
    void testDetectPluginIdWithoutManifest() {
        Optional<String> pluginId = projectDetector.detectPluginId(tempDir);
        assertTrue(pluginId.isEmpty());
    }

    @Test
    void testDetectPluginIdWithEmptyManifest() throws IOException {
        Files.createDirectories(tempDir.resolve("META-INF"));
        Files.writeString(tempDir.resolve("META-INF/MANIFEST.MF"), "Manifest-Version: 1.0\n");

        Optional<String> pluginId = projectDetector.detectPluginId(tempDir);
        assertTrue(pluginId.isEmpty());
    }

    @Test
    void testDetectPluginVersionWithValidManifest() throws IOException {
        Files.createDirectories(tempDir.resolve("META-INF"));
        Files.writeString(tempDir.resolve("META-INF/MANIFEST.MF"),
                "Bundle-Version: 2.3.4.qualifier\n");

        Optional<String> version = projectDetector.detectPluginVersion(tempDir);
        assertTrue(version.isPresent());
        assertEquals("2.3.4.qualifier", version.get());
    }

    @Test
    void testDetectPluginVersionWithoutManifest() {
        Optional<String> version = projectDetector.detectPluginVersion(tempDir);
        assertTrue(version.isEmpty());
    }

    @Test
    void testDetectPluginVersionWithNoVersion() throws IOException {
        Files.createDirectories(tempDir.resolve("META-INF"));
        Files.writeString(tempDir.resolve("META-INF/MANIFEST.MF"),
                "Bundle-SymbolicName: org.test\n");

        Optional<String> version = projectDetector.detectPluginVersion(tempDir);
        assertTrue(version.isEmpty());
    }
}
