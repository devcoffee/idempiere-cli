package org.idempiere.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import org.idempiere.cli.service.DepsService;
import org.idempiere.cli.service.DepsService.DepsResult;
import org.idempiere.cli.service.ProjectDetector;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Analyzes plugin dependencies by scanning Java imports.
 *
 * <p>Scans source files for import statements and maps them to iDempiere bundles.
 * Reports:
 * <ul>
 *   <li>Required bundles based on imports</li>
 *   <li>Missing bundles not declared in MANIFEST.MF</li>
 *   <li>Unused bundles declared but not imported</li>
 * </ul>
 *
 * <h2>Bundle Mapping</h2>
 * <p>Common package-to-bundle mappings:
 * <ul>
 *   <li>{@code org.compiere.*} → org.adempiere.base</li>
 *   <li>{@code org.adempiere.webui.*} → org.adempiere.ui.zk</li>
 *   <li>{@code org.idempiere.rest.*} → org.idempiere.rest.api</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>
 * idempiere-cli deps
 * idempiere-cli deps --dir=/path/to/plugin
 * </pre>
 *
 * @see DepsService#analyze(Path)
 */
@Command(
        name = "deps",
        description = "Analyze plugin dependencies",
        mixinStandardHelpOptions = true
)
public class DepsCommand implements Callable<Integer> {

    @Option(names = {"--dir"}, description = "Plugin directory (default: current directory)", defaultValue = ".")
    String dir;

    @Option(names = {"--json"}, description = "Output results as JSON")
    boolean json;

    @Inject
    DepsService depsService;

    @Inject
    ProjectDetector projectDetector;

    @Override
    public Integer call() {
        Path pluginDir = Path.of(dir);
        if (!projectDetector.isIdempierePlugin(pluginDir)) {
            if (json) {
                System.out.println("{\"error\": \"Not an iDempiere plugin in " + pluginDir.toAbsolutePath() + "\"}");
            } else {
                System.err.println("Error: Not an iDempiere plugin in " + pluginDir.toAbsolutePath());
            }
            return 1;
        }

        if (json) {
            return printJson(depsService.analyzeData(pluginDir));
        } else {
            depsService.analyze(pluginDir);
            return 0;
        }
    }

    private Integer printJson(DepsResult result) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.createObjectNode();
            root.put("isFragment", result.isFragment());

            var declared = root.putArray("declaredBundles");
            result.declaredBundles().forEach(declared::add);

            var required = root.putArray("requiredBundles");
            result.requiredBundles().forEach(required::add);

            var missing = root.putArray("missingBundles");
            result.missingBundles().forEach(missing::add);

            var unused = root.putArray("unusedBundles");
            result.unusedBundles().forEach(unused::add);

            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
            return 0;
        } catch (Exception e) {
            System.err.println("{\"error\": \"Failed to serialize JSON\"}");
            return 1;
        }
    }
}
