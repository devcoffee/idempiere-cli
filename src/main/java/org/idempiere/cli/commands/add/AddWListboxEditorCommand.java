package org.idempiere.cli.commands.add;

import jakarta.inject.Inject;
import org.idempiere.cli.service.ProjectDetector;
import org.idempiere.cli.service.ScaffoldService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.nio.file.Path;

@Command(
        name = "wlistbox-editor",
        description = "Add a form with custom WListbox column editors",
        mixinStandardHelpOptions = true
)
public class AddWListboxEditorCommand implements Runnable {

    @Option(names = {"--name"}, required = true, description = "Form class name")
    String name;

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
        scaffoldService.addComponent("wlistbox-editor", name, dir, pluginId);
    }
}
