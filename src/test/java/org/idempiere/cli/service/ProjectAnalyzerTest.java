package org.idempiere.cli.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.idempiere.cli.model.ProjectContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ProjectAnalyzerTest {

    @Inject
    ProjectAnalyzer projectAnalyzer;

    private Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("project-analyzer-test-");
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
    void testAnalyzeBasicPlugin() throws IOException {
        Path pluginDir = createBasicPlugin("org.example.myplugin", "1.0.0.qualifier");

        ProjectContext ctx = projectAnalyzer.analyze(pluginDir);

        assertEquals("org.example.myplugin", ctx.getPluginId());
        assertEquals("1.0.0.qualifier", ctx.getVersion());
        assertEquals("org.example.myplugin", ctx.getBasePackage());
        assertNotNull(ctx.getManifestContent());
        assertFalse(ctx.isMultiModule());
    }

    @Test
    void testAnalyzeDetectsExistingClasses() throws IOException {
        Path pluginDir = createBasicPlugin("org.example.test", "1.0.0.qualifier");

        // Add some Java files
        Path srcDir = pluginDir.resolve("src/org/example/test");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("MyCallout.java"),
                "package org.example.test;\npublic class MyCallout {}");
        Files.writeString(srcDir.resolve("MyProcess.java"),
                "package org.example.test;\npublic class MyProcess {}");

        ProjectContext ctx = projectAnalyzer.analyze(pluginDir);

        assertEquals(2, ctx.getExistingClasses().size());
        assertTrue(ctx.getExistingClasses().contains("MyCallout"));
        assertTrue(ctx.getExistingClasses().contains("MyProcess"));
    }

    @Test
    void testAnalyzeDetectsActivator() throws IOException {
        Path pluginDir = createBasicPlugin("org.example.test", "1.0.0.qualifier");

        Path srcDir = pluginDir.resolve("src/org/example/test");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("Activator.java"),
                "package org.example.test;\n" +
                "import org.osgi.framework.BundleActivator;\n" +
                "public class Activator extends BundleActivator {}");

        ProjectContext ctx = projectAnalyzer.analyze(pluginDir);

        assertTrue(ctx.hasActivator());
    }

    @Test
    void testAnalyzeDetectsCalloutFactory() throws IOException {
        Path pluginDir = createBasicPlugin("org.example.test", "1.0.0.qualifier");

        Path srcDir = pluginDir.resolve("src/org/example/test");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("MyCalloutFactory.java"),
                "package org.example.test;\n" +
                "import org.adempiere.base.IColumnCalloutFactory;\n" +
                "public class MyCalloutFactory implements IColumnCalloutFactory {}");

        ProjectContext ctx = projectAnalyzer.analyze(pluginDir);

        assertTrue(ctx.hasCalloutFactory());
    }

    @Test
    void testAnalyzeDetectsEventManager() throws IOException {
        Path pluginDir = createBasicPlugin("org.example.test", "1.0.0.qualifier");

        Path srcDir = pluginDir.resolve("src/org/example/test");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("MyEventHandler.java"),
                "package org.example.test;\n" +
                "import org.adempiere.base.event.AbstractEventHandler;\n" +
                "public class MyEventHandler extends AbstractEventHandler {}");

        ProjectContext ctx = projectAnalyzer.analyze(pluginDir);

        assertTrue(ctx.hasEventManager());
    }

    @Test
    void testAnalyzeDetectsAnnotationPattern() throws IOException {
        Path pluginDir = createBasicPlugin("org.example.test", "1.0.0.qualifier");

        Path srcDir = pluginDir.resolve("src/org/example/test");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("MyCallout.java"),
                "package org.example.test;\n" +
                "@Callout(tableName=\"C_Order\")\n" +
                "public class MyCallout {}");

        ProjectContext ctx = projectAnalyzer.analyze(pluginDir);

        assertTrue(ctx.usesAnnotationPattern());
    }

    @Test
    void testAnalyzeNoAnnotationPattern() throws IOException {
        Path pluginDir = createBasicPlugin("org.example.test", "1.0.0.qualifier");

        Path srcDir = pluginDir.resolve("src/org/example/test");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("SimpleClass.java"),
                "package org.example.test;\npublic class SimpleClass {}");

        ProjectContext ctx = projectAnalyzer.analyze(pluginDir);

        assertFalse(ctx.usesAnnotationPattern());
    }

    @Test
    void testAnalyzeWithPomXml() throws IOException {
        Path pluginDir = createBasicPlugin("org.example.test", "1.0.0.qualifier");

        Files.writeString(pluginDir.resolve("pom.xml"),
                "<project>\n" +
                "  <properties>\n" +
                "    <tycho.version>4.0.8</tycho.version>\n" +
                "  </properties>\n" +
                "</project>");

        ProjectContext ctx = projectAnalyzer.analyze(pluginDir);

        assertNotNull(ctx.getPomXmlContent());
        assertNotNull(ctx.getPlatformVersion());
        assertEquals(13, ctx.getPlatformVersion().major());
    }

    @Test
    void testAnalyzeWithOlderTychoVersion() throws IOException {
        Path pluginDir = createBasicPlugin("org.example.test", "1.0.0.qualifier");

        Files.writeString(pluginDir.resolve("pom.xml"),
                "<project>\n" +
                "  <properties>\n" +
                "    <tycho.version>4.0.4</tycho.version>\n" +
                "  </properties>\n" +
                "</project>");

        ProjectContext ctx = projectAnalyzer.analyze(pluginDir);

        assertEquals(12, ctx.getPlatformVersion().major());
    }

    @Test
    void testAnalyzeEmptyPlugin() throws IOException {
        Path pluginDir = createBasicPlugin("org.example.empty", "1.0.0.qualifier");
        // No src directory

        ProjectContext ctx = projectAnalyzer.analyze(pluginDir);

        assertEquals("org.example.empty", ctx.getPluginId());
        assertTrue(ctx.getExistingClasses().isEmpty());
        assertFalse(ctx.hasActivator());
        assertFalse(ctx.hasCalloutFactory());
        assertFalse(ctx.hasEventManager());
        assertFalse(ctx.usesAnnotationPattern());
    }

    @Test
    void testAnalyzeNonExistentManifest() {
        Path pluginDir = tempDir.resolve("no-manifest");

        ProjectContext ctx = projectAnalyzer.analyze(pluginDir);

        assertNull(ctx.getPluginId());
        assertNull(ctx.getManifestContent());
    }

    private Path createBasicPlugin(String pluginId, String version) throws IOException {
        Path pluginDir = tempDir.resolve(pluginId);
        Files.createDirectories(pluginDir.resolve("META-INF"));

        String manifest = "Manifest-Version: 1.0\n" +
                "Bundle-ManifestVersion: 2\n" +
                "Bundle-Name: Test Plugin\n" +
                "Bundle-SymbolicName: " + pluginId + ";singleton:=true\n" +
                "Bundle-Version: " + version + "\n" +
                "Bundle-RequiredExecutionEnvironment: JavaSE-21\n";

        Files.writeString(pluginDir.resolve("META-INF/MANIFEST.MF"), manifest);
        return pluginDir;
    }
}
