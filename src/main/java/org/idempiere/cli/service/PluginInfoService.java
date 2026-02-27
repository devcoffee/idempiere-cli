package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.util.XmlUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Extracts and displays plugin metadata from MANIFEST.MF and plugin.xml.
 */
@ApplicationScoped
public class PluginInfoService {

    private static final Pattern BUNDLE_SYMBOLIC_NAME = Pattern.compile("(?m)^Bundle-SymbolicName:\\s*([^;\\s]+)");
    private static final Pattern BUNDLE_VERSION = Pattern.compile("(?m)^Bundle-Version:\\s*(\\S+)");
    private static final Pattern BUNDLE_VENDOR = Pattern.compile("(?m)^Bundle-Vendor:\\s*(.+)$");
    private static final Pattern FRAGMENT_HOST = Pattern.compile("(?m)^Fragment-Host:\\s*(\\S+)");
    private static final Pattern JAVA_SE = Pattern.compile("JavaSE-(\\d+)");
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Inject
    ProjectDetector projectDetector;

    /** Structured extension registration parsed from plugin.xml. */
    public record ExtensionRegistration(
            String type,
            String point,
            String className,
            String target
    ) {}

    /** Build artifact status from target/*.jar. */
    public record BuildArtifact(
            String path,
            long sizeBytes,
            Instant modifiedAt
    ) {}

    /** Structured plugin information. */
    public record PluginInfo(
            String pluginId,
            String version,
            String vendor,
            String fragmentHost,
            Integer javaSe,
            Integer idempiereVersion,
            List<String> requiredBundles,
            List<String> importPackages,
            List<String> exportPackages,
            List<String> dsComponents,
            List<ExtensionRegistration> extensions,
            List<String> components,
            BuildArtifact buildArtifact
    ) {}

    /** Module summary for multi-module projects. */
    public record ModuleSummary(
            String name,
            String type,
            boolean pluginModule
    ) {}

    /** Multi-module project overview. */
    public record MultiModuleInfo(
            String projectName,
            Integer idempiereVersion,
            Integer javaSe,
            String baseModule,
            List<ModuleSummary> modules
    ) {}

