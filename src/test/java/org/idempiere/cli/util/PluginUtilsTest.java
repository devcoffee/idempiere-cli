package org.idempiere.cli.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PluginUtils.
 */
class PluginUtilsTest {

    private Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("plugin-utils-test-");
    }

    @AfterEach
    void cleanup() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException ignored) {}
                    });
        }
    }

    @Test
    void testFindP2RepositoryWithNull() {
        Optional<Path> result = PluginUtils.findP2Repository(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindP2RepositoryWithNonExistentPath() {
        Optional<Path> result = PluginUtils.findP2Repository(Path.of("/non/existent/path"));
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindP2RepositoryDirectlyIsP2() throws IOException {
        // Create content.jar to make it a p2 repository
        Files.createFile(tempDir.resolve("content.jar"));

        Optional<Path> result = PluginUtils.findP2Repository(tempDir);
        assertTrue(result.isPresent());
        assertEquals(tempDir, result.get());
    }

    @Test
    void testFindP2RepositoryWithContentXml() throws IOException {
        Files.createFile(tempDir.resolve("content.xml"));

        Optional<Path> result = PluginUtils.findP2Repository(tempDir);
        assertTrue(result.isPresent());
    }

    @Test
    void testFindP2RepositoryFromProductDir() throws IOException {
        // Simulate: /path/org.idempiere.p2/target/repository structure
        Path p2Dir = tempDir.resolve("org.idempiere.p2/target/repository");
        Files.createDirectories(p2Dir);
        Files.createFile(p2Dir.resolve("content.jar"));

        // Search from tempDir (sibling of org.idempiere.p2)
        Optional<Path> result = PluginUtils.findP2Repository(tempDir);
        assertTrue(result.isPresent());
        assertEquals(p2Dir, result.get());
    }

    @Test
    void testFindP2RepositoryFromInsideP2Dir() throws IOException {
        // Create: /path/org.idempiere.p2/target/repository
        Path p2Dir = tempDir.resolve("org.idempiere.p2");
        Path repoDir = p2Dir.resolve("target/repository");
        Files.createDirectories(repoDir);
        Files.createFile(repoDir.resolve("content.jar"));

        // Search from inside org.idempiere.p2
        Optional<Path> result = PluginUtils.findP2Repository(p2Dir);
        assertTrue(result.isPresent());
        assertEquals(repoDir, result.get());
    }

    @Test
    void testIsP2RepositoryWithNull() {
        assertFalse(PluginUtils.isP2Repository(null));
    }

    @Test
    void testIsP2RepositoryWithFile() throws IOException {
        Path file = tempDir.resolve("file.txt");
        Files.createFile(file);
        assertFalse(PluginUtils.isP2Repository(file));
    }

    @Test
    void testIsP2RepositoryWithEmptyDir() {
        assertFalse(PluginUtils.isP2Repository(tempDir));
    }

    @Test
    void testIsP2RepositoryWithContentJar() throws IOException {
        Files.createFile(tempDir.resolve("content.jar"));
        assertTrue(PluginUtils.isP2Repository(tempDir));
    }

    @Test
    void testIsP2RepositoryWithContentXml() throws IOException {
        Files.createFile(tempDir.resolve("content.xml"));
        assertTrue(PluginUtils.isP2Repository(tempDir));
    }

    @Test
    void testFindBuiltJarWithNoTargetDir() {
        Optional<Path> result = PluginUtils.findBuiltJar(tempDir);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindBuiltJarWithEmptyTargetDir() throws IOException {
        Files.createDirectories(tempDir.resolve("target"));
        Optional<Path> result = PluginUtils.findBuiltJar(tempDir);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindBuiltJarFindsJar() throws IOException {
        Path targetDir = tempDir.resolve("target");
        Files.createDirectories(targetDir);
        Path jar = targetDir.resolve("my-plugin-1.0.0.jar");
        Files.createFile(jar);

        Optional<Path> result = PluginUtils.findBuiltJar(tempDir);
        assertTrue(result.isPresent());
        assertEquals(jar, result.get());
    }

    @Test
    void testFindBuiltJarExcludesSourcesJar() throws IOException {
        Path targetDir = tempDir.resolve("target");
        Files.createDirectories(targetDir);
        Files.createFile(targetDir.resolve("my-plugin-1.0.0-sources.jar"));

        Optional<Path> result = PluginUtils.findBuiltJar(tempDir);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindBuiltJarExcludesClassesJar() throws IOException {
        Path targetDir = tempDir.resolve("target");
        Files.createDirectories(targetDir);
        Files.createFile(targetDir.resolve("classes.jar"));

        Optional<Path> result = PluginUtils.findBuiltJar(tempDir);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindBuiltJarPrefersMainJarOverSources() throws IOException {
        Path targetDir = tempDir.resolve("target");
        Files.createDirectories(targetDir);
        Files.createFile(targetDir.resolve("my-plugin-1.0.0-sources.jar"));
        Path mainJar = targetDir.resolve("my-plugin-1.0.0.jar");
        Files.createFile(mainJar);

        Optional<Path> result = PluginUtils.findBuiltJar(tempDir);
        assertTrue(result.isPresent());
        assertEquals(mainJar, result.get());
    }
}
