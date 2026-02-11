package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.model.CliConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Manages multiple skill sources with priority-based resolution.
 * Skills are SKILL.md files that provide AI instructions for generating
 * specific iDempiere component types.
 */
@ApplicationScoped
public class SkillManager {

    /** Maps CLI component type to the skill directory name in hengsin/idempiere-skills. */
    public static final Map<String, String> TYPE_TO_SKILL = Map.ofEntries(
            Map.entry("callout", "idempiere-callout-generator"),
            Map.entry("process", "idempiere-annotation-process"),
            Map.entry("process-mapped", "idempiere-mapped-process"),
            Map.entry("event-handler", "idempiere-event-annotation"),
            Map.entry("zk-form", "idempiere-zul-form"),
            Map.entry("zk-form-zul", "idempiere-zul-form"),
            Map.entry("rest-extension", "idempiere-rest-resource"),
            Map.entry("window-validator", "idempiere-window-validator"),
            Map.entry("listbox-group", "idempiere-grouped-listbox"),
            Map.entry("wlistbox-editor", "idempiere-wlistbox-custom-editor"),
            Map.entry("report", "idempiere-osgi-event-handler")
    );

    @Inject
    CliConfigService configService;

    @Inject
    ProcessRunner processRunner;

    /**
     * Loads the SKILL.md content for a given component type.
     * Sources are checked in priority order (0 = highest).
     *
     * @param componentType the CLI component type (e.g., "callout", "process")
     * @return the skill content, or empty if no skill found
     */
    public Optional<String> loadSkill(String componentType) {
        Optional<SkillResolution> resolution = resolveSkill(componentType);
        if (resolution.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(resolution.get().skillMdPath()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Resolves which source provides the skill for a given component type.
     * Sources are checked in priority order (0 = highest).
     */
    public Optional<SkillResolution> resolveSkill(String componentType) {
        String skillDir = TYPE_TO_SKILL.get(componentType);
        if (skillDir == null) {
            return Optional.empty();
        }

        CliConfig config = configService.loadConfig();
        List<CliConfig.SkillSource> sources = new ArrayList<>(config.getSkills().getSources());
        sources.sort(Comparator.comparingInt(CliConfig.SkillSource::getPriority));

        for (CliConfig.SkillSource source : sources) {
            Path sourceDir = resolveSourceDir(source, config.getSkills().getCacheDir());
            if (sourceDir == null || !Files.isDirectory(sourceDir)) {
                continue;
            }

            Path skillMdPath = sourceDir.resolve(skillDir).resolve("SKILL.md");
            if (Files.exists(skillMdPath)) {
                return Optional.of(new SkillResolution(source.getName(), skillDir, skillMdPath));
            }
        }

        return Optional.empty();
    }

    /**
     * Synchronizes remote skill sources (git clone/pull).
     */
    public SyncResult syncSkills() {
        CliConfig config = configService.loadConfig();
        List<CliConfig.SkillSource> sources = config.getSkills().getSources();

        int updated = 0;
        int unchanged = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (CliConfig.SkillSource source : sources) {
            if (!source.isRemote()) {
                unchanged++;
                continue;
            }

            Path cacheDir = resolveCacheDir(config.getSkills().getCacheDir(), source.getName());

            try {
                Files.createDirectories(cacheDir.getParent());

                if (Files.isDirectory(cacheDir.resolve(".git"))) {
                    // Pull existing repo
                    ProcessRunner.RunResult result = processRunner.runInDir(cacheDir, "git", "pull", "--ff-only");
                    if (result.isSuccess()) {
                        if (result.output().contains("Already up to date")) {
                            unchanged++;
                        } else {
                            updated++;
                        }
                    } else {
                        failed++;
                        errors.add(source.getName() + ": git pull failed");
                    }
                } else {
                    // Clone new repo
                    ProcessRunner.RunResult result = processRunner.run(
                            "git", "clone", "--depth", "1", source.getUrl(), cacheDir.toString()
                    );
                    if (result.isSuccess()) {
                        updated++;
                    } else {
                        failed++;
                        errors.add(source.getName() + ": git clone failed");
                    }
                }
            } catch (IOException e) {
                failed++;
                errors.add(source.getName() + ": " + e.getMessage());
            }
        }

        return new SyncResult(updated, unchanged, failed, errors);
    }

    /**
     * Lists all configured sources and their available skills.
     */
    public List<SkillSourceInfo> listSources() {
        CliConfig config = configService.loadConfig();
        List<SkillSourceInfo> result = new ArrayList<>();

        for (CliConfig.SkillSource source : config.getSkills().getSources()) {
            Path sourceDir = resolveSourceDir(source, config.getSkills().getCacheDir());
            List<String> skills = new ArrayList<>();

            if (sourceDir != null && Files.isDirectory(sourceDir)) {
                try (Stream<Path> dirs = Files.list(sourceDir)) {
                    dirs.filter(Files::isDirectory)
                            .filter(d -> Files.exists(d.resolve("SKILL.md")))
                            .forEach(d -> skills.add(d.getFileName().toString()));
                } catch (IOException e) {
                    // ignore
                }
            }

            String location = source.isRemote() ? source.getUrl() : source.getPath();
            result.add(new SkillSourceInfo(source.getName(), location, source.isRemote(), skills));
        }

        return result;
    }

    /**
     * Resolves the local directory for a skill source.
     */
    private Path resolveSourceDir(CliConfig.SkillSource source, String cacheDir) {
        if (source.isRemote()) {
            return resolveCacheDir(cacheDir, source.getName());
        }
        if (source.getPath() != null) {
            return Path.of(expandHome(source.getPath()));
        }
        return null;
    }

    private Path resolveCacheDir(String cacheDir, String sourceName) {
        return Path.of(expandHome(cacheDir)).resolve(sourceName);
    }

    private String expandHome(String path) {
        if (path.startsWith("~/")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }

    // Records for return types

    public record SyncResult(int updated, int unchanged, int failed, List<String> errors) {}

    public record SkillSourceInfo(String name, String location, boolean isRemote, List<String> availableSkills) {}

    public record SkillResolution(String sourceName, String skillDir, Path skillMdPath) {}
}
