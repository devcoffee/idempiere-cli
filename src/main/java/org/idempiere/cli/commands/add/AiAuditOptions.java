package org.idempiere.cli.commands.add;

import picocli.CommandLine.Option;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared AI audit options for add subcommands that support --prompt.
 */
public class AiAuditOptions {

    static final String EXTRA_SHOW_AI_PROMPT = "showAiPrompt";
    static final String EXTRA_SAVE_AI_DEBUG = "saveAiDebug";

    @Option(names = "--show-ai-prompt", description = "Print the full AI prompt before generation")
    boolean showAiPrompt;

    @Option(names = "--save-ai-debug", description = "Save AI prompt/response debug artifact under .idempiere-cli/ai-debug")
    boolean saveAiDebug;

    Map<String, Object> createExtraData(String prompt) {
        Map<String, Object> extraData = new HashMap<>();
        if (prompt != null && !prompt.isBlank()) {
            extraData.put("prompt", prompt);
        }
        applyAuditFlags(extraData);
        return extraData.isEmpty() ? null : extraData;
    }

    void applyAuditFlags(Map<String, Object> extraData) {
        if (showAiPrompt) {
            extraData.put(EXTRA_SHOW_AI_PROMPT, true);
        }
        if (saveAiDebug) {
            extraData.put(EXTRA_SAVE_AI_DEBUG, true);
        }
    }
}
