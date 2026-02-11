package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.service.SkillManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;

/**
 * Manages AI skill sources for code generation.
 */
@Command(
        name = "skills",
        description = "Manage AI skill sources for code generation",
        mixinStandardHelpOptions = true,
        subcommands = {
                SkillsCommand.ListCmd.class,
                SkillsCommand.SyncCmd.class,
                SkillsCommand.WhichCmd.class
        }
)
public class SkillsCommand {

    @Command(name = "list", description = "List configured skill sources and available skills")
    static class ListCmd implements Runnable {

        @Inject
        SkillManager skillManager;

        @Override
        public void run() {
            List<SkillManager.SkillSourceInfo> sources = skillManager.listSources();

            if (sources.isEmpty()) {
                System.out.println("No skill sources configured.");
                System.out.println();
                System.out.println("To add a source, edit ~/.idempiere-cli.yaml:");
                System.out.println("  skills:");
                System.out.println("    sources:");
                System.out.println("      - name: official");
                System.out.println("        url: https://github.com/hengsin/idempiere-skills.git");
                System.out.println("        priority: 1");
                System.out.println();
                System.out.println("Then run: idempiere-cli skills sync");
                return;
            }

            System.out.println("Skill Sources:");
            System.out.println();
            for (SkillManager.SkillSourceInfo source : sources) {
                String type = source.isRemote() ? "remote" : "local";
                System.out.println("  " + source.name() + " (" + type + ")");
                System.out.println("    Location: " + source.location());
                if (source.availableSkills().isEmpty()) {
                    System.out.println("    Skills: (none found - run 'skills sync' first)");
                } else {
                    System.out.println("    Skills: " + String.join(", ", source.availableSkills()));
                }
                System.out.println();
            }
        }
    }

    @Command(name = "sync", description = "Synchronize remote skill sources")
    static class SyncCmd implements Runnable {

        @Inject
        SkillManager skillManager;

        @Override
        public void run() {
            System.out.println("Synchronizing skill sources...");
            System.out.println();

            SkillManager.SyncResult result = skillManager.syncSkills();

            System.out.println("Sync complete: " + result.updated() + " updated, "
                    + result.unchanged() + " unchanged, " + result.failed() + " failed");

            if (!result.errors().isEmpty()) {
                System.err.println();
                System.err.println("Errors:");
                result.errors().forEach(e -> System.err.println("  - " + e));
            }
        }
    }

    @Command(name = "which", description = "Show which source provides a skill for a component type")
    static class WhichCmd implements Runnable {

        @Parameters(index = "0", description = "Component type (e.g., callout, process, event-handler)")
        String componentType;

        @Inject
        SkillManager skillManager;

        @Override
        public void run() {
            var resolution = skillManager.resolveSkill(componentType);

            if (resolution.isEmpty()) {
                System.out.println("No skill found for: " + componentType);
                String skillDir = SkillManager.TYPE_TO_SKILL.get(componentType);
                if (skillDir != null) {
                    System.out.println("  Expected skill directory: " + skillDir);
                    System.out.println("  Make sure a source contains this directory with a SKILL.md file.");
                } else {
                    System.out.println("  Unknown component type. Known types:");
                    SkillManager.TYPE_TO_SKILL.keySet().stream().sorted()
                            .forEach(t -> System.out.println("    - " + t));
                }
                return;
            }

            var res = resolution.get();
            System.out.println("Component: " + componentType);
            System.out.println("Source: " + res.sourceName());
            System.out.println("Skill: " + res.skillDir());
            System.out.println("Path: " + res.skillMdPath());
        }
    }
}
