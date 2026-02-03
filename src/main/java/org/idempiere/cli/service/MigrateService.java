package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.idempiere.cli.model.PlatformVersion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Migrates plugin configuration between iDempiere platform versions.
 */
@ApplicationScoped
public class MigrateService {

    public void migrate(Path pluginDir, PlatformVersion from, PlatformVersion to) {
        System.out.println();
        System.out.println("Migrating plugin from iDempiere " + from.major() + " to " + to.major());
        System.out.println("==========================================");
        System.out.println();

        List<String> changes = new ArrayList<>();

        migratePom(pluginDir, from, to, changes);
        migrateManifest(pluginDir, from, to, changes);
        migrateBuildProperties(pluginDir, from, to, changes);

        if (changes.isEmpty()) {
            System.out.println("  No changes needed.");
        } else {
            System.out.println("Changes applied:");
            changes.forEach(c -> System.out.println("  " + c));
        }
        System.out.println();
    }

    private void migratePom(Path pluginDir, PlatformVersion from, PlatformVersion to, List<String> changes) {
        Path pom = pluginDir.resolve("pom.xml");
        if (!Files.exists(pom)) return;

        try {
            String content = Files.readString(pom);
            String updated = content;

            String fromJava = "<maven.compiler.release>" + from.javaRelease() + "</maven.compiler.release>";
            String toJava = "<maven.compiler.release>" + to.javaRelease() + "</maven.compiler.release>";
            if (updated.contains(fromJava)) {
                updated = updated.replace(fromJava, toJava);
                changes.add("pom.xml: maven.compiler.release " + from.javaRelease() + " -> " + to.javaRelease());
            }

            String fromTycho = "<tycho.version>" + from.tychoVersion() + "</tycho.version>";
            String toTycho = "<tycho.version>" + to.tychoVersion() + "</tycho.version>";
            if (updated.contains(fromTycho)) {
                updated = updated.replace(fromTycho, toTycho);
                changes.add("pom.xml: tycho.version " + from.tychoVersion() + " -> " + to.tychoVersion());
            }

            if (!updated.equals(content)) {
                Files.writeString(pom, updated);
            }
        } catch (IOException e) {
            System.err.println("  Error updating pom.xml: " + e.getMessage());
        }
    }

    private void migrateManifest(Path pluginDir, PlatformVersion from, PlatformVersion to, List<String> changes) {
        Path manifest = pluginDir.resolve("META-INF/MANIFEST.MF");
        if (!Files.exists(manifest)) return;

        try {
            String content = Files.readString(manifest);
            String updated = content;

            if (updated.contains(from.javaSeVersion())) {
                updated = updated.replace(from.javaSeVersion(), to.javaSeVersion());
                changes.add("MANIFEST.MF: " + from.javaSeVersion() + " -> " + to.javaSeVersion());
            }

            String fromBundle = "bundle-version=\"" + from.bundleVersion() + "\"";
            String toBundle = "bundle-version=\"" + to.bundleVersion() + "\"";
            if (updated.contains(fromBundle)) {
                updated = updated.replace(fromBundle, toBundle);
                changes.add("MANIFEST.MF: bundle-version " + from.bundleVersion() + " -> " + to.bundleVersion());
            }

            if (!updated.equals(content)) {
                Files.writeString(manifest, updated);
            }
        } catch (IOException e) {
            System.err.println("  Error updating MANIFEST.MF: " + e.getMessage());
        }
    }

    private void migrateBuildProperties(Path pluginDir, PlatformVersion from, PlatformVersion to, List<String> changes) {
        Path buildProps = pluginDir.resolve("build.properties");
        if (!Files.exists(buildProps)) return;

        try {
            String content = Files.readString(buildProps);
            String updated = content;

            String fromSource = "javacSource = " + from.javaRelease();
            String toSource = "javacSource = " + to.javaRelease();
            if (updated.contains(fromSource)) {
                updated = updated.replace(fromSource, toSource);
                changes.add("build.properties: javacSource " + from.javaRelease() + " -> " + to.javaRelease());
            }

            String fromTarget = "javacTarget = " + from.javaRelease();
            String toTarget = "javacTarget = " + to.javaRelease();
            if (updated.contains(fromTarget)) {
                updated = updated.replace(fromTarget, toTarget);
                changes.add("build.properties: javacTarget " + from.javaRelease() + " -> " + to.javaRelease());
            }

            if (!updated.equals(content)) {
                Files.writeString(buildProps, updated);
            }
        } catch (IOException e) {
            System.err.println("  Error updating build.properties: " + e.getMessage());
        }
    }
}
