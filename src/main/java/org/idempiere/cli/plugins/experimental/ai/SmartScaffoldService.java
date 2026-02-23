package org.idempiere.cli.plugins.experimental.ai;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.model.GeneratedCode;
import org.idempiere.cli.model.ProjectContext;
import org.idempiere.cli.service.ProjectAnalyzer;
import org.idempiere.cli.service.SessionLogger;
import org.idempiere.cli.service.skills.SkillsService;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
@IfBuildProperty(name = "idempiere.experimental.ai.enabled", stringValue = "true")
public class SmartScaffoldService {

    @Inject
    AiClientFactory aiClientFactory;

    @Inject
    SkillsService skillsService;

    @Inject
    ProjectAnalyzer projectAnalyzer;

    @Inject
    SessionLogger sessionLogger;

    @Inject
    AiPromptBuilderService aiPromptBuilderService;

    @Inject
    AiResponseParserService aiResponseParserService;

    @Inject
    AiGuardrailService aiGuardrailService;

    private static final int MAX_ISSUES_TO_PRINT = 10;
    private static final DateTimeFormatter DEBUG_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

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
        boolean showAiPrompt = optionEnabled(extraData, "showAiPrompt");
        boolean saveAiDebug = optionEnabled(extraData, "saveAiDebug");
        String userPrompt = extractUserPrompt(extraData);

        Optional<String> skill = skillsService.loadSkill(type);

        // If no skill file and no user prompt, skip AI generation
        boolean hasUserPrompt = userPrompt != null && !userPrompt.isBlank();
        if (skill.isEmpty() && !hasUserPrompt) {
            return Optional.empty();
        }

        Map<String, Object> effectiveExtraData = prepareExtraData(extraData, pluginDir, type, userPrompt);
        String prompt = buildAiPrompt(skill.orElse(null), ctx, type, name, effectiveExtraData);
        sessionLogger.logCommandOutput("ai-prompt", prompt);
        Path debugFile = null;

        if (showAiPrompt) {
            printAiPrompt(prompt);
        }
        if (saveAiDebug) {
            debugFile = initializeAiDebugFile(pluginDir, type, name, prompt);
        }

        System.out.println("  Generating with AI (" + client.providerName() + ")...");
        AiResponse response = client.generate(prompt);

        if (!response.success()) {
            sessionLogger.logError("AI generation failed: " + response.error());
            System.err.println("  AI generation failed: " + response.error());
            appendAiDebug(debugFile, "AI ERROR", response.error());
            return Optional.empty();
        }

        sessionLogger.logCommandOutput("ai-response", response.content());
        appendAiDebug(debugFile, "AI RESPONSE", response.content());

        AiResponseParserService.ParseResult parsed = parseAiResponseDetailed(response.content());
        GeneratedCode code = parsed.code();
        if (code == null || code.getFiles().isEmpty()) {
            sessionLogger.logError("AI parse failed: " + parsed.errorMessage());
            sessionLogger.logCommandOutput("ai-response-raw", response.content());
            System.err.println("  Failed to parse AI response. Falling back to template generation.");
            System.err.println("  See log for details: " + currentLogPathHint());
            appendAiDebug(debugFile, "PARSE ERROR", parsed.errorMessage());
            return Optional.empty();
        }

        List<String> issues = validateGeneratedCode(code, pluginId, pluginDir);
        if (!issues.isEmpty()) {
            System.err.println("  AI output validation warnings:");
            issues.stream().limit(MAX_ISSUES_TO_PRINT).forEach(i -> System.err.println("    - " + i));
            if (issues.size() > MAX_ISSUES_TO_PRINT) {
                System.err.println("    - ... and " + (issues.size() - MAX_ISSUES_TO_PRINT) + " more");
            }
            sessionLogger.logCommandOutput("ai-validation-issues", String.join("\n", issues));
        }

        if (aiGuardrailService.hasBlockingIssue(issues)) {
            sessionLogger.logError("AI output has guardrail blockers (non-blocking mode): " + String.join("; ", issues));
            System.err.println("  Warning: AI output may be incompatible with current target platform.");
            System.err.println("  Review manually before build/deploy.");
            System.err.println("  See log for details: " + currentLogPathHint());
            appendAiDebug(debugFile, "GUARDRAIL BLOCKERS (NON-BLOCKING)", String.join("\n", issues));
        }

