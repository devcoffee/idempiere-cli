package org.idempiere.cli.service.skills;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

/**
 * Core fallback when experimental skills feature is not included in the build.
 */
@ApplicationScoped
@DefaultBean
public class NoopSkillsService implements SkillsService {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public String disabledReason() {
        return "skills feature is available only in experimental build (-Pexp)";
    }

    @Override
    public Optional<String> loadSkill(String componentType) {
        return Optional.empty();
    }

    @Override
    public Optional<SkillResolution> resolveSkill(String componentType) {
        return Optional.empty();
    }

    @Override
    public SyncResult syncSkills() {
        return new SyncResult(0, 0, 0, List.of());
    }

    @Override
    public List<SkillSourceInfo> listSources() {
        return List.of();
    }

    @Override
    public List<String> listAvailableTypes() {
        return List.of();
    }
}
