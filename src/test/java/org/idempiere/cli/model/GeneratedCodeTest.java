package org.idempiere.cli.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCodeTest {

    @Test
    void testWriteToAppliesManifestAndBuildPropertiesAdditions(@TempDir Path tempDir) throws IOException {
        Path pluginDir = tempDir.resolve("plugin");
        Files.createDirectories(pluginDir.resolve("META-INF"));
        Files.createDirectories(pluginDir.resolve("src/org/example"));

        Path manifest = pluginDir.resolve("META-INF/MANIFEST.MF");
        Files.writeString(manifest, """
                Manifest-Version: 1.0
                Bundle-ManifestVersion: 2
                Bundle-SymbolicName: org.example.plugin
                Bundle-Version: 1.0.0.qualifier
                Require-Bundle: org.adempiere.base
                Import-Package: org.osgi.framework;version="1.3.0"
                Service-Component: OSGI-INF/*.xml
                """);

        Path buildProperties = pluginDir.resolve("build.properties");
        Files.writeString(buildProperties, """
                source.. = src/
                output.. = target/classes/
                bin.includes = META-INF/,.
                """);

        GeneratedCode code = new GeneratedCode();
        code.setFiles(List.of(
                new GeneratedCode.GeneratedFile(
                        "src/org/example/AiGeneratedCallout.java",
                        "package org.example;\npublic class AiGeneratedCallout {}"
                )
        ));
        code.setManifestAdditions(List.of(
                "Import-Package: org.osgi.framework;version=\"1.3.0\"",
                "org.osgi.service.event;version=\"1.4.0\""
        ));
        code.setBuildPropertiesAdditions(List.of(
                "build.properties: additional.property = true",
                "additional.property = true"
        ));

        code.writeTo(pluginDir);

        assertTrue(Files.exists(pluginDir.resolve("src/org/example/AiGeneratedCallout.java")));

        String manifestResult = Files.readString(manifest);
        assertEquals(1, countOccurrences(manifestResult, "org.osgi.framework;version=\"1.3.0\""));
        assertEquals(1, countOccurrences(manifestResult, "org.osgi.service.event;version=\"1.4.0\""));

        String buildPropsResult = Files.readString(buildProperties);
        assertEquals(1, countOccurrences(buildPropsResult, "additional.property = true"));
    }

    @Test
    void testWriteToAddsImportHeaderWhenMissing(@TempDir Path tempDir) throws IOException {
        Path pluginDir = tempDir.resolve("plugin");
        Files.createDirectories(pluginDir.resolve("META-INF"));

        Path manifest = pluginDir.resolve("META-INF/MANIFEST.MF");
        Files.writeString(manifest, """
                Manifest-Version: 1.0
                Bundle-ManifestVersion: 2
                Bundle-SymbolicName: org.example.plugin
                Service-Component: OSGI-INF/*.xml
                """);

        Path buildProperties = pluginDir.resolve("build.properties");
        Files.writeString(buildProperties, "source.. = src/\n");

        GeneratedCode code = new GeneratedCode();
        code.setFiles(List.of());
        code.setManifestAdditions(List.of("org.osgi.service.event;version=\"1.4.0\""));

        code.writeTo(pluginDir);

        String manifestResult = Files.readString(manifest);
        assertTrue(manifestResult.contains("Import-Package:"));
        assertTrue(manifestResult.contains("org.osgi.service.event;version=\"1.4.0\""));
    }

    @Test
    void testWriteToPreservesVersionRangeCommasInManifestImport(@TempDir Path tempDir) throws IOException {
        Path pluginDir = tempDir.resolve("plugin");
        Files.createDirectories(pluginDir.resolve("META-INF"));
        Path manifest = pluginDir.resolve("META-INF/MANIFEST.MF");
        Files.writeString(manifest, """
                Manifest-Version: 1.0
                Bundle-ManifestVersion: 2
                Bundle-SymbolicName: org.example.plugin
                Service-Component: OSGI-INF/*.xml
                """);
        Files.writeString(pluginDir.resolve("build.properties"), "source.. = src/\n");

        GeneratedCode code = new GeneratedCode();
        code.setManifestAdditions(List.of("org.example.foo;version=\"[1.0.0,2.0.0)\""));

        code.writeTo(pluginDir);

        String manifestResult = Files.readString(manifest);
        assertTrue(manifestResult.contains("org.example.foo;version=\"[1.0.0,2.0.0)\""));
    }

    @Test
    void testWriteToHandlesMissingManifestAndBuildProperties(@TempDir Path tempDir) throws IOException {
        Path pluginDir = tempDir.resolve("plugin");
        Files.createDirectories(pluginDir.resolve("src/org/example"));

        GeneratedCode code = new GeneratedCode();
        code.setFiles(List.of(new GeneratedCode.GeneratedFile(
                "src/org/example/OnlyJava.java",
                "package org.example;\npublic class OnlyJava {}"
        )));
        code.setManifestAdditions(List.of("org.osgi.service.event;version=\"1.4.0\""));
        code.setBuildPropertiesAdditions(List.of("additional.property = true"));

        code.writeTo(pluginDir);

        assertTrue(Files.exists(pluginDir.resolve("src/org/example/OnlyJava.java")));
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
