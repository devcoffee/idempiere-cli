package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.idempiere.cli.model.GeneratedCode;
import org.idempiere.cli.model.ProjectContext;
import org.idempiere.cli.plugin.add.AddGenerationPlugin;
import org.idempiere.cli.service.generator.ComponentGenerator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles component addition flow: AI generation, template fallback, and session logging.
 */
@ApplicationScoped
public class AddComponentService {

    @Inject
    Instance<ComponentGenerator> generators;

    @Inject
    Instance<AddGenerationPlugin> generationPlugins;

    @Inject
    ProjectAnalyzer projectAnalyzer;

    @Inject
    SessionLogger sessionLogger;

    public ScaffoldResult addComponent(String type, String name, Path pluginDir, String pluginId, Map<String, Object> extraData) {
        boolean startedSession = false;
        boolean success = false;

        if (!sessionLogger.isActive()) {
            sessionLogger.startSession(buildAddCommandLine(type, name, pluginDir, extraData));
            startedSession = true;
        }

        System.out.println();
        System.out.println("Adding " + type + ": " + name);
        System.out.println();

        try {
            // 1. Try non-template generation plugins (experimental/optional)
            boolean generatedByPlugin = tryGenerationPlugins(type, name, pluginDir, pluginId, extraData);

            if (!generatedByPlugin) {
                // 2. Deterministic template fallback (core default)
                if (extraData != null && extraData.get("prompt") instanceof String prompt && !prompt.isBlank()) {
                    if (generationPlugins.isUnsatisfied()) {
                        System.out.println("  AI/experimental generation is not enabled in this build. Using deterministic template.");
                    } else {
                        System.out.println("  No experimental generator produced output. Using deterministic template.");
                    }
                }
                ScaffoldResult templateResult = templateGeneration(type, name, pluginDir, pluginId, extraData);
                if (!templateResult.success()) {
                    return templateResult;
                }
            }

            System.out.println();
            System.out.println("Component added successfully!");
            System.out.println();
            success = true;
            return ScaffoldResult.ok(pluginDir);
        } catch (IOException e) {
            return ioError("Error adding component", e);
        } finally {
            if (startedSession) {
                sessionLogger.endSession(success);
            }
        }
    }

    private boolean tryGenerationPlugins(String type,
                                         String name,
                                         Path pluginDir,
                                         String pluginId,
                                         Map<String, Object> extraData) throws IOException {
        List<AddGenerationPlugin> plugins = generationPlugins.stream()
                .sorted(Comparator.comparingInt(AddGenerationPlugin::order))
                .toList();
        if (plugins.isEmpty()) {
            return false;
        }

        ProjectContext ctx = projectAnalyzer.analyze(pluginDir);
        for (AddGenerationPlugin plugin : plugins) {
            Optional<AddGenerationPlugin.AddGenerationResult> result = plugin.tryGenerate(
                    type, name, pluginDir, pluginId, ctx, extraData
            );
            if (result.isEmpty()) {
                continue;
            }
            if (!result.get().generated()) {
                continue;
            }
            GeneratedCode code = result.get().generatedCode();
            if (code == null) {
                continue;
            }
            code.writeTo(pluginDir);
            return true;
        }
        return false;
    }

    private String buildAddCommandLine(String type, String name, Path pluginDir, Map<String, Object> extraData) {
        StringBuilder cmd = new StringBuilder("add ").append(type)
                .append(" --name=").append(name)
                .append(" --to=").append(pluginDir.toAbsolutePath());

        if (extraData != null && extraData.get("prompt") instanceof String prompt && !prompt.isBlank()) {
            cmd.append(" --prompt=<provided>");
        }
        if (extraData != null && Boolean.TRUE.equals(extraData.get("showAiPrompt"))) {
            cmd.append(" --show-ai-prompt");
        }
        if (extraData != null && Boolean.TRUE.equals(extraData.get("saveAiDebug"))) {
            cmd.append(" --save-ai-debug");
        }
        return cmd.toString();
    }

    private ScaffoldResult templateGeneration(String type, String name, Path pluginDir,
                                              String pluginId, Map<String, Object> extraData) throws IOException {
        Optional<ComponentGenerator> generator = findGenerator(type);
        if (generator.isEmpty()) {
            return unknownComponentTypeError(type);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("pluginId", pluginId);
        data.put("className", name);
        if (extraData != null) {
            data.putAll(extraData);
        }

        Path srcDir = pluginDir.resolve("src").resolve(pluginId.replace('.', '/'));
        generator.get().addToExisting(srcDir, pluginDir, data);
        return ScaffoldResult.ok(pluginDir);
    }

    private Optional<ComponentGenerator> findGenerator(String type) {
        return generators.stream()
                .filter(g -> g.type().equals(type))
                .findFirst();
    }

    private ScaffoldResult ioError(String context, IOException e) {
        String message = context + ": " + e.getMessage();
        System.err.println(message);
        return ScaffoldResult.error("IO_ERROR", message);
    }

    private ScaffoldResult unknownComponentTypeError(String type) {
        String message = "Unknown component type: " + type;
        System.err.println(message);
        return ScaffoldResult.error("UNKNOWN_COMPONENT_TYPE", message);
    }
}
