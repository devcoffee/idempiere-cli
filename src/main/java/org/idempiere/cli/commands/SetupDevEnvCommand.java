package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.model.SetupConfig;
import org.idempiere.cli.service.SetupDevEnvService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "setup-dev-env",
        description = "Bootstrap a complete local iDempiere development environment",
        mixinStandardHelpOptions = true
)
public class SetupDevEnvCommand implements Callable<Integer> {

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

    @Option(names = "--oracle-docker-container", description = "Oracle Docker container name (default: idempiere-oracle)",
            defaultValue = "idempiere-oracle")
    String oracleDockerContainer;

    @Option(names = "--oracle-docker-image", description = "Oracle Docker image (default: gvenzl/oracle-xe:21-slim)",
            defaultValue = "gvenzl/oracle-xe:21-slim")
    String oracleDockerImage;

    @Option(names = "--oracle-docker-home", description = "Oracle Docker home directory (default: /opt/oracle)",
            defaultValue = "/opt/oracle")
    String oracleDockerHome;

    @Option(names = "--non-interactive", description = "Run without prompting for confirmation")
    boolean nonInteractive;

    @Option(names = "--continue-on-error", description = "Continue setup even if a step fails")
    boolean continueOnError;

    @Inject
    SetupDevEnvService setupDevEnvService;

    @Override
    public Integer call() {
        // Validate parameter combinations
        if (!validateParameters()) {
            return 1;
        }

        // Check for headless environment BEFORE doing any work
        if (isHeadlessEnvironment()) {
            System.err.println("Error: setup-dev-env requires a graphical environment (display).");
            System.err.println();
            System.err.println("This command installs Eclipse plugins and configures the workspace,");
            System.err.println("which requires a display to run the Eclipse P2 director.");
            System.err.println();
            System.err.println("Options:");
            System.err.println("  - Run this command on a machine with a display (macOS, Linux desktop, Windows)");
            System.err.println("  - Use a VM with GUI (UTM, VirtualBox, Parallels)");
            System.err.println("  - On Linux server: use X11 forwarding (ssh -X) or VNC");
            System.err.println();
            System.err.println("For testing CLI commands that don't require Eclipse, use Docker:");
            System.err.println("  ./test-cli.sh");
            return 1;
        }

        SetupConfig config = new SetupConfig();

        // Normalize paths to absolute to avoid issues when running from different directories
        Path resolvedSourceDir = (sourceDir != null ? sourceDir : Path.of("idempiere")).toAbsolutePath().normalize();
        Path resolvedEclipseDir = (eclipseDir != null ? eclipseDir : Path.of("eclipse")).toAbsolutePath().normalize();

        config.setSourceDir(resolvedSourceDir);
        config.setEclipseDir(resolvedEclipseDir);
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
        config.setOracleDockerContainer(oracleDockerContainer);
        config.setOracleDockerImage(oracleDockerImage);
        config.setOracleDockerHome(oracleDockerHome);
        config.setSkipDb(skipDb);
        config.setSkipWorkspace(skipWorkspace);
        config.setIncludeRest(includeRest);
        config.setInstallCopilot(installCopilot);
        config.setNonInteractive(nonInteractive);
        config.setContinueOnError(continueOnError);

        setupDevEnvService.setup(config);
        return 0;
    }

