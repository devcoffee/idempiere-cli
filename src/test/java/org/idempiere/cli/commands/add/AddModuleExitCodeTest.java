package org.idempiere.cli.commands.add;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusMainTest
class AddModuleExitCodeTest {

    private static final Path PLUGIN_ROOT = Path.of("/tmp/idempiere-cli-test-add-plugin-root");
    private static final Path FRAGMENT_ROOT = Path.of("/tmp/idempiere-cli-test-add-fragment-root");
    private static final Path FEATURE_ROOT = Path.of("/tmp/idempiere-cli-test-add-feature-root");

    @BeforeEach
    void setup() throws IOException {
        deleteDir(PLUGIN_ROOT);
        deleteDir(FRAGMENT_ROOT);
        deleteDir(FEATURE_ROOT);

        createMultiModuleRoot(PLUGIN_ROOT);
        Files.createDirectories(PLUGIN_ROOT.resolve("org.test.existing.module"));

        createMultiModuleRoot(FRAGMENT_ROOT);
        String fragmentId = FRAGMENT_ROOT.getFileName() + ".fragment";
        Files.createDirectories(FRAGMENT_ROOT.resolve(fragmentId));

        createMultiModuleRoot(FEATURE_ROOT);
        String featureId = FEATURE_ROOT.getFileName() + ".feature";
        Files.createDirectories(FEATURE_ROOT.resolve(featureId));
    }

    @AfterEach
    void cleanup() throws IOException {
        deleteDir(PLUGIN_ROOT);
        deleteDir(FRAGMENT_ROOT);
        deleteDir(FEATURE_ROOT);
    }

    private void createMultiModuleRoot(Path rootDir) throws IOException {
        Files.createDirectories(rootDir);
        Files.writeString(rootDir.resolve("pom.xml"),
                "<project>\n" +
                        "  <modelVersion>4.0.0</modelVersion>\n" +
                        "  <groupId>org.test</groupId>\n" +
                        "  <artifactId>test-parent</artifactId>\n" +
                        "  <version>1.0.0-SNAPSHOT</version>\n" +
                        "  <packaging>pom</packaging>\n" +
                        "  <modules>\n" +
                        "    <module>dummy.module</module>\n" +
                        "  </modules>\n" +
                        "</project>\n");
        Files.createDirectories(rootDir.resolve("dummy.module"));
    }

    private void deleteDir(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Test
    @Launch(value = {"add", "plugin", "org.test.existing.module", "--to=/tmp/idempiere-cli-test-add-plugin-root"}, exitCode = 3)
    void testAddPluginReturnsErrorWhenModuleDirectoryAlreadyExists(LaunchResult result) {
        assertEquals(3, result.exitCode());
        String output = result.getOutput() + result.getErrorOutput();
        assertTrue(output.contains("already exists"));
    }

    @Test
    @Launch(value = {"add", "fragment", "--to=/tmp/idempiere-cli-test-add-fragment-root"}, exitCode = 3)
    void testAddFragmentReturnsErrorWhenModuleDirectoryAlreadyExists(LaunchResult result) {
        assertEquals(3, result.exitCode());
        String output = result.getOutput() + result.getErrorOutput();
        assertTrue(output.contains("already exists"));
    }

    @Test
    @Launch(value = {"add", "feature", "--to=/tmp/idempiere-cli-test-add-feature-root"}, exitCode = 3)
    void testAddFeatureReturnsErrorWhenModuleDirectoryAlreadyExists(LaunchResult result) {
        assertEquals(3, result.exitCode());
        String output = result.getOutput() + result.getErrorOutput();
        assertTrue(output.contains("already exists"));
    }
}
