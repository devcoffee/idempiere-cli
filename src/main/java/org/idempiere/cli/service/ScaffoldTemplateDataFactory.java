package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.idempiere.cli.model.PlatformVersion;
import org.idempiere.cli.model.PluginDescriptor;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds template data maps used by scaffold generation.
 */
@ApplicationScoped
public class ScaffoldTemplateDataFactory {

    public Map<String, Object> buildMultiModuleData(PluginDescriptor descriptor) {
        Map<String, Object> data = buildPluginData(descriptor);

        // Multi-module specific data
        data.put("groupId", descriptor.getGroupId());
        data.put("basePluginId", descriptor.getBasePluginId());
        data.put("withFragment", descriptor.isWithFragment());
        data.put("withFeature", descriptor.isWithFeature());
        data.put("withTest", descriptor.isWithTest());
        data.put("fragmentHost", descriptor.getFragmentHost());
        data.put("categoryName", descriptor.getPluginId().replace('.', '-'));

        return data;
    }

    public Map<String, Object> buildPluginData(PluginDescriptor descriptor) {
        Map<String, Object> data = new HashMap<>();
        data.put("pluginId", descriptor.getPluginId());
        data.put("pluginName", descriptor.getPluginName());
        data.put("version", descriptor.getVersion());
        data.put("baseVersion", toBaseVersion(descriptor.getVersion()));
        // Maven version: convert OSGi .qualifier to Maven -SNAPSHOT
        data.put("mavenVersion", toMavenVersion(descriptor.getVersion()));
        data.put("vendor", descriptor.getVendor());
        data.put("vendorXml", escapeXml(descriptor.getVendor()));
        data.put("withCallout", descriptor.hasFeature("callout"));
        data.put("withEventHandler", descriptor.hasFeature("event-handler"));
        data.put("withProcess", descriptor.hasFeature("process"));
        data.put("withProcessMapped", descriptor.hasFeature("process-mapped"));
        data.put("withZkForm", descriptor.hasFeature("zk-form"));
        data.put("withZkFormZul", descriptor.hasFeature("zk-form-zul"));
        data.put("withListboxGroup", descriptor.hasFeature("listbox-group"));
        data.put("withWListboxEditor", descriptor.hasFeature("wlistbox-editor"));
        data.put("withReport", descriptor.hasFeature("report"));
        data.put("withJasperReport", descriptor.hasFeature("jasper-report"));
        data.put("withWindowValidator", descriptor.hasFeature("window-validator"));
        data.put("withRestExtension", descriptor.hasFeature("rest-extension"));
        data.put("withFactsValidator", descriptor.hasFeature("facts-validator"));
        data.put("withTest", descriptor.hasFeature("test"));
        // Computed flags for MANIFEST.MF
        boolean needsZk = descriptor.hasFeature("zk-form") || descriptor.hasFeature("zk-form-zul")
                || descriptor.hasFeature("listbox-group") || descriptor.hasFeature("wlistbox-editor")
                || descriptor.hasFeature("window-validator");
        data.put("needsZkBundle", needsZk);
        boolean needsActivator = descriptor.hasFeature("process-mapped") || descriptor.hasFeature("jasper-report");
        data.put("needsActivator", needsActivator);
        data.put("packagePath", descriptor.getPackagePath());

        PlatformVersion pv = descriptor.getPlatformVersion();
        data.put("javaRelease", pv.javaRelease());
        data.put("javaSeVersion", pv.javaSeVersion());
        data.put("tychoVersion", pv.tychoVersion());
        data.put("bundleVersion", pv.bundleVersion());
        data.put("eclipseRepoUrl", pv.eclipseRepoUrl());

        return data;
    }

    /**
     * Convert OSGi version to Maven version.
     * OSGi uses ".qualifier" for snapshot builds, Maven uses "-SNAPSHOT".
     */
    private String toMavenVersion(String osgiVersion) {
        if (osgiVersion == null) return null;
        if (osgiVersion.endsWith(".qualifier")) {
            return osgiVersion.replace(".qualifier", "-SNAPSHOT");
        }
        return osgiVersion;
    }

    private String toBaseVersion(String osgiVersion) {
        if (osgiVersion == null) return null;
        if (osgiVersion.endsWith(".qualifier")) {
            return osgiVersion.substring(0, osgiVersion.length() - ".qualifier".length());
        }
        return osgiVersion;
    }

    private String escapeXml(String value) {
        if (value == null || value.isEmpty()) return value;
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
