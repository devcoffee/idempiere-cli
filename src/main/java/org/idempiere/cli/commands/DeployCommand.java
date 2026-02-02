package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.service.DeployService;
import org.idempiere.cli.service.ProjectDetector;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.nio.file.Path;
import java.util.Optional;

@Command(
        name = "deploy",
        description = "Deploy a built plugin to an iDempiere instance",
        mixinStandardHelpOptions = true
)
public class DeployCommand implements Runnable {

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
    public void run() {
        Path pluginDir = Path.of(dir);
        if (!projectDetector.isIdempierePlugin(pluginDir)) {
            System.err.println("Error: Not an iDempiere plugin in " + pluginDir.toAbsolutePath());
            return;
        }

        Optional<Path> jar = deployService.findBuiltJar(pluginDir);
        if (jar.isEmpty()) {
            System.err.println("Error: No built .jar found in target/");
            System.err.println("Run 'idempiere-cli build' first.");
            return;
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

        if (!success) {
            System.exit(1);
        }
    }
}
