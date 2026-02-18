package org.idempiere.cli.commands.add;

import org.idempiere.cli.commands.ExitCodeMapper;
import jakarta.inject.Inject;
import org.idempiere.cli.service.ProjectDetector;
import org.idempiere.cli.service.ScaffoldService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "window-validator",
        description = "Add a window validator to the plugin",
        mixinStandardHelpOptions = true
)
public class AddWindowValidatorCommand implements Callable<Integer> {

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
    public Integer call() {
        Path dir = pluginDir != null ? Path.of(pluginDir) : Path.of(".");
        String pluginId = projectDetector.detectPluginId(dir).orElse(null);
        if (pluginId == null) {
            projectDetector.printPluginNotFoundError(dir);
            return 1;
        }
        return ExitCodeMapper.fromScaffold(scaffoldService.addComponent("window-validator", name, dir, pluginId,
                prompt != null ? Map.of("prompt", prompt) : null));
    }
}
