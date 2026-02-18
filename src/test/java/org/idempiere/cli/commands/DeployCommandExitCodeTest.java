package org.idempiere.cli.commands;

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
class DeployCommandExitCodeTest {

    private static final Path PLUGIN_DIR = Path.of("/tmp/idempiere-cli-test-deploy-hot-plugin");

    @BeforeEach
    void setup() throws IOException {
        deleteDir(PLUGIN_DIR);

        Path manifestDir = PLUGIN_DIR.resolve("META-INF");
        Path targetDir = PLUGIN_DIR.resolve("target");
        Files.createDirectories(manifestDir);
        Files.createDirectories(targetDir);

        Files.writeString(manifestDir.resolve("MANIFEST.MF"),
                "Bundle-SymbolicName: org.test.deploy\n" +
                        "Bundle-Version: 1.0.0.qualifier\n");
        Files.writeString(targetDir.resolve("org.test.deploy-1.0.0.jar"), "dummy");
    }

    @AfterEach
    void cleanup() throws IOException {
        deleteDir(PLUGIN_DIR);
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
    @Launch(value = {
            "deploy",
            "--dir=/tmp/idempiere-cli-test-deploy-hot-plugin",
            "--target=/tmp",
            "--hot",
            "--osgi-host=127.0.0.1",
            "--osgi-port=1"
    }, exitCode = 2)
    void testDeployHotReturnsIoErrorWhenOsgiUnavailable(LaunchResult result) {
        assertEquals(2, result.exitCode());
        assertTrue(result.getErrorOutput().contains("Error connecting to OSGi console"));
    }
}
