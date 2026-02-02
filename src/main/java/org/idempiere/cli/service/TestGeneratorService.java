package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

@ApplicationScoped
public class TestGeneratorService {

    @Inject
    TemplateRenderer templateRenderer;

    private static final Map<String, String> COMPONENT_PATTERNS = Map.of(
            "extends SvrProcess", "process",
            "implements IColumnCallout", "callout",
            "extends ModelEventDelegate", "event-handler",
            "extends AbstractEventHandler", "event-handler",
            "extends FactsValidateDelegate", "facts-validator"
    );

    public void generateAllTests(Path pluginDir, String pluginId) {
        Path srcDir = pluginDir.resolve("src").resolve(pluginId.replace('.', '/'));
        if (!Files.exists(srcDir)) {
            System.err.println("  Error: Source directory not found: " + srcDir);
            return;
        }

        Path testDir = pluginDir.resolve("test").resolve(pluginId.replace('.', '/'));

        try (Stream<Path> walk = Files.list(srcDir)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().endsWith("Factory.java"))
                    .filter(p -> !p.getFileName().toString().startsWith("I_"))
                    .filter(p -> !p.getFileName().toString().startsWith("X_"))
                    .forEach(javaFile -> {
                        String className = javaFile.getFileName().toString().replace(".java", "");
                        generateTestForFile(javaFile, className, pluginId, testDir);
                    });
        } catch (IOException e) {
            System.err.println("  Error scanning source files: " + e.getMessage());
        }
    }

    public void generateTestFor(String className, Path pluginDir, String pluginId) {
        Path srcDir = pluginDir.resolve("src").resolve(pluginId.replace('.', '/'));
        Path javaFile = srcDir.resolve(className + ".java");

        if (!Files.exists(javaFile)) {
            System.err.println("  Error: File not found: " + javaFile);
            return;
        }

        Path testDir = pluginDir.resolve("test").resolve(pluginId.replace('.', '/'));
        generateTestForFile(javaFile, className, pluginId, testDir);
    }

    private void generateTestForFile(Path javaFile, String className, String pluginId, Path testDir) {
        try {
            String content = Files.readString(javaFile);
            String componentType = detectComponentType(content);

            String templateName = switch (componentType) {
                case "process" -> "test/ProcessTest.java";
                case "callout" -> "test/CalloutTest.java";
                case "event-handler" -> "test/EventHandlerTest.java";
                default -> "test/ComponentTest.java";
            };

            Path testFile = testDir.resolve(className + "Test.java");
            if (Files.exists(testFile)) {
                System.out.println("  Skipped: " + testFile.getFileName() + " (already exists)");
                return;
            }

            Files.createDirectories(testDir);

            Map<String, Object> data = new HashMap<>();
            data.put("pluginId", pluginId);
            data.put("className", className);

            templateRenderer.render(templateName, data, testFile);
        } catch (IOException e) {
            System.err.println("  Error generating test for " + className + ": " + e.getMessage());
        }
    }

    private String detectComponentType(String content) {
        for (var entry : COMPONENT_PATTERNS.entrySet()) {
            if (content.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "generic";
    }
}
