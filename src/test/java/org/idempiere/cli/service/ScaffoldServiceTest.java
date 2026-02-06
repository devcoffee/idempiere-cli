package org.idempiere.cli.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.idempiere.cli.model.PlatformVersion;
import org.idempiere.cli.model.PluginDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScaffoldService.
 * Tests plugin creation and component addition operations.
 */
@QuarkusTest
class ScaffoldServiceTest {

    @Inject
    ScaffoldService scaffoldService;

    private Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        // Create temp directory manually since @TempDir doesn't work with @QuarkusTest
        tempDir = Files.createTempDirectory("scaffold-test-");
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
    void testCreateBasicPlugin() throws IOException {
        // Given
        PluginDescriptor descriptor = new PluginDescriptor("org.test.basic");
        descriptor.setVersion("1.0.0.qualifier");
        descriptor.setVendor("Test Vendor");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.setOutputDir(tempDir);

        // When
        Path pluginDir = tempDir.resolve("org.test.basic");
        scaffoldService.createPlugin(descriptor);

        // Then: Basic structure should exist
        assertTrue(Files.exists(pluginDir.resolve("pom.xml")), "pom.xml should exist");
        assertTrue(Files.exists(pluginDir.resolve("META-INF/MANIFEST.MF")), "MANIFEST.MF should exist");
        assertTrue(Files.exists(pluginDir.resolve("plugin.xml")), "plugin.xml should exist");
        assertTrue(Files.exists(pluginDir.resolve("build.properties")), "build.properties should exist");
        assertTrue(Files.exists(pluginDir.resolve("OSGI-INF")), "OSGI-INF directory should exist");
        assertTrue(Files.exists(pluginDir.resolve(".mvn/jvm.config")), ".mvn/jvm.config should exist");
    }

    @Test
    void testCreatePluginWithCallout() throws IOException {
        // Given
        PluginDescriptor descriptor = new PluginDescriptor("org.test.callout");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.addFeature("callout");
        descriptor.setOutputDir(tempDir);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then
        Path pluginDir = tempDir.resolve("org.test.callout");
        Path srcDir = pluginDir.resolve("src/org/test/callout");

        assertTrue(Files.exists(srcDir.resolve("CalloutCallout.java")), "Callout class should exist");
        assertTrue(Files.exists(srcDir.resolve("CalloutCalloutFactory.java")), "CalloutFactory should exist");
    }

    @Test
    void testCreatePluginWithProcess() throws IOException {
        // Given
        PluginDescriptor descriptor = new PluginDescriptor("org.test.proc");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.addFeature("process");
        descriptor.setOutputDir(tempDir);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then
        Path srcDir = tempDir.resolve("org.test.proc/src/org/test/proc");
        assertTrue(Files.exists(srcDir.resolve("ProcProcess.java")), "Process class should exist");
        assertTrue(Files.exists(srcDir.resolve("ProcProcessFactory.java")), "ProcessFactory should exist");
    }

    @Test
    void testCreatePluginWithZkForm() throws IOException {
        // Given
        PluginDescriptor descriptor = new PluginDescriptor("org.test.form");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.addFeature("zk-form");
        descriptor.setOutputDir(tempDir);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then
        Path srcDir = tempDir.resolve("org.test.form/src/org/test/form");
        assertTrue(Files.exists(srcDir.resolve("FormForm.java")), "Form class should exist");
        assertTrue(Files.exists(srcDir.resolve("FormFormFactory.java")), "FormFactory should exist");

        // Check MANIFEST.MF has ZK bundles
        String manifest = Files.readString(tempDir.resolve("org.test.form/META-INF/MANIFEST.MF"));
        assertTrue(manifest.contains("org.adempiere.ui.zk"), "Should include ZK bundle");
    }

    @Test
    void testCreatePluginWithZulForm() throws IOException {
        // Given
        PluginDescriptor descriptor = new PluginDescriptor("org.test.zul");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.addFeature("zk-form-zul");
        descriptor.setOutputDir(tempDir);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then: ZUL form uses different naming to avoid conflicts
        Path srcDir = tempDir.resolve("org.test.zul/src/org/test/zul");
        assertTrue(Files.exists(srcDir.resolve("ZulZulForm.java")), "ZulForm class should exist");
        assertTrue(Files.exists(srcDir.resolve("ZulZulFormController.java")), "Controller should exist");
        assertTrue(Files.exists(tempDir.resolve("org.test.zul/src/web/form.zul")), "ZUL file should exist");
    }

    @Test
    void testCreatePluginWithBothFormTypes() throws IOException {
        // Given: Plugin with both programmatic and ZUL forms
        PluginDescriptor descriptor = new PluginDescriptor("org.test.forms");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.addFeature("zk-form");
        descriptor.addFeature("zk-form-zul");
        descriptor.setOutputDir(tempDir);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then: Both form types should have distinct names
        Path srcDir = tempDir.resolve("org.test.forms/src/org/test/forms");
        assertTrue(Files.exists(srcDir.resolve("FormsForm.java")), "Programmatic form should exist");
        assertTrue(Files.exists(srcDir.resolve("FormsZulForm.java")), "ZUL form should exist");
    }

