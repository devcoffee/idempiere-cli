package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.model.PlatformVersion;
import org.idempiere.cli.model.PluginDescriptor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Creates new iDempiere plugin projects and adds components to existing plugins.
 */
@ApplicationScoped
public class ScaffoldService {

    @Inject
    TemplateRenderer templateRenderer;

    public void createPlugin(PluginDescriptor descriptor) {
        Path baseDir = Path.of(descriptor.getPluginId());

        if (Files.exists(baseDir)) {
            System.err.println("Error: Directory '" + baseDir + "' already exists.");
            return;
        }

        System.out.println();
        System.out.println("Creating iDempiere plugin: " + descriptor.getPluginId());
        System.out.println("==========================================");
        System.out.println();

        try {
            createDirectoryStructure(baseDir, descriptor);
            generatePluginFiles(baseDir, descriptor);
            generateFeatureFiles(baseDir, descriptor);

            System.out.println();
            System.out.println("Plugin created successfully!");
            System.out.println();
            System.out.println("Next steps:");
            System.out.println("  1. cd " + descriptor.getPluginId());
            System.out.println("  2. Import in Eclipse: File > Import > Maven > Existing Maven Projects");
            System.out.println("  3. Select this directory as root and click Finish");
            System.out.println();
            System.out.println("To build and package for distribution:");
            System.out.println("  idempiere-cli package");
            System.out.println();
        } catch (IOException e) {
            System.err.println("Error creating plugin: " + e.getMessage());
        }
    }

    public void addComponent(String type, String name, Path pluginDir, String pluginId) {
        System.out.println();
        System.out.println("Adding " + type + ": " + name);
        System.out.println();

        try {
            Map<String, Object> data = new HashMap<>();
            data.put("pluginId", pluginId);
            data.put("className", name);

            Path srcDir = pluginDir.resolve("src").resolve(pluginId.replace('.', '/'));

            switch (type) {
                case "callout" -> {
                    templateRenderer.render("callout/Callout.java", data,
                            srcDir.resolve(name + ".java"));
                    templateRenderer.render("callout/CalloutFactory.java", data,
                            srcDir.resolve(name + "Factory.java"));
                }
                case "event-handler" -> {
                    templateRenderer.render("event-handler/EventHandler.java", data,
                            srcDir.resolve(name + ".java"));
                    templateRenderer.render("event-handler/EventManager.java", data,
                            srcDir.resolve(name + "Manager.java"));
                }
                case "process" -> {
                    templateRenderer.render("process/Process.java", data,
                            srcDir.resolve(name + ".java"));
                    templateRenderer.render("process/ProcessFactory.java", data,
                            srcDir.resolve(name + "Factory.java"));
                }
                case "zk-form" -> {
                    templateRenderer.render("zk-form/ZkForm.java", data,
                            srcDir.resolve(name + ".java"));
                    templateRenderer.render("zk-form/FormFactory.java", data,
                            srcDir.resolve(name + "Factory.java"));
                }
                case "report" -> {
                    templateRenderer.render("report/Report.java", data,
                            srcDir.resolve(name + ".java"));
                }
                case "window-validator" -> {
                    templateRenderer.render("window-validator/WindowValidator.java", data,
                            srcDir.resolve(name + ".java"));
                }
                case "facts-validator" -> {
                    templateRenderer.render("facts-validator/FactsValidator.java", data,
                            srcDir.resolve(name + ".java"));
                    ensureEventManager(srcDir, pluginId, name);
                }
                default -> {
                    System.err.println("Unknown component type: " + type);
                    return;
                }
            }

            System.out.println();
            System.out.println("Component added successfully!");
            System.out.println();
        } catch (IOException e) {
            System.err.println("Error adding component: " + e.getMessage());
        }
    }

    public void addRestExtension(String name, String resourcePath, Path pluginDir, String pluginId) {
        System.out.println();
        System.out.println("Adding rest-extension: " + name);
        System.out.println();

        try {
            Map<String, Object> data = new HashMap<>();
            data.put("pluginId", pluginId);
            data.put("className", name);
            data.put("resourcePath", resourcePath);

            Path srcDir = pluginDir.resolve("src").resolve(pluginId.replace('.', '/'));

            templateRenderer.render("rest-extension/ResourceExtension.java", data,
                    srcDir.resolve(name + "ResourceExtension.java"));
            templateRenderer.render("rest-extension/Resource.java", data,
                    srcDir.resolve(name + "Resource.java"));

            System.out.println();
            System.out.println("Component added successfully!");
            System.out.println();
        } catch (IOException e) {
            System.err.println("Error adding component: " + e.getMessage());
        }
    }

    private void ensureEventManager(Path srcDir, String pluginId, String name) throws IOException {
        boolean hasManager = false;
        if (Files.exists(srcDir)) {
            try (var stream = Files.list(srcDir)) {
                hasManager = stream.anyMatch(p -> p.getFileName().toString().endsWith("Manager.java"));
            }
        }
        if (!hasManager) {
            Map<String, Object> data = new HashMap<>();
            data.put("pluginId", pluginId);
            data.put("className", name);
            templateRenderer.render("event-handler/EventManager.java", data,
                    srcDir.resolve(name + "Manager.java"));
        }
    }

    private void createDirectoryStructure(Path baseDir, PluginDescriptor descriptor) throws IOException {
        Files.createDirectories(baseDir.resolve("META-INF"));
        Files.createDirectories(baseDir.resolve("OSGI-INF"));
        Files.createDirectories(baseDir.resolve("src").resolve(descriptor.getPackagePath()));
    }

