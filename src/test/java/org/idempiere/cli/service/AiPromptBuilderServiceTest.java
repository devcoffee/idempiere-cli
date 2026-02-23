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

    @Test
    void testBuildPromptIncludesTargetPlatformContextSection() {
        ProjectContext ctx = ProjectContext.builder()
                .pluginId("org.example.myplugin")
                .basePackage("org.example.myplugin")
                .version("1.0.0")
                .build();

        String prompt = service.buildAiPrompt(
                null, ctx, "process", "MyProcess",
                Map.of(
                        "prompt", "Generate process logic",
                        "_targetContextRepo", "/tmp/ws/idempiere/org.idempiere.p2/target/repository",
                        "_targetContextPackages", java.util.List.of("org.compiere.process", "org.compiere.model"),
                        "_targetContextClasses", java.util.List.of(
                                "org.compiere.process.SvrProcess",
                                "org.compiere.model.MBPartner")
                )
        );

        assertTrue(prompt.contains("Target Platform Context (local)"));
        assertTrue(prompt.contains("Allowed/available packages (sample)"));
        assertTrue(prompt.contains("org.compiere.process"));
        assertTrue(prompt.contains("Example classes found (sample)"));
        assertTrue(prompt.contains("org.compiere.process.SvrProcess"));
    }

    @Test
    void testBuildPromptHidesInternalKeysFromAdditionalParameters() {
        ProjectContext ctx = ProjectContext.builder()
                .pluginId("org.example.myplugin")
                .basePackage("org.example.myplugin")
                .version("1.0.0")
                .build();

        String prompt = service.buildAiPrompt(
                null, ctx, "process", "MyProcess",
                Map.of(
                        "prompt", "Generate process logic",
                        "tableName", "C_Order",
                        "showAiPrompt", true,
                        "_targetContextRepo", "/tmp/repo"
                )
        );

        assertTrue(prompt.contains("Additional parameters: {tableName=C_Order}"));
        assertFalse(prompt.contains("showAiPrompt"));
        assertFalse(prompt.contains("_targetContextRepo"));
    }
}
