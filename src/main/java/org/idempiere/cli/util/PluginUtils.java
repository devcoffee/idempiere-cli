package org.idempiere.cli.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Shared utility methods for plugin operations.
 */
public final class PluginUtils {

    private PluginUtils() {
        // Utility class
    }

    /**
     * Find the built JAR file in a plugin's target directory.
     * Excludes sources JARs and classes.jar.
     *
     * @param pluginDir the plugin directory
     * @return the path to the built JAR, or empty if not found
     */
    public static Optional<Path> findBuiltJar(Path pluginDir) {
        Path targetDir = pluginDir.resolve("target");
        if (!Files.exists(targetDir)) {
            return Optional.empty();
        }
        try (var stream = Files.list(targetDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().endsWith("-sources.jar"))
                    .filter(p -> !p.getFileName().toString().equals("classes.jar"))
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
