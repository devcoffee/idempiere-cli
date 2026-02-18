package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.service.EclipseManager;
import org.idempiere.cli.service.SessionLogger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Imports plugin projects with Eclipse .project files into an Eclipse workspace.
 *
 * <p>This command automates the "File > Import > Existing Projects into Workspace" step
 * by running Eclipse in headless mode with an Ant script that imports projects.
 *
 * <p>Eclipse must NOT be running when this command is executed (the workspace lock
 * must be free).
 *
 * <h2>Example Usage</h2>
 * <pre>
 * # Import a plugin into the default workspace
 * idempiere-cli import-workspace --dir=myplugin --eclipse-dir=~/idempiere/eclipse --workspace=~/idempiere/src
 * </pre>
 */
@Command(
        name = "import-workspace",
        description = "Import plugin projects into Eclipse workspace",
        mixinStandardHelpOptions = true
)
public class ImportWorkspaceCommand implements Callable<Integer> {

    @Option(names = {"--dir"}, description = "Plugin project directory to import", required = true)
    Path pluginDir;

    @Option(names = {"--eclipse-dir"}, description = "Eclipse installation directory", required = true)
    Path eclipseDir;

    @Option(names = {"--workspace"}, description = "Eclipse workspace directory (used as -data)", required = true)
    Path workspaceDir;

    @Inject
    EclipseManager eclipseManager;

    @Inject
    SessionLogger sessionLogger;

    @Override
    public Integer call() {
        // Resolve paths
        pluginDir = pluginDir.toAbsolutePath();
        eclipseDir = eclipseDir.toAbsolutePath();
        workspaceDir = workspaceDir.toAbsolutePath();

        // Validate plugin directory
        if (!Files.isDirectory(pluginDir)) {
            System.err.println("Error: Plugin directory does not exist: " + pluginDir);
            return 1;
        }

        // Check for .project files
        boolean hasProject = Files.exists(pluginDir.resolve(".project"));
        if (!hasProject) {
            // Check children for multi-module
            try (var children = Files.list(pluginDir)) {
                hasProject = children.filter(Files::isDirectory)
                        .anyMatch(d -> Files.exists(d.resolve(".project")));
            } catch (Exception e) {
                // ignore
            }
        }
        if (!hasProject) {
            System.err.println("Error: No .project files found in: " + pluginDir);
            System.err.println("Hint: Use 'idempiere-cli init' with --eclipse-project to generate .project files.");
            return 1;
        }

        // Validate Eclipse directory
        if (!Files.isDirectory(eclipseDir)) {
            System.err.println("Error: Eclipse directory does not exist: " + eclipseDir);
            return 1;
        }

        // Check workspace lock
        Path lockFile = workspaceDir.resolve(".metadata/.lock");
        if (Files.exists(lockFile)) {
            System.err.println("Warning: Eclipse workspace may be in use (" + lockFile + ").");
            System.err.println("Please close Eclipse before running this command.");
        }

        System.out.println();
        System.out.println("Importing projects into Eclipse workspace");
        System.out.println("==========================================");
        System.out.println();
        System.out.println("  Plugin directory: " + pluginDir);
        System.out.println("  Eclipse:          " + eclipseDir);
        System.out.println("  Workspace:        " + workspaceDir);
        System.out.println();

        sessionLogger.logInfo("import-workspace: dir=" + pluginDir + " eclipse=" + eclipseDir + " workspace=" + workspaceDir);

        boolean success = eclipseManager.importProject(eclipseDir, workspaceDir, pluginDir);

        if (success) {
            System.out.println();
            System.out.println("Done! Open Eclipse with workspace: " + workspaceDir);
            return 0;
        }
        return 1;
    }
}
