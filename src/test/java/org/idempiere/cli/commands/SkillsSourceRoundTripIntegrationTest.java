package org.idempiere.cli.commands;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.idempiere.cli.IdempiereCli;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SkillsSourceRoundTripIntegrationTest {

    @Inject
    CommandLine.IFactory factory;

    private Path tempHome;
    private String originalHome;

    @BeforeEach
    void setup() throws IOException {
        tempHome = Files.createTempDirectory("skills-source-roundtrip-");
        originalHome = System.getProperty("user.home");
        System.setProperty("user.home", tempHome.toString());
    }

    @AfterEach
    void cleanup() throws IOException {
        if (originalHome != null) {
            System.setProperty("user.home", originalHome);
        }
        if (tempHome != null && Files.exists(tempHome)) {
            Files.walk(tempHome)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    @Test
    void testSkillsSourceAddThenListRoundTrip() {
        ExecutionResult add = execute("skills", "source", "add",
                "--name=roundtrip-source",
                "--url=https://github.com/hengsin/idempiere-skills.git",
                "--priority=0");
        assertEquals(0, add.exitCode());
        assertTrue(add.stdout().contains("Added source 'roundtrip-source'"));

        ExecutionResult list = execute("skills", "source", "list");
        assertEquals(0, list.exitCode());
        assertTrue(list.stdout().contains("roundtrip-source"));
        assertTrue(list.stdout().contains("priority 0"));
        assertTrue(list.stdout().contains("url: https://github.com/hengsin/idempiere-skills.git"));
    }

    private ExecutionResult execute(String... args) {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        try {
            System.setOut(new PrintStream(outBuffer));
            System.setErr(new PrintStream(errBuffer));
            CommandLine commandLine = new CommandLine(new IdempiereCli(), factory);
            int exitCode = commandLine.execute(args);
            return new ExecutionResult(
                    exitCode,
                    outBuffer.toString(StandardCharsets.UTF_8),
                    errBuffer.toString(StandardCharsets.UTF_8)
            );
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private record ExecutionResult(int exitCode, String stdout, String stderr) {
    }
}
