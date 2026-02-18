package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.model.PlatformVersion;
import org.idempiere.cli.service.MigrateService;
import org.idempiere.cli.service.ProjectDetector;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Migrates a plugin between iDempiere versions.
 *
 * <p>Updates plugin configuration files to match target version requirements:
 * <ul>
 *   <li>pom.xml: Java release, Tycho version</li>
 *   <li>MANIFEST.MF: JavaSE version, bundle-version dependencies</li>
 *   <li>build.properties: javac target settings</li>
 * </ul>
 *
 * <h2>Supported Versions</h2>
 * <ul>
 *   <li><b>v12</b>: Java 17, Tycho 4.0.4, Eclipse 2023-09</li>
 *   <li><b>v13</b>: Java 21, Tycho 4.0.8, Eclipse 2024-09</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>
 * # Upgrade from v12 to v13
 * idempiere-cli migrate --from=12 --to=13
 *
 * # Downgrade from v13 to v12
 * idempiere-cli migrate --from=13 --to=12
 * </pre>
 *
 * @see MigrateService#migrate(Path, PlatformVersion, PlatformVersion)
 */
@Command(
        name = "migrate",
        description = "Migrate a plugin from one iDempiere version to another",
        mixinStandardHelpOptions = true
)
public class MigrateCommand implements Callable<Integer> {

    @Option(names = {"--from"}, required = true, description = "Source iDempiere major version")
    int fromVersion;

    @Option(names = {"--to"}, required = true, description = "Target iDempiere major version")
    int toVersion;

    @Option(names = {"--dir"}, description = "Plugin directory (default: current directory)", defaultValue = ".")
    String dir;

    @Inject
    MigrateService migrateService;

    @Inject
    ProjectDetector projectDetector;

    @Override
    public Integer call() {
        Path pluginDir = Path.of(dir);
        if (!projectDetector.isIdempierePlugin(pluginDir)) {
            System.err.println("Error: Not an iDempiere plugin in " + pluginDir.toAbsolutePath());
            return 1;
        }
        PlatformVersion from = PlatformVersion.of(fromVersion);
        PlatformVersion to = PlatformVersion.of(toVersion);
        migrateService.migrate(pluginDir, from, to);
        return 0;
    }
}
