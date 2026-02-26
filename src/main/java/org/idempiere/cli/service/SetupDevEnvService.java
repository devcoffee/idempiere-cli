package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.model.SetupConfig;
import org.idempiere.cli.util.CliDefaults;
import org.idempiere.cli.util.CliOutput;
import org.idempiere.cli.util.ExitCodes;

import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orchestrates complete development environment setup (source, database, Eclipse).
 */
@ApplicationScoped
public class SetupDevEnvService {
    private static final Pattern JAVA_VERSION_PATTERN = Pattern.compile("version \"(\\d+)");
    private static final Pattern RELEASE_BRANCH_PATTERN = Pattern.compile("release-(\\d+)");

    @Inject
    SourceManager sourceManager;

    @Inject
    EclipseManager eclipseManager;

    @Inject
    DatabaseManager databaseManager;

    @Inject
    ProcessRunner processRunner;

    @Inject
    SessionLogger sessionLogger;

    public int setup(SetupConfig config) {
        // Start session logging
        sessionLogger.startSession(buildCommandLine(config));

        // Clean old logs
        sessionLogger.cleanOldLogs(CliDefaults.SESSION_LOGS_KEEP_COUNT);
        System.out.println();
        System.out.println("iDempiere Development Environment Setup");
        System.out.println("========================================");
        System.out.println();

        printConfiguration(config);

        if (!config.isNonInteractive()) {
            System.out.print("Proceed with setup? [Y/n] ");
            @SuppressWarnings("resource")
            Scanner scanner = new Scanner(System.in);
                String answer = scanner.nextLine().trim();
                if (answer.equalsIgnoreCase("n") || answer.equalsIgnoreCase("no")) {
                    sessionLogger.logInfo("Setup cancelled by user");
                    sessionLogger.endSession(false);
                    System.out.println("Setup cancelled.");
                    return ExitCodes.SUCCESS;
                }
            }

        System.out.println();

        // Pre-flight checks: enforce branch-specific Java compatibility before long operations
        Integer requiredJava = resolveRequiredJavaMajor(config.getBranch());
        Integer currentJava = detectJavaMajor();
        if (requiredJava != null) {
            if (currentJava == null) {
                sessionLogger.logError("Could not determine Java version for compatibility preflight.");
                System.err.println("Error: Could not determine Java version.");
                System.err.println("Required: Java " + requiredJava + "+ for branch '" + config.getBranch() + "'.");
                System.err.println("Fix: install/select a compatible JDK and retry.");
                System.err.println("Try: idempiere-cli doctor --fix");
                sessionLogger.endSession(false);
                return ExitCodes.STATE_ERROR;
            }
            if (currentJava < requiredJava) {
                sessionLogger.logError("Java compatibility preflight failed (required " + requiredJava
                        + "+, detected " + currentJava + ", branch " + config.getBranch() + ").");
                System.err.println("Error: Java " + requiredJava + "+ required for branch '" + config.getBranch()
                        + "'; detected Java " + currentJava + ".");
                if (requiredJava >= 21) {
                    System.err.println("Fix: iDempiere 13+ requires Java 21.");
                    System.err.println("Alternative: use --branch=release-12 with Java 17.");
                } else {
                    System.err.println("Fix: use Java 17+ for this branch.");
                }
                System.err.println("Try: idempiere-cli doctor --fix");
                sessionLogger.endSession(false);
                return ExitCodes.STATE_ERROR;
            }
        }

        // Pre-flight checks: verify database is reachable before starting long operations
        if (!config.isSkipDb()) {
            if (config.isUseDocker()) {
                DatabaseManager.DockerStatus dockerStatus = databaseManager.getDockerStatus();
                if (dockerStatus != DatabaseManager.DockerStatus.RUNNING) {
                    if (dockerStatus == DatabaseManager.DockerStatus.PERMISSION_DENIED) {
                        sessionLogger.logError("Docker permission denied (user not in docker group). Aborting.");
                    } else {
                        sessionLogger.logError("Docker is not running. Aborting.");
                    }
                    databaseManager.printDockerError(dockerStatus);
                    sessionLogger.endSession(false);
                    return ExitCodes.STATE_ERROR;
                }
            } else {
                // Without Docker, verify PostgreSQL/Oracle is reachable now
                // to avoid wasting time on clone/build only to fail at the DB step
                if (!databaseManager.validateConnection(config)) {
                    String checkUser = "oracle".equals(config.getDbType()) ? config.getDbUser() : "postgres";
                    System.err.println("Error: Cannot connect to database at "
                            + config.getDbHost() + ":" + config.getDbPort()
                            + " (user: " + checkUser + ").");
                    System.err.println();
                    System.err.println("Options:");
                    System.err.println("  1. Use Docker (recommended):");
                    System.err.println("       idempiere-cli setup-dev-env --with-docker");
                    String os = System.getProperty("os.name", "").toLowerCase();
                    if (os.contains("linux")) {
                        System.err.println("  2. Install PostgreSQL locally:");
                        System.err.println("       sudo apt install postgresql");
                        System.err.println("       echo \"alter user postgres password 'YOUR_PASSWORD'\" \\");
                        System.err.println("         | sudo su postgres -c \"psql -U postgres\"");
                        System.err.println("       # Edit pg_hba.conf: change 'peer' to 'scram-sha-256'");
                        System.err.println("       sudo service postgresql reload");
                        System.err.println("       See: https://wiki.idempiere.org/en/Install_Prerequisites");
                    } else if (os.contains("mac")) {
                        System.err.println("  2. Install PostgreSQL locally:");
                        System.err.println("       brew install postgresql@16 && brew services start postgresql@16");
                        System.err.println("       See: https://wiki.idempiere.org/en/Install_Prerequisites");
                    } else {
                        System.err.println("  2. Verify PostgreSQL is running and credentials are correct");
                    }
                    System.err.println("  3. Check host/port and credentials if PostgreSQL is already running");
                    abortSetup("Database not reachable. Aborting.", null);
                    return ExitCodes.STATE_ERROR;
                }
            }
        }

        int totalSteps = calculateSteps(config);
        int currentStep = 0;
        boolean hadErrors = false;

        // Step 1: Clone/update source
        currentStep++;
        printStep(currentStep, totalSteps, "Setting up iDempiere source code");
        boolean sourceOk = sourceManager.cloneOrUpdate(config);
        printStepResult(sourceOk, "Source code");
        System.out.println();

        // Source is always required - cannot continue without it
        if (!sourceOk) {
            abortSetup("Cannot continue without source code. Aborting.",
                    "Cannot continue without source code. Aborting.");
            return ExitCodes.IO_ERROR;
        }

        // Step 2: Build source
        if (!config.isSkipBuild()) {
            currentStep++;
            printStep(currentStep, totalSteps, "Building iDempiere with Maven");
            boolean buildOk = sourceManager.buildSource(config);
            printStepResult(buildOk, "Maven build");
            System.out.println();

            if (!buildOk) {
                hadErrors = true;
                if (abortIfStepFailed(buildOk, config,
                        "Build failed. Aborting.",
                        "Build failed. Aborting. Use --continue-on-error to proceed anyway.")) {
                    return ExitCodes.IO_ERROR;
                }
            }

            // Download Jython (part of build)
            currentStep++;
            printStep(currentStep, totalSteps, "Downloading Jython");
            boolean jythonOk = sourceManager.downloadJython(config);
            printStepResult(jythonOk, "Jython download");
            System.out.println();

            if (!jythonOk) {
                hadErrors = true;
                if (abortIfStepFailed(jythonOk, config,
                        "Jython download failed. Aborting.",
                        "Jython download failed. Aborting. Use --continue-on-error to proceed anyway.")) {
                    return ExitCodes.IO_ERROR;
                }
            }
        }

        // Step 4: Eclipse setup
        boolean eclipseOk = true;
        if (!config.isSkipWorkspace()) {
            currentStep++;
            printStep(currentStep, totalSteps, "Setting up Eclipse JEE");
            eclipseOk = eclipseManager.detectOrInstall(config);
            printStepResult(eclipseOk, "Eclipse installation");
            System.out.println();

            if (!eclipseOk) {
                hadErrors = true;
                if (abortIfStepFailed(eclipseOk, config,
                        "Eclipse installation failed. Aborting.",
                        "Eclipse installation failed. Aborting. Use --continue-on-error to proceed anyway.")) {
                    return ExitCodes.IO_ERROR;
                }
            }

            if (eclipseOk) {
                // Step 5: Eclipse plugins
                currentStep++;
                printStep(currentStep, totalSteps, "Installing Eclipse plugins");
                boolean pluginsOk = eclipseManager.installPlugins(config);
                printStepResult(pluginsOk, "Eclipse plugins");
                System.out.println();

                if (!pluginsOk) {
                    hadErrors = true;
                    if (abortIfStepFailed(pluginsOk, config,
                            "Eclipse plugins installation failed. Aborting.",
                            "Eclipse plugins installation failed. Aborting. Use --continue-on-error to proceed anyway.")) {
                        return ExitCodes.IO_ERROR;
                    }
                }

                // Step 6: Workspace configuration
                currentStep++;
                printStep(currentStep, totalSteps, "Configuring Eclipse workspace");
                boolean workspaceOk = eclipseManager.setupWorkspace(config);
                printStepResult(workspaceOk, "Eclipse workspace");
                System.out.println();

                if (!workspaceOk) {
                    hadErrors = true;
                    if (abortIfStepFailed(workspaceOk, config,
                            "Workspace configuration failed. Aborting.",
                            "Workspace configuration failed. Aborting. Use --continue-on-error to proceed anyway.")) {
                        return ExitCodes.IO_ERROR;
                    }
                }
            }
        }

        // Step 7: Database setup
        if (!config.isSkipDb()) {
            currentStep++;
            printStep(currentStep, totalSteps, "Setting up database");
            boolean dbOk = databaseManager.setupDatabase(config);
            printStepResult(dbOk, "Database setup");
            System.out.println();

            if (!dbOk) {
                hadErrors = true;
                if (abortIfStepFailed(dbOk, config,
                        "Database setup failed. Aborting.",
                        "Database setup failed. Aborting. Use --continue-on-error to proceed anyway.")) {
                    return ExitCodes.IO_ERROR;
                }
            }
        }

        // Summary
        printSummary(config, hadErrors);
        return hadErrors ? ExitCodes.IO_ERROR : ExitCodes.SUCCESS;
    }

