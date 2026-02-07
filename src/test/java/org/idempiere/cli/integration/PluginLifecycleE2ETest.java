package org.idempiere.cli.integration;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.idempiere.cli.model.PlatformVersion;
import org.idempiere.cli.model.PluginDescriptor;
import org.idempiere.cli.service.ScaffoldService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test for plugin lifecycle: init → validate structure.
 *
 * <p>This test creates real plugins and verifies the generated structure
 * is complete and valid for an iDempiere development workflow.
 */
@QuarkusTest
@Tag("integration")
class PluginLifecycleE2ETest {

    @Inject
    ScaffoldService scaffoldService;

    private Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("e2e-test-");
    }

    @AfterEach
    void cleanup() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            deleteDir(tempDir);
        }
    }

    private void deleteDir(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private PluginDescriptor createDescriptor(String pluginId) {
        PluginDescriptor descriptor = new PluginDescriptor(pluginId);
        descriptor.setVersion("1.0.0.qualifier");
        descriptor.setVendor("E2E Test");
        descriptor.setPlatformVersion(PlatformVersion.of(13));
        descriptor.setOutputDir(tempDir);
        descriptor.setMultiModule(false);
        return descriptor;
    }

    @Test
    void testPluginLifecycle_basic_structure() throws IOException {
        String pluginId = "org.e2e.basic";
        PluginDescriptor descriptor = createDescriptor(pluginId);
        scaffoldService.createPlugin(descriptor);

        Path pluginDir = tempDir.resolve(pluginId);

        // Verify plugin structure was created
        assertTrue(Files.exists(pluginDir), "Plugin directory should exist");
        assertTrue(Files.exists(pluginDir.resolve("pom.xml")), "pom.xml should exist");
        assertTrue(Files.exists(pluginDir.resolve("META-INF/MANIFEST.MF")), "MANIFEST.MF should exist");
        assertTrue(Files.exists(pluginDir.resolve("plugin.xml")), "plugin.xml should exist");
        assertTrue(Files.exists(pluginDir.resolve("build.properties")), "build.properties should exist");
        assertTrue(Files.exists(pluginDir.resolve("OSGI-INF")), "OSGI-INF should exist");
        assertTrue(Files.exists(pluginDir.resolve(".mvn/jvm.config")), ".mvn/jvm.config should exist");

        // Verify pom.xml has essential elements
        String pom = Files.readString(pluginDir.resolve("pom.xml"));
        assertTrue(pom.contains(pluginId), "pom.xml should contain plugin ID");

        // Verify MANIFEST.MF
        String manifest = Files.readString(pluginDir.resolve("META-INF/MANIFEST.MF"));
        assertTrue(manifest.contains("Bundle-SymbolicName: " + pluginId),
                "MANIFEST.MF should contain Bundle-SymbolicName");
        assertTrue(manifest.contains("Bundle-Version:"),
                "MANIFEST.MF should contain Bundle-Version");
    }

    @Test
    void testPluginWithCallout_complete_workflow() throws IOException {
        String pluginId = "org.e2e.callout";
        PluginDescriptor descriptor = createDescriptor(pluginId);
        descriptor.addFeature("callout");
        scaffoldService.createPlugin(descriptor);

        Path pluginDir = tempDir.resolve(pluginId);
        Path srcDir = pluginDir.resolve("src/org/e2e/callout");

        // Verify callout files were created with correct naming
        assertTrue(Files.exists(srcDir.resolve("CalloutCallout.java")),
                "Callout Java file should exist");
        assertTrue(Files.exists(srcDir.resolve("CalloutCalloutFactory.java")),
                "CalloutFactory Java file should exist");

        // Verify callout code structure
        String callout = Files.readString(srcDir.resolve("CalloutCallout.java"));
        assertTrue(callout.contains("package org.e2e.callout"),
                "Callout should have correct package");
    }

    @Test
    void testPluginWithProcess_complete_workflow() throws IOException {
        String pluginId = "org.e2e.process";
        PluginDescriptor descriptor = createDescriptor(pluginId);
        descriptor.addFeature("process");
        scaffoldService.createPlugin(descriptor);

        Path pluginDir = tempDir.resolve(pluginId);
        Path srcDir = pluginDir.resolve("src/org/e2e/process");

        // Verify process files were created
        assertTrue(Files.exists(srcDir.resolve("ProcessProcess.java")),
                "Process Java file should exist");
        assertTrue(Files.exists(srcDir.resolve("ProcessProcessFactory.java")),
                "ProcessFactory Java file should exist");

        // Verify process code structure
        String process = Files.readString(srcDir.resolve("ProcessProcess.java"));
        assertTrue(process.contains("SvrProcess"),
                "Process should extend SvrProcess");
    }

    @Test
    void testPluginWithZkForm_complete_workflow() throws IOException {
        String pluginId = "org.e2e.form";
        PluginDescriptor descriptor = createDescriptor(pluginId);
        descriptor.addFeature("zk-form");
        scaffoldService.createPlugin(descriptor);

        Path pluginDir = tempDir.resolve(pluginId);
        Path srcDir = pluginDir.resolve("src/org/e2e/form");

        // Verify ZK form files were created
        assertTrue(Files.exists(srcDir.resolve("FormForm.java")),
                "Form Java file should exist");
        assertTrue(Files.exists(srcDir.resolve("FormFormFactory.java")),
                "FormFactory Java file should exist");

        // Verify MANIFEST.MF has ZK bundles
        String manifest = Files.readString(pluginDir.resolve("META-INF/MANIFEST.MF"));
        assertTrue(manifest.contains("org.adempiere.ui.zk"),
                "Should include ZK bundle dependency");
    }

    @Test
    void testPluginWithEventHandler_complete_workflow() throws IOException {
        String pluginId = "org.e2e.event";
        PluginDescriptor descriptor = createDescriptor(pluginId);
        descriptor.addFeature("event-handler");
        scaffoldService.createPlugin(descriptor);

        Path pluginDir = tempDir.resolve(pluginId);
        Path srcDir = pluginDir.resolve("src/org/e2e/event");

        // Verify event handler files were created
        // EventHandlerGenerator creates {BaseName}EventDelegate.java and {BaseName}EventManager.java
        assertTrue(Files.exists(srcDir.resolve("EventEventDelegate.java")),
                "EventDelegate Java file should exist");
        assertTrue(Files.exists(srcDir.resolve("EventEventManager.java")),
                "EventManager Java file should exist");
    }

    @Test
    void testPluginWithMultipleComponents() throws IOException {
        String pluginId = "org.e2e.multi";
        PluginDescriptor descriptor = createDescriptor(pluginId);
        descriptor.addFeature("callout");
        descriptor.addFeature("process");
        scaffoldService.createPlugin(descriptor);

        Path pluginDir = tempDir.resolve(pluginId);
        Path srcDir = pluginDir.resolve("src/org/e2e/multi");

        // Verify both callout and process files exist
        assertTrue(Files.exists(srcDir.resolve("MultiCallout.java")),
                "Callout should exist");
        assertTrue(Files.exists(srcDir.resolve("MultiProcess.java")),
                "Process should exist");
    }

    @Test
    void testMultiModuleProject_structure() throws IOException {
        String pluginId = "org.e2e.multimod";
        PluginDescriptor descriptor = createDescriptor(pluginId);
        descriptor.setMultiModule(true);
        scaffoldService.createPlugin(descriptor);

        Path rootDir = tempDir.resolve(pluginId);
        String basePluginId = pluginId + ".base";

        // Verify root pom.xml exists
        assertTrue(Files.exists(rootDir.resolve("pom.xml")),
                "Root pom.xml should exist");

        // Verify parent module
        assertTrue(Files.exists(rootDir.resolve(pluginId + ".parent/pom.xml")),
                "Parent module pom should exist");

        // Verify base plugin module
        assertTrue(Files.exists(rootDir.resolve(basePluginId + "/pom.xml")),
                "Base plugin module pom should exist");

        // Verify test module (uses basePluginId.test)
        assertTrue(Files.exists(rootDir.resolve(basePluginId + ".test/pom.xml")),
                "Test module pom should exist");

        // Verify p2 module
        assertTrue(Files.exists(rootDir.resolve(pluginId + ".p2/pom.xml")),
                "P2 module pom should exist");

        // Verify root pom includes modules
        String rootPom = Files.readString(rootDir.resolve("pom.xml"));
        assertTrue(rootPom.contains("<modules>"),
                "Root pom should have modules section");
    }
}
