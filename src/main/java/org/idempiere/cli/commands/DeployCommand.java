package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.service.DeployService;
import org.idempiere.cli.service.ProjectDetector;
import org.idempiere.cli.util.ExitCodes;
import org.idempiere.cli.util.Troubleshooting;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Deploys a built plugin to an iDempiere instance.
 *
 * <p>Supports two deployment modes:
 * <ul>
 *   <li><b>Copy deploy</b> (default): Copies JAR to plugins/ directory, requires restart</li>
 *   <li><b>Hot deploy</b> ({@code --hot}): Installs via OSGi console, no restart needed</li>
 * </ul>
 *
 * <h2>Hot Deploy</h2>
 * <p>Hot deploy connects to the OSGi console (default: localhost:12612) and
 * installs/updates the bundle without restarting iDempiere. Requires the
 * OSGi console to be enabled in iDempiere configuration.
 *
 * <h2>Example Usage</h2>
 * <pre>
 * # Copy to plugins directory
 * idempiere-cli deploy --target=/opt/idempiere
 *
 * # Hot deploy to running instance
 * idempiere-cli deploy --target=/opt/idempiere --hot
 * </pre>
 *
 * @see DeployService#copyDeploy(Path, Path)
 * @see DeployService#hotDeploy(Path, String, int)
 */
@Command(
        name = "deploy",
        description = "Deploy a built plugin to an iDempiere instance",
        mixinStandardHelpOptions = true
)
public class DeployCommand implements Callable<Integer> {

    @Option(names = {"--dir"}, description = "Plugin directory (default: current directory)", defaultValue = ".")
    String dir;

    @Option(names = {"--target"}, required = true, description = "Path to iDempiere installation")
    String target;

    @Option(names = {"--hot"}, description = "Hot deploy via OSGi console (no restart needed)", defaultValue = "false")
    boolean hot;

    @Option(names = {"--osgi-host"}, description = "OSGi console host", defaultValue = "localhost")
    String osgiHost;

    @Option(names = {"--osgi-port"}, description = "OSGi console port", defaultValue = "12612")
    int osgiPort;

    @Inject
    DeployService deployService;

    @Inject
    ProjectDetector projectDetector;

    @Override
    public Integer call() {
        Path pluginDir = Path.of(dir);
        if (!projectDetector.isIdempierePlugin(pluginDir)) {
            System.err.println("Error: Not an iDempiere plugin in " + pluginDir.toAbsolutePath());
            Troubleshooting.printHowToResolve(
                    "Run deploy from a plugin directory or provide --dir with plugin path.",
                    "Validate plugin structure: idempiere-cli doctor --plugin --dir /path/to/plugin"
            );
            return ExitCodes.STATE_ERROR;
        }

        Optional<Path> jar = deployService.findBuiltJar(pluginDir);
        if (jar.isEmpty()) {
            System.err.println("Error: No built .jar found in target/");
            System.err.println("Run 'idempiere-cli build' first.");
            Troubleshooting.printHowToResolve(
                    "Build the plugin: idempiere-cli build --dir " + pluginDir.toAbsolutePath(),
                    "Retry deploy after confirming target/*.jar exists."
            );
            return ExitCodes.STATE_ERROR;
        }

        String pluginId = projectDetector.detectPluginId(pluginDir).orElse("unknown");
        System.out.println();
        System.out.println("Deploying plugin: " + pluginId);
        System.out.println("==========================================");
        System.out.println();

        boolean success;
        if (hot) {
            success = deployService.hotDeploy(jar.get(), osgiHost, osgiPort);
        } else {
            success = deployService.copyDeploy(jar.get(), Path.of(target));
        }

        return success ? ExitCodes.SUCCESS : ExitCodes.IO_ERROR;
    }
}
