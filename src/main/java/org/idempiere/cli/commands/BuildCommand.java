package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.service.BuildService;
import org.idempiere.cli.service.ProjectDetector;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.nio.file.Files;
import java.nio.file.Path;

@Command(
        name = "build",
        description = "Build an iDempiere plugin using Maven/Tycho",
        mixinStandardHelpOptions = true
)
public class BuildCommand implements Runnable {

    @Option(names = {"--dir"}, description = "Plugin directory (default: current directory)", defaultValue = ".")
    String dir;

    @Option(names = {"--idempiere-home"}, description = "Path to iDempiere installation (for resolving dependencies)")
    String idempiereHome;

    @Option(names = {"--clean"}, description = "Run clean before build", defaultValue = "false")
    boolean clean;

    @Option(names = {"--skip-tests"}, description = "Skip tests during build", defaultValue = "false")
    boolean skipTests;

    @Inject
    BuildService buildService;

    @Inject
    ProjectDetector projectDetector;

    @Override
    public void run() {
        Path pluginDir = Path.of(dir);
        if (!projectDetector.isIdempierePlugin(pluginDir)) {
            System.err.println("Error: Not an iDempiere plugin in " + pluginDir.toAbsolutePath());
            System.err.println("Make sure you are inside a plugin directory or use --dir to specify one.");
            return;
        }

        Path idHome = resolveIdempiereHome();
        if (idHome != null && !Files.exists(idHome)) {
            System.err.println("Error: iDempiere home directory not found: " + idHome.toAbsolutePath());
            return;
        }

        System.out.println();
        System.out.println("Building plugin: " + projectDetector.detectPluginId(pluginDir).orElse("unknown"));
        System.out.println("==========================================");
        System.out.println();

        boolean success = buildService.build(pluginDir, idHome, clean, skipTests);
        if (!success) {
            System.exit(1);
        }
    }

    private Path resolveIdempiereHome() {
        // 1. CLI flag
        if (idempiereHome != null) {
            return Path.of(idempiereHome);
        }
        // 2. Environment variable
        String envHome = System.getenv("IDEMPIERE_HOME");
        if (envHome != null && !envHome.isBlank()) {
            return Path.of(envHome);
        }
        // 3. No iDempiere home — build without target platform
        return null;
    }
}
