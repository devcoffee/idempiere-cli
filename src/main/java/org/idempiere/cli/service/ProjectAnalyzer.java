package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.model.PlatformVersion;
import org.idempiere.cli.model.ProjectContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Analyzes an existing iDempiere plugin project and extracts context
 * for AI-powered code generation.
 */
@ApplicationScoped
public class ProjectAnalyzer {

    private static final Pattern BUNDLE_SYMBOLIC_NAME = Pattern.compile(
            "Bundle-SymbolicName:\\s*([^;\\s]+)");
    private static final Pattern BUNDLE_VERSION = Pattern.compile(
            "Bundle-Version:\\s*(\\S+)");
    private static final Pattern TYCHO_VERSION = Pattern.compile(
            "<tycho\\.version>([^<]+)</tycho\\.version>");
    private static final Pattern JAVA_SE_VERSION = Pattern.compile(
            "JavaSE-(\\d+)");

    @Inject
    ProjectDetector projectDetector;

    /**
     * Analyzes the plugin at the given directory and returns its context.
     *
     * @param pluginDir the plugin directory (must contain META-INF/MANIFEST.MF)
     * @return project context with metadata and detected patterns
     */
    public ProjectContext analyze(Path pluginDir) {
        ProjectContext.Builder builder = ProjectContext.builder();

        // Read MANIFEST.MF
        String manifestContent = readFileQuietly(pluginDir.resolve("META-INF/MANIFEST.MF"));
        builder.manifestContent(manifestContent);

        // Extract plugin ID and version from MANIFEST
        if (manifestContent != null) {
            extractFromManifest(manifestContent, builder);
        } else {
            // Fallback: try ProjectDetector
            projectDetector.detectPluginId(pluginDir).ifPresent(builder::pluginId);
            projectDetector.detectPluginVersion(pluginDir).ifPresent(builder::version);
        }

        // Derive base package from plugin ID
        String pluginId = builder.build().getPluginId();
        if (pluginId != null) {
            builder.basePackage(pluginId);
        }

        // Read build.properties and pom.xml
        builder.buildPropertiesContent(readFileQuietly(pluginDir.resolve("build.properties")));
        String pomContent = readFileQuietly(pluginDir.resolve("pom.xml"));
        builder.pomXmlContent(pomContent);

        // Detect platform version
        builder.platformVersion(detectPlatformVersion(pluginDir, pomContent));

        // Detect multi-module
        Optional<Path> multiModuleRoot = projectDetector.findMultiModuleRoot(pluginDir);
        builder.multiModule(multiModuleRoot.isPresent());

        // Scan source files
        Path srcDir = pluginDir.resolve("src");
        if (Files.isDirectory(srcDir)) {
            List<String> classes = listJavaClasses(srcDir);
            builder.existingClasses(classes);

            // Detect patterns by scanning file contents
            builder.hasActivator(hasClassExtending(srcDir, "BundleActivator"));
            builder.hasCalloutFactory(hasClassImplementing(srcDir, "IColumnCalloutFactory"));
            builder.hasEventManager(hasAnnotationOrExtends(srcDir, "AbstractEventHandler", "@EventTopics"));
            builder.hasProcessFactory(hasClassContaining(srcDir, "MappedProcessFactory"));
            builder.usesAnnotationPattern(hasAnnotation(srcDir, "@Callout", "@Process", "@EventTopics"));
        }

        return builder.build();
    }

    private void extractFromManifest(String content, ProjectContext.Builder builder) {
        Matcher nameMatcher = BUNDLE_SYMBOLIC_NAME.matcher(content);
        if (nameMatcher.find()) {
            builder.pluginId(nameMatcher.group(1).trim());
        }
        Matcher versionMatcher = BUNDLE_VERSION.matcher(content);
        if (versionMatcher.find()) {
            builder.version(versionMatcher.group(1).trim());
        }
    }

    private PlatformVersion detectPlatformVersion(Path pluginDir, String pomContent) {
        // Try from pom.xml tycho version
        if (pomContent != null) {
            Matcher tychoMatcher = TYCHO_VERSION.matcher(pomContent);
            if (tychoMatcher.find()) {
                String tychoVersion = tychoMatcher.group(1);
                // Tycho 4.0.8 = iDempiere 13, 4.0.4 = iDempiere 12
                if (tychoVersion.startsWith("4.0.8") || tychoVersion.compareTo("4.0.5") >= 0) {
                    return PlatformVersion.of(13);
                }
                return PlatformVersion.of(12);
            }
        }

        // Try from parent pom in multi-module
        Optional<Path> root = projectDetector.findMultiModuleRoot(pluginDir);
        if (root.isPresent()) {
            Optional<Integer> version = projectDetector.detectIdempiereVersion(root.get());
            if (version.isPresent()) {
                return PlatformVersion.of(version.get());
            }
        }

        // Try JavaSE from MANIFEST.MF
        String manifest = readFileQuietly(pluginDir.resolve("META-INF/MANIFEST.MF"));
        if (manifest != null) {
            Matcher javaMatcher = JAVA_SE_VERSION.matcher(manifest);
            if (javaMatcher.find()) {
                int javaVersion = Integer.parseInt(javaMatcher.group(1));
                if (javaVersion >= 21) return PlatformVersion.of(13);
                return PlatformVersion.of(12);
            }
        }

        return PlatformVersion.latest();
    }

    /**
     * Lists all Java class simple names found under srcDir.
     */
    List<String> listJavaClasses(Path srcDir) {
        List<String> classes = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(srcDir)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        String fileName = p.getFileName().toString();
                        classes.add(fileName.replace(".java", ""));
                    });
        } catch (IOException e) {
            // ignore
        }
        return classes;
    }

    boolean hasClassExtending(Path srcDir, String superClass) {
        return scanSourceFiles(srcDir, "extends " + superClass);
    }

    boolean hasClassImplementing(Path srcDir, String interfaceName) {
        return scanSourceFiles(srcDir, "implements " + interfaceName) ||
               scanSourceFiles(srcDir, interfaceName);
    }

    boolean hasClassContaining(Path srcDir, String text) {
        return scanSourceFiles(srcDir, text);
    }

    boolean hasAnnotationOrExtends(Path srcDir, String superClass, String... annotations) {
        if (hasClassExtending(srcDir, superClass)) return true;
        for (String annotation : annotations) {
            if (scanSourceFiles(srcDir, annotation)) return true;
        }
        return false;
    }

    boolean hasAnnotation(Path srcDir, String... annotations) {
        for (String annotation : annotations) {
            if (scanSourceFiles(srcDir, annotation)) return true;
        }
        return false;
    }

    private boolean scanSourceFiles(Path srcDir, String searchText) {
        try (Stream<Path> paths = Files.walk(srcDir)) {
            return paths.filter(p -> p.toString().endsWith(".java"))
                    .anyMatch(p -> {
                        try {
                            return Files.readString(p).contains(searchText);
                        } catch (IOException e) {
                            return false;
                        }
                    });
        } catch (IOException e) {
            return false;
        }
    }

    private String readFileQuietly(Path path) {
        if (!Files.exists(path)) return null;
        try {
            return Files.readString(path);
        } catch (IOException e) {
            return null;
        }
    }
}