    @Test
    void testCreatePluginWithProcessMapped() throws IOException {
        // Given
        PluginDescriptor descriptor = new PluginDescriptor("org.test.mapped");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.addFeature("process-mapped");
        descriptor.setOutputDir(tempDir);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then
        Path srcDir = tempDir.resolve("org.test.mapped/src/org/test/mapped");
        assertTrue(Files.exists(srcDir.resolve("MappedProcess.java")), "Process class should exist");
        assertTrue(Files.exists(srcDir.resolve("MappedActivator.java")), "Activator should exist");

        // Verify Activator content
        String activator = Files.readString(srcDir.resolve("MappedActivator.java"));
        assertTrue(activator.contains("Incremental2PackActivator"), "Should extend Incremental2PackActivator");
        assertTrue(activator.contains("class MappedActivator"), "Class name should be correct (not MappedActivatorActivator)");
    }

    @Test
    void testCreatePluginWithJasperReport() throws IOException {
        // Given
        PluginDescriptor descriptor = new PluginDescriptor("org.test.jasper");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.addFeature("jasper-report");
        descriptor.setOutputDir(tempDir);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then
        Path pluginDir = tempDir.resolve("org.test.jasper");
        assertTrue(Files.exists(pluginDir.resolve("reports/JasperReport.jrxml")), "JRXML file should exist");
        assertTrue(Files.exists(pluginDir.resolve("src/org/test/jasper/JasperActivator.java")), "Activator should exist");
    }

    @Test
    void testSharedActivatorBetweenProcessMappedAndJasper() throws IOException {
        // Given: Plugin with both process-mapped and jasper-report
        PluginDescriptor descriptor = new PluginDescriptor("org.test.shared");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.addFeature("process-mapped");
        descriptor.addFeature("jasper-report");
        descriptor.setOutputDir(tempDir);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then: Only one Activator should be created (shared)
        Path srcDir = tempDir.resolve("org.test.shared/src/org/test/shared");

        // Count Activator files
        long activatorCount = Files.list(srcDir)
                .filter(p -> p.getFileName().toString().contains("Activator"))
                .count();

        assertEquals(1, activatorCount, "Should have exactly one shared Activator");
    }

    @Test
    void testCreatePluginWithWListboxEditor() throws IOException {
        // Given
        PluginDescriptor descriptor = new PluginDescriptor("org.test.wlist");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.addFeature("wlistbox-editor");
        descriptor.setOutputDir(tempDir);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then: Should have minigrid import
        String manifest = Files.readString(tempDir.resolve("org.test.wlist/META-INF/MANIFEST.MF"));
        assertTrue(manifest.contains("org.compiere.minigrid"), "Should include minigrid import for WListbox");
    }

    @Test
    void testCreatePluginWithTest() throws IOException {
        // Given
        PluginDescriptor descriptor = new PluginDescriptor("org.test.testing");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.addFeature("test");
        descriptor.setOutputDir(tempDir);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then
        Path srcDir = tempDir.resolve("org.test.testing/src/org/test/testing");
        assertTrue(Files.exists(srcDir.resolve("TestingTest.java")), "Test class should exist");

        // Check MANIFEST.MF has test imports
        String manifest = Files.readString(tempDir.resolve("org.test.testing/META-INF/MANIFEST.MF"));
        assertTrue(manifest.contains("org.idempiere.test"), "Should include iDempiere test import");
        assertTrue(manifest.contains("org.junit.jupiter.api"), "Should include JUnit import");
    }

    @Test
    void testPlatformVersion13() throws IOException {
        // Given: Plugin for iDempiere v13
        PluginDescriptor descriptor = new PluginDescriptor("org.test.v13");
        descriptor.setPlatformVersion(PlatformVersion.of(13));
        descriptor.setOutputDir(tempDir);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then: Should use Java 21 and Tycho 4.0.8
        String pom = Files.readString(tempDir.resolve("org.test.v13/pom.xml"));
        assertTrue(pom.contains("<maven.compiler.release>21</maven.compiler.release>"), "Should use Java 21");
        assertTrue(pom.contains("<tycho.version>4.0.8</tycho.version>"), "Should use Tycho 4.0.8");

        String manifest = Files.readString(tempDir.resolve("org.test.v13/META-INF/MANIFEST.MF"));
        assertTrue(manifest.contains("JavaSE-21"), "Should target JavaSE-21");
        assertTrue(manifest.contains("bundle-version=\"13.0.0\""), "Should use bundle version 13.0.0");
    }

    @Test
    void testPluginIdWithMultipleSegments() throws IOException {
        // Given: Complex plugin ID
        PluginDescriptor descriptor = new PluginDescriptor("com.mycompany.idempiere.customizations");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.addFeature("callout");
        descriptor.setOutputDir(tempDir);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then: Source should be in correct package path
        Path srcDir = tempDir.resolve("com.mycompany.idempiere.customizations/src/com/mycompany/idempiere/customizations");
        assertTrue(Files.exists(srcDir), "Source directory should match package structure");
        assertTrue(Files.exists(srcDir.resolve("CustomizationsCallout.java")), "Callout should use last segment for naming");
    }
}