    /**
     * Extracts plugin information and returns structured data without printing.
     */
    public PluginInfo getInfo(Path pluginDir) {
        Path manifest = pluginDir.resolve("META-INF/MANIFEST.MF");
        if (!Files.exists(manifest)) {
            return null;
        }

        try {
            String content = Files.readString(manifest);

            String pluginId = extract(content, BUNDLE_SYMBOLIC_NAME, "unknown");
            String version = extract(content, BUNDLE_VERSION, "unknown");
            String vendor = extract(content, BUNDLE_VENDOR, "");
            String fragmentHost = extract(content, FRAGMENT_HOST, null);

            List<String> requiredBundles = parseManifestListHeader(content, "Require-Bundle", true);
            List<String> importPackages = parseManifestListHeader(content, "Import-Package", true);
            List<String> exportPackages = parseManifestListHeader(content, "Export-Package", true);
            List<String> dsComponents = parseManifestListHeader(content, "Service-Component", false);
            if (dsComponents.isEmpty()) {
                dsComponents = scanOsgiInfComponents(pluginDir);
            }

            List<ExtensionRegistration> extensions = parsePluginXmlExtensions(pluginDir.resolve("plugin.xml"));
            List<String> components = scanSourceComponents(pluginDir, pluginId);
            BuildArtifact buildArtifact = detectBuildArtifact(pluginDir);

            Integer javaSe = parseJavaSe(content);
            Integer idempiereVersion = detectIdempiereVersion(pluginDir, javaSe);

            return new PluginInfo(
                    pluginId,
                    version,
                    vendor,
                    fragmentHost,
                    javaSe,
                    idempiereVersion,
                    requiredBundles,
                    importPackages,
                    exportPackages,
                    dsComponents,
                    extensions,
                    components,
                    buildArtifact
            );
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Returns multi-module project overview for root directories.
     */
    public MultiModuleInfo getMultiModuleInfo(Path rootDir) {
        if (!projectDetector.isMultiModuleRoot(rootDir)) {
            return null;
        }

        List<String> modules = projectDetector.getModules(rootDir);
        List<ModuleSummary> summaries = modules.stream()
                .map(name -> {
                    Path moduleDir = rootDir.resolve(name);
                    return new ModuleSummary(name, detectModuleType(name), projectDetector.isIdempierePlugin(moduleDir));
                })
                .toList();

        Optional<Integer> idempiereVersion = projectDetector.detectIdempiereVersion(rootDir);
        Integer javaSe = idempiereVersion.map(v -> v >= 13 ? 21 : 17).orElse(null);
        String baseModule = projectDetector.findBasePluginModule(rootDir)
                .map(path -> rootDir.relativize(path).toString())
                .orElse(null);

        return new MultiModuleInfo(
                rootDir.getFileName().toString(),
                idempiereVersion.orElse(null),
                javaSe,
                baseModule,
                summaries
        );
    }

    public void printInfo(Path pluginDir) {
        printInfo(pluginDir, false);
    }

    public void printInfo(Path pluginDir, boolean verbose) {
        PluginInfo info = getInfo(pluginDir);
        if (info == null) {
            System.err.println("Error: Not an iDempiere plugin - META-INF/MANIFEST.MF not found.");
            return;
        }

        System.out.println();
        System.out.println("Plugin: " + info.pluginId());
        System.out.println("Version: " + info.version());
        if (!info.vendor().isBlank()) {
            System.out.println("Vendor: " + info.vendor());
        }

        if (info.fragmentHost() != null) {
            System.out.println("Fragment-Host: " + info.fragmentHost());
        }

        System.out.println("Target: " + formatTarget(info.idempiereVersion(), info.javaSe()));
        System.out.println("OSGi: "
                + info.requiredBundles().size() + " Require-Bundle, "
                + info.importPackages().size() + " Import-Package, "
                + info.exportPackages().size() + " Export-Package");

        printBuildStatus(info.buildArtifact());

        if (!info.requiredBundles().isEmpty()) {
            System.out.println();
            System.out.println("Dependencies:");
            printList(info.requiredBundles(), verbose, 12);
        }

        if (!info.extensions().isEmpty()) {
            System.out.println();
            System.out.println("Registered Extensions:");
            printExtensions(info.extensions(), verbose, 10);
        }

        if (!info.components().isEmpty()) {
            System.out.println();
            System.out.println("Components:");
            printList(info.components(), verbose, 12);
        }

        if (verbose) {
            printVerboseSections(info);
        }

        System.out.println();
    }

    public void printMultiModuleInfo(Path rootDir, boolean verbose) {
        MultiModuleInfo info = getMultiModuleInfo(rootDir);
        if (info == null) {
            System.err.println("Error: Not a multi-module iDempiere project root.");
            return;
        }

        System.out.println();
        System.out.println("Project: " + info.projectName());
        System.out.println("Type: multi-module");
        System.out.println("Target: " + formatTarget(info.idempiereVersion(), info.javaSe()));
        if (info.baseModule() != null) {
            System.out.println("Base module: " + info.baseModule());
        }

        System.out.println();
        System.out.println("Modules (" + info.modules().size() + "):");
        for (ModuleSummary module : info.modules()) {
            String suffix = module.pluginModule() ? " [plugin]" : "";
            System.out.println("  - " + module.name() + " (" + module.type() + ")" + suffix);
        }

        if (verbose && info.baseModule() != null) {
            Path baseModuleDir = rootDir.resolve(info.baseModule());
            if (projectDetector.isIdempierePlugin(baseModuleDir)) {
                System.out.println();
                System.out.println("Base module details:");
                printInfo(baseModuleDir, true);
            }
        }

        System.out.println();
    }

    private void printVerboseSections(PluginInfo info) {
        if (!info.importPackages().isEmpty()) {
            System.out.println();
            System.out.println("Import-Package:");
            printList(info.importPackages(), true, Integer.MAX_VALUE);
        }

        if (!info.exportPackages().isEmpty()) {
            System.out.println();
            System.out.println("Export-Package:");
            printList(info.exportPackages(), true, Integer.MAX_VALUE);
        }

        if (!info.dsComponents().isEmpty()) {
            System.out.println();
            System.out.println("DS Components:");
            printList(info.dsComponents(), true, Integer.MAX_VALUE);
        }
    }

    private void printBuildStatus(BuildArtifact artifact) {
        if (artifact == null) {
            System.out.println("Build: no JAR found under target/");
            return;
        }

        System.out.println("Build: " + artifact.path()
                + " (" + humanSize(artifact.sizeBytes())
                + ", " + TIMESTAMP_FMT.format(artifact.modifiedAt()) + ")");
    }

    private static String formatTarget(Integer idempiereVersion, Integer javaSe) {
        if (idempiereVersion != null && javaSe != null) {
            return "iDempiere " + idempiereVersion + " (Java " + javaSe + ")";
        }
        if (idempiereVersion != null) {
            return "iDempiere " + idempiereVersion;
        }
        if (javaSe != null) {
            return "Java " + javaSe;
        }
        return "unknown";
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        return String.format(Locale.ROOT, "%.1f MB", mb);
    }

    private static void printList(List<String> values, boolean verbose, int max) {
        int limit = verbose ? values.size() : Math.min(values.size(), max);
        for (int i = 0; i < limit; i++) {
            System.out.println("  " + values.get(i));
        }
        if (!verbose && values.size() > max) {
            System.out.println("  ... (" + (values.size() - max) + " more)");
        }
    }

    private static void printExtensions(List<ExtensionRegistration> extensions, boolean verbose, int max) {
        int limit = verbose ? extensions.size() : Math.min(extensions.size(), max);
        for (int i = 0; i < limit; i++) {
            ExtensionRegistration ext = extensions.get(i);
            String classPart = ext.className() == null ? "(class not declared)" : ext.className();
            String targetPart = ext.target() == null ? "" : " [" + ext.target() + "]";
            System.out.println("  " + ext.type() + ": " + classPart + targetPart);
            if (verbose) {
                System.out.println("    point=" + ext.point());
            }
        }
        if (!verbose && extensions.size() > max) {
            System.out.println("  ... (" + (extensions.size() - max) + " more)");
        }
    }

    private List<ExtensionRegistration> parsePluginXmlExtensions(Path pluginXml) {
        if (!Files.exists(pluginXml)) {
            return List.of();
        }

        try {
            var doc = XmlUtils.load(pluginXml);
            NodeList extensions = doc.getElementsByTagName("extension");
            List<ExtensionRegistration> result = new ArrayList<>();

            for (int i = 0; i < extensions.getLength(); i++) {
                Element ext = (Element) extensions.item(i);
                String point = ext.getAttribute("point");
                if (point == null || point.isBlank()) {
                    point = "unknown";
                }

                String className = firstNonBlank(
                        ext.getAttribute("class"),
                        findFirstAttribute(ext, "class"),
                        findFirstAttribute(ext, "handlerClass")
                );

                String target = collectTargetHint(ext);

                result.add(new ExtensionRegistration(
                        mapExtensionType(point),
                        point,
                        className,
                        target
                ));
            }

            return result;
        } catch (IOException e) {
            return List.of();
        }
    }

    private static String findFirstAttribute(Element root, String attrName) {
        NodeList descendants = root.getElementsByTagName("*");
        for (int i = 0; i < descendants.getLength(); i++) {
            Node node = descendants.item(i);
            if (node instanceof Element element) {
                String value = element.getAttribute(attrName);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String collectTargetHint(Element extension) {
        List<String> keys = List.of("table", "column", "name", "id", "event");
        List<String> hints = new ArrayList<>();

        collectHintsFromElement(extension, keys, hints);
        if (!hints.isEmpty()) {
            return String.join(", ", hints);
        }

        NodeList descendants = extension.getElementsByTagName("*");
        for (int i = 0; i < descendants.getLength(); i++) {
            Node node = descendants.item(i);
            if (node instanceof Element element) {
                collectHintsFromElement(element, keys, hints);
                if (!hints.isEmpty()) {
                    return String.join(", ", hints);
                }
            }
        }

        return null;
    }

    private static void collectHintsFromElement(Element element, List<String> keys, List<String> hints) {
        for (String key : keys) {
            String value = element.getAttribute(key);
            if (value != null && !value.isBlank()) {
                hints.add(key + "=" + value);
            }
        }
    }

    private static String mapExtensionType(String point) {
        String p = point.toLowerCase(Locale.ROOT);
        if (p.contains("callout")) return "Callout";
        if (p.contains("modelvalidator") || p.contains("model.validator")) return "Model Validator";
        if (p.contains("windowvalidator") || p.contains("window.validator")) return "Window Validator";
        if (p.contains("process")) return "Process";
        if (p.contains("form")) return "Form";
        if (p.contains("report")) return "Report";
        return "Extension";
    }

    private List<String> scanSourceComponents(Path pluginDir, String pluginId) {
        Path srcDir = pluginDir.resolve("src").resolve(pluginId.replace('.', '/'));
        if (!Files.exists(srcDir)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.walk(srcDir, 2)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .map(path -> srcDir.relativize(path).toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private List<String> scanOsgiInfComponents(Path pluginDir) {
        Path osgiInfDir = pluginDir.resolve("OSGI-INF");
        if (!Files.exists(osgiInfDir)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.walk(osgiInfDir, 2)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".xml"))
                    .map(path -> pluginDir.relativize(path).toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private BuildArtifact detectBuildArtifact(Path pluginDir) {
        Path targetDir = pluginDir.resolve("target");
        if (!Files.exists(targetDir)) {
            return null;
        }

        try (Stream<Path> files = Files.list(targetDir)) {
            Optional<Path> jar = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> {
                        String file = path.getFileName().toString();
                        return !file.contains("-sources") && !file.contains("-javadoc") && !file.contains("-tests");
                    })
                    .max(Comparator.comparing(this::lastModifiedOrEpoch));

            if (jar.isEmpty()) {
                return null;
            }

            Path artifact = jar.get();
            return new BuildArtifact(
                    pluginDir.relativize(artifact).toString(),
                    Files.size(artifact),
                    Files.getLastModifiedTime(artifact).toInstant()
            );
        } catch (IOException e) {
            return null;
        }
    }

    private FileTime lastModifiedOrEpoch(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            return FileTime.from(Instant.EPOCH);
        }
    }

    private Integer parseJavaSe(String manifestContent) {
        Matcher matcher = JAVA_SE.matcher(manifestContent);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer detectIdempiereVersion(Path pluginDir, Integer javaSe) {
        Optional<Path> root = projectDetector.findMultiModuleRoot(pluginDir);
        if (root.isPresent()) {
            Optional<Integer> detected = projectDetector.detectIdempiereVersion(root.get());
            if (detected.isPresent()) {
                return detected.get();
            }
        }

        if (javaSe == null) {
            return null;
        }
        if (javaSe >= 21) {
            return 13;
        }
        if (javaSe >= 17) {
            return 12;
        }
        return null;
    }

    private List<String> parseManifestListHeader(String content, String header, boolean stripAttributes) {
        Pattern pattern = Pattern.compile("(?m)^" + Pattern.quote(header) + ":\\s*(.+(?:\\n\\s+.+)*)");
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            return List.of();
        }

        String headerValue = matcher.group(1).replace("\n", " ").trim();
        List<String> parsed = new ArrayList<>();
        for (String part : headerValue.split(",")) {
            String value = part.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (stripAttributes) {
                value = value.split(";")[0].trim();
            }
            if (!value.isEmpty()) {
                parsed.add(value);
            }
        }
        return parsed.stream().distinct().sorted().toList();
    }

    private static String detectModuleType(String moduleName) {
        if (moduleName.endsWith(".parent") || moduleName.endsWith(".extensions.parent")) return "parent";
        if (moduleName.endsWith(".base")) return "base";
        if (moduleName.endsWith(".fragment")) return "fragment";
        if (moduleName.endsWith(".feature")) return "feature";
        if (moduleName.endsWith(".p2")) return "p2";
        if (moduleName.endsWith(".test")) return "test";
        return "module";
    }

    private String extract(String content, Pattern pattern, String defaultValue) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return defaultValue;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
