package org.idempiere.cli.model;

import java.nio.file.Path;

public class SetupConfig {

    private Path sourceDir;
    private Path eclipseDir;
    private String branch = "master";
    private String repositoryUrl = "https://github.com/idempiere/idempiere.git";
    private String dbType = "postgresql";
    private String dbHost = "localhost";
    private int dbPort = 5432;
    private String dbName = "idempiere";
    private String dbUser = "adempiere";
    private String dbPass = "adempiere";
    private String dbAdminPass = "postgres";
    private String httpHost = "0.0.0.0";
    private int httpPort = 8080;
    private int httpsPort = 8443;
    private boolean useDocker;
    private String dockerContainerName = "idempiere-postgres";
    private String dockerPostgresVersion = "15.3";
    private boolean skipDb;
    private boolean skipWorkspace;
    private boolean includeRest;
    private boolean installCopilot;
    private boolean nonInteractive;

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

    public String getDbConnectionString() {
        if ("oracle".equals(dbType)) {
            return "oracle://" + dbHost + ":" + dbPort + "/" + dbName;
        }
        return "postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
    }
}
