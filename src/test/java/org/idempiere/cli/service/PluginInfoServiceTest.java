package org.idempiere.cli.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PluginInfoService.
 */
@QuarkusTest
class PluginInfoServiceTest {

    @Inject
    PluginInfoService pluginInfoService;

    private Path tempDir;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("plugin-info-test-");
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void cleanup() throws IOException {
        System.setOut(originalOut);
        System.setErr(originalErr);
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
    void testPrintInfoWithoutManifest() {
        pluginInfoService.printInfo(tempDir);

        String errOutput = errContent.toString();
        assertTrue(errOutput.contains("META-INF/MANIFEST.MF not found"));
    }

    @Test
    void testPrintInfoWithBasicManifest() throws IOException {
        Files.createDirectories(tempDir.resolve("META-INF"));
        Files.writeString(tempDir.resolve("META-INF/MANIFEST.MF"),
                "Manifest-Version: 1.0\n" +
                "Bundle-SymbolicName: org.test.plugin\n" +
                "Bundle-Version: 1.2.3.qualifier\n" +
                "Bundle-Vendor: Test Vendor Inc\n");

        pluginInfoService.printInfo(tempDir);

        String output = outContent.toString();
        assertTrue(output.contains("Plugin: org.test.plugin"));
        assertTrue(output.contains("Version: 1.2.3.qualifier"));
        assertTrue(output.contains("Vendor: Test Vendor Inc"));
    }

    @Test
    void testPrintInfoWithDependencies() throws IOException {
        Files.createDirectories(tempDir.resolve("META-INF"));
        Files.writeString(tempDir.resolve("META-INF/MANIFEST.MF"),
                "Bundle-SymbolicName: org.test.plugin\n" +
                "Bundle-Version: 1.0.0\n" +
                "Require-Bundle: org.adempiere.base,\n" +
                " org.adempiere.ui.zk,\n" +
                " org.compiere.model\n");

        pluginInfoService.printInfo(tempDir);

        String output = outContent.toString();
        assertTrue(output.contains("Dependencies:"));
        assertTrue(output.contains("org.adempiere.base"));
        assertTrue(output.contains("org.adempiere.ui.zk"));
        assertTrue(output.contains("org.compiere.model"));
    }

    @Test
    void testPrintInfoWithFragmentHost() throws IOException {
        Files.createDirectories(tempDir.resolve("META-INF"));
        Files.writeString(tempDir.resolve("META-INF/MANIFEST.MF"),
                "Bundle-SymbolicName: org.test.rest\n" +
                "Bundle-Version: 1.0.0\n" +
                "Fragment-Host: com.idempiere.rest.api\n");

        pluginInfoService.printInfo(tempDir);

        String output = outContent.toString();
        assertTrue(output.contains("Fragment-Host: com.idempiere.rest.api"));
    }

    @Test
    void testPrintInfoWithComponents() throws IOException {
        Files.createDirectories(tempDir.resolve("META-INF"));
        Files.writeString(tempDir.resolve("META-INF/MANIFEST.MF"),
                "Bundle-SymbolicName: org.test.comp\n" +
                "Bundle-Version: 1.0.0\n");

        // Create source directory with Java files
        Path srcDir = tempDir.resolve("src/org/test/comp");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("MyCallout.java"), "public class MyCallout {}");
        Files.writeString(srcDir.resolve("MyProcess.java"), "public class MyProcess {}");

        pluginInfoService.printInfo(tempDir);

        String output = outContent.toString();
        assertTrue(output.contains("Components:"));
        assertTrue(output.contains("MyCallout.java"));
        assertTrue(output.contains("MyProcess.java"));
    }

