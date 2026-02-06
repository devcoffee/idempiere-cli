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
 * Unit tests for DeployService.
 */
@QuarkusTest
class DeployServiceTest {

    @Inject
    DeployService deployService;

    private Path tempDir;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("deploy-test-");
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
    void testCopyDeployWithNoPluginsDir() throws IOException {
        Path jarFile = tempDir.resolve("myplugin-1.0.0.jar");
        Files.createFile(jarFile);
        Path idempiereHome = tempDir.resolve("idempiere");
        Files.createDirectories(idempiereHome);
        // No plugins/ directory

        boolean result = deployService.copyDeploy(jarFile, idempiereHome);

        assertFalse(result);
        String errOutput = errContent.toString();
        assertTrue(errOutput.contains("plugins/ directory not found"));
    }

    @Test
    void testCopyDeploySuccess() throws IOException {
        // Create jar file
        Path jarFile = tempDir.resolve("myplugin-1.0.0.jar");
        Files.writeString(jarFile, "fake jar content");

        // Create iDempiere home with plugins dir
        Path idempiereHome = tempDir.resolve("idempiere");
        Path pluginsDir = idempiereHome.resolve("plugins");
        Files.createDirectories(pluginsDir);

        boolean result = deployService.copyDeploy(jarFile, idempiereHome);

        assertTrue(result);
        assertTrue(Files.exists(pluginsDir.resolve("myplugin-1.0.0.jar")));
        String output = outContent.toString();
        assertTrue(output.contains("Deployed to:"));
        assertTrue(output.contains("Restart iDempiere"));
    }

    @Test
    void testFindBuiltJarWithNoTarget() {
        Optional<Path> result = deployService.findBuiltJar(tempDir);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindBuiltJarWithJar() throws IOException {
        Path targetDir = tempDir.resolve("target");
        Files.createDirectories(targetDir);
        Path jar = targetDir.resolve("myplugin-1.0.0.jar");
        Files.createFile(jar);

        Optional<Path> result = deployService.findBuiltJar(tempDir);
        assertTrue(result.isPresent());
        assertEquals(jar, result.get());
    }

    @Test
    void testHotDeployConnectionRefused() {
        Path jarFile = tempDir.resolve("myplugin.jar");
        try {
            Files.createFile(jarFile);
        } catch (IOException e) {
            fail("Could not create test file");
        }

        // Try to connect to a port that should not have anything running
        boolean result = deployService.hotDeploy(jarFile, "localhost", 59999);

        assertFalse(result);
        String errOutput = errContent.toString();
        assertTrue(errOutput.contains("Error connecting") || errOutput.contains("Connection refused"));
    }
}
