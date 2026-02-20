package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.model.CliConfig;
import org.idempiere.cli.service.CliConfigService;
import org.idempiere.cli.service.SkillManager;
import org.idempiere.cli.util.ExitCodes;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

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
                SkillsCommand.WhichCmd.class,
                SkillsCommand.SourceCmd.class
        }
)
public class SkillsCommand {

    @Command(name = "list", description = "List configured skill sources and available skills")
    static class ListCmd implements Callable<Integer> {

        @Inject
        SkillManager skillManager;

        @Override
        public Integer call() {
            List<SkillManager.SkillSourceInfo> sources = skillManager.listSources();

            if (sources.isEmpty()) {
                System.out.println("No skill sources configured.");
                System.out.println();
                System.out.println("Add a source with:");
                System.out.println("  idempiere-cli skills source add --name=official \\");
                System.out.println("    --url=https://github.com/hengsin/idempiere-skills.git --priority=1");
                System.out.println();
                System.out.println("Then run: idempiere-cli skills sync");
                return ExitCodes.SUCCESS;
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
            return ExitCodes.SUCCESS;
        }
    }

    @Command(name = "sync", description = "Synchronize remote skill sources")
    static class SyncCmd implements Callable<Integer> {

        @Inject
        SkillManager skillManager;

        @Override
        public Integer call() {
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
            return result.failed() > 0 ? ExitCodes.IO_ERROR : ExitCodes.SUCCESS;
        }
    }

    @Command(name = "which", description = "Show which source provides a skill for a component type")
    static class WhichCmd implements Callable<Integer> {

        @Parameters(index = "0", description = "Component type (e.g., callout, process, event-handler)")
        String componentType;

        @Inject
        SkillManager skillManager;

        @Override
        public Integer call() {
            var resolution = skillManager.resolveSkill(componentType);

            if (resolution.isEmpty()) {
                System.out.println("No skill found for: " + componentType);
                String skillDir = SkillManager.TYPE_TO_SKILL.get(componentType);
                if (skillDir != null) {
                    System.out.println("  Expected skill directory: " + skillDir);
                    System.out.println("  Make sure a source contains this directory with a SKILL.md file.");
                } else {
                    System.out.println("  No matching skill directory found.");
                    System.out.println("  Looked for: " + componentType + ", idempiere-" + componentType);
                    List<String> available = skillManager.listAvailableTypes();
                    if (!available.isEmpty()) {
                        System.out.println("  Available types:");
                        available.forEach(t -> System.out.println("    - " + t));
                    } else {
                        System.out.println("  No skills available. Run 'skills sync' to fetch skills.");
                    }
                }
                return ExitCodes.SUCCESS;
            }

            var res = resolution.get();
            System.out.println("Component: " + componentType);
            System.out.println("Source: " + res.sourceName());
            System.out.println("Skill: " + res.skillDir());
            System.out.println("Path: " + res.skillMdPath());
            return ExitCodes.SUCCESS;
        }
    }

    @Command(
            name = "source",
            description = "Manage skill sources in global config",
            mixinStandardHelpOptions = true,
            subcommands = {
                    SkillsCommand.SourceCmd.ListSourcesCmd.class,
                    SkillsCommand.SourceCmd.AddSourceCmd.class,
                    SkillsCommand.SourceCmd.RemoveSourceCmd.class
            }
    )
    static class SourceCmd {

        @Command(
                name = "list",
                description = "List configured skill sources from global config",
                mixinStandardHelpOptions = true
        )
        static class ListSourcesCmd implements Callable<Integer> {

            @Inject
            CliConfigService configService;

            @Override
            public Integer call() {
                CliConfig config = loadGlobalConfig(configService);
                List<CliConfig.SkillSource> sources = new ArrayList<>(config.getSkills().getSources());
                sources.sort(Comparator.comparingInt(CliConfig.SkillSource::getPriority)
                        .thenComparing(CliConfig.SkillSource::getName, String.CASE_INSENSITIVE_ORDER));

                if (sources.isEmpty()) {
                    System.out.println("No skill sources configured.");
                    return ExitCodes.SUCCESS;
                }

                System.out.println("Skill sources (global):");
                for (CliConfig.SkillSource source : sources) {
                    String type = source.isRemote() ? "url" : "path";
                    String location = source.isRemote() ? source.getUrl() : source.getPath();
                    System.out.println("  - " + source.getName() + " (priority " + source.getPriority() + ")");
                    System.out.println("    " + type + ": " + location);
                }
                return ExitCodes.SUCCESS;
            }
        }

