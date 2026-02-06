package org.idempiere.cli.model;

import org.idempiere.cli.util.CliDefaults;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SetupConfig.
 */
class SetupConfigTest {

    @Test
    void testDefaultValues() {
        SetupConfig config = new SetupConfig();

        assertEquals(CliDefaults.GIT_BRANCH, config.getBranch());
        assertEquals(CliDefaults.IDEMPIERE_REPO_URL, config.getRepositoryUrl());
        assertEquals(CliDefaults.DB_TYPE, config.getDbType());
        assertEquals(CliDefaults.DB_HOST, config.getDbHost());
        assertEquals(CliDefaults.DB_PORT, config.getDbPort());
        assertEquals(CliDefaults.DB_NAME, config.getDbName());
        assertEquals(CliDefaults.DB_USER, config.getDbUser());
        assertEquals(CliDefaults.DB_PASSWORD, config.getDbPass());
        assertEquals(CliDefaults.DB_ADMIN_PASSWORD, config.getDbAdminPass());
        assertEquals(CliDefaults.HTTP_BIND_ADDRESS, config.getHttpHost());
        assertEquals(CliDefaults.HTTP_PORT, config.getHttpPort());
        assertEquals(CliDefaults.HTTPS_PORT, config.getHttpsPort());
        assertEquals(CliDefaults.DOCKER_CONTAINER_NAME, config.getDockerContainerName());
        assertEquals(CliDefaults.DOCKER_POSTGRES_VERSION, config.getDockerPostgresVersion());
        assertFalse(config.isUseDocker());
        assertFalse(config.isSkipBuild());
        assertFalse(config.isSkipDb());
        assertFalse(config.isSkipWorkspace());
        assertFalse(config.isIncludeRest());
        assertFalse(config.isInstallCopilot());
        assertFalse(config.isNonInteractive());
        assertFalse(config.isContinueOnError());
    }

    @Test
    void testSetSourceDir() {
        SetupConfig config = new SetupConfig();
        Path path = Path.of("/tmp/idempiere");
        config.setSourceDir(path);

        assertEquals(path, config.getSourceDir());
    }

    @Test
    void testSetEclipseDir() {
        SetupConfig config = new SetupConfig();
        Path path = Path.of("/opt/eclipse");
        config.setEclipseDir(path);

        assertEquals(path, config.getEclipseDir());
    }

    @Test
    void testSetBranch() {
        SetupConfig config = new SetupConfig();
        config.setBranch("release-12");

        assertEquals("release-12", config.getBranch());
    }

    @Test
    void testSetRepositoryUrl() {
        SetupConfig config = new SetupConfig();
        config.setRepositoryUrl("https://github.com/myorg/idempiere.git");

        assertEquals("https://github.com/myorg/idempiere.git", config.getRepositoryUrl());
    }

    @Test
    void testSetDbType() {
        SetupConfig config = new SetupConfig();
        config.setDbType("oracle");

        assertEquals("oracle", config.getDbType());
    }

    @Test
    void testSetDbHost() {
        SetupConfig config = new SetupConfig();
        config.setDbHost("dbserver.example.com");

        assertEquals("dbserver.example.com", config.getDbHost());
    }

    @Test
    void testSetDbPort() {
        SetupConfig config = new SetupConfig();
        config.setDbPort(5433);

        assertEquals(5433, config.getDbPort());
    }

    @Test
    void testSetDbName() {
        SetupConfig config = new SetupConfig();
        config.setDbName("mydb");

        assertEquals("mydb", config.getDbName());
    }

    @Test
    void testSetDbUser() {
        SetupConfig config = new SetupConfig();
        config.setDbUser("myuser");

        assertEquals("myuser", config.getDbUser());
    }

    @Test
    void testSetDbPass() {
        SetupConfig config = new SetupConfig();
        config.setDbPass("secret");

        assertEquals("secret", config.getDbPass());
    }

