package org.idempiere.cli.commands;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit-code contract tests for {@code doctor --dir}.
 *
 * <p>Covers the four code paths that must return STATE_ERROR (3):
 * missing directory and failing checks, in both text and {@code --json} modes.
 */
@QuarkusMainTest
@Tag("core-contract")
class DoctorDirExitCodeTest {

    private static final Path MISSING_DIR = Path.of("/tmp/idempiere-cli-test-doctor-missing");
    private static final Path EMPTY_DIR = Path.of("/tmp/idempiere-cli-test-doctor-empty");

    @BeforeAll
    static void setup() throws IOException {
        deleteDir(MISSING_DIR);
        deleteDir(EMPTY_DIR);
        Files.createDirectories(EMPTY_DIR);
    }

    @AfterAll
    static void cleanup() throws IOException {
        deleteDir(EMPTY_DIR);
    }

    @Test
    @Launch(value = {"doctor", "--dir=/tmp/idempiere-cli-test-doctor-missing"}, exitCode = 3)
    void testDoctorDirMissingReturnsStateError(LaunchResult result) {
        assertEquals(3, result.exitCode());
        assertTrue(result.getErrorOutput().contains("does not exist"));
    }

    @Test
    @Launch(value = {"doctor", "--dir=/tmp/idempiere-cli-test-doctor-missing", "--json"}, exitCode = 3)
    void testDoctorDirMissingJsonReturnsStateError(LaunchResult result) {
        assertEquals(3, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("\"error\""));
        assertTrue(output.contains("\"DIR_NOT_FOUND\""));
        assertTrue(output.contains("does not exist"));
    }

    @Test
    @Launch(value = {"doctor", "--dir=/tmp/idempiere-cli-test-doctor-empty"}, exitCode = 3)
    void testDoctorDirWithFailingChecksReturnsStateError(LaunchResult result) {
        assertEquals(3, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("Plugin Validation"));
        assertTrue(output.contains("failed"));
    }

    @Test
    @Launch(value = {"doctor", "--dir=/tmp/idempiere-cli-test-doctor-empty", "--json"}, exitCode = 3)
    void testDoctorDirWithFailingChecksJsonReturnsStateError(LaunchResult result) {
        assertEquals(3, result.exitCode());
        String output = result.getOutput();
        assertTrue(output.contains("\"failed\""));
        assertTrue(output.contains("\"checks\""));
    }

    private static void deleteDir(Path dir) throws IOException {
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
}
