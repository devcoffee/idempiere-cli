package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.service.BuildService;
import org.idempiere.cli.service.ProjectDetector;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Builds an iDempiere plugin using Maven and Tycho.
 *
 * <p>Executes the Maven build process with Tycho configuration for OSGi bundle
 * creation. The built JAR is placed in {@code target/} directory.
 *
 * <h2>Dependency Resolution</h2>
 * <p>If {@code --idempiere-home} or {@code IDEMPIERE_HOME} environment variable
 * is set, uses the local p2 repository for dependency resolution. Otherwise,
 * falls back to remote repositories (slower, requires network).
 *
 * <h2>Example Usage</h2>
 * <pre>
 * # Basic build
 * idempiere-cli build
 *
 * # Clean build with tests skipped
 * idempiere-cli build --clean --skip-tests
 *
 * # Build with local iDempiere installation
 * idempiere-cli build --idempiere-home=/opt/idempiere
 * </pre>
 *
 * @see BuildService#build(Path, Path, boolean, boolean, boolean, boolean, String)
 */
@Command(
        name = "build",
        description = "Build an iDempiere plugin using Maven/Tycho",
        mixinStandardHelpOptions = true
)
public class BuildCommand implements Callable<Integer> {

    @Option(names = {"--dir"}, description = "Plugin directory (default: current directory)", defaultValue = ".")
    String dir;

    @Option(names = {"--idempiere-home"}, description = "Path to iDempiere installation (for resolving dependencies)")
    String idempiereHome;

    @Option(names = {"--clean"}, description = "Run clean before build", defaultValue = "false")
    boolean clean;

    @Option(names = {"--skip-tests"}, description = "Skip tests during build", defaultValue = "false")
    boolean skipTests;

    @Option(names = {"--update", "-U"}, description = "Force update of snapshots", defaultValue = "false")
    boolean update;

    @Option(names = {"--disable-p2-mirrors"}, description = "Disable p2 mirrors (recommended for CI)", defaultValue = "true")
    boolean disableP2Mirrors;

    @Option(names = {"--maven-args", "-A"}, description = "Additional Maven arguments (e.g. -A=\"-X -e\")")
    String mavenArgs;

    @Inject
    BuildService buildService;

    @Inject
    ProjectDetector projectDetector;

    @Override
    public Integer call() {
        Path pluginDir = Path.of(dir);
        if (!projectDetector.isIdempierePlugin(pluginDir)) {
            System.err.println("Error: Not an iDempiere plugin in " + pluginDir.toAbsolutePath());
            System.err.println("Make sure you are inside a plugin directory or use --dir to specify one.");
            return 1;
        }

        Path idHome = resolveIdempiereHome();
        if (idHome != null && !Files.exists(idHome)) {
            System.err.println("Error: iDempiere home directory not found: " + idHome.toAbsolutePath());
            return 1;
        }

        System.out.println();
        System.out.println("Building plugin: " + projectDetector.detectPluginId(pluginDir).orElse("unknown"));
        System.out.println("==========================================");
        System.out.println();

        boolean success = buildService.build(pluginDir, idHome, clean, skipTests, update, disableP2Mirrors, mavenArgs);
        return success ? 0 : 1;
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
