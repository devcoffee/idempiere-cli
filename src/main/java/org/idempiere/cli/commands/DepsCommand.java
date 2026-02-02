package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.service.DepsService;
import org.idempiere.cli.service.ProjectDetector;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

@Command(
        name = "deps",
        description = "Analyze plugin dependencies",
        mixinStandardHelpOptions = true
)
public class DepsCommand implements Runnable {

    @Option(names = {"--dir"}, description = "Plugin directory (default: current directory)", defaultValue = ".")
    String dir;

    @Inject
    DepsService depsService;

    @Inject
    ProjectDetector projectDetector;

    @Override
    public void run() {
        Path pluginDir = Path.of(dir);
        if (!projectDetector.isIdempierePlugin(pluginDir)) {
            System.err.println("Error: Not an iDempiere plugin in " + pluginDir.toAbsolutePath());
            return;
        }
        depsService.analyze(pluginDir);
    }
}
