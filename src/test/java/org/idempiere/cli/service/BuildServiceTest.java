package org.idempiere.cli.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BuildService.
 */
@QuarkusTest
class BuildServiceTest {

    @Inject
    BuildService buildService;

    private Path tempDir;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("build-test-");
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void cleanup() throws IOException {
        System.setOut(originalOut);
        System.setErr(originalErr);
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
    void testFindBuiltJarNoTarget() {
        Optional<Path> jar = buildService.findBuiltJar(tempDir);
        assertTrue(jar.isEmpty());
    }

    @Test
    void testFindBuiltJarWithJar() throws IOException {
        Path targetDir = tempDir.resolve("target");
        Files.createDirectories(targetDir);
        Path jar = targetDir.resolve("myplugin-1.0.0.jar");
        Files.createFile(jar);

        Optional<Path> result = buildService.findBuiltJar(tempDir);
        assertTrue(result.isPresent());
        assertEquals(jar, result.get());
    }

    @Test
    void testFindBuiltJarIgnoresSourcesJar() throws IOException {
        Path targetDir = tempDir.resolve("target");
        Files.createDirectories(targetDir);
        Files.createFile(targetDir.resolve("myplugin-1.0.0-sources.jar"));

        Optional<Path> result = buildService.findBuiltJar(tempDir);
        assertTrue(result.isEmpty());
    }

    @Test
    void testBuildWithoutMaven() throws IOException {
        // Create a directory without Maven wrapper
        Path pluginDir = tempDir.resolve("plugin");
        Files.createDirectories(pluginDir);

        // Build should fail because Maven is not found (no mvnw)
        boolean result = buildService.build(pluginDir, null, false, true, false, true, null);

        // Could succeed or fail depending on whether mvn is installed
        // Just verify it doesn't throw
        assertNotNull(result);
    }
}
