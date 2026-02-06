package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.service.DepsService;
import org.idempiere.cli.service.ProjectDetector;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

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
