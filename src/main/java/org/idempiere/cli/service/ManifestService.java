package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for updating MANIFEST.MF files with required bundles and packages.
 */
@ApplicationScoped
public class ManifestService {

    // Bundle constants for different feature types
    public static final String BUNDLE_ZK = "org.adempiere.ui.zk";
    public static final String BUNDLE_ZK_CORE = "zk";
    public static final String BUNDLE_ZUL = "zul";
    public static final String BUNDLE_PLUGIN_UTILS = "org.adempiere.plugin.utils";
    public static final String BUNDLE_TEST = "org.idempiere.test";
    public static final String BUNDLE_BASE = "org.adempiere.base";

    // Import-Package constants
    public static final String IMPORT_OSGI_EVENT = "org.osgi.service.event;version=\"1.4.0\"";
    public static final String IMPORT_OSGI_FRAMEWORK = "org.osgi.framework;version=\"1.3.0\"";
    public static final String IMPORT_IDEMPIERE_TEST = "org.idempiere.test";
    public static final String IMPORT_JUNIT = "org.junit.jupiter.api;version=\"[5.9.0,6.0.0]\"";
    public static final String IMPORT_MINIGRID = "org.compiere.minigrid";

    /**
     * Add required bundles and packages to MANIFEST.MF based on the component type.
     */
    public void addRequiredBundles(Path pluginDir, String componentType) {
        Path manifestPath = pluginDir.resolve("META-INF/MANIFEST.MF");
        if (!Files.exists(manifestPath)) {
            System.err.println("  Warning: MANIFEST.MF not found at " + manifestPath);
            return;
        }

        Set<String> bundlesToAdd = getBundlesForComponent(componentType);
        Set<String> importsToAdd = getImportsForComponent(componentType);

        if (bundlesToAdd.isEmpty() && importsToAdd.isEmpty()) {
            return;
        }

        try {
            String content = Files.readString(manifestPath);
            List<String> updates = new ArrayList<>();

            // Handle Require-Bundle
            if (!bundlesToAdd.isEmpty()) {
                Set<String> existingBundles = parseExistingBundles(content);
                bundlesToAdd.removeAll(existingBundles);

                if (!bundlesToAdd.isEmpty()) {
                    content = addBundlesToManifest(content, bundlesToAdd);
                    updates.add("bundles: " + String.join(", ", bundlesToAdd));
                }
            }

            // Handle Import-Package
            if (!importsToAdd.isEmpty()) {
                Set<String> existingImports = parseExistingImports(content);
                // Filter imports that already exist (check by package name without version)
                importsToAdd.removeIf(imp -> {
                    String pkgName = imp.split(";")[0].trim();
                    return existingImports.stream().anyMatch(e -> e.startsWith(pkgName));
                });

                if (!importsToAdd.isEmpty()) {
                    content = addImportsToManifest(content, importsToAdd);
                    updates.add("imports: " + String.join(", ", importsToAdd.stream()
                            .map(i -> i.split(";")[0]).toList()));
                }
            }

            if (!updates.isEmpty()) {
                Files.writeString(manifestPath, content);
                System.out.println("  Updated MANIFEST.MF with " + String.join("; ", updates));
            }
        } catch (IOException e) {
            System.err.println("  Warning: Could not update MANIFEST.MF: " + e.getMessage());
        }
    }

    private Set<String> getBundlesForComponent(String componentType) {
        Set<String> bundles = new LinkedHashSet<>();

        switch (componentType) {
            case "zk-form", "zk-form-zul", "listbox-group", "wlistbox-editor", "window-validator" -> {
                bundles.add(BUNDLE_ZK);
                bundles.add(BUNDLE_ZK_CORE);
                bundles.add(BUNDLE_ZUL);
            }
            case "process-mapped", "jasper-report" -> {
                bundles.add(BUNDLE_PLUGIN_UTILS);
            }
            case "base-test" -> {
                bundles.add(BUNDLE_TEST);
            }
            // callout, event-handler, process, report, facts-validator only need base (usually already there)
        }

        return bundles;
    }

    private Set<String> getImportsForComponent(String componentType) {
        Set<String> imports = new LinkedHashSet<>();

        switch (componentType) {
            case "event-handler", "facts-validator" -> {
                imports.add(IMPORT_OSGI_EVENT);
            }
            case "process-mapped", "jasper-report" -> {
                imports.add(IMPORT_OSGI_FRAMEWORK);
            }
            case "base-test" -> {
                imports.add(IMPORT_IDEMPIERE_TEST);
                imports.add(IMPORT_JUNIT);
            }
            case "wlistbox-editor" -> {
                imports.add(IMPORT_MINIGRID);
            }
        }

        return imports;
    }

