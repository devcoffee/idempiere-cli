package org.idempiere.cli.service;

import org.idempiere.cli.model.ProjectContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPromptBuilderServiceTest {

    private final AiPromptBuilderService service = new AiPromptBuilderService();

    @Test
    void testBuildPromptWithoutSkillUsesComponentDescription() {
        ProjectContext ctx = ProjectContext.builder()
                .pluginId("org.example.myplugin")
                .basePackage("org.example.myplugin")
                .version("1.0.0")
                .build();

        String prompt = service.buildAiPrompt(
                null, ctx, "callout", "MyCallout",
                Map.of("prompt", "Fill Description from Name")
        );

        assertFalse(prompt.contains("Skill Instructions"));
        assertTrue(prompt.contains("Component Type"));
        assertTrue(prompt.contains("IColumnCallout"));
        assertTrue(prompt.contains("Fill Description from Name"));
    }

    @Test
    void testBuildPromptWithSkillIncludesSkillSectionAndParams() {
        ProjectContext ctx = ProjectContext.builder()
                .pluginId("org.example.myplugin")
                .basePackage("org.example.myplugin")
                .version("1.0.0")
                .hasActivator(true)
                .build();

        String prompt = service.buildAiPrompt(
                "# Skill Header", ctx, "process", "MyProcess",
                Map.of("tableName", "C_Order")
        );

        assertTrue(prompt.contains("Skill Instructions"));
        assertTrue(prompt.contains("# Skill Header"));
        assertTrue(prompt.contains("Has Activator: true"));
        assertTrue(prompt.contains("Additional parameters: {tableName=C_Order}"));
    }
}
