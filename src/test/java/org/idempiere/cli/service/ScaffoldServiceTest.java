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
        // Given - standalone plugin (not multi-module)
        PluginDescriptor descriptor = new PluginDescriptor("org.test.basic");
        descriptor.setVersion("1.0.0.qualifier");
        descriptor.setVendor("Test Vendor");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.setOutputDir(tempDir);
        descriptor.setMultiModule(false); // Test standalone mode

        // When
        // projectName defaults to pluginName ("basic")
        Path pluginDir = tempDir.resolve("basic");
        scaffoldService.createPlugin(descriptor);

        // Then: Basic structure should exist
        assertTrue(Files.exists(pluginDir.resolve("pom.xml")), "pom.xml should exist");
        assertTrue(Files.exists(pluginDir.resolve("META-INF/MANIFEST.MF")), "MANIFEST.MF should exist");
        assertTrue(Files.exists(pluginDir.resolve("plugin.xml")), "plugin.xml should exist");
        assertTrue(Files.exists(pluginDir.resolve("build.properties")), "build.properties should exist");
        assertTrue(Files.exists(pluginDir.resolve("OSGI-INF")), "OSGI-INF directory should exist");
        assertTrue(Files.exists(pluginDir.resolve(".mvn/jvm.config")), ".mvn/jvm.config should exist");
        assertTrue(Files.exists(pluginDir.resolve(".gitignore")), ".gitignore should exist");
        assertTrue(Files.exists(pluginDir.resolve(".project")), ".project should exist (default enabled)");
    }

    @Test
    void testCreatePluginWithCallout() throws IOException {
        // Given
        PluginDescriptor descriptor = new PluginDescriptor("org.test.callout");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.addFeature("callout");
        descriptor.setOutputDir(tempDir);
        descriptor.setMultiModule(false);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then
        Path pluginDir = tempDir.resolve("callout");
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
        descriptor.setMultiModule(false);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then
        Path srcDir = tempDir.resolve("proc/src/org/test/proc");
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
        descriptor.setMultiModule(false);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then
        Path pluginDir = tempDir.resolve("form");
        Path srcDir = pluginDir.resolve("src/org/test/form");
        assertTrue(Files.exists(srcDir.resolve("FormForm.java")), "Form class should exist");
        assertTrue(Files.exists(srcDir.resolve("FormFormFactory.java")), "FormFactory should exist");

        // Check MANIFEST.MF has ZK bundles
        String manifest = Files.readString(pluginDir.resolve("META-INF/MANIFEST.MF"));
        assertTrue(manifest.contains("org.adempiere.ui.zk"), "Should include ZK bundle");
    }

    @Test
    void testCreatePluginWithZulForm() throws IOException {
        // Given
        PluginDescriptor descriptor = new PluginDescriptor("org.test.zul");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.addFeature("zk-form-zul");
        descriptor.setOutputDir(tempDir);
        descriptor.setMultiModule(false);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then: ZUL form uses different naming to avoid conflicts
        Path pluginDir = tempDir.resolve("zul");
        Path srcDir = pluginDir.resolve("src/org/test/zul");
        assertTrue(Files.exists(srcDir.resolve("ZulZulForm.java")), "ZulForm class should exist");
        assertTrue(Files.exists(srcDir.resolve("ZulZulFormController.java")), "Controller should exist");
        assertTrue(Files.exists(pluginDir.resolve("src/web/form.zul")), "ZUL file should exist");
    }

    @Test
    void testCreatePluginWithBothFormTypes() throws IOException {
        // Given: Plugin with both programmatic and ZUL forms
        PluginDescriptor descriptor = new PluginDescriptor("org.test.forms");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.addFeature("zk-form");
        descriptor.addFeature("zk-form-zul");
        descriptor.setOutputDir(tempDir);
        descriptor.setMultiModule(false);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then: Both form types should have distinct names
        Path srcDir = tempDir.resolve("forms/src/org/test/forms");
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
        descriptor.setMultiModule(false);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then
        Path srcDir = tempDir.resolve("mapped/src/org/test/mapped");
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
        descriptor.setMultiModule(false);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then
        Path pluginDir = tempDir.resolve("jasper");
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
        descriptor.setMultiModule(false);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then: Only one Activator should be created (shared)
        Path srcDir = tempDir.resolve("shared/src/org/test/shared");

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
        descriptor.setMultiModule(false);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then: Should have minigrid import
        String manifest = Files.readString(tempDir.resolve("wlist/META-INF/MANIFEST.MF"));
        assertTrue(manifest.contains("org.compiere.minigrid"), "Should include minigrid import for WListbox");
    }

    @Test
    void testCreatePluginWithTest() throws IOException {
        // Given
        PluginDescriptor descriptor = new PluginDescriptor("org.test.testing");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.addFeature("test");
        descriptor.setOutputDir(tempDir);
        descriptor.setMultiModule(false);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then
        Path pluginDir = tempDir.resolve("testing");
        Path srcDir = pluginDir.resolve("src/org/test/testing");
        assertTrue(Files.exists(srcDir.resolve("TestingTest.java")), "Test class should exist");

        // Check MANIFEST.MF has test imports
        String manifest = Files.readString(pluginDir.resolve("META-INF/MANIFEST.MF"));
        assertTrue(manifest.contains("org.idempiere.test"), "Should include iDempiere test import");
        assertTrue(manifest.contains("org.junit.jupiter.api"), "Should include JUnit import");
    }

    @Test
    void testPlatformVersion13() throws IOException {
        // Given: Plugin for iDempiere v13
        PluginDescriptor descriptor = new PluginDescriptor("org.test.v13");
        descriptor.setPlatformVersion(PlatformVersion.of(13));
        descriptor.setOutputDir(tempDir);
        descriptor.setMultiModule(false);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then: Should use Java 21 and Tycho 4.0.8
        Path pluginDir = tempDir.resolve("v13");
        String pom = Files.readString(pluginDir.resolve("pom.xml"));
        assertTrue(pom.contains("<maven.compiler.release>21</maven.compiler.release>"), "Should use Java 21");
        assertTrue(pom.contains("<tycho.version>4.0.8</tycho.version>"), "Should use Tycho 4.0.8");

        String manifest = Files.readString(pluginDir.resolve("META-INF/MANIFEST.MF"));
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
        descriptor.setMultiModule(false);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then: Source should be in correct package path (dir uses pluginName = "customizations")
        Path srcDir = tempDir.resolve("customizations/src/com/mycompany/idempiere/customizations");
        assertTrue(Files.exists(srcDir), "Source directory should match package structure");
        assertTrue(Files.exists(srcDir.resolve("CustomizationsCallout.java")), "Callout should use last segment for naming");
    }

    @Test
    void testProjectNameUsedForDirectory() throws IOException {
        // Given: Custom project name
        PluginDescriptor descriptor = new PluginDescriptor("org.mycompany.myplugin");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.setOutputDir(tempDir);
        descriptor.setMultiModule(false);
        descriptor.setProjectName("my-custom-dir");

        // When
        scaffoldService.createPlugin(descriptor);

        // Then: Directory should use projectName, not pluginId
        assertTrue(Files.exists(tempDir.resolve("my-custom-dir/pom.xml")), "Should use custom directory name");
        assertFalse(Files.exists(tempDir.resolve("org.mycompany.myplugin")), "Should NOT use pluginId as directory");
    }

    @Test
    void testGitignoreGenerated() throws IOException {
        // Given
        PluginDescriptor descriptor = new PluginDescriptor("org.test.gitignore");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.setOutputDir(tempDir);
        descriptor.setMultiModule(false);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then
        Path gitignore = tempDir.resolve("gitignore/.gitignore");
        assertTrue(Files.exists(gitignore), ".gitignore should exist");
        String content = Files.readString(gitignore);
        assertTrue(content.contains("target/"), "Should ignore target/");
        assertTrue(content.contains("bin/"), "Should ignore bin/");
        assertTrue(content.contains(".settings/"), "Should ignore .settings/");
    }

    @Test
    void testEclipseProjectGenerated() throws IOException {
        // Given: Eclipse project enabled (default)
        PluginDescriptor descriptor = new PluginDescriptor("org.test.eclipse");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.setOutputDir(tempDir);
        descriptor.setMultiModule(false);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then
        Path projectFile = tempDir.resolve("eclipse/.project");
        assertTrue(Files.exists(projectFile), ".project should exist");
        String content = Files.readString(projectFile);
        assertTrue(content.contains("<name>org.test.eclipse</name>"), "Should use pluginId as project name");
        assertTrue(content.contains("org.eclipse.jdt.core.javabuilder"), "Should have Java builder");
        assertTrue(content.contains("org.eclipse.pde.PluginNature"), "Should have PDE nature");
    }

    @Test
    void testEclipseProjectNotGeneratedWhenDisabled() throws IOException {
        // Given: Eclipse project disabled
        PluginDescriptor descriptor = new PluginDescriptor("org.test.noeclipse");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.setOutputDir(tempDir);
        descriptor.setMultiModule(false);
        descriptor.setWithEclipseProject(false);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then
        assertFalse(Files.exists(tempDir.resolve("noeclipse/.project")), ".project should NOT exist");
    }

    @Test
    void testMultiModuleWithEclipseProject() throws IOException {
        // Given: Multi-module with Eclipse project
        PluginDescriptor descriptor = new PluginDescriptor("org.test.multi");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.setOutputDir(tempDir);
        descriptor.setMultiModule(true);

        // When
        scaffoldService.createPlugin(descriptor);

        // Then: .project for base and test modules, .gitignore at root
        Path rootDir = tempDir.resolve("multi");
        assertTrue(Files.exists(rootDir.resolve(".gitignore")), ".gitignore at root");
        assertTrue(Files.exists(rootDir.resolve("org.test.multi.base/.project")), ".project for base module");
        assertTrue(Files.exists(rootDir.resolve("org.test.multi.base.test/.project")), ".project for test module");

        // Verify .project content
        String baseProject = Files.readString(rootDir.resolve("org.test.multi.base/.project"));
        assertTrue(baseProject.contains("<name>org.test.multi.base</name>"), "Base project name");
        String testProject = Files.readString(rootDir.resolve("org.test.multi.base.test/.project"));
        assertTrue(testProject.contains("<name>org.test.multi.base.test</name>"), "Test project name");
    }

    @Test
    void testMultiModuleManifestUsesBasePluginId() throws IOException {
        // Given: Multi-module project
        PluginDescriptor descriptor = new PluginDescriptor("org.test.manifest");
        descriptor.setPlatformVersion(PlatformVersion.of(12));
        descriptor.setOutputDir(tempDir);
        descriptor.setMultiModule(true);
        descriptor.addFeature("callout");

        // When
        scaffoldService.createPlugin(descriptor);

        // Then: Base module MANIFEST should use basePluginId (org.test.manifest.base)
        Path baseDir = tempDir.resolve("manifest/org.test.manifest.base");
        String manifest = Files.readString(baseDir.resolve("META-INF/MANIFEST.MF"));
        assertTrue(manifest.contains("Bundle-SymbolicName: org.test.manifest.base"),
                "MANIFEST should use basePluginId as Bundle-SymbolicName");

        // Callout source should be in the matching package
        Path srcDir = baseDir.resolve("src/org/test/manifest/base");
        assertTrue(Files.exists(srcDir.resolve("BaseCallout.java")),
                "Callout should be in base package");
        assertTrue(Files.exists(srcDir.resolve("BaseCalloutFactory.java")),
                "CalloutFactory should be in base package");
    }
}
