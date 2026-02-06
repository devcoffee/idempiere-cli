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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TemplateRendererTest {

    @Inject
    TemplateRenderer templateRenderer;

    private Path tempDir;
    private Path outputDir;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("template-test-");
        outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);
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
    void testRenderBuiltInTemplate() throws IOException {
        Path outputFile = outputDir.resolve("Test.java");

        Map<String, Object> data = Map.of(
                "pluginId", "com.example",
                "className", "TestProcess"
        );

        templateRenderer.render("process/Process.java", data, outputFile);

        assertTrue(Files.exists(outputFile));
        String content = Files.readString(outputFile);
        assertTrue(content.contains("package com.example;"));
        assertTrue(content.contains("class TestProcess"));
    }

    @Test
    void testRenderSkipsExistingFile() throws IOException {
        Path outputFile = outputDir.resolve("Existing.java");
        Files.writeString(outputFile, "original content");

        Map<String, Object> data = Map.of(
                "pluginId", "com.example",
                "className", "TestProcess"
        );

        templateRenderer.render("process/Process.java", data, outputFile);

        String content = Files.readString(outputFile);
        assertEquals("original content", content);
    }

    @Test
    void testRenderOverwritesWithFlag() throws IOException {
        Path outputFile = outputDir.resolve("Overwrite.java");
        Files.writeString(outputFile, "original content");

        Map<String, Object> data = Map.of(
                "pluginId", "com.example",
                "className", "TestProcess"
        );

        templateRenderer.render("process/Process.java", data, outputFile, true);

        String content = Files.readString(outputFile);
        assertNotEquals("original content", content);
        assertTrue(content.contains("class TestProcess"));
    }

    @Test
    void testCopyResource() throws IOException {
        Path outputFile = outputDir.resolve("form.zul");

        templateRenderer.copyResource("zk-form-zul/form.zul", outputFile);

        assertTrue(Files.exists(outputFile));
        String content = Files.readString(outputFile);
        assertTrue(content.contains("<?xml") || content.contains("<zk") || content.contains("window"));
    }

    @Test
    void testCopyResourceSkipsExisting() throws IOException {
        Path outputFile = outputDir.resolve("form.zul");
        Files.writeString(outputFile, "original zul");

        templateRenderer.copyResource("zk-form-zul/form.zul", outputFile);

        String content = Files.readString(outputFile);
        assertEquals("original zul", content);
    }

    @Test
    void testTemplateNotFoundThrowsException() {
        Path outputFile = outputDir.resolve("Test.java");

        Map<String, Object> data = Map.of("key", "value");

        assertThrows(IllegalArgumentException.class, () ->
                templateRenderer.render("non-existent/template", data, outputFile)
        );
    }
}
