package org.idempiere.cli.commands.add;

import jakarta.inject.Inject;
import org.idempiere.cli.model.PlatformVersion;
import org.idempiere.cli.model.PluginDescriptor;
import org.idempiere.cli.service.ProjectDetector;
import org.idempiere.cli.service.ScaffoldService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Adds a feature module to an existing multi-module project.
 *
 * <p>A feature is an Eclipse/p2 construct that groups plugins and fragments
 * for easier installation and dependency management. Users can install all
 * related plugins at once by installing the feature.</p>
 *
 * <p>This command creates a feature module and automatically:</p>
 * <ul>
 *   <li>Creates the feature directory</li>
 *   <li>Generates pom.xml with eclipse-feature packaging</li>
 *   <li>Generates feature.xml listing all existing plugins and fragments</li>
 *   <li>Updates the root pom.xml to include the new module</li>
 *   <li>Updates category.xml to reference the feature</li>
 * </ul>
 *
 * <p><b>Note:</b> Features are optional in iDempiere. OSGi handles dependencies
 * via MANIFEST.MF Require-Bundle headers. Features are mainly useful when you
 * want to provide a single installable unit for end users.</p>
 *
 * <h2>Example Usage</h2>
 * <pre>
 * # Add feature to current project
 * idempiere-cli add feature
 *
 * # From outside the project
 * idempiere-cli add feature --to=/path/to/project
 * </pre>
 */
@Command(
        name = "feature",
        description = "Add a feature module to a multi-module project",
        mixinStandardHelpOptions = true
)
public class AddFeatureModuleCommand implements Callable<Integer> {

    @Option(names = {"--to"}, description = "Multi-module project root directory (default: current directory)")
    String projectDir;

    @Option(names = {"--version"}, description = "Feature version (default: from project)", defaultValue = "1.0.0.qualifier")
    String version;

    @Option(names = {"--vendor"}, description = "Feature vendor name", defaultValue = "")
    String vendor;

    @Inject
    ScaffoldService scaffoldService;

    @Inject
    ProjectDetector projectDetector;

    @Override
    public Integer call() {
        Path dir = projectDir != null ? Path.of(projectDir) : Path.of(".");

        // Find multi-module root
        Optional<Path> rootOpt = projectDetector.findMultiModuleRoot(dir);
        if (rootOpt.isEmpty()) {
            System.err.println("Error: Not inside a multi-module project.");
            System.err.println("Use 'idempiere-cli init' to create a new project first.");
            return 1;
        }

        Path rootDir = rootOpt.get();

        // Check if feature already exists
        if (projectDetector.hasFeature(rootDir)) {
            System.err.println("Error: A feature module already exists in this project.");
            return 1;
        }

        // Detect project settings from existing project
        Optional<String> baseIdOpt = projectDetector.detectProjectBaseId(rootDir);
        String baseId = baseIdOpt.orElse(rootDir.getFileName().toString());

        // Detect iDempiere version from existing project
        Optional<Integer> idempiereVersionOpt = projectDetector.detectIdempiereVersion(rootDir);
        int idempiereVersion = idempiereVersionOpt.orElse(13);

        // Check if fragment exists
        boolean hasFragment = projectDetector.hasFragment(rootDir);

        // Build descriptor
        PluginDescriptor descriptor = new PluginDescriptor(baseId);
        descriptor.setVersion(version);
        descriptor.setVendor(vendor);
        descriptor.setPlatformVersion(PlatformVersion.of(idempiereVersion));
        descriptor.setMultiModule(true);
        descriptor.setWithFeature(true);
        descriptor.setWithFragment(hasFragment);

        return scaffoldService.addFeatureModuleToProject(rootDir, descriptor).success() ? 0 : 1;
    }
}
