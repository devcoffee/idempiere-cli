package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.model.CliConfig;
import org.idempiere.cli.service.CliConfigService;
import org.idempiere.cli.service.InteractivePromptService;
import org.idempiere.cli.util.CliOutput;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.util.Map;

/**
 * Manages CLI configuration (AI provider, skills, defaults).
 */
@Command(
        name = "config",
        description = "Manage CLI configuration",
        mixinStandardHelpOptions = true,
        subcommands = {
                ConfigCommand.ShowCmd.class,
                ConfigCommand.GetCmd.class,
                ConfigCommand.SetCmd.class,
                ConfigCommand.InitCmd.class
        }
)
public class ConfigCommand {

    @Command(name = "show", description = "Show current configuration (without API keys)")
    static class ShowCmd implements Runnable {

        @Inject
        CliConfigService configService;

        @Override
        public void run() {
            CliConfig config = configService.loadConfig();

            System.out.println("Configuration:");
            System.out.println();

            System.out.println("defaults:");
            System.out.println("  vendor: " + config.getDefaults().getVendor());
            System.out.println("  idempiereVersion: " + config.getDefaults().getIdempiereVersion());
            System.out.println();

            System.out.println("ai:");
            System.out.println("  provider: " + config.getAi().getProvider());
            // Show API key status (masked)
            String apiKeyEnv = config.getAi().getApiKeyEnv();
            String envValue = apiKeyEnv != null ? System.getenv(apiKeyEnv) : null;
            if (envValue != null && !envValue.isEmpty()) {
                System.out.println("  apiKey: (set via $" + apiKeyEnv + ")");
            } else if (config.getAi().hasApiKey()) {
                String key = config.getAi().getApiKey();
                String masked = key.length() > 8
                        ? key.substring(0, 4) + "***" + key.substring(key.length() - 4)
                        : "***";
                System.out.println("  apiKey: " + masked);
            } else {
                System.out.println("  apiKey: (not set)");
            }
            System.out.println("  model: " + (config.getAi().getModel() != null ? config.getAi().getModel() : "(default)"));
            System.out.println("  fallback: " + config.getAi().getFallback());
            System.out.println();

            System.out.println("skills:");
            var sources = config.getSkills().getSources();
            if (sources.isEmpty()) {
                System.out.println("  sources: (none)");
            } else {
                System.out.println("  sources:");
                for (var source : sources) {
                    System.out.println("    - " + source.getName() + " (priority " + source.getPriority() + ")");
                    if (source.isRemote()) {
                        System.out.println("      url: " + source.getUrl());
                    } else {
                        System.out.println("      path: " + source.getPath());
                    }
                }
            }
            System.out.println("  cacheDir: " + config.getSkills().getCacheDir());
            System.out.println("  updateInterval: " + config.getSkills().getUpdateInterval());
        }
    }

    @Command(name = "get", description = "Get a configuration value")
    static class GetCmd implements Runnable {

        @Parameters(index = "0", description = "Config key (e.g., ai.provider, defaults.vendor)")
        String key;

        @Inject
        CliConfigService configService;

        @Override
        public void run() {
            CliConfig config = configService.loadConfig();
            String value = getConfigValue(config, key);
            if (value != null) {
                System.out.println(value);
            } else {
                System.err.println("Unknown config key: " + key);
                System.err.println("Available keys: ai.provider, ai.apiKey, ai.apiKeyEnv, ai.model, ai.fallback, "
                        + "defaults.vendor, defaults.idempiereVersion, skills.cacheDir, skills.updateInterval");
            }
        }
    }

    @Command(name = "set", description = "Set a configuration value in global config")
    static class SetCmd implements Runnable {

        @Parameters(index = "0", description = "Config key (e.g., ai.provider)")
        String key;

        @Parameters(index = "1", description = "Value to set")
        String value;

        @Inject
        CliConfigService configService;

        @Override
        public void run() {
            CliConfig config = configService.loadConfig();

            if (!setConfigValue(config, key, value)) {
                System.err.println("Unknown config key: " + key);
                System.err.println("Available keys: ai.provider, ai.apiKey, ai.apiKeyEnv, ai.model, ai.fallback, "
                        + "defaults.vendor, defaults.idempiereVersion, skills.cacheDir, skills.updateInterval");
                return;
            }

            try {
                configService.saveGlobalConfig(config);
                System.out.println("Set " + key + " = " + value);
            } catch (IOException e) {
                System.err.println("Failed to save config: " + e.getMessage());
            }
        }
    }

    @Command(name = "init", description = "Initialize configuration interactively",
            mixinStandardHelpOptions = true)
    static class InitCmd implements Runnable {

        private static final Map<String, String> DEFAULT_API_KEY_ENV = Map.of(
                "anthropic", "ANTHROPIC_API_KEY",
                "openai", "OPENAI_API_KEY",
                "google", "GOOGLE_API_KEY"
        );

        private static final Map<String, String> DEFAULT_MODEL = Map.of(
                "anthropic", "claude-sonnet-4-20250514",
                "openai", "gpt-4o",
                "google", "gemini-2.5-flash"
        );

