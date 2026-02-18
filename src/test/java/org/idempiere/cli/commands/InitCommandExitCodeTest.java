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
class InitCommandExitCodeTest {

    private static final Path EXISTING_DIR = Path.of("/tmp/idempiere-cli-test-init-existing");

    @BeforeEach
    void setup() throws IOException {
        deleteDir(EXISTING_DIR);
        Files.createDirectories(EXISTING_DIR);
    }

    @AfterEach
    void cleanup() throws IOException {
        deleteDir(EXISTING_DIR);
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
    @Launch(value = {"init", "org.test.existing", "--standalone", "--name=/tmp/idempiere-cli-test-init-existing"}, exitCode = 3)
    void testInitReturnsErrorWhenTargetDirectoryAlreadyExists(LaunchResult result) {
        assertEquals(3, result.exitCode());
        String output = result.getOutput() + result.getErrorOutput();
        assertTrue(output.contains("already exists"));
    }
}
