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
 * Adds a fragment module to an existing multi-module project.
 *
 * <p>A fragment is an OSGi bundle that extends another bundle (the host).
 * This is commonly used to add ZK UI components to iDempiere without
 * modifying the core UI bundle.</p>
 *
 * <p>This command creates a fragment module and automatically:</p>
 * <ul>
 *   <li>Creates the fragment directory with standard structure</li>
 *   <li>Generates pom.xml and MANIFEST.MF with Fragment-Host header</li>
 *   <li>Updates the root pom.xml to include the new module</li>
 *   <li>Updates category.xml if a p2 module exists</li>
 *   <li>Updates feature.xml if a feature module exists</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>
 * # Add fragment with default host (org.adempiere.ui.zk)
 * idempiere-cli add fragment
 *
 * # Add fragment with custom host
 * idempiere-cli add fragment --host=org.adempiere.base
 *
 * # From outside the project
 * idempiere-cli add fragment --to=/path/to/project
 * </pre>
 */
@Command(
        name = "fragment",
        description = "Add a fragment module to a multi-module project",
        mixinStandardHelpOptions = true
)
public class AddFragmentModuleCommand implements Callable<Integer> {

    @Option(names = {"--to"}, description = "Multi-module project root directory (default: current directory)")
    String projectDir;

    @Option(names = {"--host"}, description = "Fragment host bundle (default: org.adempiere.ui.zk)", defaultValue = "org.adempiere.ui.zk")
    String fragmentHost;

    @Option(names = {"--version"}, description = "Fragment version (default: from project)", defaultValue = "1.0.0.qualifier")
    String version;

    @Option(names = {"--vendor"}, description = "Fragment vendor name", defaultValue = "")
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

        // Check if fragment already exists
        if (projectDetector.hasFragment(rootDir)) {
            System.err.println("Error: A fragment module already exists in this project.");
            return 1;
        }

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
        descriptor.setWithFragment(true);
        descriptor.setFragmentHost(fragmentHost);

        return scaffoldService.addFragmentModuleToProject(rootDir, fragmentHost, descriptor).success() ? 0 : 1;
    }
}
