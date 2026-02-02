package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.model.SetupConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@ApplicationScoped
public class DatabaseManager {

    @Inject
    ProcessRunner processRunner;

    public boolean setupDatabase(SetupConfig config) {
        if (config.isUseDocker() && "postgresql".equals(config.getDbType())) {
            if (!createDockerPostgres(config)) {
                return false;
            }
            // Wait for PostgreSQL to be ready
            waitForDatabase(config, 30);
        }

        if (!validateConnection(config)) {
            System.err.println("  Could not connect to database. Please check your configuration.");
            if (config.isUseDocker()) {
                System.err.println("  Docker container may still be starting. Try again in a few seconds.");
            }
            return false;
        }

        System.out.println("  Database connection verified.");

        // Check if database exists and has data
        if (isDatabaseEmpty(config)) {
            return importSeedData(config);
        } else {
            System.out.println("  Database already contains data.");
            return runMigrations(config);
        }
    }

    public boolean createDockerPostgres(SetupConfig config) {
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
        String connStr = config.getDbUser() + "/" + config.getDbPass() + "@"
                + config.getDbHost() + ":" + config.getDbPort() + "/" + config.getDbName();
        ProcessRunner.RunResult result = processRunner.run("sqlplus", "-S", connStr, "<<EOF\nSELECT 1 FROM DUAL;\nEOF");
        return result.isSuccess();
    }

    public boolean importSeedData(SetupConfig config) {
        Path sourceDir = config.getSourceDir();
        Path importScript = findScript(sourceDir, "RUN_ImportIdempiere");

        if (importScript == null) {
            System.err.println("  Import script not found in source directory.");
            System.err.println("  Make sure the iDempiere source has been built first.");
            return false;
        }

        System.out.println("  Importing seed database...");
        System.out.println("  This may take several minutes.");

        Map<String, String> env = Map.of(
                "PGPASSWORD", config.getDbAdminPass(),
                "IDEMPIERE_HOME", sourceDir.toAbsolutePath().toString()
        );

        int exitCode = processRunner.runLiveInDir(
                importScript.getParent(), env,
                importScript.toString(),
                config.getDbHost(),
                String.valueOf(config.getDbPort()),
                config.getDbName(),
                config.getDbUser(),
                config.getDbPass()
        );

        if (exitCode != 0) {
            System.err.println("  Seed data import failed.");
            return false;
        }

        System.out.println("  Seed data imported successfully.");
        return true;
    }

    public boolean runMigrations(SetupConfig config) {
        Path sourceDir = config.getSourceDir();
        Path syncScript = findScript(sourceDir, "RUN_SyncDB");

        if (syncScript == null) {
            System.out.println("  Migration script not found. Skipping migrations.");
            return true;
        }

        System.out.println("  Running database migrations...");

        Map<String, String> env = Map.of(
                "PGPASSWORD", config.getDbAdminPass(),
                "IDEMPIERE_HOME", sourceDir.toAbsolutePath().toString()
        );

        int exitCode = processRunner.runLiveInDir(
                syncScript.getParent(), env,
                syncScript.toString()
        );

        if (exitCode != 0) {
            System.err.println("  Warning: Migration encountered errors.");
            return false;
        }

        System.out.println("  Migrations completed successfully.");
        return true;
    }

    private boolean isDatabaseEmpty(SetupConfig config) {
        if ("postgresql".equals(config.getDbType())) {
            Map<String, String> env = Map.of("PGPASSWORD", config.getDbPass());
            ProcessRunner.RunResult result = processRunner.runWithEnv(env,
                    "psql", "-h", config.getDbHost(),
                    "-p", String.valueOf(config.getDbPort()),
                    "-U", config.getDbUser(),
                    "-d", config.getDbName(),
                    "-tAc", "SELECT COUNT(*) FROM ad_client"
            );
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

    private Path findScript(Path sourceDir, String scriptName) {
        // Search common locations
        String[] searchPaths = {
                "utils",
                "org.adempiere.server-feature/utils.unix",
                "org.adempiere.server-feature/utils.windows"
        };

        String os = System.getProperty("os.name", "").toLowerCase();
        String ext = os.contains("win") ? ".bat" : ".sh";

        for (String searchPath : searchPaths) {
            Path script = sourceDir.resolve(searchPath).resolve(scriptName + ext);
            if (Files.exists(script)) {
                return script;
            }
        }

        return null;
    }
}
