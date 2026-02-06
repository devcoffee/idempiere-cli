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
 * Unit tests for ValidateService.
 */
@QuarkusTest
class ValidateServiceTest {

    @Inject
    ValidateService validateService;

    private Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("validate-test-");
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
    void testValidateNonExistentDir() {
        Path nonExistent = tempDir.resolve("nonexistent");
        ValidateService.ValidationResult result = validateService.validate(nonExistent);

        assertFalse(result.isValid());
        assertEquals(1, result.errors());
        assertTrue(result.issues().stream()
                .anyMatch(i -> i.message().contains("does not exist")));
    }

    @Test
    void testValidateEmptyDir() {
        ValidateService.ValidationResult result = validateService.validate(tempDir);

        assertFalse(result.isValid());
        assertTrue(result.errors() > 0);
        // Check that MANIFEST.MF is mentioned in file or message
        assertTrue(result.issues().stream()
                .anyMatch(i -> i.file().contains("MANIFEST") || i.message().contains("MANIFEST") ||
                              i.message().contains("File not found")));
    }

    @Test
    void testValidateWithMinimalManifest() throws IOException {
        Files.createDirectories(tempDir.resolve("META-INF"));
        Files.writeString(tempDir.resolve("META-INF/MANIFEST.MF"),
                "Manifest-Version: 1.0\n" +
                "Bundle-ManifestVersion: 2\n" +
                "Bundle-SymbolicName: org.test.plugin\n" +
                "Bundle-Version: 1.0.0\n" +
                "Bundle-RequiredExecutionEnvironment: JavaSE-17\n" +
                "Require-Bundle: org.adempiere.base\n");

        ValidateService.ValidationResult result = validateService.validate(tempDir);

        assertEquals("org.test.plugin", result.pluginId());
        // Will still have errors because no build.properties, pom.xml, etc.
        assertTrue(result.issues().stream()
                .anyMatch(i -> i.file().equals("build.properties")));
    }

    @Test
    void testValidateWithCompleteStructure() throws IOException {
        // Create complete plugin structure
        Files.createDirectories(tempDir.resolve("META-INF"));
        Files.writeString(tempDir.resolve("META-INF/MANIFEST.MF"),
                "Manifest-Version: 1.0\n" +
                "Bundle-ManifestVersion: 2\n" +
                "Bundle-SymbolicName: org.test.complete\n" +
                "Bundle-Version: 1.0.0\n" +
                "Bundle-Name: Test Plugin\n" +
                "Bundle-Vendor: Test\n" +
                "Bundle-RequiredExecutionEnvironment: JavaSE-17\n" +
                "Require-Bundle: org.adempiere.base\n");

        Files.writeString(tempDir.resolve("build.properties"),
                "source.. = src/\n" +
                "output.. = bin/\n" +
                "bin.includes = META-INF/,\\\n" +
                "               plugin.xml\n");

        Files.writeString(tempDir.resolve("pom.xml"),
                "<?xml version=\"1.0\"?>\n" +
                "<project>\n" +
                "  <artifactId>org.test.complete</artifactId>\n" +
                "  <packaging>eclipse-plugin</packaging>\n" +
                "  <properties><tycho.version>4.0.4</tycho.version></properties>\n" +
                "</project>\n");

        Files.writeString(tempDir.resolve("plugin.xml"), "<?xml version=\"1.0\"?>\n<plugin/>\n");

        Path srcDir = tempDir.resolve("src/org/test/complete");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("MyClass.java"),
                "package org.test.complete;\n\npublic class MyClass {}\n");

        ValidateService.ValidationResult result = validateService.validate(tempDir);

        assertEquals("org.test.complete", result.pluginId());
        // Should have fewer errors with complete structure
        assertTrue(result.errors() < 3);
    }

    @Test
    void testValidationIssueRecord() {
        ValidateService.ValidationIssue error = new ValidateService.ValidationIssue(
                ValidateService.Severity.ERROR, "test.java", "Error message");
        ValidateService.ValidationIssue warning = new ValidateService.ValidationIssue(
                ValidateService.Severity.WARNING, "test.java", "Warning message");
        ValidateService.ValidationIssue info = new ValidateService.ValidationIssue(
                ValidateService.Severity.INFO, "test.java", "Info message");

        assertTrue(error.toString().contains("ERROR"));
        assertTrue(warning.toString().contains("WARNING"));
        assertTrue(info.toString().contains("INFO"));
    }

    @Test
    void testValidationResultRecord() {
        ValidateService.ValidationResult valid = new ValidateService.ValidationResult(
                tempDir, "test.plugin", java.util.List.of(), 0, 0);
        assertTrue(valid.isValid());

        ValidateService.ValidationResult invalid = new ValidateService.ValidationResult(
                tempDir, "test.plugin", java.util.List.of(), 1, 0);
        assertFalse(invalid.isValid());
    }

    @Test
    void testValidateManifestWithCRLF() throws IOException {
        Files.createDirectories(tempDir.resolve("META-INF"));
        Files.writeString(tempDir.resolve("META-INF/MANIFEST.MF"),
                "Manifest-Version: 1.0\r\n" +
                "Bundle-ManifestVersion: 2\r\n" +
                "Bundle-SymbolicName: org.test.crlf\r\n" +
                "Bundle-Version: 1.0.0\r\n" +
                "Bundle-RequiredExecutionEnvironment: JavaSE-17\r\n" +
                "Require-Bundle: org.adempiere.base\r\n");

        ValidateService.ValidationResult result = validateService.validate(tempDir);

        // Should warn about CRLF line endings
        assertTrue(result.warnings() > 0);
        assertTrue(result.issues().stream()
                .anyMatch(i -> i.message().contains("CRLF") || i.message().contains("line endings")));
    }

    @Test
    void testValidateMissingAdempiereBase() throws IOException {
        Files.createDirectories(tempDir.resolve("META-INF"));
        Files.writeString(tempDir.resolve("META-INF/MANIFEST.MF"),
                "Manifest-Version: 1.0\n" +
                "Bundle-ManifestVersion: 2\n" +
                "Bundle-SymbolicName: org.test.nobase\n" +
                "Bundle-Version: 1.0.0\n" +
                "Bundle-RequiredExecutionEnvironment: JavaSE-17\n" +
                "Require-Bundle: org.other.bundle\n");

        ValidateService.ValidationResult result = validateService.validate(tempDir);

        // Should warn about missing org.adempiere.base
        assertTrue(result.issues().stream()
                .anyMatch(i -> i.message().contains("org.adempiere.base")));
    }
}
