package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.model.PlatformVersion;
import org.idempiere.cli.service.MigrateService;
import org.idempiere.cli.service.ProjectDetector;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

@Command(
        name = "migrate",
        description = "Migrate a plugin from one iDempiere version to another",
        mixinStandardHelpOptions = true
)
public class MigrateCommand implements Runnable {

    @Option(names = {"--from"}, required = true, description = "Source iDempiere major version")
    int fromVersion;

    @Option(names = {"--to"}, required = true, description = "Target iDempiere major version")
    int toVersion;

    @Option(names = {"--dir"}, description = "Plugin directory (default: current directory)", defaultValue = ".")
    String dir;

    @Inject
    MigrateService migrateService;

    @Inject
    ProjectDetector projectDetector;

    @Override
    public void run() {
        Path pluginDir = Path.of(dir);
        if (!projectDetector.isIdempierePlugin(pluginDir)) {
            System.err.println("Error: Not an iDempiere plugin in " + pluginDir.toAbsolutePath());
            return;
        }
        PlatformVersion from = PlatformVersion.of(fromVersion);
        PlatformVersion to = PlatformVersion.of(toVersion);
        migrateService.migrate(pluginDir, from, to);
    }
}
