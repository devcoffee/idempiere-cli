package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.model.SetupConfig;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Manages database setup including Docker PostgreSQL and schema import.
 */
@ApplicationScoped
public class DatabaseManager {

    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    @Inject
    ProcessRunner processRunner;

    @Inject
    SessionLogger sessionLogger;

    public boolean setupDatabase(SetupConfig config) {
        if (config.isUseDocker()) {
            if ("postgresql".equals(config.getDbType())) {
                if (!createDockerPostgres(config)) {
                    return false;
                }
                // Wait for PostgreSQL to be ready
                waitForDatabase(config, 30);
            } else if ("oracle".equals(config.getDbType())) {
                if (!createDockerOracle(config)) {
                    return false;
                }
                // Wait for Oracle to be ready (takes longer than PostgreSQL)
                waitForOracleReady(config, 60);
            }
        }

        if (!validateConnection(config)) {
            System.err.println("  Could not connect to database. Please check your configuration.");
            if (config.isUseDocker()) {
                System.err.println("  Docker container may still be starting. Try again in a few seconds.");
            }
            return false;
        }

        sessionLogger.logInfo("Database connection verified: " + config.getDbConnectionString());
        System.out.println("  Database connection verified.");

        // Check if database exists and has data
        if (isDatabaseEmpty(config)) {
            sessionLogger.logInfo("Database is empty, importing seed data");
            return importSeedData(config);
        } else {
            sessionLogger.logInfo("Database already contains data, running migrations");
            System.out.println("  Database already contains data.");
            // Ensure config files exist before running migrations
            if (!ensureConfigFiles(config)) {
                return false;
            }
            return runMigrations(config);
        }
    }

    /**
     * Ensure configuration files exist (myEnvironment.sh, idempiereEnv.properties, etc.)
     * These are needed for migrations and other operations.
     */
    private boolean ensureConfigFiles(SetupConfig config) {
        Path sourceDir = config.getSourceDir();
        Path productDir = findProductDirectory(sourceDir);
        if (productDir == null) {
            System.err.println("  Product directory not found.");
            return false;
        }

        Path myEnvScript = productDir.resolve("utils/myEnvironment.sh");
        if (!Files.exists(myEnvScript)) {
            System.out.println("  Configuration files not found. Running console setup...");
            if (!runConsoleSetup(productDir, config)) {
                System.err.println("  Console setup failed.");
                return false;
            }
        }
        return true;
    }

    public boolean createDockerPostgres(SetupConfig config) {
        // First, check if Docker daemon is running
        if (!isDockerRunning()) {
            System.err.println("  Docker is not running.");
            System.err.println();
            System.err.println("  Troubleshooting:");
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("mac")) {
                System.err.println("    1. Start Docker Desktop: open -a Docker");
                System.err.println("    2. Wait for Docker to finish starting (icon stops animating)");
                System.err.println("    3. Run this command again");
            } else if (os.contains("linux")) {
                System.err.println("    1. Start Docker daemon: sudo systemctl start docker");
                System.err.println("    2. Ensure your user is in the docker group: sudo usermod -aG docker $USER");
                System.err.println("    3. Run this command again");
            } else {
                System.err.println("    1. Start Docker Desktop");
                System.err.println("    2. Wait for Docker to finish starting");
                System.err.println("    3. Run this command again");
            }
            System.err.println();
            return false;
        }

        System.out.println("  Creating Docker PostgreSQL container...");

        // Check if container already exists
        ProcessRunner.RunResult result = processRunner.run(
                "docker", "inspect", config.getDockerContainerName()
        );

        if (result.isSuccess()) {
            System.out.println("  Container '" + config.getDockerContainerName() + "' already exists.");

            // Check if it's running
            ProcessRunner.RunResult statusResult = processRunner.run(
                    "docker", "inspect", "-f", "{{.State.Running}}", config.getDockerContainerName()
            );

            if (statusResult.isSuccess() && statusResult.output().trim().equals("true")) {
                System.out.println("  Container is already running.");
                return true;
            }

            // Start existing container
            System.out.println("  Starting existing container...");
            int exitCode = processRunner.runLive(
                    "docker", "start", config.getDockerContainerName()
            );
            return exitCode == 0;
        }

