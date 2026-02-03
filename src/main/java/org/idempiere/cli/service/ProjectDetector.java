package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects iDempiere plugin projects and extracts basic metadata.
 */
@ApplicationScoped
public class ProjectDetector {

    private static final Pattern BUNDLE_SYMBOLIC_NAME = Pattern.compile(
            "Bundle-SymbolicName:\\s*([^;\\s]+)");
    private static final Pattern BUNDLE_VERSION = Pattern.compile(
            "Bundle-Version:\\s*(\\S+)");

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
}
