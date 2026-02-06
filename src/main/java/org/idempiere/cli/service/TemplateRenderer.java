package org.idempiere.cli.service;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.model.CliConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders Qute templates for code generation.
 *
 * <p>Supports custom template paths via configuration:
 * <ul>
 *   <li>Custom templates from config: {@code templates.path} in .idempiere-cli.yaml</li>
 *   <li>Falls back to built-in templates if custom not found</li>
 * </ul>
 *
 * <h2>Example Configuration</h2>
 * <pre>
 * templates:
 *   path: ~/.idempiere-cli/templates
 * </pre>
 *
 * <p>Custom templates follow the same structure as built-in:
 * <pre>
 * ~/.idempiere-cli/templates/
 * ├── plugin/
 * │   ├── pom.xml.qute
 * │   └── MANIFEST.MF.qute
 * ├── process/
 * │   └── Process.java.qute
 * └── ...
 * </pre>
 */
@ApplicationScoped
public class TemplateRenderer {

    @Inject
    Engine engine;

    @Inject
    CliConfigService configService;

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

        Files.createDirectories(outputFile.getParent());

        // Try custom resource path first
        Path customResource = findCustomResource(resourcePath);
        if (customResource != null) {
            if (allowOverwrite) {
                Files.copy(customResource, outputFile, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(customResource, outputFile);
            }
            System.out.println("  Created: " + outputFile);
            return;
        }

        // Fallback to built-in resource
        String fullPath = "templates/" + resourcePath;
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(fullPath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + fullPath);
            }
            if (allowOverwrite) {
                Files.copy(is, outputFile, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(is, outputFile);
            }
            System.out.println("  Created: " + outputFile);
        }
    }

    /**
     * Finds a custom resource file if configured.
     *
     * @param resourcePath the relative resource path (e.g., "zk-form-zul/form.zul")
     * @return the Path to custom resource, or null if not found
     */
    private Path findCustomResource(String resourcePath) {
        CliConfig config = configService.loadConfig();
        if (!config.getTemplates().hasPath()) {
            return null;
        }

        String customBasePath = config.getTemplates().getPath();
        Path resolvedPath = resolvePath(customBasePath).resolve(resourcePath);

        if (Files.exists(resolvedPath)) {
            return resolvedPath;
        }

        return null;
    }

    private Template loadTemplate(String templatePath) {
        // Try custom template path first
        Path customTemplate = findCustomTemplate(templatePath);
        if (customTemplate != null) {
            try {
                String content = Files.readString(customTemplate);
                return engine.parse(content);
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to load custom template: " + customTemplate, e);
            }
        }

        // Fallback to built-in template
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

    /**
     * Finds a custom template file if configured.
     *
     * @param templatePath the relative template path (e.g., "plugin/pom.xml")
     * @return the Path to custom template, or null if not found
     */
    private Path findCustomTemplate(String templatePath) {
        CliConfig config = configService.loadConfig();
        if (!config.getTemplates().hasPath()) {
            return null;
        }

        String customBasePath = config.getTemplates().getPath();
        Path resolvedPath = resolvePath(customBasePath).resolve(templatePath + ".qute");

        if (Files.exists(resolvedPath)) {
            return resolvedPath;
        }

        return null;
    }

    /**
     * Resolves a path, expanding ~ to user home directory.
     *
     * @param path the path string (may contain ~)
     * @return the resolved Path
     */
    private Path resolvePath(String path) {
        if (path.startsWith("~")) {
            String home = System.getProperty("user.home");
            return Path.of(home + path.substring(1));
        }
        return Path.of(path);
    }
}
