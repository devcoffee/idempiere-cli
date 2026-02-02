package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.model.SetupConfig;
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

    public void setup(SetupConfig config) {
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
                System.out.println("Setup cancelled.");
                return;
            }
        }

        System.out.println();

        int totalSteps = calculateSteps(config);
        int currentStep = 0;

        // Step 1: Clone/update source
        currentStep++;
        printStep(currentStep, totalSteps, "Setting up iDempiere source code");
        boolean sourceOk = sourceManager.cloneOrUpdate(config);
        printStepResult(sourceOk, "Source code");
        System.out.println();

        if (!sourceOk) {
            System.err.println("Cannot continue without source code. Aborting.");
            return;
        }

        // Step 2: Build source
        currentStep++;
        printStep(currentStep, totalSteps, "Building iDempiere with Maven");
        boolean buildOk = sourceManager.buildSource(config);
        printStepResult(buildOk, "Maven build");
        System.out.println();

        // Step 3: Download Jython
        currentStep++;
        printStep(currentStep, totalSteps, "Downloading Jython");
        boolean jythonOk = sourceManager.downloadJython(config);
        printStepResult(jythonOk, "Jython download");
        System.out.println();

        // Step 4: Eclipse setup
        if (!config.isSkipWorkspace()) {
            currentStep++;
            printStep(currentStep, totalSteps, "Setting up Eclipse JEE");
            boolean eclipseOk = eclipseManager.detectOrInstall(config);
            printStepResult(eclipseOk, "Eclipse installation");
            System.out.println();

            if (eclipseOk) {
                // Step 5: Eclipse plugins
                currentStep++;
                printStep(currentStep, totalSteps, "Installing Eclipse plugins");
                boolean pluginsOk = eclipseManager.installPlugins(config);
                printStepResult(pluginsOk, "Eclipse plugins");
                System.out.println();

                // Step 6: Workspace configuration
                currentStep++;
                printStep(currentStep, totalSteps, "Configuring Eclipse workspace");
                boolean workspaceOk = eclipseManager.setupWorkspace(config);
                printStepResult(workspaceOk, "Eclipse workspace");
                System.out.println();
            }
        }

        // Step 7: Database setup
        if (!config.isSkipDb()) {
            currentStep++;
            printStep(currentStep, totalSteps, "Setting up database");
            boolean dbOk = databaseManager.setupDatabase(config);
            printStepResult(dbOk, "Database setup");
            System.out.println();
        }

        // Summary
        printSummary(config);
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
        System.out.println("[" + current + "/" + total + "] " + description + "...");
    }

    private void printStepResult(boolean success, String component) {
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

    private void printSummary(SetupConfig config) {
        System.out.println("==========================================");
        System.out.println(CHECK + " Setup completed!");
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
            System.out.println("    2. Workspace is at: " + config.getEclipseDir().resolve("workspace").toAbsolutePath());
        }
        System.out.println("    - Create plugins: idempiere-cli init org.mycompany.myplugin --with-callout");
        System.out.println();
    }
}
