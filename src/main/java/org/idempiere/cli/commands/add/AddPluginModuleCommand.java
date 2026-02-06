package org.idempiere.cli.commands.add;

import jakarta.inject.Inject;
import org.idempiere.cli.model.PlatformVersion;
import org.idempiere.cli.model.PluginDescriptor;
import org.idempiere.cli.service.ProjectDetector;
import org.idempiere.cli.service.ScaffoldService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Adds a new plugin module to an existing multi-module project.
 *
 * <p>This command creates a new plugin (OSGi bundle) module within a multi-module
 * iDempiere project structure. It automatically:</p>
 * <ul>
 *   <li>Creates the plugin directory with standard structure (META-INF, OSGI-INF, src)</li>
 *   <li>Generates pom.xml, MANIFEST.MF, plugin.xml, build.properties</li>
 *   <li>Updates the root pom.xml to include the new module</li>
 *   <li>Updates category.xml if a p2 module exists</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>
 * # From within a multi-module project
 * cd org.example.myproject
 * idempiere-cli add plugin org.example.myproject.reports
 *
 * # From outside, specifying the project root
 * idempiere-cli add plugin org.example.myproject.reports --to=/path/to/project
 * </pre>
 */
@Command(
        name = "plugin",
        description = "Add a new plugin module to a multi-module project",
        mixinStandardHelpOptions = true
)
public class AddPluginModuleCommand implements Runnable {

    @Parameters(index = "0", description = "Plugin ID (e.g., org.example.myproject.newplugin)")
    String pluginId;

    @Option(names = {"--to"}, description = "Multi-module project root directory (default: current directory)")
    String projectDir;

    @Option(names = {"--version"}, description = "Plugin version (default: 1.0.0.qualifier)", defaultValue = "1.0.0.qualifier")
    String version;

    @Option(names = {"--vendor"}, description = "Plugin vendor name", defaultValue = "")
    String vendor;

    @Inject
    ScaffoldService scaffoldService;

    @Inject
    ProjectDetector projectDetector;

    @Override
    public void run() {
        Path dir = projectDir != null ? Path.of(projectDir) : Path.of(".");

        // Find multi-module root
        Optional<Path> rootOpt = projectDetector.findMultiModuleRoot(dir);
        if (rootOpt.isEmpty()) {
            System.err.println("Error: Not inside a multi-module project.");
            System.err.println("Use 'idempiere-cli init' to create a new project first.");
            return;
        }

        Path rootDir = rootOpt.get();

        // Detect project settings from existing project
        Optional<String> baseIdOpt = projectDetector.detectProjectBaseId(rootDir);
        String baseId = baseIdOpt.orElse(rootDir.getFileName().toString());

        // Detect iDempiere version from existing project
        Optional<Integer> idempiereVersionOpt = projectDetector.detectIdempiereVersion(rootDir);
        int idempiereVersion = idempiereVersionOpt.orElse(13);

        // Build descriptor
        PluginDescriptor descriptor = new PluginDescriptor(baseId);
        descriptor.setVersion(version);
        descriptor.setVendor(vendor);
        descriptor.setPlatformVersion(PlatformVersion.of(idempiereVersion));
        descriptor.setMultiModule(true);

        // Use the provided pluginId directly for the new module
        // Override basePluginId to the new plugin ID
        descriptor.setBasePluginId(pluginId);

        scaffoldService.addPluginModuleToProject(rootDir, pluginId, descriptor);
    }
}
