package org.idempiere.cli.commands.add;

import jakarta.inject.Inject;
import org.idempiere.cli.service.MavenWrapperService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

@Command(
        name = "mvnw",
        description = "Add Maven Wrapper to the project",
        mixinStandardHelpOptions = true
)
public class AddMavenWrapperCommand implements Runnable {

    @Parameters(index = "0", arity = "0..1", description = "Target project directory (default: current directory)")
    String projectDir;

    @Option(names = {"--force", "-f"}, description = "Overwrite existing wrapper files")
    boolean force;

    @Option(names = {"--maven-version"}, description = "Maven version to use (default: 3.9.9)")
    String mavenVersion;

    @Inject
    MavenWrapperService mavenWrapperService;

    @Override
    public void run() {
        Path dir = projectDir != null ? Path.of(projectDir) : Path.of(".");

        // Check if wrapper already exists
        if (mavenWrapperService.hasWrapper(dir) && !force) {
            System.out.println("Maven Wrapper already exists in " + dir.toAbsolutePath());
            System.out.println("Use --force to overwrite.");
            return;
        }

        System.out.println("Adding Maven Wrapper to " + dir.toAbsolutePath() + "...");

        if (!mavenWrapperService.addWrapper(dir)) {
            System.err.println("Failed to add Maven Wrapper.");
            return;
        }

        // Update Maven version if specified
        if (mavenVersion != null && !mavenVersion.isBlank()) {
            if (!mavenWrapperService.updateMavenVersion(dir, mavenVersion)) {
                System.err.println("Failed to set Maven version.");
                return;
            }
            System.out.println("Maven version set to: " + mavenVersion);
        }

        System.out.println("Maven Wrapper added successfully.");
        System.out.println();
        System.out.println("Files created:");
        System.out.println("  mvnw              - Unix/macOS/Linux script");
        System.out.println("  mvnw.cmd          - Windows script");
        System.out.println("  .mvn/wrapper/     - Wrapper configuration");
        System.out.println();
        System.out.println("Usage: ./mvnw verify (or mvnw.cmd on Windows)");
    }
}
