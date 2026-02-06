package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.service.PluginInfoService;
import org.idempiere.cli.service.ProjectDetector;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.nio.file.Path;

/**
 * Displays plugin metadata and detected components.
 *
 * <p>Extracts and displays information from plugin files:
 * <ul>
 *   <li>Plugin ID (Bundle-SymbolicName)</li>
 *   <li>Version (Bundle-Version)</li>
 *   <li>Vendor (Bundle-Vendor)</li>
 *   <li>Required bundles (Require-Bundle)</li>
 *   <li>Detected components (callouts, processes, forms, etc.)</li>
 * </ul>
 *
 * <h2>Component Detection</h2>
 * <p>Scans source files and OSGI-INF to detect registered components
 * based on annotations and service declarations.
 *
 * <h2>Example Usage</h2>
 * <pre>
 * idempiere-cli info
 * idempiere-cli info --dir=/path/to/plugin
 * </pre>
 *
 * @see PluginInfoService#printInfo(Path)
 */
@Command(
        name = "info",
        description = "Show plugin metadata and components",
        mixinStandardHelpOptions = true
)
public class InfoCommand implements Runnable {

    @Option(names = {"--dir"}, description = "Plugin directory (default: current directory)", defaultValue = ".")
    String dir;

    @Inject
    PluginInfoService pluginInfoService;

    @Inject
    ProjectDetector projectDetector;

    @Override
    public void run() {
        Path pluginDir = Path.of(dir);
        if (!projectDetector.isIdempierePlugin(pluginDir)) {
            System.err.println("Error: Not an iDempiere plugin in " + pluginDir.toAbsolutePath());
            System.err.println("Make sure you are inside a plugin directory or use --dir to specify one.");
            return;
        }
        pluginInfoService.printInfo(pluginDir);
    }
}
