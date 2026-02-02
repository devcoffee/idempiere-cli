package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.model.SetupConfig;
import org.idempiere.cli.service.SetupDevEnvService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.nio.file.Path;

@Command(
        name = "setup-dev-env",
        description = "Bootstrap a complete local iDempiere development environment",
        mixinStandardHelpOptions = true
)
public class SetupDevEnvCommand implements Runnable {

    @Option(names = "--ide", description = "Target IDE (default: eclipse)", defaultValue = "eclipse")
    String ide;

    @Option(names = "--db", description = "Database type: postgresql or oracle (default: postgresql)", defaultValue = "postgresql")
    String dbType;

    @Option(names = "--with-docker", description = "Create database using Docker")
    boolean withDocker;

    @Option(names = "--branch", description = "iDempiere branch to checkout (default: master)", defaultValue = "master")
    String branch;

    @Option(names = "--repository-url", description = "iDempiere Git repository URL",
            defaultValue = "https://github.com/idempiere/idempiere.git")
    String repositoryUrl;

    @Option(names = "--source-dir", description = "Path to iDempiere source directory")
    Path sourceDir;

    @Option(names = "--eclipse-dir", description = "Path to Eclipse installation directory")
    Path eclipseDir;

    @Option(names = "--db-host", description = "Database host (default: localhost)", defaultValue = "localhost")
    String dbHost;

    @Option(names = "--db-port", description = "Database port (default: 5432)", defaultValue = "5432")
    int dbPort;

    @Option(names = "--db-name", description = "Database name (default: idempiere)", defaultValue = "idempiere")
    String dbName;

    @Option(names = "--db-user", description = "Database user (default: adempiere)", defaultValue = "adempiere")
    String dbUser;

    @Option(names = "--db-pass", description = "Database password (default: adempiere)", defaultValue = "adempiere")
    String dbPass;

    @Option(names = "--db-admin-pass", description = "Database admin password (default: postgres)", defaultValue = "postgres")
    String dbAdminPass;

    @Option(names = "--http-port", description = "HTTP port (default: 8080)", defaultValue = "8080")
    int httpPort;

    @Option(names = "--https-port", description = "HTTPS port (default: 8443)", defaultValue = "8443")
    int httpsPort;

    @Option(names = "--skip-db", description = "Skip database setup")
    boolean skipDb;

    @Option(names = "--skip-workspace", description = "Skip Eclipse workspace setup")
    boolean skipWorkspace;

    @Option(names = "--include-rest", description = "Also clone idempiere-rest repository")
    boolean includeRest;

    @Option(names = "--install-copilot", description = "Install GitHub Copilot plugin in Eclipse")
    boolean installCopilot;

    @Option(names = "--docker-postgres-name", description = "Docker container name (default: idempiere-postgres)",
            defaultValue = "idempiere-postgres")
    String dockerContainerName;

    @Option(names = "--docker-postgres-version", description = "Docker PostgreSQL version (default: 15.3)",
            defaultValue = "15.3")
    String dockerPostgresVersion;

    @Option(names = "--non-interactive", description = "Run without prompting for confirmation")
    boolean nonInteractive;

    @Inject
    SetupDevEnvService setupDevEnvService;

    @Override
    public void run() {
        SetupConfig config = new SetupConfig();

        config.setSourceDir(sourceDir != null ? sourceDir : Path.of("idempiere"));
        config.setEclipseDir(eclipseDir != null ? eclipseDir : Path.of("eclipse"));
        config.setBranch(branch);
        config.setRepositoryUrl(repositoryUrl);
        config.setDbType(dbType);
        config.setDbHost(dbHost);
        config.setDbPort(dbPort);
        config.setDbName(dbName);
        config.setDbUser(dbUser);
        config.setDbPass(dbPass);
        config.setDbAdminPass(dbAdminPass);
        config.setHttpPort(httpPort);
        config.setHttpsPort(httpsPort);
        config.setUseDocker(withDocker);
        config.setDockerContainerName(dockerContainerName);
        config.setDockerPostgresVersion(dockerPostgresVersion);
        config.setSkipDb(skipDb);
        config.setSkipWorkspace(skipWorkspace);
        config.setIncludeRest(includeRest);
        config.setInstallCopilot(installCopilot);
        config.setNonInteractive(nonInteractive);

        setupDevEnvService.setup(config);
    }
}
