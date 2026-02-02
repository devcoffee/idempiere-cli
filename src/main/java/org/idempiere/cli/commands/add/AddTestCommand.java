package org.idempiere.cli.commands.add;

import jakarta.inject.Inject;
import org.idempiere.cli.service.ProjectDetector;
import org.idempiere.cli.service.TestGeneratorService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.Optional;

@Command(
        name = "test",
        description = "Generate test stubs for plugin components",
        mixinStandardHelpOptions = true
)
public class AddTestCommand implements Runnable {

    @Option(names = {"--for"}, description = "Specific class name to generate test for")
    String forClass;

    @Option(names = {"--dir"}, description = "Plugin directory (default: current directory)", defaultValue = ".")
    String dir;

    @Inject
    TestGeneratorService testGeneratorService;

    @Inject
    ProjectDetector projectDetector;

    @Override
    public void run() {
        Path pluginDir = Path.of(dir);
        if (!projectDetector.isIdempierePlugin(pluginDir)) {
            System.err.println("Error: Not an iDempiere plugin in " + pluginDir.toAbsolutePath());
            return;
        }

        Optional<String> pluginId = projectDetector.detectPluginId(pluginDir);
        if (pluginId.isEmpty()) {
            System.err.println("Error: Could not detect plugin ID.");
            return;
        }

        System.out.println();
        System.out.println("Generating test stubs");
        System.out.println("==========================================");
        System.out.println();

        if (forClass != null) {
            testGeneratorService.generateTestFor(forClass, pluginDir, pluginId.get());
        } else {
            testGeneratorService.generateAllTests(pluginDir, pluginId.get());
        }

        System.out.println();
        System.out.println("Test stubs generated successfully.");
        System.out.println();
    }
}
