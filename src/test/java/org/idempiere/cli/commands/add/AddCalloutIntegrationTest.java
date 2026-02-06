package org.idempiere.cli.commands.add;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.idempiere.cli.service.ProjectDetector;
import org.idempiere.cli.service.ScaffoldService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Add commands - tests actual component generation.
 */
@QuarkusTest
class AddCalloutIntegrationTest {

    @Inject
    ScaffoldService scaffoldService;

    @Inject
    ProjectDetector projectDetector;

    private Path tempDir;
    private Path pluginDir;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("add-cmd-test-");
        pluginDir = tempDir.resolve("org.test.plugin");

        // Create a minimal plugin structure
        Files.createDirectories(pluginDir.resolve("META-INF"));
        Files.writeString(pluginDir.resolve("META-INF/MANIFEST.MF"),
                "Manifest-Version: 1.0\n" +
                "Bundle-ManifestVersion: 2\n" +
                "Bundle-SymbolicName: org.test.plugin\n" +
                "Bundle-Version: 1.0.0.qualifier\n" +
                "Bundle-RequiredExecutionEnvironment: JavaSE-17\n");

        Files.createDirectories(pluginDir.resolve("src/org/test/plugin"));
        Files.createFile(pluginDir.resolve("plugin.xml"));

        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void cleanup() throws IOException {
        System.setOut(originalOut);
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
    void testProjectDetectorRecognizesPlugin() {
        assertTrue(projectDetector.isIdempierePlugin(pluginDir));
        assertEquals("org.test.plugin", projectDetector.detectPluginId(pluginDir).orElse(""));
    }

    @Test
    void testAddCalloutComponent() throws IOException {
        scaffoldService.addComponent("callout", "MyCallout", pluginDir, "org.test.plugin");

        Path srcDir = pluginDir.resolve("src/org/test/plugin");
        // Callout creates {name}.java and {PluginBaseName}CalloutFactory.java
        assertTrue(Files.exists(srcDir.resolve("MyCallout.java")));
        // Factory uses plugin base name (last segment), so "Plugin" -> "PluginCalloutFactory"
        assertTrue(Files.exists(srcDir.resolve("PluginCalloutFactory.java")));

        String calloutContent = Files.readString(srcDir.resolve("MyCallout.java"));
        assertTrue(calloutContent.contains("MyCallout"));
    }

    @Test
    void testAddProcessComponent() throws IOException {
        scaffoldService.addComponent("process", "MyProcess", pluginDir, "org.test.plugin");

        Path srcDir = pluginDir.resolve("src/org/test/plugin");
        // Process creates {name}.java and {name}Factory.java
        assertTrue(Files.exists(srcDir.resolve("MyProcess.java")));
        assertTrue(Files.exists(srcDir.resolve("MyProcessFactory.java")));

        String processContent = Files.readString(srcDir.resolve("MyProcess.java"));
        assertTrue(processContent.contains("MyProcess"));
    }

    @Test
    void testAddEventHandlerComponent() throws IOException {
        scaffoldService.addComponent("event-handler", "MyEventHandler", pluginDir, "org.test.plugin");

        Path srcDir = pluginDir.resolve("src/org/test/plugin");
        // EventHandler creates {name}.java and {name}Manager.java (not Factory)
        assertTrue(Files.exists(srcDir.resolve("MyEventHandler.java")));
        assertTrue(Files.exists(srcDir.resolve("MyEventHandlerManager.java")));

        String content = Files.readString(srcDir.resolve("MyEventHandler.java"));
        assertTrue(content.contains("MyEventHandler"));
    }

    @Test
    void testAddZkFormComponent() throws IOException {
        scaffoldService.addComponent("zk-form", "MyForm", pluginDir, "org.test.plugin");

        Path srcDir = pluginDir.resolve("src/org/test/plugin");
        // ZkForm creates {name}.java and {name}Factory.java
        assertTrue(Files.exists(srcDir.resolve("MyForm.java")));
        assertTrue(Files.exists(srcDir.resolve("MyFormFactory.java")));

        String content = Files.readString(srcDir.resolve("MyForm.java"));
        assertTrue(content.contains("MyForm"));
    }

    @Test
    void testAddReportComponent() throws IOException {
        scaffoldService.addComponent("report", "MyReport", pluginDir, "org.test.plugin");

        Path srcDir = pluginDir.resolve("src/org/test/plugin");
        // Report creates just {name}.java (no factory)
        assertTrue(Files.exists(srcDir.resolve("MyReport.java")));

        String content = Files.readString(srcDir.resolve("MyReport.java"));
        assertTrue(content.contains("MyReport"));
    }

    @Test
    void testAddWindowValidatorComponent() throws IOException {
        scaffoldService.addComponent("window-validator", "MyValidator", pluginDir, "org.test.plugin");

        Path srcDir = pluginDir.resolve("src/org/test/plugin");
        // WindowValidator creates just {name}.java (no factory)
        assertTrue(Files.exists(srcDir.resolve("MyValidator.java")));
    }

    @Test
    void testAddFactsValidatorComponent() throws IOException {
        scaffoldService.addComponent("facts-validator", "MyFactsValidator", pluginDir, "org.test.plugin");

        Path srcDir = pluginDir.resolve("src/org/test/plugin");
        // FactsValidator creates {name}.java
        assertTrue(Files.exists(srcDir.resolve("MyFactsValidator.java")));
    }
}