        // Create new container
        int exitCode = processRunner.runLive(
                "docker", "run", "-d",
                "--name", config.getDockerContainerName(),
                "-p", config.getDbPort() + ":5432",
                "-e", "POSTGRES_PASSWORD=" + config.getDbAdminPass(),
                "postgres:" + config.getDockerPostgresVersion()
        );

        if (exitCode != 0) {
            System.err.println("  Failed to create Docker PostgreSQL container.");
            System.err.println("  Make sure Docker is installed and running.");
            return false;
        }

        System.out.println("  Container '" + config.getDockerContainerName() + "' created successfully.");
        return true;
    }

    public boolean createDockerOracle(SetupConfig config) {
        // First, check if Docker daemon is running
        if (!isDockerRunning()) {
            System.err.println("  Docker is not running.");
            System.err.println();
            System.err.println("  Troubleshooting:");
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("mac")) {
                System.err.println("    1. Start Docker Desktop: open -a Docker");
                System.err.println("    2. Wait for Docker to finish starting (icon stops animating)");
                System.err.println("    3. Run this command again");
            } else if (os.contains("linux")) {
                System.err.println("    1. Start Docker daemon: sudo systemctl start docker");
                System.err.println("    2. Ensure your user is in the docker group: sudo usermod -aG docker $USER");
                System.err.println("    3. Run this command again");
            } else {
                System.err.println("    1. Start Docker Desktop");
                System.err.println("    2. Wait for Docker to finish starting");
                System.err.println("    3. Run this command again");
            }
            System.err.println();
            return false;
        }

        String containerName = config.getOracleDockerContainer();
        System.out.println("  Creating Docker Oracle XE container...");

        // Check if container already exists
        ProcessRunner.RunResult result = processRunner.run(
                "docker", "inspect", containerName
        );

        if (result.isSuccess()) {
            System.out.println("  Container '" + containerName + "' already exists.");

            // Check if it's running
            ProcessRunner.RunResult statusResult = processRunner.run(
                    "docker", "inspect", "-f", "{{.State.Running}}", containerName
            );

            if (statusResult.isSuccess() && statusResult.output().trim().equals("true")) {
                System.out.println("  Container is already running.");
                return true;
            }

            // Start existing container
            System.out.println("  Starting existing container...");
            int exitCode = processRunner.runLive(
                    "docker", "start", containerName
            );
            return exitCode == 0;
        }

        // Create new container using gvenzl/oracle-xe image
        // This image supports APP_USER and APP_USER_PASSWORD environment variables
        int exitCode = processRunner.runLive(
                "docker", "run", "-d",
                "--name", containerName,
                "-p", config.getDbPort() + ":1521",
                "-e", "ORACLE_PASSWORD=" + config.getDbAdminPass(),
                "-e", "APP_USER=" + config.getDbUser(),
                "-e", "APP_USER_PASSWORD=" + config.getDbPass(),
                config.getOracleDockerImage()
        );

        if (exitCode != 0) {
            System.err.println("  Failed to create Docker Oracle XE container.");
            System.err.println("  Make sure Docker is installed and running.");
            System.err.println();
            System.err.println("  Note: Oracle XE image is ~1GB. First pull may take a few minutes.");
            return false;
        }

        System.out.println("  Container '" + containerName + "' created successfully.");
        System.out.println("  Oracle XE takes 1-2 minutes to initialize. Please wait...");
        return true;
    }

    private boolean waitForOracleReady(SetupConfig config, int maxRetries) {
        System.out.println("  Waiting for Oracle database to be ready...");
        for (int i = 0; i < maxRetries; i++) {
            // Use docker exec to check if Oracle is ready
            // The gvenzl/oracle-xe image has sqlplus available
            ProcessRunner.RunResult result = processRunner.run(
                    "docker", "exec", config.getOracleDockerContainer(),
                    "sqlplus", "-S", "-L",
                    "system/" + config.getDbAdminPass() + "@//localhost:1521/XEPDB1",
                    "<<EOF", "SELECT 1 FROM DUAL;", "EXIT;", "EOF"
            );

            if (result.isSuccess() && result.output().contains("1")) {
                System.out.println();
                System.out.println("  Oracle database is ready.");
                return true;
            }

            try {
                Thread.sleep(5000); // Oracle takes longer to start than PostgreSQL
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            System.out.print(".");
        }
        System.out.println();
        System.err.println("  Oracle database did not become ready in time.");
        return false;
    }

    public boolean validateConnection(SetupConfig config) {
        if ("postgresql".equals(config.getDbType())) {
            return validatePostgresConnection(config);
        } else if ("oracle".equals(config.getDbType())) {
            return validateOracleConnection(config);
        }
        System.err.println("  Unsupported database type: " + config.getDbType());
        return false;
    }

    private boolean validatePostgresConnection(SetupConfig config) {
        // When using Docker, use docker exec to run psql inside the container
        if (config.isUseDocker()) {
            ProcessRunner.RunResult result = processRunner.run(
                    "docker", "exec", config.getDockerContainerName(),
                    "psql", "-U", "postgres", "-c", "SELECT 1"
            );
            return result.isSuccess();
        }

        // Otherwise, use local psql client
        Map<String, String> env = Map.of("PGPASSWORD", config.getDbAdminPass());
        ProcessRunner.RunResult result = processRunner.runWithEnv(env,
                "psql", "-h", config.getDbHost(),
                "-p", String.valueOf(config.getDbPort()),
                "-U", "postgres",
                "-c", "SELECT 1"
        );
        return result.isSuccess();
    }

    private boolean validateOracleConnection(SetupConfig config) {
        if (config.isUseDocker()) {
            // Use docker exec to run sqlplus inside the container
            ProcessRunner.RunResult result = processRunner.run(
                    "docker", "exec", config.getOracleDockerContainer(),
                    "sqlplus", "-S", "-L",
                    config.getDbUser() + "/" + config.getDbPass() + "@//localhost:1521/XEPDB1",
                    "<<EOF", "SELECT 1 FROM DUAL;", "EXIT;", "EOF"
            );
            return result.isSuccess() && result.output().contains("1");
        }

        // Otherwise, use local sqlplus client
        String connStr = config.getDbUser() + "/" + config.getDbPass() + "@"
                + config.getDbHost() + ":" + config.getDbPort() + "/" + config.getDbName();
        ProcessRunner.RunResult result = processRunner.run("sqlplus", "-S", connStr, "<<EOF\nSELECT 1 FROM DUAL;\nEOF");
        return result.isSuccess();
    }

    public boolean importSeedData(SetupConfig config) {
        Path sourceDir = config.getSourceDir();

        // Check prerequisites before running
        if (!checkImportPrerequisites()) {
            return false;
        }

        // Following hengsin/idempiere-dev-setup approach:
        // Use the built product directory for database import
        Path productDir = findProductDirectory(sourceDir);
        if (productDir == null) {
            System.err.println("  Product directory not found.");
            System.err.println("  Make sure the iDempiere source has been built with Maven.");
            return false;
        }

        // Step 1: Run console-setup-alt.sh to create configuration files
        System.out.println("  Running console setup...");
        if (!runConsoleSetup(productDir, config)) {
            System.err.println("  Console setup failed.");
            return false;
        }

        // Step 2: Import the database using RUN_ImportIdempiere.sh
        // This script handles: extract seed, import DB, sync migrations, sign database.
        // We use live output so the user can see progress in real-time.
        Path utilsDir = productDir.resolve("utils");
        Path importScript = utilsDir.resolve("RUN_ImportIdempiere.sh");
        if (!Files.exists(importScript)) {
            System.err.println("  Import script not found: " + importScript);
            return false;
        }

        System.out.println("  Importing seed database (this may take several minutes)...");
        System.out.println();

        Map<String, String> importEnv = Map.of(
                "PGPASSWORD", config.getDbAdminPass(),
                "IDEMPIERE_HOME", toBashPath(productDir.toAbsolutePath().toString())
        );

        // Pipe newlines to skip "Press enter to continue..." prompt.
        // Use live output so user sees exactly what's happening.
        String importScriptPath = toBashPath(importScript.toAbsolutePath().toString());
        int exitCode = processRunner.runLiveInDirWithInput(
                utilsDir, importEnv,
                "\n",
                "bash", importScriptPath
        );

        System.out.println();

        if (exitCode != 0) {
            System.err.println("  Seed data import failed (exit code " + exitCode + ").");
            return false;
        }

        // Step 3: Copy generated config files back to source directory
        copyConfigToSource(productDir, sourceDir);

        System.out.println("  Seed data imported successfully.");
        return true;
    }

    private Path findProductDirectory(Path sourceDir) {
        // Product is built at: org.idempiere.p2/target/products/org.adempiere.server.product/<os>/<ws>/<arch>
        // Try OS-specific path first, then fall back to linux (which has the same scripts)
        Path productsBase = sourceDir.resolve("org.idempiere.p2/target/products/org.adempiere.server.product");

        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "");

        // Determine platform-specific path
        String platformPath;
        if (os.contains("mac")) {
            // macOS: macosx/cocoa/x86_64 or macosx/cocoa/aarch64
            String macArch = arch.contains("aarch64") || arch.contains("arm") ? "aarch64" : "x86_64";
            platformPath = "macosx/cocoa/" + macArch;
        } else if (os.contains("win")) {
            platformPath = "win32/win32/x86_64";
        } else {
            platformPath = "linux/gtk/x86_64";
        }

        Path productDir = productsBase.resolve(platformPath);
        if (Files.isDirectory(productDir)) {
            return productDir;
        }

        // Fallback to linux (scripts are platform-independent)
        productDir = productsBase.resolve("linux/gtk/x86_64");
        if (Files.isDirectory(productDir)) {
            return productDir;
        }

        return null;
    }

    private boolean runConsoleSetup(Path productDir, SetupConfig config) {
        Path consoleSetup = productDir.resolve("console-setup-alt.sh");
        if (!Files.exists(consoleSetup)) {
            System.err.println("  console-setup-alt.sh not found in product directory.");
            return false;
        }

        // Build input for console-setup-alt.sh (following hengsin's approach)
        // Format: JAVA_HOME, JAVA_OPTIONS, IDEMPIERE_HOME, KEY_STORE_PASS, KEY_STORE_ON, KEY_STORE_OU,
        //         KEY_STORE_O, KEY_STORE_L, KEY_STORE_S, KEY_STORE_C, IDEMPIERE_HOST, IDEMPIERE_PORT,
        //         IDEMPIERE_SSL_PORT, SSL (N), DB_TYPE (2=postgres), DB_HOST, DB_PORT, DB_NAME,
        //         DB_USER, DB_PASS, DB_SYSTEM, MAIL_HOST, MAIL_USER, MAIL_PASS, MAIL_ADMIN, SAVE (Y)
        String javaHome = toBashPath(System.getProperty("java.home"));
        String productDirPath = toBashPath(productDir.toAbsolutePath().toString());
        String dbTypeNum = "postgresql".equals(config.getDbType()) ? "2" : "1";

        // Use higher ports by default to avoid conflicts with common services
        // 8080/8443 are often in use by other applications
        int httpPort = findAvailablePort(8880, 9080);
        int httpsPort = findAvailablePort(8843, 9443);

        StringBuilder input = new StringBuilder();
        input.append(javaHome).append("\n");           // JAVA_HOME
        input.append("-Xmx2048M").append("\n");        // JAVA_OPTIONS
        input.append(productDirPath).append("\n");     // IDEMPIERE_HOME
        input.append("myPassword").append("\n");       // KEY_STORE_PASS
        input.append("idempiere.org").append("\n");    // KEY_STORE_ON
        input.append("iDempiere").append("\n");        // KEY_STORE_OU
        input.append("iDempiere").append("\n");        // KEY_STORE_O
        input.append("myTown").append("\n");           // KEY_STORE_L
        input.append("CA").append("\n");               // KEY_STORE_S
        input.append("US").append("\n");               // KEY_STORE_C
        input.append("0.0.0.0").append("\n");          // IDEMPIERE_HOST
        input.append(httpPort).append("\n");           // IDEMPIERE_PORT
        input.append(httpsPort).append("\n");          // IDEMPIERE_SSL_PORT
        input.append("N").append("\n");                // SSL
        input.append(dbTypeNum).append("\n");          // DB_TYPE (2=postgresql)
        input.append(config.getDbHost()).append("\n"); // DB_HOST
        input.append(config.getDbPort()).append("\n"); // DB_PORT
        input.append(config.getDbName()).append("\n"); // DB_NAME
        input.append(config.getDbUser()).append("\n"); // DB_USER
        input.append(config.getDbPass()).append("\n"); // DB_PASS
        input.append(config.getDbAdminPass()).append("\n"); // DB_SYSTEM
        input.append("0.0.0.0").append("\n");          // MAIL_HOST
        input.append("info").append("\n");             // MAIL_USER
        input.append("info").append("\n");             // MAIL_PASS
        input.append("info@idempiere").append("\n");   // MAIL_ADMIN
        input.append("Y").append("\n");                // SAVE

        Map<String, String> env = Map.of(
                "CONSOLE_SETUP_BATCH_MODE", "Y",
                "PGPASSWORD", config.getDbAdminPass()
        );

        // Pipe input via stdin directly (avoids printf escaping issues with Windows paths)
        String scriptPath = toBashPath(consoleSetup.toAbsolutePath().toString());
        ProcessRunner.RunResult result = processRunner.runQuietInDirWithEnvAndInput(
                productDir, env, input.toString(),
                "bash", scriptPath
        );

        if (!result.isSuccess()) {
            System.err.println("  Console setup output:");
            // Show last 20 lines
            String[] lines = result.output().split("\n");
            int start = Math.max(0, lines.length - 20);
            for (int i = start; i < lines.length; i++) {
                System.err.println("    " + lines[i]);
            }
            return false;
        }

        // On Windows, console-setup-alt.sh (Java) may write files with \r\n line endings.
        // When bash sources myEnvironment.sh, the \r gets included in variable values
        // (e.g. PGPASSWORD becomes "postgres\r" instead of "postgres"), causing auth failures.
        if (IS_WINDOWS) {
            fixLineEndings(productDir.resolve("utils/myEnvironment.sh"));
            fixLineEndings(productDir.resolve("idempiereEnv.properties"));
        }

        return true;
    }

    private void fixLineEndings(Path file) {
        if (!Files.exists(file)) return;
        try {
            String content = Files.readString(file);
            if (content.contains("\r")) {
                Files.writeString(file, content.replace("\r\n", "\n").replace("\r", "\n"));
            }
        } catch (IOException e) {
            // Non-fatal - log warning and continue
            System.err.println("  Warning: Could not fix line endings in " + file.getFileName() + ": " + e.getMessage());
        }
    }

    private void copyConfigToSource(Path productDir, Path sourceDir) {
        try {
            // Copy configuration files from product to source (for Eclipse development)
            Path[] filesToCopy = {
                    productDir.resolve("idempiere.properties"),
                    productDir.resolve("idempiereEnv.properties"),
                    productDir.resolve("hazelcast.xml"),
                    productDir.resolve(".idpass")
            };

            for (Path file : filesToCopy) {
                if (Files.exists(file)) {
                    Files.copy(file, sourceDir.resolve(file.getFileName()),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }

            // Copy jettyhome directory
            Path jettyHome = productDir.resolve("jettyhome");
            if (Files.isDirectory(jettyHome)) {
                Path targetJetty = sourceDir.resolve("jettyhome");
                copyDirectory(jettyHome, targetJetty);
            }
        } catch (IOException e) {
            System.err.println("  Warning: Could not copy some config files: " + e.getMessage());
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(sourcePath -> {
            try {
                Path targetPath = target.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.copy(sourcePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public boolean runMigrations(SetupConfig config) {
        Path sourceDir = config.getSourceDir();

        // Following hengsin's approach: use the product directory for migrations
        Path productDir = findProductDirectory(sourceDir);
        if (productDir == null) {
            System.out.println("  Product directory not found. Skipping migrations.");
            return true;
        }

        Path utilsDir = productDir.resolve("utils");
        Path syncScript = utilsDir.resolve("RUN_SyncDB.sh");
        if (!Files.exists(syncScript)) {
            System.out.println("  Migration script not found. Skipping migrations.");
            return true;
        }

        Map<String, String> env = Map.of(
                "PGPASSWORD", config.getDbAdminPass(),
                "IDEMPIERE_HOME", toBashPath(productDir.toAbsolutePath().toString())
        );

        // Run with live output so user can see progress
        System.out.println("  Running database migrations...");
        System.out.println();
        String syncScriptPath = toBashPath(syncScript.toAbsolutePath().toString());
        int syncExitCode = processRunner.runLiveInDir(utilsDir, env,
                "bash", syncScriptPath
        );
        System.out.println();

        if (syncExitCode != 0) {
            System.err.println("  Migration failed (exit code " + syncExitCode + ").");
            return false;
        }

        // Sign database after migration (like hengsin does)
        Path signScript = productDir.resolve("sign-database-build-alt.sh");
        if (Files.exists(signScript)) {
            System.out.println("  Signing database...");
            System.out.println();
            String signScriptPath = toBashPath(signScript.toAbsolutePath().toString());
            int signExitCode = processRunner.runLiveInDir(productDir, env,
                    "bash", signScriptPath
            );
            System.out.println();

            if (signExitCode != 0) {
                System.err.println("  Database signing failed (exit code " + signExitCode + ").");
                return false;
            }
        }

        return true;
    }

    private boolean isDatabaseEmpty(SetupConfig config) {
        if ("postgresql".equals(config.getDbType())) {
            ProcessRunner.RunResult result;

            if (config.isUseDocker()) {
                // Use docker exec to run psql inside the container
                result = processRunner.run(
                        "docker", "exec", config.getDockerContainerName(),
                        "psql", "-U", config.getDbUser(),
                        "-d", config.getDbName(),
                        "-tAc", "SELECT COUNT(*) FROM ad_client"
                );
            } else {
                // Use local psql client
                Map<String, String> env = Map.of("PGPASSWORD", config.getDbPass());
                result = processRunner.runWithEnv(env,
                        "psql", "-h", config.getDbHost(),
                        "-p", String.valueOf(config.getDbPort()),
                        "-U", config.getDbUser(),
                        "-d", config.getDbName(),
                        "-tAc", "SELECT COUNT(*) FROM ad_client"
                );
            }
            // If query fails (table doesn't exist), database is empty
            return !result.isSuccess();
        }
        return true;
    }

    private void waitForDatabase(SetupConfig config, int maxRetries) {
        System.out.println("  Waiting for database to be ready...");
        for (int i = 0; i < maxRetries; i++) {
            if (validateConnection(config)) {
                return;
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            System.out.print(".");
        }
        System.out.println();
    }

    public boolean isDockerRunning() {
        ProcessRunner.RunResult result = processRunner.run("docker", "info");
        return result.isSuccess();
    }

    private int findAvailablePort(int preferred, int fallback) {
        if (isPortAvailable(preferred)) {
            return preferred;
        }
        if (isPortAvailable(fallback)) {
            return fallback;
        }
        // Try to find any available port in range
        for (int port = fallback; port < fallback + 100; port++) {
            if (isPortAvailable(port)) {
                return port;
            }
        }
        return fallback; // Return fallback even if not available, let the setup fail with clear message
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean checkImportPrerequisites() {
        String os = System.getProperty("os.name", "").toLowerCase();
        java.util.List<String> missing = new java.util.ArrayList<>();

        // On macOS, RUN_ImportIdempiereDev.sh requires greadlink from coreutils
        if (os.contains("mac") && !processRunner.isAvailable("greadlink")) {
            missing.add("greadlink (brew install coreutils)");
        }

        if (!processRunner.isAvailable("psql")) {
            missing.add("psql (PostgreSQL client)");
        }

        if (!processRunner.isAvailable("jar")) {
            missing.add("jar (part of JDK)");
        }

        if (!missing.isEmpty()) {
            System.err.println("  Missing prerequisites: " + String.join(", ", missing));
            System.err.println();
            System.err.println("  Run 'idempiere-cli doctor --fix' to install missing dependencies.");
            return false;
        }

        return true;
    }

    /**
     * Convert a Windows path to forward slashes for use in bash commands.
     * On non-Windows systems, returns the path unchanged.
     */
    private String toBashPath(String path) {
        if (IS_WINDOWS) {
            return path.replace("\\", "/");
        }
        return path;
    }
}
