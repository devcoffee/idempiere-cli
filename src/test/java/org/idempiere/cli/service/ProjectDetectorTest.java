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

    // ========================================================================
    // Multi-module project detection tests
    // ========================================================================

    @Test
    void testIsMultiModuleRootWithValidPom() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"),
                "<?xml version=\"1.0\"?>\n" +
                "<project>\n" +
                "  <packaging>pom</packaging>\n" +
                "  <modules>\n" +
                "    <module>org.test.base</module>\n" +
                "    <module>org.test.p2</module>\n" +
                "  </modules>\n" +
                "</project>\n");

        assertTrue(projectDetector.isMultiModuleRoot(tempDir));
    }

    @Test
    void testIsMultiModuleRootWithNoPom() {
        assertFalse(projectDetector.isMultiModuleRoot(tempDir));
    }

    @Test
    void testIsMultiModuleRootWithNonPomPackaging() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"),
                "<?xml version=\"1.0\"?>\n" +
                "<project>\n" +
                "  <packaging>eclipse-plugin</packaging>\n" +
                "</project>\n");

        assertFalse(projectDetector.isMultiModuleRoot(tempDir));
    }

    @Test
    void testFindMultiModuleRootFromSubdir() throws IOException {
        // Create root pom
        Files.writeString(tempDir.resolve("pom.xml"),
                "<?xml version=\"1.0\"?>\n" +
                "<project>\n" +
                "  <packaging>pom</packaging>\n" +
                "  <modules>\n" +
                "    <module>org.test.base</module>\n" +
                "  </modules>\n" +
                "</project>\n");

        // Create subdir
        Path subDir = tempDir.resolve("org.test.base");
        Files.createDirectories(subDir);

        Optional<Path> root = projectDetector.findMultiModuleRoot(subDir);
        assertTrue(root.isPresent());
        assertEquals(tempDir, root.get());
    }

    @Test
    void testResolvePluginDirectoryReturnsSamePluginDir() throws IOException {
        Files.createDirectories(tempDir.resolve("META-INF"));
        Files.writeString(tempDir.resolve("META-INF/MANIFEST.MF"), "Bundle-SymbolicName: org.test.single\n");

        Optional<Path> resolved = projectDetector.resolvePluginDirectory(tempDir);
        assertTrue(resolved.isPresent());
        assertEquals(tempDir.toAbsolutePath().normalize(), resolved.get());
    }

    @Test
    void testFindBasePluginModulePrefersBaseModule() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"),
                "<?xml version=\"1.0\"?>\n" +
                "<project>\n" +
                "  <packaging>pom</packaging>\n" +
                "  <modules>\n" +
                "    <module>org.test.fragment</module>\n" +
                "    <module>org.test.base</module>\n" +
                "    <module>org.test.base.test</module>\n" +
                "  </modules>\n" +
                "</project>\n");

        Path fragmentDir = tempDir.resolve("org.test.fragment");
        Files.createDirectories(fragmentDir.resolve("META-INF"));
        Files.writeString(fragmentDir.resolve("META-INF/MANIFEST.MF"), "Bundle-SymbolicName: org.test.fragment\n");

        Path baseDir = tempDir.resolve("org.test.base");
        Files.createDirectories(baseDir.resolve("META-INF"));
        Files.writeString(baseDir.resolve("META-INF/MANIFEST.MF"), "Bundle-SymbolicName: org.test.base\n");

        Path testDir = tempDir.resolve("org.test.base.test");
        Files.createDirectories(testDir.resolve("META-INF"));
        Files.writeString(testDir.resolve("META-INF/MANIFEST.MF"), "Bundle-SymbolicName: org.test.base.test\n");

        Optional<Path> basePlugin = projectDetector.findBasePluginModule(tempDir);
        assertTrue(basePlugin.isPresent());
        assertEquals(baseDir.toAbsolutePath().normalize(), basePlugin.get());
    }

    @Test
    void testResolvePluginDirectoryFromMultiModuleRoot() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"),
                "<?xml version=\"1.0\"?>\n" +
                "<project>\n" +
                "  <packaging>pom</packaging>\n" +
                "  <modules>\n" +
                "    <module>org.test.parent</module>\n" +
                "    <module>org.test.base</module>\n" +
                "    <module>org.test.p2</module>\n" +
                "  </modules>\n" +
                "</project>\n");

        Path baseDir = tempDir.resolve("org.test.base");
        Files.createDirectories(baseDir.resolve("META-INF"));
        Files.writeString(baseDir.resolve("META-INF/MANIFEST.MF"), "Bundle-SymbolicName: org.test.base\n");

        Optional<Path> resolved = projectDetector.resolvePluginDirectory(tempDir);
        assertTrue(resolved.isPresent());
        assertEquals(baseDir.toAbsolutePath().normalize(), resolved.get());
    }

    @Test
    void testGetModules() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"),
                "<?xml version=\"1.0\"?>\n" +
                "<project>\n" +
                "  <packaging>pom</packaging>\n" +
                "  <modules>\n" +
                "    <module>org.test.parent</module>\n" +
                "    <module>org.test.base</module>\n" +
                "    <module>org.test.base.test</module>\n" +
                "    <module>org.test.p2</module>\n" +
                "  </modules>\n" +
                "</project>\n");

        var modules = projectDetector.getModules(tempDir);
        assertEquals(4, modules.size());
        assertTrue(modules.contains("org.test.parent"));
        assertTrue(modules.contains("org.test.base"));
        assertTrue(modules.contains("org.test.base.test"));
        assertTrue(modules.contains("org.test.p2"));
    }

    @Test
    void testHasFragmentModule() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"),
                "<?xml version=\"1.0\"?>\n" +
                "<project>\n" +
                "  <packaging>pom</packaging>\n" +
                "  <modules>\n" +
                "    <module>org.test.base</module>\n" +
                "    <module>org.test.fragment</module>\n" +
                "  </modules>\n" +
                "</project>\n");

        assertTrue(projectDetector.hasFragment(tempDir));
    }

    @Test
    void testHasNoFragmentModule() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"),
                "<?xml version=\"1.0\"?>\n" +
                "<project>\n" +
                "  <packaging>pom</packaging>\n" +
                "  <modules>\n" +
                "    <module>org.test.base</module>\n" +
                "  </modules>\n" +
                "</project>\n");

        assertFalse(projectDetector.hasFragment(tempDir));
    }

    @Test
    void testHasFeatureModule() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"),
                "<?xml version=\"1.0\"?>\n" +
                "<project>\n" +
                "  <packaging>pom</packaging>\n" +
                "  <modules>\n" +
                "    <module>org.test.base</module>\n" +
                "    <module>org.test.feature</module>\n" +
                "  </modules>\n" +
                "</project>\n");

        assertTrue(projectDetector.hasFeature(tempDir));
    }

    @Test
    void testDetectProjectBaseIdFromParent() throws IOException {
        // Create root pom
        Files.writeString(tempDir.resolve("pom.xml"),
                "<?xml version=\"1.0\"?>\n" +
                "<project>\n" +
                "  <packaging>pom</packaging>\n" +
                "  <modules>\n" +
                "    <module>org.example.myproject.parent</module>\n" +
                "    <module>org.example.myproject.base</module>\n" +
                "  </modules>\n" +
                "</project>\n");

        // Create parent module
        Path parentDir = tempDir.resolve("org.example.myproject.parent");
        Files.createDirectories(parentDir);
        Files.writeString(parentDir.resolve("pom.xml"),
                "<?xml version=\"1.0\"?>\n" +
                "<project>\n" +
                "  <artifactId>org.example.myproject.parent</artifactId>\n" +
                "  <packaging>pom</packaging>\n" +
                "</project>\n");

        Optional<String> baseId = projectDetector.detectProjectBaseId(tempDir);
        assertTrue(baseId.isPresent());
        assertEquals("org.example.myproject", baseId.get());
    }
}
