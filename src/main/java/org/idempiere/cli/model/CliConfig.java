package org.idempiere.cli.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration model for .idempiere-cli.yaml.
 *
 * <p>This class represents the user's configuration file that can be placed at:
 * <ul>
 *   <li>{@code ~/.idempiere-cli.yaml} - Global user configuration</li>
 *   <li>{@code ./.idempiere-cli.yaml} - Project-specific configuration</li>
 * </ul>
 *
 * <p>Project configuration takes precedence over global configuration.
 *
 * <h2>Example Configuration</h2>
 * <pre>
 * # .idempiere-cli.yaml
 * defaults:
 *   vendor: "My Company Inc."
 *   idempiereVersion: 13
 *
 * templates:
 *   path: ~/.idempiere-cli/templates
 *
 * ai:
 *   provider: anthropic
 *   apiKey: sk-ant-...
 *   apiKeyEnv: ANTHROPIC_API_KEY
 *   model: claude-sonnet-4-20250514
 *   fallback: templates
 *
 * skills:
 *   sources:
 *     - name: official
 *       url: https://github.com/hengsin/idempiere-skills.git
 *       priority: 1
 *   cacheDir: ~/.idempiere-cli/skills
 *   updateInterval: 7d
 * </pre>
 *
 * @see org.idempiere.cli.service.CliConfigService
 */
public class CliConfig {

    private Defaults defaults = new Defaults();
    private Templates templates = new Templates();
    private AiConfig ai = new AiConfig();
    private SkillsConfig skills = new SkillsConfig();

    public Defaults getDefaults() {
        return defaults;
    }

    public void setDefaults(Defaults defaults) {
        this.defaults = defaults;
    }

    public Templates getTemplates() {
        return templates;
    }

    public void setTemplates(Templates templates) {
        this.templates = templates;
    }

    public AiConfig getAi() {
        return ai;
    }

    public void setAi(AiConfig ai) {
        this.ai = ai;
    }

    public SkillsConfig getSkills() {
        return skills;
    }

    public void setSkills(SkillsConfig skills) {
        this.skills = skills;
    }

    /**
     * Default values for plugin scaffolding and other commands.
     */
    public static class Defaults {
        private String vendor;
        private Integer idempiereVersion;

        public String getVendor() {
            return vendor != null ? vendor : "";
        }

        public void setVendor(String vendor) {
            this.vendor = vendor;
        }

        public int getIdempiereVersion() {
            return idempiereVersion != null ? idempiereVersion : 13;
        }

        public void setIdempiereVersion(int idempiereVersion) {
            this.idempiereVersion = idempiereVersion;
        }

        public void setIdempiereVersion(Integer idempiereVersion) {
            this.idempiereVersion = idempiereVersion;
        }

        /**
         * Checks if vendor was explicitly set in config.
         * @return true if vendor is set
         */
        public boolean hasVendor() {
            return vendor != null && !vendor.isEmpty();
        }

        /**
         * Checks if idempiereVersion was explicitly set in config.
         * @return true if idempiereVersion is set
         */
        public boolean hasIdempiereVersion() {
            return idempiereVersion != null;
        }

        /**
         * Merges another Defaults instance into this one.
         * Only explicitly set values from the other instance override this instance.
         *
         * @param other the other Defaults to merge from (project config)
         */
        public void mergeFrom(Defaults other) {
            if (other.hasVendor()) {
                this.vendor = other.vendor;
            }
            if (other.hasIdempiereVersion()) {
                this.idempiereVersion = other.idempiereVersion;
            }
        }
    }

    /**
     * Template configuration for custom template paths.
     */
    public static class Templates {
        private String path;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        /**
         * Checks if a custom template path was set.
         * @return true if path is set
         */
        public boolean hasPath() {
            return path != null && !path.isEmpty();
        }

        /**
         * Merges another Templates instance into this one.
         * @param other the other Templates to merge from
         */
        public void mergeFrom(Templates other) {
            if (other.hasPath()) {
                this.path = other.path;
            }
        }
    }

    /**
     * AI provider configuration for intelligent code generation.
     */
    public static class AiConfig {
        private String provider;
        private String apiKeyEnv;
        private String apiKey;
        private String model;
        private String fallback;