    /**
     * Validate parameter combinations for consistency.
     * Returns true if validation passes, false if there are errors.
     */
    private boolean validateParameters() {
        boolean hasErrors = false;

        // Warning: --skip-db makes database options redundant
        if (skipDb) {
            if (withDocker) {
                System.err.println("Warning: --with-docker is ignored when --skip-db is set.");
            }
            if (!"localhost".equals(dbHost)) {
                System.err.println("Warning: --db-host is ignored when --skip-db is set.");
            }
            if (dbPort != 5432) {
                System.err.println("Warning: --db-port is ignored when --skip-db is set.");
            }
            if (!"idempiere".equals(dbName)) {
                System.err.println("Warning: --db-name is ignored when --skip-db is set.");
            }
            if (!"adempiere".equals(dbUser)) {
                System.err.println("Warning: --db-user is ignored when --skip-db is set.");
            }
            if (!"adempiere".equals(dbPass)) {
                System.err.println("Warning: --db-pass is ignored when --skip-db is set.");
            }
            if (!"postgres".equals(dbAdminPass)) {
                System.err.println("Warning: --db-admin-pass is ignored when --skip-db is set.");
            }
            if (!"idempiere-postgres".equals(dockerContainerName)) {
                System.err.println("Warning: --docker-postgres-name is ignored when --skip-db is set.");
            }
            if (!"15.3".equals(dockerPostgresVersion)) {
                System.err.println("Warning: --docker-postgres-version is ignored when --skip-db is set.");
            }
        }

        // Warning: --skip-workspace makes Eclipse options redundant
        if (skipWorkspace) {
            if (eclipseDir != null) {
                System.err.println("Warning: --eclipse-dir is ignored when --skip-workspace is set.");
            }
            if (installCopilot) {
                System.err.println("Warning: --install-copilot is ignored when --skip-workspace is set.");
            }
        }

        // Warning: Docker options without --with-docker
        if (!withDocker && !skipDb) {
            if (!"idempiere-postgres".equals(dockerContainerName)) {
                System.err.println("Warning: --docker-postgres-name is ignored without --with-docker.");
            }
            if (!"15.3".equals(dockerPostgresVersion)) {
                System.err.println("Warning: --docker-postgres-version is ignored without --with-docker.");
            }
            if (!"idempiere-oracle".equals(oracleDockerContainer)) {
                System.err.println("Warning: --oracle-docker-container is ignored without --with-docker.");
            }
            if (!"gvenzl/oracle-xe:21-slim".equals(oracleDockerImage)) {
                System.err.println("Warning: --oracle-docker-image is ignored without --with-docker.");
            }
            if (!"/opt/oracle".equals(oracleDockerHome)) {
                System.err.println("Warning: --oracle-docker-home is ignored without --with-docker.");
            }
        }

        // Warning: --with-docker with non-localhost db-host
        if (withDocker && !"localhost".equals(dbHost)) {
            System.err.println("Warning: --db-host is ignored when --with-docker is set (Docker uses localhost).");
        }

        // Warning: PostgreSQL Docker options used with Oracle
        if (withDocker && "oracle".equalsIgnoreCase(dbType)) {
            if (!"idempiere-postgres".equals(dockerContainerName)) {
                System.err.println("Warning: --docker-postgres-name is ignored with --db=oracle.");
            }
            if (!"15.3".equals(dockerPostgresVersion)) {
                System.err.println("Warning: --docker-postgres-version is ignored with --db=oracle.");
            }
        }

        // Warning: Oracle Docker options used with PostgreSQL
        if (withDocker && "postgresql".equalsIgnoreCase(dbType)) {
            if (!"idempiere-oracle".equals(oracleDockerContainer)) {
                System.err.println("Warning: --oracle-docker-container is ignored with --db=postgresql.");
            }
            if (!"gvenzl/oracle-xe:21-slim".equals(oracleDockerImage)) {
                System.err.println("Warning: --oracle-docker-image is ignored with --db=postgresql.");
            }
            if (!"/opt/oracle".equals(oracleDockerHome)) {
                System.err.println("Warning: --oracle-docker-home is ignored with --db=postgresql.");
            }
        }

        return !hasErrors;
    }

    /**
     * Check if running in a headless environment (no display available).
     * Works across Linux, macOS, and Windows.
     */
    private boolean isHeadlessEnvironment() {
        String os = System.getProperty("os.name", "").toLowerCase();

        // Windows typically always has a display
        if (os.contains("win")) {
            return false;
        }

        // macOS: check if running via SSH (no local display)
        if (os.contains("mac")) {
            String sshConnection = System.getenv("SSH_CONNECTION");
            String sshClient = System.getenv("SSH_CLIENT");
            // If SSH variables are set and no DISPLAY, we're headless
            if ((sshConnection != null && !sshConnection.isEmpty()) ||
                (sshClient != null && !sshClient.isEmpty())) {
                String display = System.getenv("DISPLAY");
                return display == null || display.isEmpty();
            }
            return false; // Local macOS session has display
        }

        // Linux: check for DISPLAY or WAYLAND_DISPLAY
        String display = System.getenv("DISPLAY");
        if (display != null && !display.isEmpty()) {
            return false;
        }
        String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
        if (waylandDisplay != null && !waylandDisplay.isEmpty()) {
            return false;
        }

        return true; // No display found
    }
}
