package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.service.BuildService;
import org.idempiere.cli.service.PackageService;
import org.idempiere.cli.service.ProjectDetector;
import org.idempiere.cli.util.ExitCodes;
import org.idempiere.cli.util.Troubleshooting;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Packages a built plugin for distribution.
 *
 * <p>Creates distributable artifacts from the built plugin JAR.
 * Supports multiple output formats:
 * <ul>
 *   <li><b>zip</b> (default): Simple ZIP archive with JAR and metadata</li>
 *   <li><b>p2</b>: Eclipse p2 update site (requires multi-module project)</li>
 * </ul>
 *
 * <h2>p2 Update Site</h2>
 * <p>The p2 format requires a multi-module project structure with a dedicated
 * p2 module. Use {@code idempiere-cli init} (without --standalone) to create
 * a project with p2 support.
 *
 * <h2>Example Usage</h2>
 * <pre>
 * # Create ZIP package (works for standalone and multi-module)
 * idempiere-cli package
 *
 * # Create p2 update site (multi-module only)
 * cd my-project  # multi-module root
 * idempiere-cli package --format=p2
 *
 * # Custom output directory
 * idempiere-cli package --output=release
 * </pre>
 *
 * @see PackageService#packageZip(Path, String, String, Path, Path)
 * @see PackageService#packageP2MultiModule(Path, Path)
 */
@Command(
        name = "package",
        description = "Package plugin for distribution",
        mixinStandardHelpOptions = true
)
public class PackageCommand implements Callable<Integer> {

    @Option(names = {"--dir"}, description = "Project directory (default: current directory)", defaultValue = ".")
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
    public Integer call() {
        Path projectDir = Path.of(dir).toAbsolutePath();

        // Check if we're in a multi-module project
        Optional<Path> multiModuleRoot = projectDetector.findMultiModuleRoot(projectDir);

        if ("p2".equals(format)) {
            return handleP2Package(projectDir, multiModuleRoot);
        } else if ("zip".equals(format)) {
            return handleZipPackage(projectDir, multiModuleRoot);
        } else {
            System.err.println("Error: Unknown format '" + format + "'. Use 'zip' or 'p2'.");
            Troubleshooting.printHowToResolve(
                    "Use --format=zip for standalone plugins.",
                    "Use --format=p2 only in a multi-module project created with idempiere-cli init."
            );
            return ExitCodes.VALIDATION_ERROR;
        }
    }

    private Integer handleP2Package(Path projectDir, Optional<Path> multiModuleRoot) {
        if (multiModuleRoot.isEmpty()) {
            // Standalone plugin - p2 not supported
            System.err.println("Error: p2 format requires a multi-module project structure.");
            System.err.println();
            System.err.println("To use p2 update sites, create a multi-module project:");
            System.err.println("  idempiere-cli init org.example.myplugin");
            System.err.println();
            System.err.println("Multi-module projects include a dedicated .p2 module that");
            System.err.println("generates proper Eclipse update site metadata.");
            System.err.println();
            System.err.println("For standalone plugins, use --format=zip instead.");
            Troubleshooting.printHowToResolve(
                    "Generate a multi-module project: idempiere-cli init org.example.myplugin",
                    "Or switch to zip packaging: idempiere-cli package --format=zip"
            );
            return ExitCodes.STATE_ERROR;
        }

        Path rootDir = multiModuleRoot.get();

        // Find p2 module
        Optional<Path> p2Module = findP2Module(rootDir);
        if (p2Module.isEmpty()) {
            System.err.println("Error: No .p2 module found in multi-module project.");
            System.err.println("The project may have been created without p2 support.");
            Troubleshooting.printHowToResolve(
                    "Confirm the root pom.xml includes a *.p2 module.",
                    "If missing, recreate or migrate the project to include the p2 module."
            );
            return ExitCodes.STATE_ERROR;
        }

        Path p2Repository = p2Module.get().resolve("target/repository");
        if (!Files.exists(p2Repository)) {
            System.err.println("Error: p2 repository not found at " + p2Repository);
            System.err.println();
            System.err.println("Build the project first:");
            System.err.println("  cd " + rootDir);
            System.err.println("  idempiere-cli build");
            Troubleshooting.printHowToResolve(
                    "Run build at the multi-module root before packaging.",
                    "Confirm the p2 repository exists at <project>.p2/target/repository."
            );
            return ExitCodes.STATE_ERROR;
        }

        String projectId = projectDetector.detectProjectBaseId(rootDir).orElse("plugin");
        Path outputDir = rootDir.resolve(output);

        System.out.println();
        System.out.println("Packaging p2 update site: " + projectId);
        System.out.println("==========================================");
        System.out.println();

        packageService.packageP2MultiModule(p2Repository, outputDir);
        System.out.println();
        return ExitCodes.SUCCESS;
    }

    private Integer handleZipPackage(Path projectDir, Optional<Path> multiModuleRoot) {
        Path pluginDir;

        if (multiModuleRoot.isPresent()) {
            // Multi-module: find the base plugin module
            Path rootDir = multiModuleRoot.get();
            Optional<Path> basePlugin = findBasePluginModule(rootDir);
            if (basePlugin.isEmpty()) {
                System.err.println("Error: Could not find base plugin module in " + rootDir);
                Troubleshooting.printHowToResolve(
                        "Ensure at least one plugin module has META-INF/MANIFEST.MF.",
                        "If modules were renamed, update the multi-module structure and try again."
                );
                return ExitCodes.STATE_ERROR;
            }
            pluginDir = basePlugin.get();
        } else {
            // Standalone: use current directory
            pluginDir = projectDir;
        }

        if (!projectDetector.isIdempierePlugin(pluginDir)) {
            System.err.println("Error: Not an iDempiere plugin in " + pluginDir);
            Troubleshooting.printHowToResolve(
                    "Run package from a plugin directory or provide --dir with plugin path.",
                    "Validate plugin structure: idempiere-cli doctor --dir " + pluginDir
            );
            return ExitCodes.STATE_ERROR;
        }

        Optional<Path> jar = buildService.findBuiltJar(pluginDir);
        if (jar.isEmpty()) {
            System.err.println("Error: No built .jar found in target/");
            System.err.println("Run 'idempiere-cli build' first.");
            Troubleshooting.printHowToResolve(
                    "Build the plugin first: idempiere-cli build --dir " + pluginDir,
                    "Then run package again."
            );
            return ExitCodes.STATE_ERROR;
        }

        String pluginId = projectDetector.detectPluginId(pluginDir).orElse("unknown");
        String pluginVersion = projectDetector.detectPluginVersion(pluginDir).orElse("1.0.0");
        Path outputDir = (multiModuleRoot.isPresent() ? multiModuleRoot.get() : pluginDir).resolve(output);

        System.out.println();
        System.out.println("Packaging plugin: " + pluginId);
        System.out.println("==========================================");
        System.out.println();

        packageService.packageZip(jar.get(), pluginId, pluginVersion, pluginDir, outputDir);
        System.out.println();
        return ExitCodes.SUCCESS;
    }

    private Optional<Path> findP2Module(Path rootDir) {
        try (var stream = Files.list(rootDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().endsWith(".p2"))
                    .findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<Path> findBasePluginModule(Path rootDir) {
        try (var stream = Files.list(rootDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".base") ||
                               (Files.exists(p.resolve("META-INF/MANIFEST.MF")) &&
                                !name.endsWith(".test") &&
                                !name.endsWith(".fragment"));
                    })
                    .findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
