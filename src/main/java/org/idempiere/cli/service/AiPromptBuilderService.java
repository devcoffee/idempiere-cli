package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.idempiere.cli.model.ProjectContext;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class AiPromptBuilderService {

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
            Map<String, Object> params = new HashMap<>(extraData);
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
}