        try {
            code.writeTo(pluginDir);
            System.out.println("  Generated with AI");
            if (hasUserPrompt) {
                System.out.println("  Note: AI output may require manual review against your target platform.");
            }
            appendAiDebug(debugFile, "RESULT", "Generated with AI and written to disk.");
            return Optional.of(code);
        } catch (IOException e) {
            sessionLogger.logError("Failed to write AI-generated files: " + e.getMessage());
            System.err.println("  Failed to write AI-generated files: " + e.getMessage());
            appendAiDebug(debugFile, "WRITE ERROR", e.getMessage());
            return Optional.empty();
        }
    }

    /** Backward-compatible alias used in tests and callers. */
    static final Map<String, String> COMPONENT_DESCRIPTIONS = AiPromptBuilderService.COMPONENT_DESCRIPTIONS;

    String buildAiPrompt(String skill, ProjectContext ctx, String type,
                          String name, Map<String, Object> extraData) {
        return aiPromptBuilderService.buildAiPrompt(skill, ctx, type, name, extraData);
    }

    GeneratedCode parseAiResponse(String raw) {
        return aiResponseParserService.parse(raw);
    }

    private AiResponseParserService.ParseResult parseAiResponseDetailed(String raw) {
        return aiResponseParserService.parseDetailed(raw);
    }

    private String currentLogPathHint() {
        Path sessionLog = sessionLogger.getSessionLogFile();
        if (sessionLog != null) {
            return sessionLog.toAbsolutePath().toString();
        }
        return "~/.idempiere-cli/logs/latest.log";
    }

    private String extractUserPrompt(Map<String, Object> extraData) {
        if (extraData == null) {
            return null;
        }
        if (extraData.get("prompt") instanceof String prompt && !prompt.isBlank()) {
            return prompt;
        }
        return null;
    }

    private boolean optionEnabled(Map<String, Object> extraData, String key) {
        if (extraData == null) {
            return false;
        }
        Object value = extraData.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str) {
            return Boolean.parseBoolean(str);
        }
        return false;
    }

    private Map<String, Object> prepareExtraData(Map<String, Object> extraData,
                                                 Path pluginDir,
                                                 String type,
                                                 String userPrompt) {
        Map<String, Object> merged = extraData == null ? new java.util.HashMap<>() : new java.util.HashMap<>(extraData);
        aiGuardrailService.buildPromptContext(pluginDir, type, userPrompt, 20, 80)
                .ifPresent(context -> {
                    merged.put("_targetContextRepo", context.repositoryPath().toString());
                    merged.put("_targetContextPackages", context.packages());
                    merged.put("_targetContextClasses", context.classes());
                });
        return merged.isEmpty() ? null : merged;
    }

    private void printAiPrompt(String prompt) {
        System.out.println("  AI prompt (audit):");
        System.out.println("  --------------------");
        for (String line : prompt.split("\\R")) {
            System.out.println("  " + line);
        }
        System.out.println("  --------------------");
    }

    private Path initializeAiDebugFile(Path pluginDir, String type, String name, String prompt) {
        try {
            Path debugDir = pluginDir.resolve(".idempiere-cli").resolve("ai-debug");
            java.nio.file.Files.createDirectories(debugDir);
            String timestamp = DEBUG_TS.format(Instant.now());
            String safeType = sanitizeName(type);
            String safeName = sanitizeName(name);
            Path debugFile = debugDir.resolve(timestamp + "-" + safeType + "-" + safeName + ".log");
            appendAiDebug(debugFile, "AI PROMPT", prompt);
            System.out.println("  AI debug saved: " + debugFile.toAbsolutePath());
            return debugFile;
        } catch (IOException e) {
            System.err.println("  Warning: could not initialize AI debug file: " + e.getMessage());
            return null;
        }
    }

    private void appendAiDebug(Path debugFile, String section, String content) {
        if (debugFile == null) {
            return;
        }
        try {
            List<String> lines = new ArrayList<>();
            lines.add("### " + section);
            lines.add(content == null ? "(empty)" : content);
            lines.add("");
            java.nio.file.Files.write(
                    debugFile,
                    lines,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {
            // Best effort only; session log already contains core diagnostics.
        }
    }

    private String sanitizeName(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    List<String> validateGeneratedCode(GeneratedCode code, String pluginId) {
        return aiGuardrailService.validateGeneratedCode(code, pluginId);
    }

    List<String> validateGeneratedCode(GeneratedCode code, String pluginId, Path pluginDir) {
        return aiGuardrailService.validateGeneratedCode(code, pluginId, pluginDir);
    }
}
