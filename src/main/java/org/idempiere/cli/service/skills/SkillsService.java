package org.idempiere.cli.service.skills;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Skills source/sync/resolution service abstraction.
 * Allows core builds to run without experimental providers.
 */
public interface SkillsService {

    Map<String, String> TYPE_TO_SKILL = Map.ofEntries(
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

    default boolean enabled() {
        return true;
    }

    default String disabledReason() {
        return "";
    }

    Optional<String> loadSkill(String componentType);

    Optional<SkillResolution> resolveSkill(String componentType);

    SyncResult syncSkills();

    List<SkillSourceInfo> listSources();

    List<String> listAvailableTypes();

    record SyncResult(int updated, int unchanged, int failed, List<String> errors) {}

    record SkillSourceInfo(String name, String location, boolean isRemote, List<String> availableSkills) {}

    record SkillResolution(String sourceName, String skillDir, Path skillMdPath) {}
}
