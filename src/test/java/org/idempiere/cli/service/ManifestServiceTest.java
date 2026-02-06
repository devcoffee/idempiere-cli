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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ManifestService.
 * Tests MANIFEST.MF parsing and modification operations.
 */
@QuarkusTest
class ManifestServiceTest {

    @Inject
    ManifestService manifestService;

    private Path tempDir;
    private Path pluginDir;
    private Path manifestPath;

    @BeforeEach
    void setup() throws IOException {
        // Create temp directory manually since @TempDir doesn't work with @QuarkusTest
        tempDir = Files.createTempDirectory("manifest-test-");
        pluginDir = tempDir.resolve("test.plugin");
        Files.createDirectories(pluginDir.resolve("META-INF"));
        manifestPath = pluginDir.resolve("META-INF/MANIFEST.MF");
    }

    @AfterEach
    void cleanup() throws IOException {
        // Clean up temp directory
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
    void testAddZkBundlesForZkForm() throws IOException {
        // Given: A basic MANIFEST.MF without ZK bundles
        String initialManifest = """
                Manifest-Version: 1.0
                Bundle-ManifestVersion: 2
                Bundle-Name: Test
                Bundle-SymbolicName: test.plugin
                Bundle-Version: 1.0.0
                Bundle-RequiredExecutionEnvironment: JavaSE-17
                Require-Bundle: org.adempiere.base;bundle-version="12.0.0"
                Service-Component: OSGI-INF/*.xml
                Bundle-ActivationPolicy: lazy
                """;
        Files.writeString(manifestPath, initialManifest);

        // When: Adding ZK form component
        manifestService.addRequiredBundles(pluginDir, "zk-form");

        // Then: ZK bundles should be added
        String result = Files.readString(manifestPath);
        assertTrue(result.contains("org.adempiere.ui.zk"), "Should add ZK UI bundle");
        assertTrue(result.contains("zk"), "Should add ZK core bundle");
        assertTrue(result.contains("zul"), "Should add ZUL bundle");
    }

    @Test
    void testAddOsgiEventImportForEventHandler() throws IOException {
        // Given: MANIFEST.MF without Import-Package
        String initialManifest = """
                Manifest-Version: 1.0
                Bundle-ManifestVersion: 2
                Bundle-Name: Test
                Bundle-SymbolicName: test.plugin
                Bundle-Version: 1.0.0
                Require-Bundle: org.adempiere.base;bundle-version="12.0.0"
                Service-Component: OSGI-INF/*.xml
                Bundle-ActivationPolicy: lazy
                """;
        Files.writeString(manifestPath, initialManifest);

        // When: Adding event handler component
        manifestService.addRequiredBundles(pluginDir, "event-handler");

        // Then: OSGi event import should be added
        String result = Files.readString(manifestPath);
        assertTrue(result.contains("Import-Package:"), "Should add Import-Package header");
        assertTrue(result.contains("org.osgi.service.event"), "Should add OSGi event import");
    }

    @Test
    void testAddPluginUtilsForProcessMapped() throws IOException {
        // Given: MANIFEST.MF without plugin utils
        String initialManifest = """
                Manifest-Version: 1.0
                Bundle-ManifestVersion: 2
                Bundle-Name: Test
                Bundle-SymbolicName: test.plugin
                Bundle-Version: 1.0.0
                Require-Bundle: org.adempiere.base;bundle-version="12.0.0"
                Service-Component: OSGI-INF/*.xml
                """;
        Files.writeString(manifestPath, initialManifest);

        // When: Adding process-mapped component
        manifestService.addRequiredBundles(pluginDir, "process-mapped");

        // Then: Plugin utils bundle and OSGi framework import should be added
        String result = Files.readString(manifestPath);
        assertTrue(result.contains("org.adempiere.plugin.utils"), "Should add plugin utils bundle");
        assertTrue(result.contains("org.osgi.framework"), "Should add OSGi framework import");
    }

    @Test
    void testAddMinigridImportForWListbox() throws IOException {
        // Given: MANIFEST.MF without minigrid import
        String initialManifest = """
                Manifest-Version: 1.0
                Bundle-ManifestVersion: 2
                Bundle-Name: Test
                Bundle-SymbolicName: test.plugin
                Bundle-Version: 1.0.0
                Require-Bundle: org.adempiere.base;bundle-version="12.0.0",
                 org.adempiere.ui.zk;bundle-version="12.0.0"
                Service-Component: OSGI-INF/*.xml
                """;
        Files.writeString(manifestPath, initialManifest);

        // When: Adding wlistbox-editor component
        manifestService.addRequiredBundles(pluginDir, "wlistbox-editor");

        // Then: Minigrid import should be added
        String result = Files.readString(manifestPath);
        assertTrue(result.contains("org.compiere.minigrid"), "Should add minigrid import for WListbox");
    }

    @Test
    void testDoNotDuplicateExistingBundles() throws IOException {
        // Given: MANIFEST.MF already has ZK bundles
        String initialManifest = """
                Manifest-Version: 1.0
                Bundle-ManifestVersion: 2
                Bundle-Name: Test
                Bundle-SymbolicName: test.plugin
                Bundle-Version: 1.0.0
                Require-Bundle: org.adempiere.base;bundle-version="12.0.0",
                 org.adempiere.ui.zk;bundle-version="12.0.0",
                 zk;bundle-version="10.0.1",
                 zul;bundle-version="10.0.1"
                Service-Component: OSGI-INF/*.xml
                """;
        Files.writeString(manifestPath, initialManifest);

        // When: Adding another ZK component
        manifestService.addRequiredBundles(pluginDir, "zk-form-zul");

        // Then: Should not duplicate bundles
        String result = Files.readString(manifestPath);
        int zkCount = countOccurrences(result, "org.adempiere.ui.zk");
        assertEquals(1, zkCount, "Should not duplicate ZK bundle");
    }

    @Test
    void testAddTestImports() throws IOException {
        // Given: MANIFEST.MF without test imports
        String initialManifest = """
                Manifest-Version: 1.0
                Bundle-ManifestVersion: 2
                Bundle-Name: Test
                Bundle-SymbolicName: test.plugin
                Bundle-Version: 1.0.0
                Require-Bundle: org.adempiere.base;bundle-version="12.0.0"
                Service-Component: OSGI-INF/*.xml
                """;
        Files.writeString(manifestPath, initialManifest);

        // When: Adding base-test component
        manifestService.addRequiredBundles(pluginDir, "base-test");

        // Then: Test imports should be added
        String result = Files.readString(manifestPath);
        assertTrue(result.contains("org.idempiere.test"), "Should add iDempiere test import");
        assertTrue(result.contains("org.junit.jupiter.api"), "Should add JUnit import");
    }

    @Test
    void testHandleMissingManifest() {
        // Given: No MANIFEST.MF exists
        Path nonExistentPlugin = tempDir.resolve("nonexistent");

        // When/Then: Should handle gracefully without throwing
        assertDoesNotThrow(() -> manifestService.addRequiredBundles(nonExistentPlugin, "callout"));
    }

    @Test
    void testNoChangesForBasicComponents() throws IOException {
        // Given: MANIFEST.MF with base bundle
        String initialManifest = """
                Manifest-Version: 1.0
                Bundle-ManifestVersion: 2
                Bundle-Name: Test
                Bundle-SymbolicName: test.plugin
                Bundle-Version: 1.0.0
                Require-Bundle: org.adempiere.base;bundle-version="12.0.0"
                Service-Component: OSGI-INF/*.xml
                """;
        Files.writeString(manifestPath, initialManifest);

        // When: Adding callout (only needs base, which is already there)
        manifestService.addRequiredBundles(pluginDir, "callout");

        // Then: Manifest should be unchanged
        String result = Files.readString(manifestPath);
        assertEquals(initialManifest, result, "Should not modify manifest for basic components");
    }

    private int countOccurrences(String text, String substring) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(substring, idx)) != -1) {
            count++;
            idx += substring.length();
        }
        return count;
    }
}
