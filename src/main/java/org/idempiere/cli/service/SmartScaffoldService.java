package org.idempiere.cli.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.model.GeneratedCode;
import org.idempiere.cli.model.ProjectContext;
import org.idempiere.cli.service.ai.AiClient;
import org.idempiere.cli.service.ai.AiClientFactory;
import org.idempiere.cli.service.ai.AiResponse;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI-powered code generation service.
 * Orchestrates skill loading, project analysis, AI client calls, and validation.
 * Returns empty if AI is not configured or generation fails.
 */
@ApplicationScoped
public class SmartScaffoldService {

    @Inject
    AiClientFactory aiClientFactory;

    @Inject
    SkillManager skillManager;

    @Inject
    ProjectAnalyzer projectAnalyzer;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Tries AI generation for a component. Returns the generated code if successful, empty otherwise.
     */
    public Optional<GeneratedCode> generate(String type, String name, Path pluginDir,
                                             String pluginId, Map<String, Object> extraData) {
        Optional<AiClient> client = aiClientFactory.getClient();
        if (client.isEmpty()) {
            return Optional.empty();
        }

        ProjectContext ctx = projectAnalyzer.analyze(pluginDir);
        return tryAiGeneration(type, name, pluginDir, pluginId, ctx, extraData, client.get());
    }

    private Optional<GeneratedCode> tryAiGeneration(String type, String name, Path pluginDir,
                                                     String pluginId, ProjectContext ctx,
                                                     Map<String, Object> extraData, AiClient client) {
        Optional<String> skill = skillManager.loadSkill(type);
        if (skill.isEmpty()) {
            return Optional.empty();
        }

        String prompt = buildAiPrompt(skill.get(), ctx, type, name, extraData);

        System.out.println("  Generating with AI (" + client.providerName() + ")...");
        AiResponse response = client.generate(prompt);

        if (!response.success()) {
            System.err.println("  AI generation failed: " + response.error());
            return Optional.empty();
        }

        GeneratedCode code = parseAiResponse(response.content());
        if (code == null || code.getFiles().isEmpty()) {
            System.err.println("  Failed to parse AI response");
            return Optional.empty();
        }

        List<String> issues = validateGeneratedCode(code, pluginId);
        if (!issues.isEmpty()) {
            System.err.println("  AI output validation warnings:");
            issues.forEach(i -> System.err.println("    - " + i));
        }

        try {
            code.writeTo(pluginDir);
            System.out.println("  Generated with AI");
            return Optional.of(code);
        } catch (IOException e) {
            System.err.println("  Failed to write AI-generated files: " + e.getMessage());
            return Optional.empty();
        }
    }

    String buildAiPrompt(String skill, ProjectContext ctx, String type,
                          String name, Map<String, Object> extraData) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are generating an iDempiere plugin component.\n\n");

        prompt.append("## Skill Instructions\n");
        prompt.append(skill).append("\n\n");

        prompt.append("## Project Context\n");
        prompt.append("- Plugin ID: ").append(ctx.getPluginId()).append("\n");
        prompt.append("- Base package: ").append(ctx.getBasePackage()).append("\n");
        if (ctx.getPlatformVersion() != null) {
            prompt.append("- Platform version: iDempiere ").append(ctx.getPlatformVersion().major()).append("\n");
        }
        if (!ctx.getExistingClasses().isEmpty()) {
            prompt.append("- Existing classes: ").append(ctx.getExistingClasses()).append("\n");
        }
        prompt.append("- Uses annotation pattern: ").append(ctx.usesAnnotationPattern()).append("\n");
        prompt.append("- Has Activator: ").append(ctx.hasActivator()).append("\n");
        prompt.append("- Has CalloutFactory: ").append(ctx.hasCalloutFactory()).append("\n");
        prompt.append("- Has EventManager: ").append(ctx.hasEventManager()).append("\n");

        if (ctx.getManifestContent() != null) {
            prompt.append("\n## Current MANIFEST.MF\n```\n").append(ctx.getManifestContent()).append("\n```\n");
        }

        prompt.append("\n## Task\n");
        prompt.append("Generate a ").append(type).append(" named ").append(name).append(".\n");

        if (extraData != null && !extraData.isEmpty()) {
            prompt.append("Additional parameters: ").append(extraData).append("\n");
        }

        prompt.append("""

                ## Output Format
                Respond with ONLY a JSON object (no markdown fences, no explanation):
                {
                  "files": [
                    {"path": "relative/path/from/plugin/root/File.java", "content": "full file content"}
                  ],
                  "manifest_additions": ["Import-Package lines to add"],
                  "build_properties_additions": ["lines to add to build.properties"]
                }

                IMPORTANT:
                - Paths are relative to the plugin root directory
                - Include full file content, not snippets
                - Use the exact package based on the Plugin ID
                - Follow the naming conventions visible in existing classes
                """);

        return prompt.toString();
    }

    GeneratedCode parseAiResponse(String raw) {
        try {
            String json = raw.strip();
            if (json.startsWith("```")) {
                json = json.replaceFirst("```json?\\n?", "").replaceFirst("```\\s*$", "").strip();
            }
            return objectMapper.readValue(json, GeneratedCode.class);
        } catch (Exception e) {
            return null;
        }
    }

    List<String> validateGeneratedCode(GeneratedCode code, String pluginId) {
        List<String> issues = new ArrayList<>();

        for (GeneratedCode.GeneratedFile file : code.getFiles()) {
            if (file.getPath() != null && file.getPath().contains("..")) {
                issues.add("Path traversal detected: " + file.getPath());
            }

            if (file.getPath() != null && file.getPath().endsWith(".java")) {
                if (file.getContent() != null && !file.getContent().contains("package " + pluginId)) {
                    issues.add("Unexpected package in " + file.getPath());
                }
            }

            if (file.getContent() == null || file.getContent().isBlank()) {
                issues.add("Empty content for " + file.getPath());
            }
        }

        return issues;
    }
}