    @Test
    void testSetDbAdminPass() {
        SetupConfig config = new SetupConfig();
        config.setDbAdminPass("adminpw");

        assertEquals("adminpw", config.getDbAdminPass());
    }

    @Test
    void testSetHttpHost() {
        SetupConfig config = new SetupConfig();
        config.setHttpHost("127.0.0.1");

        assertEquals("127.0.0.1", config.getHttpHost());
    }

    @Test
    void testSetHttpPort() {
        SetupConfig config = new SetupConfig();
        config.setHttpPort(9090);

        assertEquals(9090, config.getHttpPort());
    }

    @Test
    void testSetHttpsPort() {
        SetupConfig config = new SetupConfig();
        config.setHttpsPort(9443);

        assertEquals(9443, config.getHttpsPort());
    }

    @Test
    void testSetUseDocker() {
        SetupConfig config = new SetupConfig();
        config.setUseDocker(true);

        assertTrue(config.isUseDocker());
    }

    @Test
    void testSetDockerContainerName() {
        SetupConfig config = new SetupConfig();
        config.setDockerContainerName("my-postgres");

        assertEquals("my-postgres", config.getDockerContainerName());
    }

    @Test
    void testSetDockerPostgresVersion() {
        SetupConfig config = new SetupConfig();
        config.setDockerPostgresVersion("14");

        assertEquals("14", config.getDockerPostgresVersion());
    }

    @Test
    void testOracleDockerSettings() {
        SetupConfig config = new SetupConfig();
        config.setOracleDockerContainer("my-oracle");
        config.setOracleDockerImage("oracle/database:19c");
        config.setOracleDockerHome("/u01/app/oracle");

        assertEquals("my-oracle", config.getOracleDockerContainer());
        assertEquals("oracle/database:19c", config.getOracleDockerImage());
        assertEquals("/u01/app/oracle", config.getOracleDockerHome());
    }

    @Test
    void testSetSkipBuild() {
        SetupConfig config = new SetupConfig();
        config.setSkipBuild(true);

        assertTrue(config.isSkipBuild());
    }

    @Test
    void testSetSkipDb() {
        SetupConfig config = new SetupConfig();
        config.setSkipDb(true);

        assertTrue(config.isSkipDb());
    }

    @Test
    void testSetSkipWorkspace() {
        SetupConfig config = new SetupConfig();
        config.setSkipWorkspace(true);

        assertTrue(config.isSkipWorkspace());
    }

    @Test
    void testSetIncludeRest() {
        SetupConfig config = new SetupConfig();
        config.setIncludeRest(true);

        assertTrue(config.isIncludeRest());
    }

    @Test
    void testSetInstallCopilot() {
        SetupConfig config = new SetupConfig();
        config.setInstallCopilot(true);

        assertTrue(config.isInstallCopilot());
    }

    @Test
    void testSetNonInteractive() {
        SetupConfig config = new SetupConfig();
        config.setNonInteractive(true);

        assertTrue(config.isNonInteractive());
    }

    @Test
    void testSetContinueOnError() {
        SetupConfig config = new SetupConfig();
        config.setContinueOnError(true);

        assertTrue(config.isContinueOnError());
    }

    @Test
    void testGetDbConnectionStringPostgres() {
        SetupConfig config = new SetupConfig();
        config.setDbType("postgresql");
        config.setDbHost("localhost");
        config.setDbPort(5432);
        config.setDbName("idempiere");

        String connStr = config.getDbConnectionString();
        assertEquals("postgresql://localhost:5432/idempiere", connStr);
    }

    @Test
    void testGetDbConnectionStringOracle() {
        SetupConfig config = new SetupConfig();
        config.setDbType("oracle");
        config.setDbHost("oradb");
        config.setDbPort(1521);
        config.setDbName("XEPDB1");

        String connStr = config.getDbConnectionString();
        assertEquals("oracle://oradb:1521/XEPDB1", connStr);
    }
}
