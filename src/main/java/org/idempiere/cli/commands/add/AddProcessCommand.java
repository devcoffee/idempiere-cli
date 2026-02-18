package org.idempiere.cli.commands.add;

import jakarta.inject.Inject;
import org.idempiere.cli.service.ProjectDetector;
import org.idempiere.cli.service.ScaffoldService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.nio.file.Path;
import java.util.Map;

@Command(
        name = "process",
        description = "Add a process to the plugin",
        mixinStandardHelpOptions = true
)
public class AddProcessCommand implements Runnable {

    @Option(names = {"--name"}, required = true, description = "Process class name")
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
            projectDetector.printPluginNotFoundError(dir);
            return;
        }
        scaffoldService.addComponent("process", name, dir, pluginId,
                prompt != null ? Map.of("prompt", prompt) : null);
    }
}
