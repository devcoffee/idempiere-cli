package org.idempiere.cli.service.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility methods shared across ComponentGenerator implementations.
 */
public final class GeneratorUtils {

    private GeneratorUtils() {
        // Utility class
    }

    /**
     * Converts a string to PascalCase.
     * Treats '-', '_', and '.' as word separators.
     *
     * @param input the input string
     * @return the PascalCase version
     */
    public static String toPascalCase(String input) {
        if (input == null || input.isEmpty()) return input;
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : input.toCharArray()) {
            if (c == '-' || c == '_' || c == '.') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Extracts the simple plugin name from a full plugin ID.
     * e.g., "org.example.myplugin" → "myplugin"
     *
     * @param pluginId the full plugin ID
     * @return the simple name (last segment after the last dot)
     */
    public static String extractPluginName(String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) return pluginId;
        int lastDot = pluginId.lastIndexOf('.');
        return lastDot >= 0 ? pluginId.substring(lastDot + 1) : pluginId;
    }

    /**
     * Check if a shared component already exists in the source directory.
     * Searches by content patterns to detect the type regardless of file name.
     *
     * @param srcDir          the source directory to search
     * @param contentPatterns patterns to search for in file content
     * @return true if any Java file contains any of the patterns
     */
    public static boolean hasSharedComponent(Path srcDir, String... contentPatterns) {
        if (!Files.exists(srcDir)) {
            return false;
        }
        try (var files = Files.list(srcDir)) {
            return files
                    .filter(f -> f.getFileName().toString().endsWith(".java"))
                    .anyMatch(f -> containsAnyPattern(f, contentPatterns));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Checks if a file contains any of the given patterns.
     */
    private static boolean containsAnyPattern(Path file, String... patterns) {
        try {
            String content = Files.readString(file);
            for (String pattern : patterns) {
                if (content.contains(pattern)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Checks if the source directory contains a CalloutFactory.
     * Detects by searching for AnnotationBasedColumnCalloutFactory or IColumnCalloutFactory.
     */
    public static boolean hasCalloutFactory(Path srcDir) {
        return hasSharedComponent(srcDir, "AnnotationBasedColumnCalloutFactory", "IColumnCalloutFactory");
    }

    /**
     * Checks if the source directory contains a plugin Activator.
     * Detects by searching for Incremental2PackActivator.
     */
    public static boolean hasPluginActivator(Path srcDir) {
        return hasSharedComponent(srcDir, "Incremental2PackActivator");
    }

    /**
     * Checks if the source directory contains an EventManager.
     */
    public static boolean hasEventManager(Path srcDir) {
        if (!Files.exists(srcDir)) {
            return false;
        }
        try (var stream = Files.list(srcDir)) {
            return stream.anyMatch(p -> p.getFileName().toString().endsWith("Manager.java"));
        } catch (IOException e) {
            return false;
        }
    }
}
