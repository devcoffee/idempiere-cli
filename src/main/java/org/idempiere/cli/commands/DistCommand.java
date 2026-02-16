package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.service.DistService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

/**
 * Creates iDempiere server distribution packages for deployment.
 *
 * <p>Builds the iDempiere source and creates distribution ZIP files
 * for all target platforms (Linux, Windows, macOS). The output is
 * compatible with Jenkins/SourceForge format and the install-idempiere
 * Ansible playbook.
 *
 * <h2>Example Usage</h2>
 * <pre>
 * # Build and package from source directory
 * idempiere-cli dist --source-dir=/path/to/idempiere
 *
 * # Skip build, just repackage existing artifacts (fast)
 * idempiere-cli dist --source-dir=/path/to/idempiere --skip-build
 *
 * # Full clean build with custom version
 * idempiere-cli dist --source-dir=/path/to/idempiere --clean --version-label=13
 * </pre>
 *
 * @see DistService#createDistribution(Path, Path, String, boolean, boolean)
 */
@Command(
        name = "dist",
        description = "Create iDempiere server distribution packages",
        mixinStandardHelpOptions = true
)
public class DistCommand implements Runnable {

    @Option(names = "--source-dir", description = "Path to iDempiere source directory (default: current directory)",
            defaultValue = ".")
    String sourceDir;

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

    @Override
    public void run() {
        distService.createDistribution(Path.of(sourceDir), Path.of(outputDir), version, skipBuild, clean);
    }
}
