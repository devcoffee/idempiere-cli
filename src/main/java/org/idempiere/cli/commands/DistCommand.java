package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.service.DistService;
import org.idempiere.cli.service.ProjectDetector;
import org.idempiere.cli.util.ExitCodes;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Creates distribution packages for iDempiere core or plugins.
 *
 * <p>For <b>iDempiere core</b>, builds the source and creates distribution
 * ZIP files for all target platforms (Linux, Windows, macOS). The output is
 * compatible with Jenkins/SourceForge format and the install-idempiere
 * Ansible playbook.
 *
 * <p>For <b>plugins</b> (standalone or multi-module), builds and packages
 * the plugin JAR and (for multi-module) the p2 update site into ZIP files.
 *
 * <h2>Example Usage</h2>
 * <pre>
 * # Core: build and create server distribution ZIPs
 * idempiere-cli dist --source-dir=/path/to/idempiere
 *
 * # Core: skip build, just repackage existing artifacts
 * idempiere-cli dist --source-dir=/path/to/idempiere --skip-build
 *
 * # Plugin: build and package (auto-detects multi-module or standalone)
 * idempiere-cli dist --dir=./my-plugin
 *
 * # Plugin: skip build, just package existing artifacts
 * idempiere-cli dist --dir=./my-plugin --skip-build
 * </pre>
 *
 * @see DistService#createDistribution(Path, Path, String, boolean, boolean)
 * @see DistService#createPluginDist(Path, Path, boolean, boolean)
 */
@Command(
        name = "dist",
        description = "Create distribution packages for iDempiere core or plugins",
        mixinStandardHelpOptions = true
)
public class DistCommand implements Callable<Integer> {

    @Option(names = "--source-dir", description = "Path to iDempiere source directory (default: current directory)",
            defaultValue = ".")
    String sourceDir;

    @Option(names = "--dir", description = "Plugin project directory (standalone or multi-module root)")
    String dir;

    @Option(names = "--skip-build", description = "Skip Maven build, use existing artifacts")
    boolean skipBuild;

    @Option(names = "--clean", description = "Run clean before build")
    boolean clean;

    @Option(names = "--version-label", description = "Version label for distribution packages (default: auto-detect from pom.xml)")
    String version;

    @Option(names = "--output", description = "Output directory for distribution packages (default: ./dist)",
            defaultValue = "dist")
    String outputDir;

    @Inject
    DistService distService;

    @Inject
    ProjectDetector projectDetector;

    @Override
    public Integer call() {
        Path path = Path.of(dir != null ? dir : sourceDir).toAbsolutePath();

        // 1. Core iDempiere source?
        if (distService.isIdempiereSource(path)) {
            boolean success = distService.createDistribution(path, Path.of(outputDir), version, skipBuild, clean);
            return success ? ExitCodes.SUCCESS : ExitCodes.IO_ERROR;
        }

        // 2. Plugin (multi-module or standalone)?
        if (projectDetector.isMultiModuleRoot(path) || projectDetector.isIdempierePlugin(path)) {
            boolean success = distService.createPluginDist(path, Path.of(outputDir), skipBuild, clean);
            return success ? ExitCodes.SUCCESS : ExitCodes.IO_ERROR;
        }

        // 3. Neither
        System.err.println("Error: Not an iDempiere source or plugin directory: " + path);
        System.err.println("For core: expected pom.xml and org.idempiere.p2/ in the directory.");
        System.err.println("For plugins: expected META-INF/MANIFEST.MF or multi-module pom.xml.");
        return ExitCodes.STATE_ERROR;
    }
}
