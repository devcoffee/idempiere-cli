package org.idempiere.cli.plugin.add;

import org.idempiere.cli.model.GeneratedCode;
import org.idempiere.cli.model.ProjectContext;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Extension point for non-template component generation.
 * <p>
 * Core CLI remains deterministic by default. Optional plugins can contribute
 * generation strategies (for example, AI-assisted generation).
 */
public interface AddGenerationPlugin {

    /**
     * Stable plugin identifier for logs/diagnostics.
     */
    String id();

    /**
     * Lower value means higher priority.
     */
    default int order() {
        return 100;
    }

    /**
     * Attempts generation for a component request.
     *
     * @return empty when plugin does not apply, otherwise a generation attempt result
     */
    Optional<AddGenerationResult> tryGenerate(String type,
                                              String name,
                                              Path pluginDir,
                                              String pluginId,
                                              ProjectContext projectContext,
                                              Map<String, Object> extraData);

    record AddGenerationResult(boolean generated, GeneratedCode generatedCode, String detail) {
        public static AddGenerationResult generated(GeneratedCode code, String detail) {
            return new AddGenerationResult(true, code, detail);
        }

        public static AddGenerationResult skipped(String detail) {
            return new AddGenerationResult(false, null, detail);
        }
    }
}
