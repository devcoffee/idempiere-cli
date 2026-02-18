package org.idempiere.cli.commands.add;

import jakarta.inject.Inject;
import org.idempiere.cli.service.ProjectDetector;
import org.idempiere.cli.service.ScaffoldService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.nio.file.Path;
import java.util.Map;

@Command(
        name = "window-validator",
        description = "Add a window validator to the plugin",
        mixinStandardHelpOptions = true
)
public class AddWindowValidatorCommand implements Runnable {

    @Option(names = {"--name"}, required = true, description = "Window validator class name")
    String name;

    @Option(names = {"--to"}, description = "Target plugin directory")
    String pluginDir;

    @Option(names = {"--prompt"}, description = "Describe what this component should do (used for AI generation)")
    String prompt;

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
        scaffoldService.addComponent("window-validator", name, dir, pluginId,
                prompt != null ? Map.of("prompt", prompt) : null);
    }
}
