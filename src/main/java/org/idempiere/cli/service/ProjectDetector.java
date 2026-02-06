package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects iDempiere plugin projects and extracts basic metadata.
 * Supports both standalone and multi-module project structures.
 */
@ApplicationScoped
public class ProjectDetector {

    private static final Pattern BUNDLE_SYMBOLIC_NAME = Pattern.compile(
            "Bundle-SymbolicName:\\s*([^;\\s]+)");
    private static final Pattern BUNDLE_VERSION = Pattern.compile(
            "Bundle-Version:\\s*(\\S+)");
    private static final Pattern POM_MODULE = Pattern.compile(
            "<module>([^<]+)</module>");
    private static final Pattern POM_ARTIFACT_ID = Pattern.compile(
            "<artifactId>([^<]+)</artifactId>");
    private static final Pattern POM_PACKAGING = Pattern.compile(
            "<packaging>([^<]+)</packaging>");

    public boolean isIdempierePlugin(Path directory) {
        return Files.exists(directory.resolve("META-INF/MANIFEST.MF"));
    }

    public Optional<String> detectPluginId(Path directory) {
        Path manifest = directory.resolve("META-INF/MANIFEST.MF");
        if (!Files.exists(manifest)) {
            return Optional.empty();
        }
        try {
            String content = Files.readString(manifest);
            Matcher matcher = BUNDLE_SYMBOLIC_NAME.matcher(content);
            if (matcher.find()) {
                return Optional.of(matcher.group(1).trim());
            }
        } catch (IOException e) {
            // ignore
        }
        return Optional.empty();
    }

    public Optional<String> detectPluginVersion(Path directory) {
        Path manifest = directory.resolve("META-INF/MANIFEST.MF");
        if (!Files.exists(manifest)) {
            return Optional.empty();
        }
        try {
            String content = Files.readString(manifest);
            Matcher matcher = BUNDLE_VERSION.matcher(content);
            if (matcher.find()) {
                return Optional.of(matcher.group(1).trim());
            }
        } catch (IOException e) {
            // ignore
        }
        return Optional.empty();
    }

    /**
     * Detects if the given directory is a multi-module project root.
     * A multi-module project has a pom.xml with &lt;packaging&gt;pom&lt;/packaging&gt;
     * and contains &lt;module&gt; entries.
     */
    public boolean isMultiModuleRoot(Path directory) {
        Path pomFile = directory.resolve("pom.xml");
        if (!Files.exists(pomFile)) {
            return false;
        }
        try {
            String content = Files.readString(pomFile);
            // Check for pom packaging
            Matcher packagingMatcher = POM_PACKAGING.matcher(content);
            if (packagingMatcher.find() && "pom".equals(packagingMatcher.group(1))) {
                // Check for module entries
                return POM_MODULE.matcher(content).find();
            }
        } catch (IOException e) {
            // ignore
        }
        return false;
    }

    /**
     * Finds the multi-module project root by walking up the directory tree.
     * Stops when it finds a directory with a pom.xml containing modules.
     */
    public Optional<Path> findMultiModuleRoot(Path directory) {
        Path current = directory.toAbsolutePath().normalize();
        while (current != null) {
            if (isMultiModuleRoot(current)) {
                return Optional.of(current);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    /**
     * Gets the list of modules defined in a multi-module project.
     */
    public List<String> getModules(Path rootDir) {
        List<String> modules = new ArrayList<>();
        Path pomFile = rootDir.resolve("pom.xml");
        if (!Files.exists(pomFile)) {
            return modules;
        }
        try {
            String content = Files.readString(pomFile);
            Matcher matcher = POM_MODULE.matcher(content);
            while (matcher.find()) {
                modules.add(matcher.group(1).trim());
            }
        } catch (IOException e) {
            // ignore
        }
        return modules;
    }

    /**
     * Detects the project base ID from a multi-module project.
     * Usually derived from the root directory name or parent artifact ID.
     */
    public Optional<String> detectProjectBaseId(Path rootDir) {
        // First try to get from parent module artifact ID
        Path parentPom = findParentModule(rootDir);
        if (parentPom != null) {
            try {
                String content = Files.readString(parentPom);
                Matcher matcher = POM_ARTIFACT_ID.matcher(content);
                if (matcher.find()) {
                    String artifactId = matcher.group(1).trim();
                    // Parent typically ends with .parent, remove it
                    if (artifactId.endsWith(".parent")) {
                        return Optional.of(artifactId.substring(0, artifactId.length() - 7));
                    }
                    if (artifactId.endsWith(".extensions.parent")) {
                        return Optional.of(artifactId.substring(0, artifactId.length() - 18));
                    }
                }
            } catch (IOException e) {
                // fallback below
            }
        }
        // Fallback: use directory name
        return Optional.of(rootDir.getFileName().toString());
    }

    /**
     * Finds the parent module directory within a multi-module project.
     */
    private Path findParentModule(Path rootDir) {
        List<String> modules = getModules(rootDir);
        for (String module : modules) {
            if (module.endsWith(".parent") || module.endsWith(".extensions.parent")) {
                Path parentDir = rootDir.resolve(module);
                Path pomFile = parentDir.resolve("pom.xml");
                if (Files.exists(pomFile)) {
                    return pomFile;
                }
            }
        }
        return null;
    }

    /**
     * Checks if a specific module type exists in the project.
     */
    public boolean hasModule(Path rootDir, String suffix) {
        List<String> modules = getModules(rootDir);
        return modules.stream().anyMatch(m -> m.endsWith(suffix));
    }

    /**
     * Checks if the project has a fragment module.
     */
    public boolean hasFragment(Path rootDir) {
        return hasModule(rootDir, ".fragment");
    }

    /**
     * Checks if the project has a feature module.
     */
    public boolean hasFeature(Path rootDir) {
        return hasModule(rootDir, ".feature");
    }

    /**
     * Checks if the project has a test module.
     */
    public boolean hasTest(Path rootDir) {
        return hasModule(rootDir, ".test");
    }

    /**
     * Checks if the project has a P2 module.
     */
    public boolean hasP2(Path rootDir) {
        return hasModule(rootDir, ".p2");
    }

    /**
     * Gets the platform version from the parent pom.
     */
    public Optional<Integer> detectIdempiereVersion(Path rootDir) {
        Path parentPom = findParentModule(rootDir);
        if (parentPom == null) {
            return Optional.empty();
        }
        try {
            String content = Files.readString(parentPom);
            // Look for bundle-version in dependency or executionEnvironment
            Pattern javaSePattern = Pattern.compile("JavaSE-(\\d+)");
            Matcher matcher = javaSePattern.matcher(content);
            if (matcher.find()) {
                int javaVersion = Integer.parseInt(matcher.group(1));
                // Java 21 = iDempiere 13, Java 17 = iDempiere 12
                if (javaVersion >= 21) return Optional.of(13);
                if (javaVersion >= 17) return Optional.of(12);
            }
        } catch (IOException | NumberFormatException e) {
            // ignore
        }
        return Optional.empty();
    }
}
