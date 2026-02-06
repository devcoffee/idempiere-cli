package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.service.BuildService;
import org.idempiere.cli.service.PackageService;
import org.idempiere.cli.service.ProjectDetector;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Packages a built plugin for distribution.
 *
 * <p>Creates distributable artifacts from the built plugin JAR.
 * Supports multiple output formats:
 * <ul>
 *   <li><b>zip</b> (default): Simple ZIP archive with JAR and metadata</li>
 *   <li><b>p2</b>: Eclipse p2 update site for Install New Software</li>
 * </ul>
 *
 * <h2>p2 Update Site</h2>
 * <p>The p2 format creates a complete update site that can be hosted
 * on a web server or used locally via {@code file://} URL in Eclipse.
 *
 * <h2>Example Usage</h2>
 * <pre>
 * # Create ZIP package
 * idempiere-cli package
 *
 * # Create p2 update site
 * idempiere-cli package --format=p2
 *
 * # Custom output directory
 * idempiere-cli package --output=release
 * </pre>
 *
 * @see PackageService#packageZip(Path, String, String, Path, Path)
 * @see PackageService#packageP2(Path, Path, Path)
 */
@Command(
        name = "package",
        description = "Package plugin for distribution",
        mixinStandardHelpOptions = true
)
public class PackageCommand implements Runnable {

    @Option(names = {"--dir"}, description = "Plugin directory (default: current directory)", defaultValue = ".")
    String dir;

    @Option(names = {"--format"}, description = "Package format: zip or p2 (default: zip)", defaultValue = "zip")
    String format;

    @Option(names = {"--output"}, description = "Output directory (default: dist)", defaultValue = "dist")
    String output;

    @Inject
    PackageService packageService;

    @Inject
    ProjectDetector projectDetector;

    @Inject
    BuildService buildService;

    @Override
    public void run() {
        Path pluginDir = Path.of(dir);
        if (!projectDetector.isIdempierePlugin(pluginDir)) {
            System.err.println("Error: Not an iDempiere plugin in " + pluginDir.toAbsolutePath());
            return;
        }

        Optional<Path> jar = buildService.findBuiltJar(pluginDir);
        if (jar.isEmpty()) {
            System.err.println("Error: No built .jar found in target/");
            System.err.println("Run 'idempiere-cli build' first.");
            return;
        }

        String pluginId = projectDetector.detectPluginId(pluginDir).orElse("unknown");
        String pluginVersion = projectDetector.detectPluginVersion(pluginDir).orElse("1.0.0");
        Path outputDir = pluginDir.resolve(output);

        System.out.println();
        System.out.println("Packaging plugin: " + pluginId);
        System.out.println("==========================================");
        System.out.println();

        switch (format) {
            case "zip" -> packageService.packageZip(jar.get(), pluginId, pluginVersion, pluginDir, outputDir);
            case "p2" -> packageService.packageP2(pluginDir, outputDir, jar.get());
            default -> System.err.println("Error: Unknown format '" + format + "'. Use 'zip' or 'p2'.");
        }

        System.out.println();
    }
}
