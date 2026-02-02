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

@ApplicationScoped
public class TemplateRenderer {

    @Inject
    Engine engine;

    private final Map<String, Template> templateCache = new ConcurrentHashMap<>();

    public void render(String templatePath, Map<String, Object> data, Path outputFile) throws IOException {
        Template template = templateCache.computeIfAbsent(templatePath, this::loadTemplate);

        var instance = template.instance();
        data.forEach(instance::data);
        String rendered = instance.render();

        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, rendered);
        System.out.println("  Created: " + outputFile);
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
