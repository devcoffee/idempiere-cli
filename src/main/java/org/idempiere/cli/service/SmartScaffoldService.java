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

    @Inject
    SessionLogger sessionLogger;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private record ParseResult(GeneratedCode code, String errorMessage) {
        static ParseResult success(GeneratedCode code) {
            return new ParseResult(code, null);
        }

        static ParseResult failure(String errorMessage) {
            return new ParseResult(null, errorMessage);
        }
    }

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

        // If no skill file and no user prompt, skip AI generation
        boolean hasUserPrompt = extraData != null && extraData.get("prompt") instanceof String p && !p.isBlank();
        if (skill.isEmpty() && !hasUserPrompt) {
            return Optional.empty();
        }

        String prompt = buildAiPrompt(skill.orElse(null), ctx, type, name, extraData);
        sessionLogger.logCommandOutput("ai-prompt", prompt);

        System.out.println("  Generating with AI (" + client.providerName() + ")...");
        AiResponse response = client.generate(prompt);

        if (!response.success()) {
            sessionLogger.logError("AI generation failed: " + response.error());
            System.err.println("  AI generation failed: " + response.error());
            return Optional.empty();
        }

        sessionLogger.logCommandOutput("ai-response", response.content());

        ParseResult parsed = parseAiResponseDetailed(response.content());
        GeneratedCode code = parsed.code();
        if (code == null || code.getFiles().isEmpty()) {
            sessionLogger.logError("AI parse failed: " + parsed.errorMessage());
            sessionLogger.logCommandOutput("ai-response-raw", response.content());
            System.err.println("  Failed to parse AI response. Falling back to template generation.");
            System.err.println("  See log for details: " + currentLogPathHint());
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
            sessionLogger.logError("Failed to write AI-generated files: " + e.getMessage());
            System.err.println("  Failed to write AI-generated files: " + e.getMessage());
            return Optional.empty();
        }
    }

    /** Built-in component descriptions used when no SKILL.md is available. */
    static final Map<String, String> COMPONENT_DESCRIPTIONS = Map.ofEntries(
            Map.entry("callout", "An iDempiere column-level callout implementing IColumnCallout. "
                    + "Use @Callout(tableName, columnName) annotation for registration. "
                    + "The existing CalloutFactory scans the package for all @Callout classes automatically."),
            Map.entry("process", "An iDempiere server-side process extending SvrProcess. "
                    + "Use @Process annotation with its own AnnotationBasedProcessFactory."),
            Map.entry("process-mapped", "An iDempiere process using MappedProcessFactory (2Pack compatible). "
                    + "Extends SvrProcess, registered via MappedProcessFactory in Activator."),
            Map.entry("event-handler", "An iDempiere model event handler using @EventDelegate annotation. "
                    + "Handles lifecycle events like BeforeNew, AfterChange on model objects."),
            Map.entry("zk-form", "A ZK programmatic form extending ADForm for iDempiere UI."),
            Map.entry("zk-form-zul", "A ZUL-based form with separate .zul layout file and Controller class."),
            Map.entry("listbox-group", "A form with grouped/collapsible Listbox using GroupsModel."),
            Map.entry("wlistbox-editor", "A form with custom WListbox column editors."),
            Map.entry("report", "An iDempiere report process extending SvrProcess."),
            Map.entry("jasper-report", "A Jasper report with Activator and sample .jrxml template."),
            Map.entry("window-validator", "An iDempiere window-level event validator."),
            Map.entry("rest-extension", "A REST API resource extension using JAX-RS annotations."),
            Map.entry("facts-validator", "An iDempiere accounting facts validator."),
            Map.entry("base-test", "A JUnit test class using AbstractTestCase (iDempiere test infrastructure).")
    );

    String buildAiPrompt(String skill, ProjectContext ctx, String type,
                          String name, Map<String, Object> extraData) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are generating an iDempiere plugin component.\n\n");

        if (skill != null) {
            prompt.append("## Skill Instructions\n");
            prompt.append(skill).append("\n\n");
        } else {
            prompt.append("## Component Type\n");
            prompt.append(COMPONENT_DESCRIPTIONS.getOrDefault(type,
                    "An iDempiere " + type + " component.")).append("\n\n");
        }

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

        if (extraData != null) {
            String userPrompt = (String) extraData.get("prompt");
            if (userPrompt != null && !userPrompt.isBlank()) {
                prompt.append("\n## User Instructions\n");
                prompt.append(userPrompt).append("\n");
            }
            // Pass remaining extraData (excluding "prompt") as additional parameters
            Map<String, Object> params = new java.util.HashMap<>(extraData);
            params.remove("prompt");
            if (!params.isEmpty()) {
                prompt.append("Additional parameters: ").append(params).append("\n");
            }
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
        return parseAiResponseDetailed(raw).code();
    }

    private ParseResult parseAiResponseDetailed(String raw) {
        if (raw == null || raw.isBlank()) {
            return ParseResult.failure("AI response is empty");
        }

        // 1. Try raw as-is
        ParseResult result = tryParseJson(raw.strip(), "raw response");
        if (result.code() != null) return result;
        String lastError = result.errorMessage();

        // 2. Try extracting from markdown code fences (```json ... ``` or ``` ... ```)
        java.util.regex.Matcher fenceMatcher = java.util.regex.Pattern
                .compile("```(?:json)?\\s*\\n?(\\{.*?\\})\\s*```", java.util.regex.Pattern.DOTALL)
                .matcher(raw);
        if (fenceMatcher.find()) {
            result = tryParseJson(fenceMatcher.group(1).strip(), "markdown code fence");
            if (result.code() != null) return result;
            lastError = result.errorMessage();
        }

        // 3. Try extracting the outermost { ... } block
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            result = tryParseJson(raw.substring(start, end + 1).strip(), "outer JSON block");
            if (result.code() != null) return result;
            lastError = result.errorMessage();
        }

        return ParseResult.failure(lastError != null ? lastError : "No parseable JSON object found in AI response");
    }

    private ParseResult tryParseJson(String json, String source) {
        try {
            GeneratedCode code = objectMapper.readValue(json, GeneratedCode.class);
            if (code != null && code.getFiles() != null && !code.getFiles().isEmpty()) {
                return ParseResult.success(code);
            }
            return ParseResult.failure("Parsed " + source + " but files array is empty");
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            message = message.replace('\n', ' ').replace('\r', ' ');
            return ParseResult.failure("Invalid JSON in " + source + ": " + message);
        }
    }

    private String currentLogPathHint() {
        Path sessionLog = sessionLogger.getSessionLogFile();
        if (sessionLog != null) {
            return sessionLog.toAbsolutePath().toString();
        }
        return "~/.idempiere-cli/logs/latest.log";
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
