package org.idempiere.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import org.idempiere.cli.service.PluginInfoService;
import org.idempiere.cli.service.PluginInfoService.PluginInfo;
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

    @Option(names = {"--json"}, description = "Output results as JSON")
    boolean json;

    @Inject
    PluginInfoService pluginInfoService;

    @Inject
    ProjectDetector projectDetector;

    @Override
    public void run() {
        Path pluginDir = Path.of(dir);
        if (!projectDetector.isIdempierePlugin(pluginDir)) {
            if (json) {
                System.out.println("{\"error\": \"Not an iDempiere plugin in " + pluginDir.toAbsolutePath() + "\"}");
            } else {
                System.err.println("Error: Not an iDempiere plugin in " + pluginDir.toAbsolutePath());
                System.err.println("Make sure you are inside a plugin directory or use --dir to specify one.");
            }
            return;
        }

        if (json) {
            printJson(pluginInfoService.getInfo(pluginDir));
        } else {
            pluginInfoService.printInfo(pluginDir);
        }
    }

    private void printJson(PluginInfo info) {
        if (info == null) {
            System.out.println("{\"error\": \"Failed to read plugin info\"}");
            return;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.createObjectNode();
            root.put("pluginId", info.pluginId());
            root.put("version", info.version());
            root.put("vendor", info.vendor());
            if (info.fragmentHost() != null) {
                root.put("fragmentHost", info.fragmentHost());
            }

            var deps = root.putArray("requiredBundles");
            info.requiredBundles().forEach(deps::add);

            var components = root.putArray("components");
            info.components().forEach(components::add);

            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (Exception e) {
            System.err.println("{\"error\": \"Failed to serialize JSON\"}");
        }
    }
}
