package org.idempiere.cli.service.check;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Validates that Java imports are covered by declared Require-Bundle dependencies.
 */
@ApplicationScoped
public class DependenciesCheck implements PluginCheck {

    private static final Pattern REQUIRE_BUNDLE_PATTERN =
            Pattern.compile("(?:Require-Bundle|Fragment-Host):\\s*(.+)", Pattern.DOTALL);

    @Override
    public String checkName() {
        return "Dependencies";
    }

    @Override
    public CheckResult check(Path pluginDir) {
        Path manifest = pluginDir.resolve("META-INF/MANIFEST.MF");
        if (!Files.exists(manifest)) {
            return new CheckResult(checkName(), CheckResult.Status.WARN, "Cannot check - no MANIFEST.MF");
        }

        try {
            String manifestContent = Files.readString(manifest);
            Set<String> declaredBundles = extractDeclaredBundles(manifestContent);

            Path srcDir = pluginDir.resolve("src");
            if (!Files.exists(srcDir)) {
                return new CheckResult(checkName(), CheckResult.Status.WARN, "No src/ directory found");
            }

            Set<String> externalPackages = scanJavaImports(srcDir);
            Set<String> uncoveredPrefixes = findUncoveredDependencies(externalPackages, declaredBundles);

            if (uncoveredPrefixes.isEmpty()) {
                return new CheckResult(checkName(), CheckResult.Status.OK, "Imports match declared bundles");
            } else {
                return new CheckResult(checkName(), CheckResult.Status.WARN,
                        "Missing bundles: " + String.join(", ", uncoveredPrefixes));
            }
        } catch (IOException e) {
            return new CheckResult(checkName(), CheckResult.Status.WARN, "Error checking: " + e.getMessage());
        }
    }

    private Set<String> extractDeclaredBundles(String manifestContent) {
        Set<String> bundles = new HashSet<>();
        Matcher m = REQUIRE_BUNDLE_PATTERN.matcher(manifestContent);
        if (m.find()) {
            String bundleStr = m.group(1).split("\\n(?!\\s)")[0];
            for (String part : bundleStr.split(",")) {
                String bundle = part.trim().split(";")[0].trim();
                if (!bundle.isEmpty()) {
                    bundles.add(bundle);
                }
            }
        }
        return bundles;
    }

    private Set<String> scanJavaImports(Path srcDir) throws IOException {
        Set<String> externalPackages = new HashSet<>();
        try (Stream<Path> walk = Files.walk(srcDir)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(javaFile -> {
                try {
                    Files.readAllLines(javaFile).stream()
                            .filter(line -> line.startsWith("import "))
                            .map(line -> line.replace("import ", "").replace(";", "").trim())
                            .filter(imp -> !imp.startsWith("java.") &&
                                          !imp.startsWith("javax.") &&
                                          !imp.startsWith("jakarta."))
                            .forEach(externalPackages::add);
                } catch (IOException ignored) {
                }
            });
        }
        return externalPackages;
    }

    private Set<String> findUncoveredDependencies(Set<String> imports, Set<String> declaredBundles) {
        Set<String> uncovered = new HashSet<>();
        for (String imp : imports) {
            if (imp.startsWith("org.compiere.") ||
                imp.startsWith("org.adempiere.") ||
                imp.startsWith("org.idempiere.")) {
                if (!declaredBundles.contains("org.adempiere.base") &&
                    !declaredBundles.contains("org.idempiere.rest.api")) {
                    uncovered.add("org.adempiere.base");
                }
            } else if (imp.startsWith("org.adempiere.webui.")) {
                if (!declaredBundles.contains("org.adempiere.ui.zk")) {
                    uncovered.add("org.adempiere.ui.zk");
                }
            }
        }
        return uncovered;
    }
}
