package org.idempiere.cli.service;

import org.idempiere.cli.model.SetupConfig;
import org.idempiere.cli.util.CliDefaults;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceManagerTest {

    private Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("source-manager-test-");
    }

    @AfterEach
    void cleanup() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    @Test
    void testCloneOrUpdateFailsWhenDirectoryIsNotGitRepo() throws IOException {
        SourceManager manager = new SourceManager();
        manager.processRunner = new ProcessRunner();
        manager.sessionLogger = new SessionLogger();

        Path sourceDir = tempDir.resolve("idempiere");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("README.txt"), "not a git repo");

        SetupConfig config = new SetupConfig();
        config.setSourceDir(sourceDir);
        config.setNonInteractive(true);

        assertFalse(manager.cloneOrUpdate(config));
    }

    @Test
    void testBuildSourceFailsWhenPomIsMissing() throws IOException {
        SourceManager manager = new SourceManager();
        manager.processRunner = new ProcessRunner();
        manager.sessionLogger = new SessionLogger();

        Path sourceDir = tempDir.resolve("idempiere");
        Files.createDirectories(sourceDir);

        SetupConfig config = new SetupConfig();
        config.setSourceDir(sourceDir);

        assertFalse(manager.buildSource(config));
    }

    @Test
    void testDownloadJythonSkipsWhenJarAlreadyExists() throws IOException {
        SourceManager manager = new SourceManager();
        manager.processRunner = new ProcessRunner();
        manager.sessionLogger = new SessionLogger();

        Path sourceDir = tempDir.resolve("idempiere");
        Path libDir = sourceDir.resolve("lib");
        Files.createDirectories(libDir);
        Files.createFile(libDir.resolve("jython-standalone-" + CliDefaults.JYTHON_VERSION + ".jar"));

        SetupConfig config = new SetupConfig();
        config.setSourceDir(sourceDir);

        assertTrue(manager.downloadJython(config));
    }
}
