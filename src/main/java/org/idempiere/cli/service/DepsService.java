package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Analyzes plugin dependencies by comparing Java imports against Require-Bundle declarations.
 */
@ApplicationScoped
public class DepsService {

    private static final Map<String, List<String>> BUNDLE_PACKAGES = Map.of(
            "org.adempiere.base", List.of("org.compiere.", "org.adempiere.base.", "org.adempiere.model.", "org.idempiere."),
            "org.adempiere.ui.zk", List.of("org.adempiere.webui."),
            "org.idempiere.rest.api", List.of("org.idempiere.rest.")
    );

    public void analyze(Path pluginDir) {
        System.out.println();
        System.out.println("Dependency Analysis");
        System.out.println("==========================================");
        System.out.println();

        // Read declared bundles from MANIFEST.MF
        Set<String> declaredBundles = parseDeclaredBundles(pluginDir);
        boolean isFragment = isFragmentHost(pluginDir);

        System.out.println("Declared dependencies:");
        if (isFragment) {
            System.out.println("  Fragment-Host (REST extension)");
        }
        if (declaredBundles.isEmpty() && !isFragment) {
            System.out.println("  (none)");
        } else {
            declaredBundles.forEach(b -> System.out.println("  " + b));
        }
        System.out.println();

        // Scan imports from source
        Set<String> imports = scanImports(pluginDir);
        if (imports.isEmpty()) {
            System.out.println("No external imports found in source files.");
            System.out.println();
            return;
        }

        // Determine which bundles are needed
        Map<String, Set<String>> usedBundles = new HashMap<>();
        Set<String> unmapped = new TreeSet<>();

        for (String imp : imports) {
            boolean matched = false;
            for (var entry : BUNDLE_PACKAGES.entrySet()) {
                for (String prefix : entry.getValue()) {
                    if (imp.startsWith(prefix)) {
                        usedBundles.computeIfAbsent(entry.getKey(), k -> new TreeSet<>()).add(imp);
                        matched = true;
                        break;
                    }
                }
                if (matched) break;
            }
            if (!matched) {
                unmapped.add(imp);
            }
        }

        // Report used bundles
        System.out.println("Bundles used by source code:");
        if (usedBundles.isEmpty()) {
            System.out.println("  (none detected)");
        } else {
            for (var entry : usedBundles.entrySet()) {
                String status = declaredBundles.contains(entry.getKey()) ? "  \u2714" : "  \u2718";
                System.out.println(status + "  " + entry.getKey() + " (" + entry.getValue().size() + " imports)");
            }
        }
        System.out.println();

        // Report missing bundles
        Set<String> missingBundles = new HashSet<>(usedBundles.keySet());
        missingBundles.removeAll(declaredBundles);
        if (!missingBundles.isEmpty()) {
            System.out.println("Missing from Require-Bundle:");
            missingBundles.forEach(b -> System.out.println("  " + b));
            System.out.println();
        }

        // Report unused declared bundles
        Set<String> unusedBundles = new HashSet<>(declaredBundles);
        unusedBundles.removeAll(usedBundles.keySet());
        if (!unusedBundles.isEmpty()) {
            System.out.println("Declared but unused bundles:");
            unusedBundles.forEach(b -> System.out.println("  " + b));
            System.out.println();
        }

        if (missingBundles.isEmpty() && unusedBundles.isEmpty()) {
            System.out.println("All dependencies are correctly declared.");
            System.out.println();
        }
    }

    private Set<String> parseDeclaredBundles(Path pluginDir) {
        Set<String> bundles = new HashSet<>();
        Path manifest = pluginDir.resolve("META-INF/MANIFEST.MF");
        if (!Files.exists(manifest)) return bundles;

        try {
            String content = Files.readString(manifest);
            Matcher m = Pattern.compile("Require-Bundle:\\s*(.+?)(?=\\n\\S|\\Z)", Pattern.DOTALL).matcher(content);
            if (m.find()) {
                String bundleStr = m.group(1);
                for (String part : bundleStr.split(",")) {
                    String bundle = part.trim().split(";")[0].trim();
                    if (!bundle.isEmpty()) bundles.add(bundle);
                }
            }
        } catch (IOException ignored) {
        }
        return bundles;
    }

    private boolean isFragmentHost(Path pluginDir) {
        Path manifest = pluginDir.resolve("META-INF/MANIFEST.MF");
        if (!Files.exists(manifest)) return false;
        try {
            return Files.readString(manifest).contains("Fragment-Host:");
        } catch (IOException e) {
            return false;
        }
    }

    private Set<String> scanImports(Path pluginDir) {
        Set<String> imports = new TreeSet<>();
        Path srcDir = pluginDir.resolve("src");
        if (!Files.exists(srcDir)) return imports;

        try (Stream<Path> walk = Files.walk(srcDir)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(javaFile -> {
                try {
                    Files.readAllLines(javaFile).stream()
                            .filter(line -> line.startsWith("import ") && !line.startsWith("import static "))
                            .map(line -> line.replace("import ", "").replace(";", "").trim())
                            .filter(imp -> !imp.startsWith("java.") && !imp.startsWith("javax.") && !imp.startsWith("jakarta."))
                            .forEach(imports::add);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
        return imports;
    }
}
