package org.idempiere.cli.service;

import org.idempiere.cli.model.SetupConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetupDevEnvServiceTest {

    private Path tempDir;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("setup-dev-env-service-test-");
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
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    @Test
    void testSetupAbortsOnDockerPreflightFailureBeforeSourceStep() {
        SetupDevEnvService service = new SetupDevEnvService();
        StubSourceManager sourceManager = new StubSourceManager();
        StubEclipseManager eclipseManager = new StubEclipseManager();
        StubDatabaseManager databaseManager = new StubDatabaseManager();
        StubSessionLogger sessionLogger = new StubSessionLogger();

        databaseManager.dockerStatus = DatabaseManager.DockerStatus.NOT_RUNNING;

        service.sourceManager = sourceManager;
        service.eclipseManager = eclipseManager;
        service.databaseManager = databaseManager;
        service.processRunner = new StubProcessRunner();
        service.sessionLogger = sessionLogger;

        SetupConfig config = baseConfig();
        config.setUseDocker(true);
        config.setSkipDb(false);

        service.setup(config);

        assertFalse(sourceManager.cloneCalled);
        assertFalse(databaseManager.setupCalled);
        assertTrue(sessionLogger.endCalled);
        assertFalse(sessionLogger.endSuccess);
        assertTrue(errContent.toString().contains("Docker is not running"));
    }

    @Test
    void testSetupAbortsWhenBuildFailsWithoutContinueOnError() {
        SetupDevEnvService service = new SetupDevEnvService();
        StubSourceManager sourceManager = new StubSourceManager();
        StubEclipseManager eclipseManager = new StubEclipseManager();
        StubDatabaseManager databaseManager = new StubDatabaseManager();
        StubSessionLogger sessionLogger = new StubSessionLogger();

        sourceManager.cloneResult = true;
        sourceManager.buildResult = false;

        service.sourceManager = sourceManager;
        service.eclipseManager = eclipseManager;
        service.databaseManager = databaseManager;
        service.processRunner = new StubProcessRunner();
        service.sessionLogger = sessionLogger;

        SetupConfig config = baseConfig();
        config.setSkipBuild(false);
        config.setSkipDb(true);
        config.setSkipWorkspace(true);
        config.setContinueOnError(false);

        service.setup(config);

        assertTrue(sourceManager.cloneCalled);
        assertTrue(sourceManager.buildCalled);
        assertFalse(sourceManager.downloadJythonCalled);
        assertTrue(sessionLogger.endCalled);
        assertFalse(sessionLogger.endSuccess);
        assertTrue(errContent.toString().contains("Build failed. Aborting."));
    }

    private SetupConfig baseConfig() {
        SetupConfig config = new SetupConfig();
        config.setSourceDir(tempDir.resolve("idempiere"));
        config.setEclipseDir(tempDir.resolve("eclipse"));
        config.setNonInteractive(true);
        config.setSkipBuild(true);
        config.setSkipWorkspace(true);
        config.setSkipDb(true);
        return config;
    }

    private static class StubSourceManager extends SourceManager {
        boolean cloneCalled;
        boolean buildCalled;
        boolean downloadJythonCalled;
        boolean cloneResult = true;
        boolean buildResult = true;
        boolean downloadJythonResult = true;

        @Override
        public boolean cloneOrUpdate(SetupConfig config) {
            cloneCalled = true;
            return cloneResult;
        }

        @Override
        public boolean buildSource(SetupConfig config) {
            buildCalled = true;
            return buildResult;
        }

        @Override
        public boolean downloadJython(SetupConfig config) {
            downloadJythonCalled = true;
            return downloadJythonResult;
        }
    }

    private static class StubEclipseManager extends EclipseManager {
        @Override
        public boolean detectOrInstall(SetupConfig config) {
            return true;
        }

        @Override
        public boolean installPlugins(SetupConfig config) {
            return true;
        }

        @Override
        public boolean setupWorkspace(SetupConfig config) {
            return true;
        }

        @Override
        public Path getEclipseExecutable(Path eclipseDir) {
            return eclipseDir.resolve("eclipse");
        }
    }

    private static class StubDatabaseManager extends DatabaseManager {
        DockerStatus dockerStatus = DockerStatus.RUNNING;
        boolean setupCalled;

        @Override
        public DockerStatus getDockerStatus() {
            return dockerStatus;
        }

        @Override
        public boolean setupDatabase(SetupConfig config) {
            setupCalled = true;
            return true;
        }
    }

    private static class StubProcessRunner extends ProcessRunner {
        @Override
        public RunResult run(String... command) {
            return new RunResult(0, "openjdk version \"21\"");
        }
    }

    private static class StubSessionLogger extends SessionLogger {
        boolean endCalled;
        boolean endSuccess;

        @Override
        public void startSession(String command) {
            // no-op
        }

        @Override
        public void cleanOldLogs(int keepCount) {
            // no-op
        }

        @Override
        public void endSession(boolean success) {
            endCalled = true;
            endSuccess = success;
        }

        @Override
        public void logInfo(String message) {
            // no-op
        }

        @Override
        public void logError(String message) {
            // no-op
        }

        @Override
        public void logStep(int current, int total, String description) {
            // no-op
        }

        @Override
        public void logStepResult(boolean success, String component) {
            // no-op
        }
    }
}