        public String getProvider() {
            return provider != null ? provider : "none";
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getApiKeyEnv() {
            return apiKeyEnv;
        }

        public void setApiKeyEnv(String apiKeyEnv) {
            this.apiKeyEnv = apiKeyEnv;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getFallback() {
            return fallback != null ? fallback : "templates";
        }

        public void setFallback(String fallback) {
            this.fallback = fallback;
        }

        public boolean hasProvider() {
            return provider != null && !provider.isEmpty() && !"none".equals(provider);
        }

        public boolean hasApiKeyEnv() {
            return apiKeyEnv != null && !apiKeyEnv.isEmpty();
        }

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isEmpty();
        }

        public boolean hasModel() {
            return model != null && !model.isEmpty();
        }

        public boolean hasFallback() {
            return fallback != null && !fallback.isEmpty();
        }

        /**
         * Checks if AI is enabled (provider is set and not "none").
         */
        public boolean isEnabled() {
            return hasProvider();
        }

        /**
         * Resolves the effective API key: env var takes precedence over config file.
         *
         * @param defaultEnvVar fallback env var name if apiKeyEnv is not set
         * @return the API key, or null if not available
         */
        public String resolveApiKey(String defaultEnvVar) {
            // 1. Environment variable takes precedence
            String envVar = hasApiKeyEnv() ? apiKeyEnv : defaultEnvVar;
            if (envVar != null) {
                String envValue = System.getenv(envVar);
                if (envValue != null && !envValue.isEmpty()) {
                    return envValue;
                }
            }
            // 2. Fall back to stored apiKey
            return hasApiKey() ? apiKey : null;
        }

        public void mergeFrom(AiConfig other) {
            if (other.hasProvider()) {
                this.provider = other.provider;
            }
            if (other.hasApiKeyEnv()) {
                this.apiKeyEnv = other.apiKeyEnv;
            }
            if (other.hasApiKey()) {
                this.apiKey = other.apiKey;
            }
            if (other.hasModel()) {
                this.model = other.model;
            }
            if (other.hasFallback()) {
                this.fallback = other.fallback;
            }
        }
    }

    /**
     * Skills configuration for AI-powered code generation.
     */
    public static class SkillsConfig {
        private List<SkillSource> sources;
        private String cacheDir;
        private String updateInterval;

        public List<SkillSource> getSources() {
            return sources != null ? sources : new ArrayList<>();
        }

        public void setSources(List<SkillSource> sources) {
            this.sources = sources;
        }

        public String getCacheDir() {
            return cacheDir != null ? cacheDir : "~/.idempiere-cli/skills";
        }

        public void setCacheDir(String cacheDir) {
            this.cacheDir = cacheDir;
        }

        public String getUpdateInterval() {
            return updateInterval != null ? updateInterval : "7d";
        }

        public void setUpdateInterval(String updateInterval) {
            this.updateInterval = updateInterval;
        }

        public boolean hasSources() {
            return sources != null && !sources.isEmpty();
        }

        public boolean hasCacheDir() {
            return cacheDir != null && !cacheDir.isEmpty();
        }

        public boolean hasUpdateInterval() {
            return updateInterval != null && !updateInterval.isEmpty();
        }

        public void mergeFrom(SkillsConfig other) {
            if (other.hasSources()) {
                this.sources = other.sources;
            }
            if (other.hasCacheDir()) {
                this.cacheDir = other.cacheDir;
            }
            if (other.hasUpdateInterval()) {
                this.updateInterval = other.updateInterval;
            }
        }
    }

    /**
     * A skill source — either a remote git repository or a local directory.
     */
    public static class SkillSource {
        private String name;
        private String url;
        private String path;
        private int priority;

        public SkillSource() {}

        public SkillSource(String name, String url, String path, int priority) {
            this.name = name;
            this.url = url;
            this.path = path;
            this.priority = priority;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public boolean isRemote() {
            return url != null && !url.isEmpty();
        }
    }

    /**
     * Merges another CliConfig instance into this one.
     * Values from the other instance override this instance.
     *
     * @param other the other config to merge from (project config)
     */
    public void mergeFrom(CliConfig other) {
        if (other.defaults != null) {
            this.defaults.mergeFrom(other.defaults);
        }
        if (other.templates != null) {
            this.templates.mergeFrom(other.templates);
        }
        if (other.ai != null) {
            this.ai.mergeFrom(other.ai);
        }
        if (other.skills != null) {
            this.skills.mergeFrom(other.skills);
        }
    }
}