    private void generatePluginFiles(Path baseDir, PluginDescriptor descriptor) throws IOException {
        Map<String, Object> data = buildPluginData(descriptor);

        templateRenderer.render("plugin/pom.xml", data, baseDir.resolve("pom.xml"));
        templateRenderer.render("plugin/MANIFEST.MF", data, baseDir.resolve("META-INF/MANIFEST.MF"));
        templateRenderer.render("plugin/plugin.xml", data, baseDir.resolve("plugin.xml"));

        // Create build.properties
        Files.writeString(baseDir.resolve("build.properties"),
                "source.. = src/\n" +
                "output.. = bin/\n" +
                "bin.includes = META-INF/,\\\n" +
                "               OSGI-INF/,\\\n" +
                "               .,\\\n" +
                "               plugin.xml\n");
        System.out.println("  Created: " + baseDir.resolve("build.properties"));
    }

    private void generateFeatureFiles(Path baseDir, PluginDescriptor descriptor) throws IOException {
        Path srcDir = baseDir.resolve("src").resolve(descriptor.getPackagePath());
        Map<String, Object> data = new HashMap<>();
        data.put("pluginId", descriptor.getPluginId());

        if (descriptor.hasFeature("callout")) {
            String className = toPascalCase(descriptor.getPluginName()) + "Callout";
            data.put("className", className);
            templateRenderer.render("callout/Callout.java", data, srcDir.resolve(className + ".java"));
            templateRenderer.render("callout/CalloutFactory.java", data, srcDir.resolve(className + "Factory.java"));
        }

        if (descriptor.hasFeature("event-handler")) {
            String className = toPascalCase(descriptor.getPluginName()) + "EventDelegate";
            data.put("className", className);
            templateRenderer.render("event-handler/EventHandler.java", data, srcDir.resolve(className + ".java"));
            String managerName = toPascalCase(descriptor.getPluginName()) + "Event";
            data.put("className", managerName);
            templateRenderer.render("event-handler/EventManager.java", data, srcDir.resolve(managerName + "Manager.java"));
        }

        if (descriptor.hasFeature("process")) {
            String className = toPascalCase(descriptor.getPluginName()) + "Process";
            data.put("className", className);
            templateRenderer.render("process/Process.java", data, srcDir.resolve(className + ".java"));
            templateRenderer.render("process/ProcessFactory.java", data, srcDir.resolve(className + "Factory.java"));
        }

        if (descriptor.hasFeature("zk-form")) {
            String className = toPascalCase(descriptor.getPluginName()) + "Form";
            data.put("className", className);
            templateRenderer.render("zk-form/ZkForm.java", data, srcDir.resolve(className + ".java"));
            templateRenderer.render("zk-form/FormFactory.java", data, srcDir.resolve(className + "Factory.java"));
        }

        if (descriptor.hasFeature("report")) {
            String className = toPascalCase(descriptor.getPluginName()) + "Report";
            data.put("className", className);
            templateRenderer.render("report/Report.java", data, srcDir.resolve(className + ".java"));
        }

        if (descriptor.hasFeature("window-validator")) {
            String className = toPascalCase(descriptor.getPluginName()) + "WindowValidator";
            data.put("className", className);
            templateRenderer.render("window-validator/WindowValidator.java", data, srcDir.resolve(className + ".java"));
        }

        if (descriptor.hasFeature("rest-extension")) {
            String className = toPascalCase(descriptor.getPluginName());
            data.put("className", className);
            data.put("resourcePath", descriptor.getPluginName().toLowerCase());
            templateRenderer.render("rest-extension/ResourceExtension.java", data,
                    srcDir.resolve(className + "ResourceExtension.java"));
            templateRenderer.render("rest-extension/Resource.java", data,
                    srcDir.resolve(className + "Resource.java"));
        }

        if (descriptor.hasFeature("facts-validator")) {
            String className = toPascalCase(descriptor.getPluginName()) + "FactsValidator";
            data.put("className", className);
            templateRenderer.render("facts-validator/FactsValidator.java", data, srcDir.resolve(className + ".java"));
            if (!descriptor.hasFeature("event-handler")) {
                String managerName = toPascalCase(descriptor.getPluginName()) + "Event";
                data.put("className", managerName);
                templateRenderer.render("event-handler/EventManager.java", data, srcDir.resolve(managerName + "Manager.java"));
            }
        }
    }

    private Map<String, Object> buildPluginData(PluginDescriptor descriptor) {
        Map<String, Object> data = new HashMap<>();
        data.put("pluginId", descriptor.getPluginId());
        data.put("pluginName", descriptor.getPluginName());
        data.put("version", descriptor.getVersion());
        data.put("vendor", descriptor.getVendor());
        data.put("withCallout", descriptor.hasFeature("callout"));
        data.put("withEventHandler", descriptor.hasFeature("event-handler"));
        data.put("withProcess", descriptor.hasFeature("process"));
        data.put("withZkForm", descriptor.hasFeature("zk-form"));
        data.put("withReport", descriptor.hasFeature("report"));
        data.put("withWindowValidator", descriptor.hasFeature("window-validator"));
        data.put("withRestExtension", descriptor.hasFeature("rest-extension"));
        data.put("withFactsValidator", descriptor.hasFeature("facts-validator"));

        PlatformVersion pv = descriptor.getPlatformVersion();
        data.put("javaRelease", pv.javaRelease());
        data.put("javaSeVersion", pv.javaSeVersion());
        data.put("tychoVersion", pv.tychoVersion());
        data.put("bundleVersion", pv.bundleVersion());

        return data;
    }

    private String toPascalCase(String input) {
        if (input == null || input.isEmpty()) return input;
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : input.toCharArray()) {
            if (c == '-' || c == '_' || c == '.') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
