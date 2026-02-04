package org.idempiere.cli.model;

import org.idempiere.cli.util.CliDefaults;
import java.nio.file.Path;

public class SetupConfig {

    private Path sourceDir;
    private Path eclipseDir;
    private String branch = CliDefaults.GIT_BRANCH;
    private String repositoryUrl = CliDefaults.IDEMPIERE_REPO_URL;
    private String dbType = CliDefaults.DB_TYPE;
    private String dbHost = CliDefaults.DB_HOST;
    private int dbPort = CliDefaults.DB_PORT;
    private String dbName = CliDefaults.DB_NAME;
    private String dbUser = CliDefaults.DB_USER;
    private String dbPass = CliDefaults.DB_PASSWORD;
    private String dbAdminPass = CliDefaults.DB_ADMIN_PASSWORD;
    private String httpHost = CliDefaults.HTTP_BIND_ADDRESS;
    private int httpPort = CliDefaults.HTTP_PORT;
    private int httpsPort = CliDefaults.HTTPS_PORT;
    private boolean useDocker;
    private String dockerContainerName = CliDefaults.DOCKER_CONTAINER_NAME;
    private String dockerPostgresVersion = CliDefaults.DOCKER_POSTGRES_VERSION;
    private String oracleDockerContainer = CliDefaults.DOCKER_ORACLE_CONTAINER;
    private String oracleDockerImage = CliDefaults.DOCKER_ORACLE_IMAGE;
    private String oracleDockerHome = CliDefaults.DOCKER_ORACLE_HOME;
    private boolean skipDb;
    private boolean skipWorkspace;
    private boolean includeRest;
    private boolean installCopilot;
    private boolean nonInteractive;
    private boolean continueOnError;

    public Path getSourceDir() {
        return sourceDir;
    }

    public void setSourceDir(Path sourceDir) {
        this.sourceDir = sourceDir;
    }

    public Path getEclipseDir() {
        return eclipseDir;
    }

    public void setEclipseDir(Path eclipseDir) {
        this.eclipseDir = eclipseDir;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public String getDbType() {
        return dbType;
    }

    public void setDbType(String dbType) {
        this.dbType = dbType;
    }

    public String getDbHost() {
        return dbHost;
    }

    public void setDbHost(String dbHost) {
        this.dbHost = dbHost;
    }

    public int getDbPort() {
        return dbPort;
    }

    public void setDbPort(int dbPort) {
        this.dbPort = dbPort;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getDbUser() {
        return dbUser;
    }

    public void setDbUser(String dbUser) {
        this.dbUser = dbUser;
    }

    public String getDbPass() {
        return dbPass;
    }

    public void setDbPass(String dbPass) {
        this.dbPass = dbPass;
    }

    public String getDbAdminPass() {
        return dbAdminPass;
    }

    public void setDbAdminPass(String dbAdminPass) {
        this.dbAdminPass = dbAdminPass;
    }

    public String getHttpHost() {
        return httpHost;
    }

    public void setHttpHost(String httpHost) {
        this.httpHost = httpHost;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    public int getHttpsPort() {
        return httpsPort;
    }

    public void setHttpsPort(int httpsPort) {
        this.httpsPort = httpsPort;
    }

    public boolean isUseDocker() {
        return useDocker;
    }

    public void setUseDocker(boolean useDocker) {
        this.useDocker = useDocker;
    }

    public String getDockerContainerName() {
        return dockerContainerName;
    }

    public void setDockerContainerName(String dockerContainerName) {
        this.dockerContainerName = dockerContainerName;
    }

    public String getDockerPostgresVersion() {
        return dockerPostgresVersion;
    }

    public void setDockerPostgresVersion(String dockerPostgresVersion) {
        this.dockerPostgresVersion = dockerPostgresVersion;
    }

    public String getOracleDockerContainer() {
        return oracleDockerContainer;
    }

    public void setOracleDockerContainer(String oracleDockerContainer) {
        this.oracleDockerContainer = oracleDockerContainer;
    }

    public String getOracleDockerImage() {
        return oracleDockerImage;
    }

    public void setOracleDockerImage(String oracleDockerImage) {
        this.oracleDockerImage = oracleDockerImage;
    }

    public String getOracleDockerHome() {
        return oracleDockerHome;
    }

    public void setOracleDockerHome(String oracleDockerHome) {
        this.oracleDockerHome = oracleDockerHome;
    }

    public boolean isSkipDb() {
        return skipDb;
    }

    public void setSkipDb(boolean skipDb) {
        this.skipDb = skipDb;
    }

    public boolean isSkipWorkspace() {
        return skipWorkspace;
    }

    public void setSkipWorkspace(boolean skipWorkspace) {
        this.skipWorkspace = skipWorkspace;
    }

    public boolean isIncludeRest() {
        return includeRest;
    }

    public void setIncludeRest(boolean includeRest) {
        this.includeRest = includeRest;
    }

    public boolean isInstallCopilot() {
        return installCopilot;
    }

    public void setInstallCopilot(boolean installCopilot) {
        this.installCopilot = installCopilot;
    }

    public boolean isNonInteractive() {
        return nonInteractive;
    }

    public void setNonInteractive(boolean nonInteractive) {
        this.nonInteractive = nonInteractive;
    }

    public boolean isContinueOnError() {
        return continueOnError;
    }

    public void setContinueOnError(boolean continueOnError) {
        this.continueOnError = continueOnError;
    }

    public String getDbConnectionString() {
        if ("oracle".equals(dbType)) {
            return "oracle://" + dbHost + ":" + dbPort + "/" + dbName;
        }
        return "postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
    }
}
