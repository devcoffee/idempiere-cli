package org.idempiere.cli.commands.add;

import jakarta.inject.Inject;
import org.idempiere.cli.model.DbConfig;
import org.idempiere.cli.service.ModelGeneratorService;
import org.idempiere.cli.service.ProjectDetector;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.Optional;

@Command(
        name = "model",
        description = "Generate model classes (X_, I_, M_) from a database table",
        mixinStandardHelpOptions = true
)
public class AddModelCommand implements Runnable {

    @Option(names = {"--table"}, required = true, description = "Database table name (e.g., C_Order)")
    String tableName;

    @Option(names = {"--to"}, description = "Target plugin directory (default: current directory)", defaultValue = ".")
    String pluginDir;

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
    ModelGeneratorService modelGeneratorService;

    @Inject
    ProjectDetector projectDetector;

    @Override
    public void run() {
        Path dir = Path.of(pluginDir);
        if (!projectDetector.isIdempierePlugin(dir)) {
            System.err.println("Error: Not an iDempiere plugin in " + dir.toAbsolutePath());
            return;
        }

        Optional<String> pluginId = projectDetector.detectPluginId(dir);
        if (pluginId.isEmpty()) {
            System.err.println("Error: Could not detect plugin ID.");
            return;
        }

        Path srcDir = dir.resolve("src").resolve(pluginId.get().replace('.', '/'));

        System.out.println();
        System.out.println("Generating model for table: " + tableName);
        System.out.println("==========================================");
        System.out.println();

        DbConfig dbConfig = modelGeneratorService.resolveDbConfig(dbHost, dbPort, dbName, dbUser, dbPass, configPath);

        boolean success = modelGeneratorService.generate(tableName, srcDir, pluginId.get(), dbConfig);

        System.out.println();
        if (success) {
            System.out.println("Model classes generated successfully.");
        } else {
            System.exit(1);
        }
    }
}