        @Command(
                name = "add",
                description = "Add or update a skill source in global config",
                mixinStandardHelpOptions = true
        )
        static class AddSourceCmd implements Callable<Integer> {

            @Option(names = "--name", required = true, description = "Source name (unique identifier)")
            String name;

            @Option(names = "--url", description = "Git repository URL (remote source)")
            String url;

            @Option(names = "--path", description = "Local directory path (local source)")
            String path;

            @Option(names = "--priority", defaultValue = "1", description = "Priority (lower = higher precedence)")
            int priority;

            @Inject
            CliConfigService configService;

            @Override
            public Integer call() {
                if (name == null || name.isBlank()) {
                    System.err.println("Source name cannot be blank.");
                    return ExitCodes.VALIDATION_ERROR;
                }
                if ((url == null || url.isBlank()) == (path == null || path.isBlank())) {
                    System.err.println("Use exactly one of --url or --path.");
                    return ExitCodes.VALIDATION_ERROR;
                }

                CliConfig config = loadGlobalConfig(configService);
                List<CliConfig.SkillSource> sources = new ArrayList<>(config.getSkills().getSources());
                CliConfig.SkillSource existing = findByName(sources, name);
                boolean created = false;

                if (existing == null) {
                    existing = new CliConfig.SkillSource();
                    existing.setName(name);
                    sources.add(existing);
                    created = true;
                }

                existing.setPriority(priority);
                if (url != null && !url.isBlank()) {
                    existing.setUrl(url);
                    existing.setPath(null);
                } else {
                    existing.setPath(path);
                    existing.setUrl(null);
                }

                sources.sort(Comparator.comparingInt(CliConfig.SkillSource::getPriority)
                        .thenComparing(CliConfig.SkillSource::getName, String.CASE_INSENSITIVE_ORDER));
                config.getSkills().setSources(sources);

                try {
                    configService.saveGlobalConfig(config);
                    String verb = created ? "Added" : "Updated";
                    System.out.println(verb + " source '" + name + "' in " + configService.getGlobalConfigPath());
                    System.out.println("Run: idempiere-cli skills sync");
                    return ExitCodes.SUCCESS;
                } catch (IOException e) {
                    System.err.println("Failed to save config: " + e.getMessage());
                    return ExitCodes.IO_ERROR;
                }
            }
        }

        @Command(
                name = "remove",
                description = "Remove a skill source from global config",
                mixinStandardHelpOptions = true
        )
        static class RemoveSourceCmd implements Callable<Integer> {

            @Option(names = "--name", required = true, description = "Source name")
            String name;

            @Inject
            CliConfigService configService;

            @Override
            public Integer call() {
                if (name == null || name.isBlank()) {
                    System.err.println("Source name cannot be blank.");
                    return ExitCodes.VALIDATION_ERROR;
                }
                CliConfig config = loadGlobalConfig(configService);
                List<CliConfig.SkillSource> sources = new ArrayList<>(config.getSkills().getSources());
                boolean removed = sources.removeIf(source -> source.getName() != null
                        && source.getName().equalsIgnoreCase(name));

                if (!removed) {
                    System.err.println("Source not found: " + name);
                    return ExitCodes.VALIDATION_ERROR;
                }

                config.getSkills().setSources(sources);
                try {
                    configService.saveGlobalConfig(config);
                    System.out.println("Removed source '" + name + "' from " + configService.getGlobalConfigPath());
                    return ExitCodes.SUCCESS;
                } catch (IOException e) {
                    System.err.println("Failed to save config: " + e.getMessage());
                    return ExitCodes.IO_ERROR;
                }
            }
        }

        private static CliConfig loadGlobalConfig(CliConfigService configService) {
            Path globalPath = configService.getGlobalConfigPath();
            CliConfig config = configService.loadFromPath(globalPath);
            return config != null ? config : new CliConfig();
        }

        private static CliConfig.SkillSource findByName(List<CliConfig.SkillSource> sources, String name) {
            for (CliConfig.SkillSource source : sources) {
                if (source.getName() != null && source.getName().equalsIgnoreCase(name)) {
                    return source;
                }
            }
            return null;
        }
    }
}
