package org.idempiere.cli.commands.add;

import jakarta.inject.Inject;
import org.idempiere.cli.service.ProjectDetector;
import org.idempiere.cli.service.ScaffoldService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.nio.file.Path;

@Command(
        name = "rest-extension",
        description = "Add a REST API resource extension to the plugin",
        mixinStandardHelpOptions = true
)
public class AddRestExtensionCommand implements Runnable {

    @Option(names = {"--name"}, required = true, description = "REST resource class name")
    String name;

    @Option(names = {"--resource-path"}, description = "REST resource path (e.g., 'myresource')", defaultValue = "myresource")
    String resourcePath;

    @Option(names = {"--to"}, description = "Target plugin directory")
    String pluginDir;

    @Inject
    ScaffoldService scaffoldService;

    @Inject
    ProjectDetector projectDetector;

    @Override
    public void run() {
        Path dir = pluginDir != null ? Path.of(pluginDir) : Path.of(".");
        String pluginId = projectDetector.detectPluginId(dir).orElse(null);
        if (pluginId == null) {
            System.err.println("Error: Could not detect iDempiere plugin in " + dir.toAbsolutePath());
            System.err.println("Make sure you are inside a plugin directory or use --to to specify one.");
            return;
        }
        scaffoldService.addRestExtension(name, resourcePath, dir, pluginId);
    }
}
