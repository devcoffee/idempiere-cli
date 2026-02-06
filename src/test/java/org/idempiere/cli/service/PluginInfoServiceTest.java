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
}