    private void printConfiguration(SetupConfig config) {
        System.out.println("Configuration:");
        System.out.println("  Source:      " + config.getSourceDir().toAbsolutePath());
        System.out.println("  Branch:      " + config.getBranch());
        System.out.println("  Repository:  " + config.getRepositoryUrl());
        System.out.println("  Java:        " + getJavaVersionString());
        if (!config.isSkipWorkspace()) {
            System.out.println("  Eclipse:     " + config.getEclipseDir().toAbsolutePath());
        }
        if (!config.isSkipDb()) {
            System.out.println("  Database:    " + config.getDbConnectionString());
            if (config.isUseDocker()) {
                if ("oracle".equals(config.getDbType())) {
                    System.out.println("  Docker:      " + config.getOracleDockerImage()
                            + " (container: " + config.getOracleDockerContainer() + ")");
                } else {
                    System.out.println("  Docker:      postgres:" + config.getDockerPostgresVersion()
                            + " (container: " + config.getDockerContainerName() + ")");
                }
            }
        }
        if (config.isIncludeRest()) {
            System.out.println("  REST API:    " + CliDefaults.IDEMPIERE_REST_REPO_URL);
        }
        System.out.println();
    }

    private String getJavaVersionString() {
        ProcessRunner.RunResult result = processRunner.run("java", "-version");
        if (result.exitCode() != 0 || result.output() == null) {
            return "Not found";
        }
        String firstLine = result.output().lines().findFirst().orElse("").trim();
        return firstLine.isEmpty() ? "Unknown" : firstLine;
    }

