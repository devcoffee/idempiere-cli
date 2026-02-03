package org.idempiere.cli.model;

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
}
