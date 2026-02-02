package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.model.DbConfig;
import org.idempiere.cli.service.DiffSchemaService;
import org.idempiere.cli.service.ModelGeneratorService;
import org.idempiere.cli.service.ProjectDetector;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.Optional;

@Command(
        name = "diff-schema",
        description = "Compare model classes against database schema",
        mixinStandardHelpOptions = true
)
public class DiffSchemaCommand implements Runnable {

    @Option(names = {"--table"}, required = true, description = "Database table name")
    String tableName;

    @Option(names = {"--dir"}, description = "Plugin directory (default: current directory)", defaultValue = ".")
    String dir;

    @Option(names = {"--db-host"}, description = "Database host", defaultValue = "localhost")
    String dbHost;

    @Option(names = {"--db-port"}, description = "Database port", defaultValue = "5432")
    int dbPort;

    @Option(names = {"--db-name"}, description = "Database name", defaultValue = "idempiere")
    String dbName;

    @Option(names = {"--db-user"}, description = "Database user", defaultValue = "adempiere")
    String dbUser;

    @Option(names = {"--db-pass"}, description = "Database password", defaultValue = "adempiere")
    String dbPass;

    @Option(names = {"--config"}, description = "Path to idempiereEnv.properties file")
    String configPath;

    @Inject
    DiffSchemaService diffSchemaService;

    @Inject
    ModelGeneratorService modelGeneratorService;

    @Inject
    ProjectDetector projectDetector;

    @Override
    public void run() {
        Path pluginDir = Path.of(dir);
        if (!projectDetector.isIdempierePlugin(pluginDir)) {
            System.err.println("Error: Not an iDempiere plugin in " + pluginDir.toAbsolutePath());
            return;
        }

        Optional<String> pluginId = projectDetector.detectPluginId(pluginDir);
        if (pluginId.isEmpty()) {
            System.err.println("Error: Could not detect plugin ID.");
            return;
        }

        DbConfig dbConfig = modelGeneratorService.resolveDbConfig(dbHost, dbPort, dbName, dbUser, dbPass, configPath);
        Path srcDir = pluginDir.resolve("src").resolve(pluginId.get().replace('.', '/'));

        diffSchemaService.diff(tableName, srcDir, pluginId.get(), dbConfig);
    }
}