        @Inject
        CliConfigService configService;

        @Inject
        InteractivePromptService promptService;

        @Override
        public void run() {
            CliConfig config = configService.loadConfig();

            System.out.println();
            System.out.println("iDempiere CLI Configuration");
            System.out.println("===========================");

            // Defaults section
            System.out.println();
            System.out.println("Plugin Defaults:");
            String vendor = promptService.prompt("  Vendor name",
                    config.getDefaults().hasVendor() ? config.getDefaults().getVendor() : null);
            config.getDefaults().setVendor(vendor);

            String version = promptService.prompt("  iDempiere version",
                    String.valueOf(config.getDefaults().getIdempiereVersion()));
            try {
                config.getDefaults().setIdempiereVersion(Integer.parseInt(version));
            } catch (NumberFormatException e) {
                System.out.println("  Invalid version, keeping default: " + config.getDefaults().getIdempiereVersion());
            }

            // AI section
            System.out.println();
            System.out.println("AI-Powered Code Generation (optional):");
            boolean enableAi = promptService.confirm("  Enable AI?", config.getAi().isEnabled());

            if (enableAi) {
                String provider = promptService.prompt("  Provider (anthropic/openai/google)",
                        config.getAi().isEnabled() ? config.getAi().getProvider() : "anthropic");
                config.getAi().setProvider(provider);

                String apiKey = promptService.prompt("  API key",
                        config.getAi().hasApiKey() ? maskKey(config.getAi().getApiKey()) : null);
                // If user kept the masked value, don't overwrite
                if (apiKey != null && !apiKey.contains("***")) {
                    config.getAi().setApiKey(apiKey);
                }

                String defaultModel = DEFAULT_MODEL.getOrDefault(provider, config.getAi().getModel());
                String currentModel = config.getAi().hasModel() ? config.getAi().getModel() : defaultModel;
                String model = promptService.prompt("  Model", currentModel);
                config.getAi().setModel(model);

                // Also set apiKeyEnv for env var override support
                String defaultKeyEnv = DEFAULT_API_KEY_ENV.getOrDefault(provider, "");
                if (!config.getAi().hasApiKeyEnv() && !defaultKeyEnv.isEmpty()) {
                    config.getAi().setApiKeyEnv(defaultKeyEnv);
                }

                // Verify
                System.out.println();
                if (config.getAi().hasApiKey()) {
                    System.out.println("  " + CliOutput.ok("API key configured"));
                } else {
                    System.out.println("  " + CliOutput.warn("No API key provided"));
                    System.out.println("    AI features won't work without an API key.");
                    String envVar = config.getAi().hasApiKeyEnv() ? config.getAi().getApiKeyEnv() : defaultKeyEnv;
                    if (!envVar.isEmpty()) {
                        System.out.println("    You can also set it later via: export " + envVar + "=your-key");
                    }
                }
            } else {
                config.getAi().setProvider("none");
            }

            // Save
            try {
                configService.saveGlobalConfig(config);
                System.out.println();
                System.out.println("Config saved to " + configService.getGlobalConfigPath());
                System.out.println();
            } catch (IOException e) {
                System.err.println("Failed to save config: " + e.getMessage());
            }
        }

        private static String maskKey(String key) {
            if (key == null || key.length() <= 8) return "***";
            return key.substring(0, 4) + "***" + key.substring(key.length() - 4);
        }
    }

    static String getConfigValue(CliConfig config, String key) {
        return switch (key) {
            case "ai.provider" -> config.getAi().getProvider();
            case "ai.apiKey" -> config.getAi().hasApiKey() ? "(set)" : "(not set)";
            case "ai.apiKeyEnv" -> config.getAi().getApiKeyEnv();
            case "ai.model" -> config.getAi().getModel();
            case "ai.fallback" -> config.getAi().getFallback();
            case "defaults.vendor" -> config.getDefaults().getVendor();
            case "defaults.idempiereVersion" -> String.valueOf(config.getDefaults().getIdempiereVersion());
            case "skills.cacheDir" -> config.getSkills().getCacheDir();
            case "skills.updateInterval" -> config.getSkills().getUpdateInterval();
            default -> null;
        };
    }

    static boolean setConfigValue(CliConfig config, String key, String value) {
        switch (key) {
            case "ai.provider" -> config.getAi().setProvider(value);
            case "ai.apiKey" -> config.getAi().setApiKey(value);
            case "ai.apiKeyEnv" -> config.getAi().setApiKeyEnv(value);
            case "ai.model" -> config.getAi().setModel(value);
            case "ai.fallback" -> config.getAi().setFallback(value);
            case "defaults.vendor" -> config.getDefaults().setVendor(value);
            case "defaults.idempiereVersion" -> config.getDefaults().setIdempiereVersion(Integer.parseInt(value));
            case "skills.cacheDir" -> config.getSkills().setCacheDir(value);
            case "skills.updateInterval" -> config.getSkills().setUpdateInterval(value);
            default -> { return false; }
        }
        return true;
    }
}
