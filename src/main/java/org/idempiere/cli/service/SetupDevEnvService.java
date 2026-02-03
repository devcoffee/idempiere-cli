package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.model.SetupConfig;
import org.idempiere.cli.util.CliDefaults;
import java.util.Scanner;

@ApplicationScoped
public class SetupDevEnvService {

    private static final String CHECK = "\u2714";
    private static final String CROSS = "\u2718";

    @Inject
    SourceManager sourceManager;

    @Inject
    EclipseManager eclipseManager;

    @Inject
    DatabaseManager databaseManager;

    @Inject
    SessionLogger sessionLogger;

    public void setup(SetupConfig config) {
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
                return;
            }
        }

        System.out.println();

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
            sessionLogger.logError("Cannot continue without source code. Aborting.");
            sessionLogger.endSession(false);
            System.err.println("Cannot continue without source code. Aborting.");
            return;
        }

        // Step 2: Build source
        currentStep++;
        printStep(currentStep, totalSteps, "Building iDempiere with Maven");
        boolean buildOk = sourceManager.buildSource(config);
        printStepResult(buildOk, "Maven build");
        System.out.println();

        if (!buildOk) {
            hadErrors = true;
            if (!config.isContinueOnError()) {
                sessionLogger.logError("Build failed. Aborting.");
                sessionLogger.endSession(false);
                System.err.println("Build failed. Aborting. Use --continue-on-error to proceed anyway.");
                return;
            }
        }

        // Step 3: Download Jython
        currentStep++;
        printStep(currentStep, totalSteps, "Downloading Jython");
        boolean jythonOk = sourceManager.downloadJython(config);
        printStepResult(jythonOk, "Jython download");
        System.out.println();

        if (!jythonOk) {
            hadErrors = true;
            if (!config.isContinueOnError()) {
                sessionLogger.logError("Jython download failed. Aborting.");
                sessionLogger.endSession(false);
                System.err.println("Jython download failed. Aborting. Use --continue-on-error to proceed anyway.");
                return;
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
                if (!config.isContinueOnError()) {
                    sessionLogger.logError("Eclipse installation failed. Aborting.");
                    sessionLogger.endSession(false);
                    System.err.println("Eclipse installation failed. Aborting. Use --continue-on-error to proceed anyway.");
                    return;
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
                    if (!config.isContinueOnError()) {
                        sessionLogger.logError("Eclipse plugins installation failed. Aborting.");
                        sessionLogger.endSession(false);
                        System.err.println("Eclipse plugins installation failed. Aborting. Use --continue-on-error to proceed anyway.");
                        return;
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
                    if (!config.isContinueOnError()) {
                        sessionLogger.logError("Workspace configuration failed. Aborting.");
                        sessionLogger.endSession(false);
                        System.err.println("Workspace configuration failed. Aborting. Use --continue-on-error to proceed anyway.");
                        return;
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
                if (!config.isContinueOnError()) {
                    sessionLogger.logError("Database setup failed. Aborting.");
                    sessionLogger.endSession(false);
                    System.err.println("Database setup failed. Aborting. Use --continue-on-error to proceed anyway.");
                    return;
                }
            }
        }

        // Summary
        printSummary(config, hadErrors);
    }

    private void printConfiguration(SetupConfig config) {
        System.out.println("Configuration:");
        System.out.println("  Source:      " + config.getSourceDir().toAbsolutePath());
        System.out.println("  Branch:      " + config.getBranch());
        System.out.println("  Repository:  " + config.getRepositoryUrl());
        if (!config.isSkipWorkspace()) {
            System.out.println("  Eclipse:     " + config.getEclipseDir().toAbsolutePath());
        }
        if (!config.isSkipDb()) {
            System.out.println("  Database:    " + config.getDbConnectionString());
            if (config.isUseDocker()) {
                System.out.println("  Docker:      postgres:" + config.getDockerPostgresVersion()
                        + " (container: " + config.getDockerContainerName() + ")");
            }
        }
        if (config.isIncludeRest()) {
            System.out.println("  REST API:    included");
        }
        System.out.println();
    }

    private void printStep(int current, int total, String description) {
        sessionLogger.logStep(current, total, description);
        System.out.println("[" + current + "/" + total + "] " + description + "...");
    }

    private void printStepResult(boolean success, String component) {
        sessionLogger.logStepResult(success, component);
        if (success) {
            System.out.println("  " + CHECK + " " + component + " completed.");
        } else {
            System.out.println("  " + CROSS + " " + component + " had issues (see above).");
        }
    }

    private int calculateSteps(SetupConfig config) {
        int steps = 3; // source, build, jython
        if (!config.isSkipWorkspace()) {
            steps += 3; // eclipse install, plugins, workspace
        }
        if (!config.isSkipDb()) {
            steps += 1; // database
        }
        return steps;
    }

    private void printSummary(SetupConfig config, boolean hadErrors) {
        sessionLogger.endSession(!hadErrors);

        System.out.println("==========================================");
        if (hadErrors) {
            System.out.println(CROSS + " Setup completed with errors!");
            System.out.println("  Some steps failed. Review the output above.");
        } else {
            System.out.println(CHECK + " Setup completed successfully!");
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
