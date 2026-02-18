package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.idempiere.cli.util.CliOutput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
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

    private static final List<MappingRule> MAPPING_RULES = List.of(
            // More specific prefixes must win over generic ones.
            new MappingRule("org.idempiere.rest.api", "org.idempiere.rest."),
            new MappingRule("org.adempiere.ui.zk", "org.adempiere.webui."),
            new MappingRule("org.adempiere.ui.zk", "org.zkoss."),
            new MappingRule("org.adempiere.base", "org.compiere."),
            new MappingRule("org.adempiere.base", "org.adempiere.base."),
            new MappingRule("org.adempiere.base", "org.adempiere.model."),
            new MappingRule("org.adempiere.base", "org.adempiere."),
            new MappingRule("org.adempiere.base", "org.idempiere.")
    ).stream().sorted(Comparator.comparingInt((MappingRule r) -> r.prefix().length()).reversed()).toList();

    private record MappingRule(String bundle, String prefix) {}
    private record ImportAnalysis(Map<String, Set<String>> usedBundles, Set<String> unmappedImports) {}

    /** Structured result of dependency analysis. */
    public record DepsResult(
            Set<String> declaredBundles,
            Set<String> requiredBundles,
            Set<String> missingBundles,
            Set<String> unusedBundles,
            boolean isFragment,
            Set<String> unmappedImports
    ) {}

    /**
     * Analyzes plugin dependencies and returns structured data without printing.
     */
    public DepsResult analyzeData(Path pluginDir) {
        Set<String> declaredBundles = parseDeclaredBundles(pluginDir);
        boolean isFragment = isFragmentHost(pluginDir);
        Set<String> imports = scanImports(pluginDir);
        ImportAnalysis importAnalysis = analyzeImports(imports);
        Set<String> requiredBundles = new TreeSet<>(importAnalysis.usedBundles().keySet());

        Set<String> missingBundles = new TreeSet<>(requiredBundles);
        missingBundles.removeAll(declaredBundles);

        Set<String> unusedBundles = new TreeSet<>(declaredBundles);
        unusedBundles.removeAll(requiredBundles);

        return new DepsResult(
                declaredBundles,
                requiredBundles,
                missingBundles,
                unusedBundles,
                isFragment,
                importAnalysis.unmappedImports()
        );
    }

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
        ImportAnalysis importAnalysis = analyzeImports(imports);
        Map<String, Set<String>> usedBundles = importAnalysis.usedBundles();
        Set<String> unmapped = importAnalysis.unmappedImports();

        // Report used bundles
        System.out.println("Bundles used by source code:");
        if (usedBundles.isEmpty()) {
            System.out.println("  (none detected)");
        } else {
            for (var entry : usedBundles.entrySet()) {
                String bundle = entry.getKey() + " (" + entry.getValue().size() + " imports)";
                if (declaredBundles.contains(entry.getKey())) {
                    System.out.println("  " + CliOutput.ok(bundle));
                } else {
                    System.out.println("  " + CliOutput.fail(bundle));
                }
            }
        }
        System.out.println();

        // Report missing bundles
        Set<String> missingBundles = new TreeSet<>(usedBundles.keySet());
        missingBundles.removeAll(declaredBundles);
        if (!missingBundles.isEmpty()) {
            System.out.println("Missing from Require-Bundle:");
            missingBundles.forEach(b -> System.out.println("  " + b));
            System.out.println();
        }

        // Report unused declared bundles
        Set<String> unusedBundles = new TreeSet<>(declaredBundles);
        unusedBundles.removeAll(usedBundles.keySet());
        if (!unusedBundles.isEmpty()) {
            System.out.println("Declared but unused bundles:");
            unusedBundles.forEach(b -> System.out.println("  " + b));
            System.out.println();
        }

        if (!unmapped.isEmpty()) {
            System.out.println("Unmapped imports (manual check):");
            unmapped.forEach(i -> System.out.println("  " + i));
            System.out.println();
        }

        if (missingBundles.isEmpty() && unusedBundles.isEmpty() && unmapped.isEmpty()) {
            System.out.println("All dependencies are correctly declared.");
            System.out.println();
        }
    }

    private ImportAnalysis analyzeImports(Set<String> imports) {
        Map<String, Set<String>> usedBundles = new HashMap<>();
        Set<String> unmappedImports = new TreeSet<>();

        for (String imp : imports) {
            String bundle = mapImportToBundle(imp);
            if (bundle == null) {
                unmappedImports.add(imp);
                continue;
            }
            usedBundles.computeIfAbsent(bundle, k -> new TreeSet<>()).add(imp);
        }

        return new ImportAnalysis(usedBundles, unmappedImports);
    }

    private String mapImportToBundle(String imp) {
        for (MappingRule rule : MAPPING_RULES) {
            if (imp.startsWith(rule.prefix())) {
                return rule.bundle();
            }
        }
        return null;
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
