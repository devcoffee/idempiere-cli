package org.idempiere.cli.commands.add;

import org.idempiere.cli.commands.ExitCodeMapper;
import org.idempiere.cli.util.ExitCodes;
import jakarta.inject.Inject;
import org.idempiere.cli.service.ProjectDetector;
import org.idempiere.cli.service.ScaffoldService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Mixin;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "event-handler",
        description = "Add an event handler to the plugin",
        mixinStandardHelpOptions = true
)
public class AddEventHandlerCommand implements Callable<Integer> {

    @Option(names = {"--name"}, required = true, description = "Event handler class name")
    String name;

    @Option(names = {"--to"}, description = "Target plugin directory")
    String pluginDir;

    @Option(names = {"--prompt"}, description = "Describe what this component should do (used for AI generation)", hidden = true)
    String prompt;

    @Mixin
    AiAuditOptions aiAuditOptions = new AiAuditOptions();

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
            return ExitCodes.STATE_ERROR;
        }
        return ExitCodeMapper.fromScaffold(scaffoldService.addComponent("event-handler", name, dir, pluginId,
                aiAuditOptions.createExtraData(prompt)));
    }
}
