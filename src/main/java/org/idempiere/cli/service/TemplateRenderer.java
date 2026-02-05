package org.idempiere.cli.service;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders Qute templates for code generation.
 */
@ApplicationScoped
public class TemplateRenderer {

    @Inject
    Engine engine;

    private final Map<String, Template> templateCache = new ConcurrentHashMap<>();

    public void render(String templatePath, Map<String, Object> data, Path outputFile) throws IOException {
        render(templatePath, data, outputFile, false);
    }

    public void render(String templatePath, Map<String, Object> data, Path outputFile, boolean allowOverwrite) throws IOException {
        if (!allowOverwrite && Files.exists(outputFile)) {
            System.err.println("  Skipped: " + outputFile + " (already exists)");
            return;
        }

        Template template = templateCache.computeIfAbsent(templatePath, this::loadTemplate);

        var instance = template.instance();
        data.forEach(instance::data);
        String rendered = instance.render();

        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, rendered);
        System.out.println("  Created: " + outputFile);
    }

    /**
     * Copy a resource file directly without Qute template processing.
     * Use this for files that have syntax conflicts with Qute (e.g., ZUL files that use ${...}).
     */
    public void copyResource(String resourcePath, Path outputFile) throws IOException {
        copyResource(resourcePath, outputFile, false);
    }

    public void copyResource(String resourcePath, Path outputFile, boolean allowOverwrite) throws IOException {
        if (!allowOverwrite && Files.exists(outputFile)) {
            System.err.println("  Skipped: " + outputFile + " (already exists)");
            return;
        }

        String fullPath = "templates/" + resourcePath;
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(fullPath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + fullPath);
            }
            Files.createDirectories(outputFile.getParent());
            Files.copy(is, outputFile);
            System.out.println("  Created: " + outputFile);
        }
    }

    private Template loadTemplate(String templatePath) {
        String resourcePath = "templates/" + templatePath + ".qute";
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Template not found: " + resourcePath);
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return engine.parse(content);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to load template: " + resourcePath, e);
        }
    }
}
