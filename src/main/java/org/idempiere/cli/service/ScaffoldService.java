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

    @Inject
    ManifestService manifestService;

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
            System.out.println("To build:");
            System.out.println("  idempiere-cli build");
            System.out.println();
            System.out.println("To package for distribution:");
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
                case "process-mapped" -> {
                    // Process template expects className to be the full class name
                    data.put("className", name + "Process");
                    templateRenderer.render("process/Process.java", data,
                            srcDir.resolve(name + "Process.java"));
                    // Activator template adds "Activator" suffix
                    data.put("className", name);
                    templateRenderer.render("process-mapped/Activator.java", data,
                            srcDir.resolve(name + "Activator.java"));
                    System.out.println();
                    System.out.println("  Note: Make sure your plugin has an Activator that extends Incremental2PackActivator");
                    System.out.println("  and calls Core.getMappedProcessFactory().scan() in start().");
                }
                case "listbox-group" -> {
                    // Form references Model and Renderer
                    data.put("className", name + "Form");
                    data.put("modelClassName", name + "GroupModel");
                    data.put("rendererClassName", name + "GroupRenderer");
                    templateRenderer.render("listbox-group/ListboxGroupForm.java", data,
                            srcDir.resolve(name + "Form.java"));
                    // Model class
                    data.put("className", name + "GroupModel");
                    templateRenderer.render("listbox-group/GroupModel.java", data,
                            srcDir.resolve(name + "GroupModel.java"));
                    // Renderer class
                    data.put("className", name + "GroupRenderer");
                    templateRenderer.render("listbox-group/GroupRenderer.java", data,
                            srcDir.resolve(name + "GroupRenderer.java"));
                }
                case "wlistbox-editor" -> {
                    data.put("className", name + "Form");
                    templateRenderer.render("wlistbox-editor/WListboxEditorForm.java", data,
                            srcDir.resolve(name + "Form.java"));
                }
                case "base-test" -> {
                    data.put("pluginName", extractPluginName(pluginId));
                    templateRenderer.render("test/PluginTest.java", data,
                            srcDir.resolve(name + ".java"));
                }
                default -> {
                    System.err.println("Unknown component type: " + type);
                    return;
                }
            }

            // Update MANIFEST.MF with required bundles
            manifestService.addRequiredBundles(pluginDir, type);

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
        Files.createDirectories(baseDir.resolve(".mvn"));
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

        // Create .mvn/jvm.config to increase XML parser limits for large p2 repositories
        Files.writeString(baseDir.resolve(".mvn/jvm.config"),
                "-Djdk.xml.maxGeneralEntitySizeLimit=0\n" +
                "-Djdk.xml.totalEntitySizeLimit=0\n");
        System.out.println("  Created: " + baseDir.resolve(".mvn/jvm.config"));
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

        if (descriptor.hasFeature("process-mapped")) {
            String baseName = toPascalCase(descriptor.getPluginName());
            // Process template expects className to be the full class name
            data.put("className", baseName + "Process");
            templateRenderer.render("process/Process.java", data, srcDir.resolve(baseName + "Process.java"));
            // Activator template adds "Activator" suffix
            data.put("className", baseName);
            templateRenderer.render("process-mapped/Activator.java", data, srcDir.resolve(baseName + "Activator.java"));
        }

        if (descriptor.hasFeature("zk-form-zul")) {
            String baseName = toPascalCase(descriptor.getPluginName());
            data.put("pluginName", descriptor.getPluginName());
            // Form class
            data.put("className", baseName + "Form");
            templateRenderer.render("zk-form-zul/Form.java", data, srcDir.resolve(baseName + "Form.java"));
            // Controller class references the Form
            data.put("className", baseName + "FormController");
            data.put("formClassName", baseName + "Form");
            templateRenderer.render("zk-form-zul/FormController.java", data, srcDir.resolve(baseName + "FormController.java"));
            // Create web folder and ZUL file (copy without Qute processing due to ${} syntax conflict)
            Path webDir = baseDir.resolve("src/web");
            Files.createDirectories(webDir);
            templateRenderer.copyResource("zk-form-zul/form.zul", webDir.resolve("form.zul"));
        }

        if (descriptor.hasFeature("listbox-group")) {
            String baseName = toPascalCase(descriptor.getPluginName());
            // Form references Model and Renderer
            data.put("className", baseName + "ListboxGroupForm");
            data.put("modelClassName", baseName + "GroupModel");
            data.put("rendererClassName", baseName + "GroupRenderer");
            templateRenderer.render("listbox-group/ListboxGroupForm.java", data, srcDir.resolve(baseName + "ListboxGroupForm.java"));
            // Model class
            data.put("className", baseName + "GroupModel");
            templateRenderer.render("listbox-group/GroupModel.java", data, srcDir.resolve(baseName + "GroupModel.java"));
            // Renderer class
            data.put("className", baseName + "GroupRenderer");
            templateRenderer.render("listbox-group/GroupRenderer.java", data, srcDir.resolve(baseName + "GroupRenderer.java"));
        }

        if (descriptor.hasFeature("wlistbox-editor")) {
            String baseName = toPascalCase(descriptor.getPluginName());
            data.put("className", baseName + "WListboxEditorForm");
            templateRenderer.render("wlistbox-editor/WListboxEditorForm.java", data, srcDir.resolve(baseName + "WListboxEditorForm.java"));
        }

        if (descriptor.hasFeature("jasper-report")) {
            String baseName = toPascalCase(descriptor.getPluginName());
            data.put("pluginName", descriptor.getPluginName());
            // Activator class
            data.put("className", baseName + "ReportActivator");
            templateRenderer.render("jasper-report/Activator.java", data, srcDir.resolve(baseName + "ReportActivator.java"));
            // Create reports folder and sample .jrxml
            Path reportsDir = baseDir.resolve("reports");
            Files.createDirectories(reportsDir);
            data.put("className", baseName + "Report");
            templateRenderer.render("jasper-report/SampleReport.jrxml", data, reportsDir.resolve(baseName + "Report.jrxml"));
        }

        if (descriptor.hasFeature("test")) {
            String baseName = toPascalCase(descriptor.getPluginName());
            data.put("className", baseName + "Test");
            data.put("pluginName", descriptor.getPluginName());
            templateRenderer.render("test/PluginTest.java", data, srcDir.resolve(baseName + "Test.java"));
        }
    }

    private Map<String, Object> buildPluginData(PluginDescriptor descriptor) {
        Map<String, Object> data = new HashMap<>();
        data.put("pluginId", descriptor.getPluginId());
        data.put("pluginName", descriptor.getPluginName());
        data.put("version", descriptor.getVersion());
        // Maven version: convert OSGi .qualifier to Maven -SNAPSHOT
        data.put("mavenVersion", toMavenVersion(descriptor.getVersion()));
        data.put("vendor", descriptor.getVendor());
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

    /**
     * Extract the simple plugin name from a full plugin ID.
     * e.g., "org.example.myplugin" -> "myplugin"
     */
    private String extractPluginName(String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) return pluginId;
        int lastDot = pluginId.lastIndexOf('.');
        return lastDot >= 0 ? pluginId.substring(lastDot + 1) : pluginId;
    }

    /**
     * Add a ZUL-based form with separate .zul file and Controller.
     */
    public void addZkFormZul(String name, Path pluginDir, String pluginId) {
        System.out.println();
        System.out.println("Adding zk-form-zul: " + name);
        System.out.println();

        try {
            Map<String, Object> data = new HashMap<>();
            data.put("pluginId", pluginId);
            data.put("pluginName", extractPluginName(pluginId));

            Path srcDir = pluginDir.resolve("src").resolve(pluginId.replace('.', '/'));

            // Form class - use name as-is (user should include "Form" suffix if desired)
            data.put("className", name);
            templateRenderer.render("zk-form-zul/Form.java", data,
                    srcDir.resolve(name + ".java"));
            // Controller class references the Form
            data.put("className", name + "Controller");
            data.put("formClassName", name);
            templateRenderer.render("zk-form-zul/FormController.java", data,
                    srcDir.resolve(name + "Controller.java"));

            // Create web folder and ZUL file (copy without Qute processing due to ${} syntax conflict)
            Path webDir = pluginDir.resolve("src/web");
            Files.createDirectories(webDir);
            String zulFileName = name.toLowerCase() + ".zul";
            templateRenderer.copyResource("zk-form-zul/form.zul", webDir.resolve(zulFileName));

            // Update MANIFEST.MF with required bundles
            manifestService.addRequiredBundles(pluginDir, "zk-form-zul");

            System.out.println();
            System.out.println("Component added successfully!");
            System.out.println();
        } catch (IOException e) {
            System.err.println("Error adding component: " + e.getMessage());
        }
    }

    /**
     * Add a Jasper report with Activator and sample .jrxml file.
     */
    public void addJasperReport(String name, Path pluginDir, String pluginId) {
        System.out.println();
        System.out.println("Adding jasper-report: " + name);
        System.out.println();

        try {
            Map<String, Object> data = new HashMap<>();
            data.put("pluginId", pluginId);
            data.put("pluginName", extractPluginName(pluginId));

            Path srcDir = pluginDir.resolve("src").resolve(pluginId.replace('.', '/'));

            // Activator class - user's name + "Activator" suffix
            data.put("className", name + "Activator");
            templateRenderer.render("jasper-report/Activator.java", data,
                    srcDir.resolve(name + "Activator.java"));

            // Create reports folder and sample .jrxml - use name directly
            Path reportsDir = pluginDir.resolve("reports");
            Files.createDirectories(reportsDir);
            data.put("className", name);
            templateRenderer.render("jasper-report/SampleReport.jrxml", data,
                    reportsDir.resolve(name + ".jrxml"));

            // Update MANIFEST.MF with required bundles
            manifestService.addRequiredBundles(pluginDir, "jasper-report");

            System.out.println();
            System.out.println("Component added successfully!");
            System.out.println();
        } catch (IOException e) {
            System.err.println("Error adding component: " + e.getMessage());
        }
    }

    /**
     * Add a base test class using AbstractTestCase.
     */
    public void addBaseTest(String name, Path pluginDir, String pluginId) {
        System.out.println();
        System.out.println("Adding base-test: " + name);
        System.out.println();

        try {
            Map<String, Object> data = new HashMap<>();
            data.put("pluginId", pluginId);
            data.put("className", name);
            data.put("pluginName", extractPluginName(pluginId));

            Path srcDir = pluginDir.resolve("src").resolve(pluginId.replace('.', '/'));

            templateRenderer.render("test/PluginTest.java", data,
                    srcDir.resolve(name + ".java"));

            // Update MANIFEST.MF with required bundles
            manifestService.addRequiredBundles(pluginDir, "base-test");

            System.out.println();
            System.out.println("Test class added successfully!");
            System.out.println();
        } catch (IOException e) {
            System.err.println("Error adding component: " + e.getMessage());
        }
    }
}
