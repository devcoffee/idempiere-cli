package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.model.CliConfig;
import org.idempiere.cli.service.CliConfigService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;

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
                ConfigCommand.SetCmd.class
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
            System.out.println("  apiKeyEnv: " + (config.getAi().getApiKeyEnv() != null ? config.getAi().getApiKeyEnv() : "(not set)"));
            String apiKeyEnv = config.getAi().getApiKeyEnv();
            if (apiKeyEnv != null && System.getenv(apiKeyEnv) != null) {
                System.out.println("  apiKey: (set via " + apiKeyEnv + ")");
            } else if (apiKeyEnv != null) {
                System.out.println("  apiKey: (NOT set - export " + apiKeyEnv + "=...)");
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
                System.err.println("Available keys: ai.provider, ai.apiKeyEnv, ai.model, ai.fallback, "
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
                System.err.println("Available keys: ai.provider, ai.apiKeyEnv, ai.model, ai.fallback, "
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

    static String getConfigValue(CliConfig config, String key) {
        return switch (key) {
            case "ai.provider" -> config.getAi().getProvider();
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
