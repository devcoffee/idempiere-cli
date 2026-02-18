package org.idempiere.cli.commands.add;

import org.idempiere.cli.commands.ExitCodeMapper;
import jakarta.inject.Inject;
import org.idempiere.cli.service.ProjectDetector;
import org.idempiere.cli.service.ScaffoldService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "rest-extension",
        description = "Add a REST API resource extension to the plugin",
        mixinStandardHelpOptions = true
)
public class AddRestExtensionCommand implements Callable<Integer> {

    @Option(names = {"--name"}, required = true, description = "REST resource class name")
    String name;

    @Option(names = {"--resource-path"}, description = "REST resource path (e.g., 'myresource')", defaultValue = "myresource")
    String resourcePath;

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
        Map<String, Object> extraData = new HashMap<>();
        extraData.put("resourcePath", resourcePath);
        if (prompt != null) extraData.put("prompt", prompt);
        return ExitCodeMapper.fromScaffold(scaffoldService.addComponent("rest-extension", name, dir, pluginId, extraData));
    }
}
