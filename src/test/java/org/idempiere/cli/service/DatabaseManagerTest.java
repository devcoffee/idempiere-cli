package org.idempiere.cli.service;

import org.idempiere.cli.model.SetupConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerTest {

    @Test
    void testGetDockerStatusRunningWhenDockerInfoSucceeds() {
        DatabaseManager manager = new DatabaseManager();
        manager.processRunner = new StubProcessRunner(new ProcessRunner.RunResult(0, "Docker is running"));
        manager.sessionLogger = new SessionLogger();

        assertEquals(DatabaseManager.DockerStatus.RUNNING, manager.getDockerStatus());
        assertTrue(manager.isDockerRunning());
    }

    @Test
    void testGetDockerStatusNotRunningWhenDockerInfoFails() {
        DatabaseManager manager = new DatabaseManager();
        manager.processRunner = new StubProcessRunner(new ProcessRunner.RunResult(1, "daemon not reachable"));
        manager.sessionLogger = new SessionLogger();

        assertEquals(DatabaseManager.DockerStatus.NOT_RUNNING, manager.getDockerStatus());
        assertFalse(manager.isDockerRunning());
    }

    @Test
    void testValidateConnectionReturnsFalseForUnsupportedDbType() {
        DatabaseManager manager = new DatabaseManager();
        manager.processRunner = new StubProcessRunner(new ProcessRunner.RunResult(0, ""));
        manager.sessionLogger = new SessionLogger();

        SetupConfig config = new SetupConfig();
        config.setDbType("sqlserver");

        assertFalse(manager.validateConnection(config));
    }

    private static class StubProcessRunner extends ProcessRunner {
        private final RunResult runResult;

        private StubProcessRunner(RunResult runResult) {
            this.runResult = runResult;
        }

        @Override
        public RunResult run(String... command) {
            return runResult;
        }

        @Override
        public RunResult runWithEnv(java.util.Map<String, String> env, String... command) {
            return runResult;
        }
    }
}
