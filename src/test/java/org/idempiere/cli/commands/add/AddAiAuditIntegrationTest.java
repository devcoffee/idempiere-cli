package org.idempiere.cli.commands.add;

import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.idempiere.cli.IdempiereCli;
import org.idempiere.cli.service.ai.AiClient;
import org.idempiere.cli.service.ai.AiClientFactory;
import org.idempiere.cli.service.ai.AiResponse;
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
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AddAiAuditIntegrationTest {

    @Inject
    CommandLine.IFactory factory;

    private Path tempHome;
    private Path pluginDir;
    private String originalHome;

    @BeforeEach
    void setup() throws IOException {
        tempHome = Files.createTempDirectory("add-ai-audit-");
        pluginDir = tempHome.resolve("org.test.plugin");

        originalHome = System.getProperty("user.home");
        System.setProperty("user.home", tempHome.toString());

        Files.createDirectories(pluginDir.resolve("META-INF"));
        Files.createDirectories(pluginDir.resolve("src/org/test/plugin"));
        Files.writeString(pluginDir.resolve("META-INF/MANIFEST.MF"),
                "Manifest-Version: 1.0\n" +
                        "Bundle-ManifestVersion: 2\n" +
                        "Bundle-SymbolicName: org.test.plugin\n" +
                        "Bundle-Version: 1.0.0.qualifier\n" +
                        "Bundle-RequiredExecutionEnvironment: JavaSE-17\n");
        Files.writeString(pluginDir.resolve("plugin.xml"), "<plugin/>");

        QuarkusMock.installMockForType(new FakeAiClientFactory(), AiClientFactory.class);
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
    void testAddCalloutWithSaveAiDebugWritesAuditArtifact() throws IOException {
        ExecutionResult result = execute(
                "add", "callout",
                "--to=" + pluginDir,
                "--name=AuditCallout",
                "--prompt=Fill Description from Name and Name2",
                "--save-ai-debug"
        );

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("AI debug saved:"));
        assertTrue(result.stdout().contains("Generated with AI"));
        assertTrue(result.stdout().contains("manual review"));

        Path debugDir = pluginDir.resolve(".idempiere-cli").resolve("ai-debug");
        assertTrue(Files.isDirectory(debugDir), "Expected ai-debug directory");

        Path debugFile;
        try (Stream<Path> files = Files.list(debugDir)) {
            debugFile = files.filter(p -> p.getFileName().toString().endsWith(".log"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected at least one AI debug log file"));
        }

        String debugContent = Files.readString(debugFile);
        assertTrue(debugContent.contains("### AI PROMPT"));
        assertTrue(debugContent.contains("### AI RESPONSE"));
        assertTrue(debugContent.contains("### RESULT"));
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
                    outBuffer.toString(StandardCharsets.UTF_8)
            );
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private record ExecutionResult(int exitCode, String stdout) {
    }

    private static class FakeAiClientFactory extends AiClientFactory {
        @Override
        public Optional<AiClient> getClient() {
            return Optional.of(new FakeAiClient());
        }
    }

    private static class FakeAiClient implements AiClient {
        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public AiResponse generate(String prompt) {
            String response = """
                    {
                      "files": [
                        {
                          "path": "src/org/test/plugin/AuditCallout.java",
                          "content": "package org.test.plugin;\\npublic class AuditCallout {}"
                        }
                      ],
                      "manifest_additions": [],
                      "build_properties_additions": []
                    }
                    """;
            return AiResponse.ok(response);
        }

        @Override
        public String providerName() {
            return "fake-ai";
        }
    }
}