    private Integer detectJavaMajor() {
        ProcessRunner.RunResult result = processRunner.run("java", "-version");
        if (result.exitCode() != 0 || result.output() == null) {
            return null;
        }
        Matcher matcher = JAVA_VERSION_PATTERN.matcher(result.output());
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private Integer resolveRequiredJavaMajor(String branch) {
        if (branch == null || branch.isBlank()) {
            return null;
        }

        String normalized = branch.trim().toLowerCase();
        if ("master".equals(normalized) || "main".equals(normalized)) {
            return 21;
        }

        Matcher matcher = RELEASE_BRANCH_PATTERN.matcher(normalized);
        if (matcher.find()) {
            int major = Integer.parseInt(matcher.group(1));
            return major >= 13 ? 21 : 17;
        }

        return null;
    }

    private void printStep(int current, int total, String description) {
        sessionLogger.logStep(current, total, description);
        System.out.println("[" + current + "/" + total + "] " + description + "...");
    }

    private void printStepResult(boolean success, String component) {
        sessionLogger.logStepResult(success, component);
        if (success) {
            System.out.println("  " + CliOutput.ok(component + " completed."));
        } else {
            System.out.println("  " + CliOutput.fail(component + " had issues (see above)."));
        }
    }

    private int calculateSteps(SetupConfig config) {
        int steps = 1; // source
        if (!config.isSkipBuild()) {
            steps += 2; // build, jython
        }
        if (!config.isSkipWorkspace()) {
            steps += 3; // eclipse install, plugins, workspace
        }
        if (!config.isSkipDb()) {
            steps += 1; // database
        }
        return steps;
    }

    private boolean abortIfStepFailed(boolean stepOk, SetupConfig config, String logMessage, String errorMessage) {
        if (stepOk || config.isContinueOnError()) {
            return false;
        }
        abortSetup(logMessage, errorMessage);
        return true;
    }

    private void abortSetup(String logMessage, String errorMessage) {
        sessionLogger.logError(logMessage);
        sessionLogger.endSession(false);
        if (errorMessage != null && !errorMessage.isBlank()) {
            System.err.println(errorMessage);
        }
    }

    private void printSummary(SetupConfig config, boolean hadErrors) {
        sessionLogger.endSession(!hadErrors);

        System.out.println("==========================================");
        if (hadErrors) {
            System.out.println(CliOutput.fail("Setup completed with errors!"));
            System.out.println("  Some steps failed. Review the output above.");
        } else {
            System.out.println(CliOutput.ok("Setup completed successfully!"));
        }
        System.out.println();
        System.out.println("  Source:    " + config.getSourceDir().toAbsolutePath());
        if (!config.isSkipWorkspace()) {
            System.out.println("  Eclipse:   " + config.getEclipseDir().toAbsolutePath());
        }
        if (!config.isSkipDb()) {
            System.out.println("  Database:  " + config.getDbConnectionString());
        }
        System.out.println();
        System.out.println("  Next steps:");
        if (!config.isSkipWorkspace()) {
            System.out.println("    1. Launch Eclipse: " + eclipseManager.getEclipseExecutable(config.getEclipseDir()));
            System.out.println("    2. Select workspace: " + config.getSourceDir().toAbsolutePath());
            System.out.println("       (Projects are already imported automatically)");
        }
        System.out.println();
        System.out.println("    - Create plugins: idempiere-cli init org.mycompany.myplugin --with-callout");
        System.out.println();
    }

    private String buildCommandLine(SetupConfig config) {
        StringBuilder cmd = new StringBuilder("setup-dev-env");
        cmd.append(" --source-dir ").append(config.getSourceDir());
        cmd.append(" --branch ").append(config.getBranch());
        if (!config.isSkipWorkspace()) {
            cmd.append(" --eclipse-dir ").append(config.getEclipseDir());
        }
        if (config.isUseDocker()) {
            cmd.append(" --with-docker");
        }
        if (config.isSkipBuild()) {
            cmd.append(" --skip-build");
        }
        if (config.isSkipDb()) {
            cmd.append(" --skip-db");
        }
        if (config.isSkipWorkspace()) {
            cmd.append(" --skip-workspace");
        }
        if (config.isIncludeRest()) {
            cmd.append(" --include-rest");
        }
        if (config.isInstallCopilot()) {
            cmd.append(" --install-copilot");
        }
        if (config.isNonInteractive()) {
            cmd.append(" --non-interactive");
        }
        if (config.isContinueOnError()) {
            cmd.append(" --continue-on-error");
        }
        return cmd.toString();
    }
}
