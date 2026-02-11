package org.idempiere.cli.model;

import java.util.List;

/**
 * Context gathered from analyzing an existing iDempiere plugin project.
 * Used to provide project-aware information to AI code generation.
 */
public class ProjectContext {

    private final String pluginId;
    private final String basePackage;
    private final String version;
    private final PlatformVersion platformVersion;
    private final boolean multiModule;
    private final List<String> existingClasses;
    private final boolean hasActivator;
    private final boolean hasCalloutFactory;
    private final boolean hasEventManager;
    private final boolean hasProcessFactory;
    private final boolean usesAnnotationPattern;
    private final String manifestContent;
    private final String buildPropertiesContent;
    private final String pomXmlContent;

    private ProjectContext(Builder builder) {
        this.pluginId = builder.pluginId;
        this.basePackage = builder.basePackage;
        this.version = builder.version;
        this.platformVersion = builder.platformVersion;
        this.multiModule = builder.multiModule;
        this.existingClasses = builder.existingClasses != null ? List.copyOf(builder.existingClasses) : List.of();
        this.hasActivator = builder.hasActivator;
        this.hasCalloutFactory = builder.hasCalloutFactory;
        this.hasEventManager = builder.hasEventManager;
        this.hasProcessFactory = builder.hasProcessFactory;
        this.usesAnnotationPattern = builder.usesAnnotationPattern;
        this.manifestContent = builder.manifestContent;
        this.buildPropertiesContent = builder.buildPropertiesContent;
        this.pomXmlContent = builder.pomXmlContent;
    }

    public String getPluginId() { return pluginId; }
    public String getBasePackage() { return basePackage; }
    public String getVersion() { return version; }
    public PlatformVersion getPlatformVersion() { return platformVersion; }
    public boolean isMultiModule() { return multiModule; }
    public List<String> getExistingClasses() { return existingClasses; }
    public boolean hasActivator() { return hasActivator; }
    public boolean hasCalloutFactory() { return hasCalloutFactory; }
    public boolean hasEventManager() { return hasEventManager; }
    public boolean hasProcessFactory() { return hasProcessFactory; }
    public boolean usesAnnotationPattern() { return usesAnnotationPattern; }
    public String getManifestContent() { return manifestContent; }
    public String getBuildPropertiesContent() { return buildPropertiesContent; }
    public String getPomXmlContent() { return pomXmlContent; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String pluginId;
        private String basePackage;
        private String version;
        private PlatformVersion platformVersion;
        private boolean multiModule;
        private List<String> existingClasses;
        private boolean hasActivator;
        private boolean hasCalloutFactory;
        private boolean hasEventManager;
        private boolean hasProcessFactory;
        private boolean usesAnnotationPattern;
        private String manifestContent;
        private String buildPropertiesContent;
        private String pomXmlContent;

        public Builder pluginId(String pluginId) { this.pluginId = pluginId; return this; }
        public Builder basePackage(String basePackage) { this.basePackage = basePackage; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder platformVersion(PlatformVersion platformVersion) { this.platformVersion = platformVersion; return this; }
        public Builder multiModule(boolean multiModule) { this.multiModule = multiModule; return this; }
        public Builder existingClasses(List<String> existingClasses) { this.existingClasses = existingClasses; return this; }
        public Builder hasActivator(boolean hasActivator) { this.hasActivator = hasActivator; return this; }
        public Builder hasCalloutFactory(boolean hasCalloutFactory) { this.hasCalloutFactory = hasCalloutFactory; return this; }
        public Builder hasEventManager(boolean hasEventManager) { this.hasEventManager = hasEventManager; return this; }
        public Builder hasProcessFactory(boolean hasProcessFactory) { this.hasProcessFactory = hasProcessFactory; return this; }
        public Builder usesAnnotationPattern(boolean usesAnnotationPattern) { this.usesAnnotationPattern = usesAnnotationPattern; return this; }
        public Builder manifestContent(String manifestContent) { this.manifestContent = manifestContent; return this; }
        public Builder buildPropertiesContent(String buildPropertiesContent) { this.buildPropertiesContent = buildPropertiesContent; return this; }
        public Builder pomXmlContent(String pomXmlContent) { this.pomXmlContent = pomXmlContent; return this; }

        public ProjectContext build() {
            return new ProjectContext(this);
        }
    }
}
