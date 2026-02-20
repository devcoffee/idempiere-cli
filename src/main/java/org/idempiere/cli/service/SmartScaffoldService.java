package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.model.GeneratedCode;
import org.idempiere.cli.model.ProjectContext;
import org.idempiere.cli.service.ai.AiClient;
import org.idempiere.cli.service.ai.AiClientFactory;
import org.idempiere.cli.service.ai.AiResponse;

import java.io.IOException;
import java.nio.file.Path;
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

    @Inject
    AiPromptBuilderService aiPromptBuilderService;

    @Inject
    AiResponseParserService aiResponseParserService;

    @Inject
    AiGuardrailService aiGuardrailService;

    private static final int MAX_ISSUES_TO_PRINT = 10;

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

        AiResponseParserService.ParseResult parsed = parseAiResponseDetailed(response.content());
        GeneratedCode code = parsed.code();
        if (code == null || code.getFiles().isEmpty()) {
            sessionLogger.logError("AI parse failed: " + parsed.errorMessage());
            sessionLogger.logCommandOutput("ai-response-raw", response.content());
            System.err.println("  Failed to parse AI response. Falling back to template generation.");
            System.err.println("  See log for details: " + currentLogPathHint());
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
            sessionLogger.logError("AI output rejected by classpath guardrails.");
            System.err.println("  AI output incompatible with current target platform. Falling back to template generation.");
            System.err.println("  See log for details: " + currentLogPathHint());
            return Optional.empty();
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

    List<String> validateGeneratedCode(GeneratedCode code, String pluginId) {
        return aiGuardrailService.validateGeneratedCode(code, pluginId);
    }

    List<String> validateGeneratedCode(GeneratedCode code, String pluginId, Path pluginDir) {
        return aiGuardrailService.validateGeneratedCode(code, pluginId, pluginDir);
    }
}
