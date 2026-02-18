package org.idempiere.cli.model;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Describes an iDempiere plugin project configuration.
 * Used during scaffolding and component generation.
 */
public class PluginDescriptor {

    private String pluginId;
    private String pluginName;
    private String version;
    private String vendor;
    private Set<String> features;
    private PlatformVersion platformVersion;
    private Path outputDir;

    // Multi-module support
    private boolean multiModule = true;  // Default to multi-module
    private boolean withFragment = false;
    private boolean withFeature = false;
    private boolean withTest = true;  // Default to include test module
    private String groupId;
    private String basePluginId;  // e.g., org.example.myplugin.base
    private String fragmentHost = "org.adempiere.ui.zk";  // Default fragment host
    private String projectName;  // Directory name for the project (defaults to pluginName)
    private boolean withEclipseProject = true;  // Generate .project files for Eclipse

    public PluginDescriptor() {
        this.version = "1.0.0.qualifier";
        this.vendor = "";
        this.features = new HashSet<>();
        this.platformVersion = PlatformVersion.stable();
    }

    public PluginDescriptor(String pluginId) {
        this();
        this.pluginId = pluginId;
        this.pluginName = pluginId.substring(pluginId.lastIndexOf('.') + 1);
    }

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public Set<String> getFeatures() {
        return features;
    }

    public void setFeatures(Set<String> features) {
        this.features = features;
    }

    public boolean hasFeature(String feature) {
        return features.contains(feature);
    }

    public void addFeature(String feature) {
        features.add(feature);
    }

    public PlatformVersion getPlatformVersion() {
        return platformVersion;
    }

    public void setPlatformVersion(PlatformVersion platformVersion) {
        this.platformVersion = platformVersion;
    }

    public String getPackagePath() {
        return pluginId.replace('.', '/');
    }

    public Path getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(Path outputDir) {
        this.outputDir = outputDir;
    }

    // Multi-module getters and setters

    public boolean isMultiModule() {
        return multiModule;
    }

    public void setMultiModule(boolean multiModule) {
        this.multiModule = multiModule;
    }

    public boolean isWithFragment() {
        return withFragment;
    }

    public void setWithFragment(boolean withFragment) {
        this.withFragment = withFragment;
    }

    public boolean isWithFeature() {
        return withFeature;
    }

    public void setWithFeature(boolean withFeature) {
        this.withFeature = withFeature;
    }

    public boolean isWithTest() {
        return withTest;
    }

    public void setWithTest(boolean withTest) {
        this.withTest = withTest;
    }

    public String getGroupId() {
        if (groupId == null || groupId.isEmpty()) {
            // Default: use first two segments of pluginId or pluginId itself
            String[] parts = pluginId.split("\\.");
            if (parts.length >= 2) {
                return parts[0] + "." + parts[1];
            }
            return pluginId;
        }
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getBasePluginId() {
        if (basePluginId == null || basePluginId.isEmpty()) {
            // Default: pluginId + ".base"
            return pluginId + ".base";
        }
        return basePluginId;
    }

    public void setBasePluginId(String basePluginId) {
        this.basePluginId = basePluginId;
    }

    public String getFragmentHost() {
        return fragmentHost;
    }

    public void setFragmentHost(String fragmentHost) {
        this.fragmentHost = fragmentHost;
    }

    /**
     * Get the project directory name.
     * Defaults to pluginName (last segment of pluginId) if not explicitly set.
     */
    public String getProjectName() {
        return projectName != null ? projectName : pluginName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public boolean isWithEclipseProject() {
        return withEclipseProject;
    }

    public void setWithEclipseProject(boolean withEclipseProject) {
        this.withEclipseProject = withEclipseProject;
    }

    /**
     * Get the package path for the base plugin (used in multi-module).
     */
    public String getBasePackagePath() {
        return getBasePluginId().replace('.', '/');
    }
}