    @Test
    void testPrintInfoNoVendor() throws IOException {
        Files.createDirectories(tempDir.resolve("META-INF"));
        Files.writeString(tempDir.resolve("META-INF/MANIFEST.MF"),
                "Bundle-SymbolicName: org.test.novendor\n" +
                "Bundle-Version: 1.0.0\n");

        pluginInfoService.printInfo(tempDir);

        String output = outContent.toString();
        assertTrue(output.contains("Plugin: org.test.novendor"));
        assertFalse(output.contains("Vendor:"));
    }

    @Test
    void testGetInfoParsesExtensionsAndHeaders() throws IOException {
        Files.createDirectories(tempDir.resolve("META-INF"));
        Files.writeString(tempDir.resolve("META-INF/MANIFEST.MF"),
                "Bundle-SymbolicName: org.test.ext\n" +
                "Bundle-Version: 1.0.0\n" +
                "Bundle-RequiredExecutionEnvironment: JavaSE-21\n" +
                "Require-Bundle: org.adempiere.base,\n" +
                " org.adempiere.ui.zk\n" +
                "Import-Package: org.osgi.service.event;version=\"1.4.0\"\n" +
                "Export-Package: org.test.ext.api;version=\"1.0.0\"\n" +
                "Service-Component: OSGI-INF/service.xml\n");
        Files.writeString(tempDir.resolve("plugin.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<plugin>\n" +
                "  <extension point=\"org.adempiere.base.ModelValidator\">\n" +
                "    <modelValidator class=\"org.test.ext.OrderValidator\" table=\"C_Order\"/>\n" +
                "  </extension>\n" +
                "</plugin>\n");

        PluginInfoService.PluginInfo info = pluginInfoService.getInfo(tempDir);
        assertNotNull(info);
        assertEquals("org.test.ext", info.pluginId());
        assertEquals(21, info.javaSe());
        assertEquals(13, info.idempiereVersion());
        assertEquals(List.of("org.adempiere.base", "org.adempiere.ui.zk"), info.requiredBundles());
        assertEquals(List.of("org.osgi.service.event"), info.importPackages());
        assertEquals(List.of("org.test.ext.api"), info.exportPackages());
        assertEquals(List.of("OSGI-INF/service.xml"), info.dsComponents());
        assertEquals(1, info.extensions().size());
        assertEquals("Model Validator", info.extensions().get(0).type());
        assertEquals("org.test.ext.OrderValidator", info.extensions().get(0).className());
        assertTrue(info.extensions().get(0).target().contains("table=C_Order"));
    }

    @Test
    void testGetMultiModuleInfo() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"),
                "<project>\n" +
                "  <packaging>pom</packaging>\n" +
                "  <modules>\n" +
                "    <module>org.test.mm.parent</module>\n" +
                "    <module>org.test.mm.base</module>\n" +
                "    <module>org.test.mm.p2</module>\n" +
                "  </modules>\n" +
                "</project>\n");

        Path parentDir = tempDir.resolve("org.test.mm.parent");
        Files.createDirectories(parentDir);
        Files.writeString(parentDir.resolve("pom.xml"),
                "<project>\n" +
                "  <artifactId>org.test.mm.parent</artifactId>\n" +
                "  <properties>\n" +
                "    <executionEnvironment>JavaSE-21</executionEnvironment>\n" +
                "  </properties>\n" +
                "</project>\n");

        Path baseDir = tempDir.resolve("org.test.mm.base");
        Files.createDirectories(baseDir.resolve("META-INF"));
        Files.writeString(baseDir.resolve("META-INF/MANIFEST.MF"),
                "Bundle-SymbolicName: org.test.mm.base\n" +
                "Bundle-Version: 1.0.0\n");

        Files.createDirectories(tempDir.resolve("org.test.mm.p2"));

        PluginInfoService.MultiModuleInfo info = pluginInfoService.getMultiModuleInfo(tempDir);
        assertNotNull(info);
        assertEquals("org.test.mm.base", info.baseModule());
        assertEquals(13, info.idempiereVersion());
        assertEquals(21, info.javaSe());
        assertEquals(3, info.modules().size());
    }
}
