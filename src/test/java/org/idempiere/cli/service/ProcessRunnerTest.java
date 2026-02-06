package org.idempiere.cli.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProcessRunner.
 */
@QuarkusTest
class ProcessRunnerTest {

    @Inject
    ProcessRunner processRunner;

    @Test
    void testRunSimpleCommand() {
        ProcessRunner.RunResult result = processRunner.run("echo", "hello");
        assertEquals(0, result.exitCode());
        assertTrue(result.isSuccess());
        assertTrue(result.output().contains("hello"));
    }

    @Test
    void testRunCommandWithFailure() {
        ProcessRunner.RunResult result = processRunner.run("ls", "/nonexistent/path/12345");
        assertFalse(result.isSuccess());
        assertNotEquals(0, result.exitCode());
    }

    @Test
    void testRunInDir() {
        ProcessRunner.RunResult result = processRunner.runInDir(Path.of("/tmp"), "pwd");
        assertTrue(result.isSuccess());
        assertTrue(result.output().contains("tmp") || result.output().contains("private"));
    }

    @Test
    void testIsAvailableForCommonCommand() {
        // echo should be available on all Unix systems
        boolean available = processRunner.isAvailable("ls");
        assertTrue(available);
    }

    @Test
    void testIsAvailableForNonexistentCommand() {
        boolean available = processRunner.isAvailable("nonexistent_command_xyz123");
        assertFalse(available);
    }

    @Test
    void testRunResultRecord() {
        ProcessRunner.RunResult success = new ProcessRunner.RunResult(0, "output");
        assertTrue(success.isSuccess());
        assertEquals(0, success.exitCode());
        assertEquals("output", success.output());

        ProcessRunner.RunResult failure = new ProcessRunner.RunResult(1, "error");
        assertFalse(failure.isSuccess());
    }
}
