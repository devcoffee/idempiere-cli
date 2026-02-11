package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.idempiere.cli.model.CliConfig;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Service for loading CLI configuration from YAML files.
 *
 * <p>Configuration is loaded with the following precedence (highest first):
 * <ol>
 *   <li>Explicit path passed to {@link #loadConfig(Path)}</li>
 *   <li>Environment variable {@code IDEMPIERE_CLI_CONFIG}</li>
 *   <li>Hierarchical search: {@code .idempiere-cli.yaml} in current dir or any parent</li>
 *   <li>Global config: {@code ~/.idempiere-cli.yaml}</li>
 * </ol>
 *
 * <h2>Example Directory Structure</h2>
 * <pre>
 * workspace/
 * ├── idempiere12/
 * │   ├── .idempiere-cli.yaml    # idempiereVersion: 12
 * │   ├── plugin1/               # inherits from parent
 * │   └── plugin2/               # inherits from parent
 * └── idempiere13/
 *     ├── .idempiere-cli.yaml    # idempiereVersion: 13
 *     └── plugina/               # inherits from parent
 * </pre>
 *
 * <h2>Environment Variable</h2>
 * <pre>
 * export IDEMPIERE_CLI_CONFIG=/path/to/idempiere12/.idempiere-cli.yaml
 * idempiere-cli init org.my.plugin
 * </pre>
 *
 * @see CliConfig
 */
@ApplicationScoped
public class CliConfigService {

    private static final String CONFIG_FILENAME = ".idempiere-cli.yaml";
    private static final String CONFIG_FILENAME_ALT = ".idempiere-cli.yml";
    private static final String ENV_VAR_NAME = "IDEMPIERE_CLI_CONFIG";

    /**
     * Loads the CLI configuration using default resolution order.
     *
     * @return the merged configuration (never null)
     */
    public CliConfig loadConfig() {
        return loadConfig(null);
    }

    /**
     * Loads the CLI configuration with optional explicit path.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Explicit path (if provided)</li>
     *   <li>Environment variable IDEMPIERE_CLI_CONFIG</li>
     *   <li>Hierarchical search up directory tree</li>
     *   <li>Global ~/.idempiere-cli.yaml</li>
     * </ol>
     *
     * @param explicitPath optional explicit config file path (from --config option)
     * @return the merged configuration (never null)
     */
    public CliConfig loadConfig(Path explicitPath) {
        CliConfig config = new CliConfig();

        // 1. Load global config first (lowest priority)
        CliConfig globalConfig = loadFromPath(getGlobalConfigPath());
        if (globalConfig != null) {
            config.mergeFrom(globalConfig);
        }

        // 2. Hierarchical search (overrides global)
        Path hierarchicalPath = findConfigInHierarchy();
        if (hierarchicalPath != null) {
            CliConfig hierarchicalConfig = loadFromPath(hierarchicalPath);
            if (hierarchicalConfig != null) {
                config.mergeFrom(hierarchicalConfig);
            }
        }

        // 3. Environment variable (overrides hierarchical)
        String envPath = System.getenv(ENV_VAR_NAME);
        if (envPath != null && !envPath.isEmpty()) {
            CliConfig envConfig = loadFromPath(Path.of(envPath));
            if (envConfig != null) {
                config.mergeFrom(envConfig);
            }
        }

        // 4. Explicit path (highest priority)
        if (explicitPath != null) {
            CliConfig explicitConfig = loadFromPath(explicitPath);
            if (explicitConfig != null) {
                config.mergeFrom(explicitConfig);
            }
        }

        return config;
    }

    /**
     * Searches up the directory tree for a config file.
     *
     * <p>Starts from current working directory and walks up to root,
     * returning the first .idempiere-cli.yaml found.
     *
     * @return path to config file, or null if not found
     */
    public Path findConfigInHierarchy() {
        Path current = Path.of("").toAbsolutePath();

        while (current != null) {
            Path configPath = current.resolve(CONFIG_FILENAME);
            if (Files.exists(configPath)) {
                return configPath;
            }

            Path configPathAlt = current.resolve(CONFIG_FILENAME_ALT);
            if (Files.exists(configPathAlt)) {
                return configPathAlt;
            }

            current = current.getParent();
        }

        return null;
    }

    /**
     * Gets the path to the global configuration file.
     *
     * @return path to ~/.idempiere-cli.yaml (may not exist)
     */
    public Path getGlobalConfigPath() {
        String home = System.getProperty("user.home");
        Path yamlPath = Path.of(home, CONFIG_FILENAME);
        if (Files.exists(yamlPath)) {
            return yamlPath;
        }
        Path ymlPath = Path.of(home, CONFIG_FILENAME_ALT);
        if (Files.exists(ymlPath)) {
            return ymlPath;
        }
        return yamlPath; // Return .yaml path even if doesn't exist
    }

    /**
     * Loads configuration from a specific file path.
     *
     * @param configPath the path to the config file
     * @return the loaded config, or null if file doesn't exist or is invalid
     */
    public CliConfig loadFromPath(Path configPath) {
        if (configPath == null || !Files.exists(configPath)) {
            return null;
        }

        try {
            String content = Files.readString(configPath);
            LoaderOptions options = new LoaderOptions();
            Yaml yaml = new Yaml(new Constructor(CliConfig.class, options));
            CliConfig config = yaml.load(content);
            return config != null ? config : new CliConfig();
        } catch (IOException e) {
            System.err.println("Warning: Could not read config file: " + configPath);
            return null;
        } catch (Exception e) {
            System.err.println("Warning: Invalid config file format: " + configPath);
            System.err.println("  " + e.getMessage());
            return null;
        }
    }

    /**
     * Saves a CliConfig to the global config file (~/.idempiere-cli.yaml).
     *
     * @param config the config to save
     * @throws IOException if writing fails
     */
    public void saveGlobalConfig(CliConfig config) throws IOException {
        saveConfig(config, getGlobalConfigPath());
    }

    /**
     * Saves a CliConfig to a specific file path.
     *
     * @param config the config to save
     * @param configPath the path to write to
     * @throws IOException if writing fails
     */
    public void saveConfig(CliConfig config, Path configPath) throws IOException {
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);

        Representer representer = new Representer(dumperOptions);
        representer.getPropertyUtils().setSkipMissingProperties(true);
        // Don't write class tags like !!org.idempiere.cli.model.CliConfig
        representer.addClassTag(CliConfig.class, org.yaml.snakeyaml.nodes.Tag.MAP);
        representer.addClassTag(CliConfig.AiConfig.class, org.yaml.snakeyaml.nodes.Tag.MAP);
        representer.addClassTag(CliConfig.SkillsConfig.class, org.yaml.snakeyaml.nodes.Tag.MAP);
        representer.addClassTag(CliConfig.SkillSource.class, org.yaml.snakeyaml.nodes.Tag.MAP);
        representer.addClassTag(CliConfig.Defaults.class, org.yaml.snakeyaml.nodes.Tag.MAP);
        representer.addClassTag(CliConfig.Templates.class, org.yaml.snakeyaml.nodes.Tag.MAP);

        Yaml yaml = new Yaml(representer, dumperOptions);
        String content = yaml.dump(config);
        Files.writeString(configPath, content);
    }

    /**
     * Checks if a global config file exists.
     *
     * @return true if ~/.idempiere-cli.yaml exists
     */
    public boolean hasGlobalConfig() {
        String home = System.getProperty("user.home");
        return Files.exists(Path.of(home, CONFIG_FILENAME)) ||
               Files.exists(Path.of(home, CONFIG_FILENAME_ALT));
    }

    /**
     * Gets the environment variable name for config path.
     *
     * @return "IDEMPIERE_CLI_CONFIG"
     */
    public String getEnvVarName() {
        return ENV_VAR_NAME;
    }
}
