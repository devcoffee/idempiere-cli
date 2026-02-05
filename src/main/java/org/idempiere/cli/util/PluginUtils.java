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
     * Find the p2 repository path given an iDempiere home directory.
     * <p>
     * The IDEMPIERE_HOME environment variable typically points to the product directory
     * (e.g., org.idempiere.p2/target/products/org.adempiere.server.product/win32/win32/x86_64),
     * but Tycho needs the p2 repository which is at org.idempiere.p2/target/repository/.
     * <p>
     * This method attempts to find the p2 repository by:
     * 1. Checking if the path itself is a p2 repository (has content.jar/content.xml)
     * 2. Looking for org.idempiere.p2/target/repository in parent directories
     *
     * @param idempiereHome the iDempiere home directory (product or source)
     * @return the p2 repository path, or empty if not found
     */
    public static Optional<Path> findP2Repository(Path idempiereHome) {
        if (idempiereHome == null || !Files.exists(idempiereHome)) {
            return Optional.empty();
        }

        // Check if the path itself is a p2 repository
        if (isP2Repository(idempiereHome)) {
            return Optional.of(idempiereHome);
        }

        // Look for org.idempiere.p2/target/repository by traversing up
        Path current = idempiereHome.toAbsolutePath().normalize();
        while (current != null && current.getParent() != null) {
            // Check if we're inside org.idempiere.p2
            if (current.getFileName() != null &&
                current.getFileName().toString().equals("org.idempiere.p2")) {
                Path repo = current.resolve("target/repository");
                if (isP2Repository(repo)) {
                    return Optional.of(repo);
                }
            }

            // Check sibling org.idempiere.p2 folder
            Path sibling = current.resolve("org.idempiere.p2/target/repository");
            if (isP2Repository(sibling)) {
                return Optional.of(sibling);
            }

            current = current.getParent();
        }

        return Optional.empty();
    }

    /**
     * Check if a directory is a valid p2 repository.
     * A p2 repository must have either content.jar or content.xml.
     */
    public static boolean isP2Repository(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        return Files.exists(dir.resolve("content.jar")) ||
               Files.exists(dir.resolve("content.xml"));
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
