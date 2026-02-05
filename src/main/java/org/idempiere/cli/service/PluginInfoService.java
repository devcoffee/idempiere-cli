package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts and displays plugin metadata from MANIFEST.MF.
 */
@ApplicationScoped
public class PluginInfoService {

    private static final Pattern BUNDLE_SYMBOLIC_NAME = Pattern.compile("Bundle-SymbolicName:\\s*([^;\\s]+)");
    private static final Pattern BUNDLE_VERSION = Pattern.compile("Bundle-Version:\\s*(\\S+)");
    private static final Pattern BUNDLE_VENDOR = Pattern.compile("Bundle-Vendor:\\s*(.+)");
    private static final Pattern REQUIRE_BUNDLE = Pattern.compile("Require-Bundle:\\s*(.+?)(?=\\n\\S|\\Z)", Pattern.DOTALL);
    private static final Pattern FRAGMENT_HOST = Pattern.compile("Fragment-Host:\\s*(\\S+)");

    public void printInfo(Path pluginDir) {
        Path manifest = pluginDir.resolve("META-INF/MANIFEST.MF");
        if (!Files.exists(manifest)) {
            System.err.println("Error: Not an iDempiere plugin - META-INF/MANIFEST.MF not found.");
            return;
        }

        try {
            String content = Files.readString(manifest);

            String pluginId = extract(content, BUNDLE_SYMBOLIC_NAME, "unknown");
            String version = extract(content, BUNDLE_VERSION, "unknown");
            String vendor = extract(content, BUNDLE_VENDOR, "");

            System.out.println();
            System.out.println("Plugin: " + pluginId);
            System.out.println("Version: " + version);
            if (!vendor.isBlank()) {
                System.out.println("Vendor: " + vendor);
            }

            // Fragment host (REST extensions)
            String fragmentHost = extract(content, FRAGMENT_HOST, null);
            if (fragmentHost != null) {
                System.out.println("Fragment-Host: " + fragmentHost);
            }

            // Dependencies
            Matcher requireMatcher = REQUIRE_BUNDLE.matcher(content);
            if (requireMatcher.find()) {
                System.out.println();
                System.out.println("Dependencies:");
                String deps = requireMatcher.group(1).trim();
                // Require-Bundle entries are comma-separated, possibly multi-line with leading spaces
                String[] bundles = deps.split(",");
                for (String bundle : bundles) {
                    String trimmed = bundle.trim();
                    if (!trimmed.isEmpty()) {
                        System.out.println("  " + trimmed);
                    }
                }
            }

            // Components (Java files in src/)
            Path srcDir = pluginDir.resolve("src").resolve(pluginId.replace('.', '/'));
            if (Files.exists(srcDir)) {
                List<String> javaFiles = new ArrayList<>();
                try (var stream = Files.list(srcDir)) {
                    stream.filter(p -> p.getFileName().toString().endsWith(".java"))
                            .map(p -> p.getFileName().toString())
                            .sorted()
                            .forEach(javaFiles::add);
                }
                if (!javaFiles.isEmpty()) {
                    System.out.println();
                    System.out.println("Components:");
                    for (String file : javaFiles) {
                        System.out.println("  " + file);
                    }
                }
            }

            System.out.println();
        } catch (IOException e) {
            System.err.println("Error reading plugin info: " + e.getMessage());
        }
    }

    private String extract(String content, Pattern pattern, String defaultValue) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return defaultValue;
    }
}