    private Set<String> parseExistingBundles(String content) {
        Set<String> bundles = new LinkedHashSet<>();

        // Match Require-Bundle header (may span multiple lines)
        Pattern pattern = Pattern.compile("Require-Bundle:\\s*([^\\n]+(?:\\n\\s+[^\\n]+)*)", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            String bundleList = matcher.group(1).replaceAll("\\s+", " ").trim();
            for (String bundle : bundleList.split(",\\s*")) {
                String bundleName = bundle.split(";")[0].trim();
                if (!bundleName.isEmpty()) {
                    bundles.add(bundleName);
                }
            }
        }

        return bundles;
    }

    private Set<String> parseExistingImports(String content) {
        Set<String> imports = new LinkedHashSet<>();

        // Match Import-Package header (may span multiple lines)
        Pattern pattern = Pattern.compile("Import-Package:\\s*([^\\n]+(?:\\n\\s+[^\\n]+)*)", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            String importList = matcher.group(1).replaceAll("\\s+", " ").trim();
            for (String imp : importList.split(",\\s*")) {
                String pkgName = imp.split(";")[0].trim();
                if (!pkgName.isEmpty()) {
                    imports.add(pkgName);
                }
            }
        }

        return imports;
    }

    private String addBundlesToManifest(String content, Set<String> bundlesToAdd) {
        Pattern pattern = Pattern.compile("(Require-Bundle:\\s*)([^\\n]+(?:\\n\\s+[^\\n]+)*)", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            String existingValue = matcher.group(2).trim();
            if (existingValue.endsWith(",")) {
                existingValue = existingValue.substring(0, existingValue.length() - 1);
            }

            StringBuilder newValue = new StringBuilder(existingValue);
            for (String bundle : bundlesToAdd) {
                newValue.append(",\n ").append(bundle);
            }

            return matcher.replaceFirst("Require-Bundle: " + Matcher.quoteReplacement(newValue.toString()));
        } else {
            StringBuilder bundleHeader = new StringBuilder("Require-Bundle: ");
            boolean first = true;
            for (String bundle : bundlesToAdd) {
                if (!first) {
                    bundleHeader.append(",\n ");
                }
                bundleHeader.append(bundle);
                first = false;
            }
            bundleHeader.append("\n");

            if (content.contains("Bundle-RequiredExecutionEnvironment:")) {
                return content.replace("Bundle-RequiredExecutionEnvironment:",
                        bundleHeader.toString() + "Bundle-RequiredExecutionEnvironment:");
            } else {
                if (!content.endsWith("\n")) {
                    content += "\n";
                }
                return content + bundleHeader.toString();
            }
        }
    }

    private String addImportsToManifest(String content, Set<String> importsToAdd) {
        Pattern pattern = Pattern.compile("(Import-Package:\\s*)([^\\n]+(?:\\n\\s+[^\\n]+)*)", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            String existingValue = matcher.group(2).trim();
            if (existingValue.endsWith(",")) {
                existingValue = existingValue.substring(0, existingValue.length() - 1);
            }

            StringBuilder newValue = new StringBuilder(existingValue);
            for (String imp : importsToAdd) {
                newValue.append(",\n ").append(imp);
            }

            return matcher.replaceFirst("Import-Package: " + Matcher.quoteReplacement(newValue.toString()));
        } else {
            // Add new Import-Package header before Service-Component or at end
            StringBuilder importHeader = new StringBuilder("Import-Package: ");
            boolean first = true;
            for (String imp : importsToAdd) {
                if (!first) {
                    importHeader.append(",\n ");
                }
                importHeader.append(imp);
                first = false;
            }
            importHeader.append("\n");

            if (content.contains("Service-Component:")) {
                return content.replace("Service-Component:",
                        importHeader.toString() + "Service-Component:");
            } else if (content.contains("Bundle-ActivationPolicy:")) {
                return content.replace("Bundle-ActivationPolicy:",
                        importHeader.toString() + "Bundle-ActivationPolicy:");
            } else {
                if (!content.endsWith("\n")) {
                    content += "\n";
                }
                return content + importHeader.toString();
            }
        }
    }
}
