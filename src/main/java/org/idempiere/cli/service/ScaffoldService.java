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
import java.util.regex.Pattern;

/**
 * Creates new iDempiere plugin projects and adds components to existing plugins.
 *
 * <h2>Shared Components</h2>
 * <p>Some components share infrastructure classes that only need to exist once per plugin:</p>
 * <ul>
 *   <li><b>CalloutFactory</b> - One {@code AnnotationBasedColumnCalloutFactory} per plugin handles
 *       all callouts. Additional callouts are automatically discovered via package scanning.</li>
 *   <li><b>Activator</b> - One {@code Incremental2PackActivator} per plugin is shared between
 *       process-mapped and jasper-report components for 2Pack support.</li>
 * </ul>
 *
 * <p>When adding components, this service detects existing shared infrastructure by scanning
 * file contents (not names), ensuring compatibility with custom-named classes.</p>
 *
 * <h2>File Protection</h2>
 * <p>Existing files are never overwritten. When a file already exists, it is skipped with
 * a warning message.</p>
 */
@ApplicationScoped
public class ScaffoldService {

    @Inject
    TemplateRenderer templateRenderer;

    @Inject
    ManifestService manifestService;

    public void createPlugin(PluginDescriptor descriptor) {
        if (descriptor.isMultiModule()) {
            createMultiModulePlugin(descriptor);
        } else {
            createStandalonePlugin(descriptor);
        }
    }

    /**
     * Creates a multi-module project structure:
     * - root pom.xml (aggregator)
     * - parent module (with Tycho config)
     * - base plugin module
     * - test module (optional)
     * - p2 site module
     * - fragment module (optional)
     * - feature module (optional)
     */
    private void createMultiModulePlugin(PluginDescriptor descriptor) {
        Path rootDir;
        if (descriptor.getOutputDir() != null) {
            rootDir = descriptor.getOutputDir().resolve(descriptor.getPluginId());
        } else {
            rootDir = Path.of(descriptor.getPluginId());
        }

        if (Files.exists(rootDir)) {
            System.err.println("Error: Directory '" + rootDir + "' already exists.");
            return;
        }

        System.out.println();
        System.out.println("Creating iDempiere multi-module project: " + descriptor.getPluginId());
        System.out.println("==========================================");
        System.out.println();

        try {
            // Create root directory
            Files.createDirectories(rootDir);

            // Build template data
            Map<String, Object> data = buildMultiModuleData(descriptor);

            // Create root pom.xml
            templateRenderer.render("multi-module/root-pom.xml", data, rootDir.resolve("pom.xml"));

            // Create parent module
            Path parentDir = rootDir.resolve(descriptor.getPluginId() + ".parent");
            Files.createDirectories(parentDir);
            templateRenderer.render("multi-module/parent-pom.xml", data, parentDir.resolve("pom.xml"));

            // Create base plugin module
            Path pluginDir = rootDir.resolve(descriptor.getBasePluginId());
            createPluginModule(pluginDir, descriptor, data);

            // Create test module (if enabled)
            if (descriptor.isWithTest()) {
                Path testDir = rootDir.resolve(descriptor.getBasePluginId() + ".test");
                createTestModule(testDir, descriptor, data);
            }

            // Create fragment module (if enabled)
            if (descriptor.isWithFragment()) {
                Path fragmentDir = rootDir.resolve(descriptor.getPluginId() + ".fragment");
                createFragmentModule(fragmentDir, descriptor, data);
            }

            // Create feature module (if enabled)
            if (descriptor.isWithFeature()) {
                Path featureDir = rootDir.resolve(descriptor.getPluginId() + ".feature");
                createFeatureModule(featureDir, descriptor, data);
            }

            // Create p2 site module
            Path p2Dir = rootDir.resolve(descriptor.getPluginId() + ".p2");
            createP2Module(p2Dir, descriptor, data);

            // Create .mvn/jvm.config at root
            Path mvnDir = rootDir.resolve(".mvn");
            Files.createDirectories(mvnDir);
            Files.writeString(mvnDir.resolve("jvm.config"),
                    "-Djdk.xml.maxGeneralEntitySizeLimit=0\n" +
                    "-Djdk.xml.totalEntitySizeLimit=0\n");
            System.out.println("  Created: " + mvnDir.resolve("jvm.config"));

            System.out.println();
            System.out.println("Multi-module project created successfully!");
            System.out.println();
            System.out.println("Structure:");
            System.out.println("  " + descriptor.getPluginId() + "/");
            System.out.println("  ├── " + descriptor.getPluginId() + ".parent/    (Maven parent)");
            System.out.println("  ├── " + descriptor.getBasePluginId() + "/    (Main plugin)");
            if (descriptor.isWithTest()) {
                System.out.println("  ├── " + descriptor.getBasePluginId() + ".test/    (Tests)");
            }
            if (descriptor.isWithFragment()) {
                System.out.println("  ├── " + descriptor.getPluginId() + ".fragment/    (Fragment)");
            }
            if (descriptor.isWithFeature()) {
                System.out.println("  ├── " + descriptor.getPluginId() + ".feature/    (Feature)");
            }
            System.out.println("  └── " + descriptor.getPluginId() + ".p2/    (P2 repository)");
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
            System.out.println("  idempiere-cli package --format=p2");
            System.out.println();
        } catch (IOException e) {
            System.err.println("Error creating project: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Creates a standalone single plugin (original behavior).
     */
    private void createStandalonePlugin(PluginDescriptor descriptor) {
        Path baseDir;
        if (descriptor.getOutputDir() != null) {
            baseDir = descriptor.getOutputDir().resolve(descriptor.getPluginId());
        } else {
            baseDir = Path.of(descriptor.getPluginId());
        }

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
            generateComponentFiles(baseDir, descriptor);

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

    /**
     * Create the main plugin module within multi-module structure.
     */
    private void createPluginModule(Path pluginDir, PluginDescriptor descriptor, Map<String, Object> data) throws IOException {
        Files.createDirectories(pluginDir.resolve("META-INF"));
        Files.createDirectories(pluginDir.resolve("OSGI-INF"));
        Files.createDirectories(pluginDir.resolve("src").resolve(descriptor.getBasePackagePath()));

        templateRenderer.render("multi-module/plugin-pom.xml", data, pluginDir.resolve("pom.xml"));
        templateRenderer.render("plugin/MANIFEST.MF", data, pluginDir.resolve("META-INF/MANIFEST.MF"));
        templateRenderer.render("plugin/plugin.xml", data, pluginDir.resolve("plugin.xml"));

        // Create build.properties
        Files.writeString(pluginDir.resolve("build.properties"),
                "source.. = src/\n" +
                "output.. = bin/\n" +
                "bin.includes = META-INF/,\\\n" +
                "               OSGI-INF/,\\\n" +
                "               .,\\\n" +
                "               plugin.xml\n");
        System.out.println("  Created: " + pluginDir.resolve("build.properties"));

        // Generate component files (callout, process, etc.) in the plugin
        generateComponentFiles(pluginDir, descriptor);
    }

    /**
     * Create the test module within multi-module structure.
     */
    private void createTestModule(Path testDir, PluginDescriptor descriptor, Map<String, Object> data) throws IOException {
        Files.createDirectories(testDir.resolve("META-INF"));
        Files.createDirectories(testDir.resolve("src").resolve(descriptor.getBasePackagePath()));

        templateRenderer.render("multi-module/test-pom.xml", data, testDir.resolve("pom.xml"));
        templateRenderer.render("multi-module/test-MANIFEST.MF", data, testDir.resolve("META-INF/MANIFEST.MF"));

        // Create build.properties
        Files.writeString(testDir.resolve("build.properties"),
                "source.. = src/\n" +
                "output.. = bin/\n" +
                "bin.includes = META-INF/,\\\n" +
                "               .\n");
        System.out.println("  Created: " + testDir.resolve("build.properties"));

        // Create a sample test class
        Path srcDir = testDir.resolve("src").resolve(descriptor.getBasePackagePath());
        String baseName = toPascalCase(descriptor.getPluginName());
        Map<String, Object> testData = new HashMap<>(data);
        testData.put("className", baseName + "Test");
        testData.put("pluginName", descriptor.getPluginName());
        testData.put("pluginId", descriptor.getBasePluginId());
        templateRenderer.render("test/PluginTest.java", testData, srcDir.resolve(baseName + "Test.java"));
    }

    /**
     * Create the fragment module within multi-module structure.
     */
    private void createFragmentModule(Path fragmentDir, PluginDescriptor descriptor, Map<String, Object> data) throws IOException {
        Files.createDirectories(fragmentDir.resolve("META-INF"));
        Files.createDirectories(fragmentDir.resolve("src"));

        templateRenderer.render("fragment/pom.xml", data, fragmentDir.resolve("pom.xml"));
        templateRenderer.render("fragment/MANIFEST.MF", data, fragmentDir.resolve("META-INF/MANIFEST.MF"));

        // Create build.properties
        Files.writeString(fragmentDir.resolve("build.properties"),
                "source.. = src/\n" +
                "output.. = bin/\n" +
                "bin.includes = META-INF/,\\\n" +
                "               .\n");
        System.out.println("  Created: " + fragmentDir.resolve("build.properties"));
    }

    /**
     * Create the feature module within multi-module structure.
     */
    private void createFeatureModule(Path featureDir, PluginDescriptor descriptor, Map<String, Object> data) throws IOException {
        Files.createDirectories(featureDir);

        templateRenderer.render("feature/pom.xml", data, featureDir.resolve("pom.xml"));
        templateRenderer.render("feature/feature.xml", data, featureDir.resolve("feature.xml"));

        // Create build.properties
        Files.writeString(featureDir.resolve("build.properties"),
                "bin.includes = feature.xml\n");
        System.out.println("  Created: " + featureDir.resolve("build.properties"));
    }

    /**
     * Create the P2 repository module within multi-module structure.
     */
    private void createP2Module(Path p2Dir, PluginDescriptor descriptor, Map<String, Object> data) throws IOException {
        Files.createDirectories(p2Dir);

        templateRenderer.render("multi-module/p2-pom.xml", data, p2Dir.resolve("pom.xml"));
        templateRenderer.render("multi-module/category.xml", data, p2Dir.resolve("category.xml"));
    }

    /**
     * Build template data for multi-module project.
     */
    private Map<String, Object> buildMultiModuleData(PluginDescriptor descriptor) {
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

    /**
     * Generate component files (callout, process, etc.) - renamed from generateFeatureFiles.
     */
    private void generateComponentFiles(Path baseDir, PluginDescriptor descriptor) throws IOException {
        Path srcDir;
        String pluginIdForPackage;
        if (descriptor.isMultiModule()) {
            srcDir = baseDir.resolve("src").resolve(descriptor.getBasePackagePath());
            pluginIdForPackage = descriptor.getBasePluginId();
        } else {
            srcDir = baseDir.resolve("src").resolve(descriptor.getPackagePath());
            pluginIdForPackage = descriptor.getPluginId();
        }

        Map<String, Object> data = new HashMap<>();
        data.put("pluginId", pluginIdForPackage);

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
            data.put("className", baseName + "Process");
            templateRenderer.render("process/Process.java", data, srcDir.resolve(baseName + "Process.java"));
            data.put("className", baseName + "Activator");
            templateRenderer.render("process-mapped/Activator.java", data, srcDir.resolve(baseName + "Activator.java"));
        }

        if (descriptor.hasFeature("zk-form-zul")) {
            String baseName = toPascalCase(descriptor.getPluginName());
            data.put("pluginName", descriptor.getPluginName());
            data.put("className", baseName + "ZulForm");
            templateRenderer.render("zk-form-zul/Form.java", data, srcDir.resolve(baseName + "ZulForm.java"));
            data.put("className", baseName + "ZulFormController");
            data.put("formClassName", baseName + "ZulForm");
            templateRenderer.render("zk-form-zul/FormController.java", data, srcDir.resolve(baseName + "ZulFormController.java"));
            Path webDir = baseDir.resolve("src/web");
            Files.createDirectories(webDir);
            templateRenderer.copyResource("zk-form-zul/form.zul", webDir.resolve("form.zul"));
        }

        if (descriptor.hasFeature("listbox-group")) {
            String baseName = toPascalCase(descriptor.getPluginName());
            data.put("className", baseName + "ListboxGroupForm");
            data.put("modelClassName", baseName + "GroupModel");
            data.put("rendererClassName", baseName + "GroupRenderer");
            templateRenderer.render("listbox-group/ListboxGroupForm.java", data, srcDir.resolve(baseName + "ListboxGroupForm.java"));
            data.put("className", baseName + "GroupModel");
            templateRenderer.render("listbox-group/GroupModel.java", data, srcDir.resolve(baseName + "GroupModel.java"));
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
            if (!descriptor.hasFeature("process-mapped")) {
                data.put("className", baseName + "Activator");
                templateRenderer.render("jasper-report/Activator.java", data, srcDir.resolve(baseName + "Activator.java"));
            }
            Path reportsDir = baseDir.resolve("reports");
            Files.createDirectories(reportsDir);
            data.put("className", baseName + "Report");
            templateRenderer.render("jasper-report/SampleReport.jrxml", data, reportsDir.resolve(baseName + "Report.jrxml"));
        }

        // For standalone plugins with test feature
        if (!descriptor.isMultiModule() && descriptor.hasFeature("test")) {
            String baseName = toPascalCase(descriptor.getPluginName());
            data.put("className", baseName + "Test");
            data.put("pluginName", descriptor.getPluginName());
            templateRenderer.render("test/PluginTest.java", data, srcDir.resolve(baseName + "Test.java"));
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
                    // Only create factory if none exists (one factory handles all callouts in package)
                    if (!hasCalloutFactory(srcDir)) {
                        String pluginBaseName = toPascalCase(extractPluginName(pluginId));
                        data.put("className", pluginBaseName + "Callout");
                        templateRenderer.render("callout/CalloutFactory.java", data,
                                srcDir.resolve(pluginBaseName + "CalloutFactory.java"));
                        data.put("className", name); // restore
                    } else {
                        System.out.println("  Using existing CalloutFactory (scans package for all @Callout classes)");
                    }
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
                    // Only create Activator if none exists (shared by process-mapped and jasper-report)
                    if (!hasPluginActivator(srcDir)) {
                        String pluginBaseName = toPascalCase(extractPluginName(pluginId));
                        data.put("className", pluginBaseName + "Activator");
                        templateRenderer.render("process-mapped/Activator.java", data,
                                srcDir.resolve(pluginBaseName + "Activator.java"));
                    } else {
                        System.out.println("  Using existing Activator (shared by process-mapped and jasper-report)");
                    }
                    data.put("className", name); // restore
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
     * Check if a shared component already exists in the source directory.
     * Searches by content patterns to detect the type regardless of file name.
     */
    private boolean hasSharedComponent(Path srcDir, String... contentPatterns) {
        if (!Files.exists(srcDir)) {
            return false;
        }
        try (var files = Files.list(srcDir)) {
            return files
                    .filter(f -> f.getFileName().toString().endsWith(".java"))
                    .anyMatch(f -> containsAnyPattern(f, contentPatterns));
        } catch (IOException e) {
            return false;
        }
    }

    private boolean containsAnyPattern(Path file, String... patterns) {
        try {
            String content = Files.readString(file);
            for (String pattern : patterns) {
                if (content.contains(pattern)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    // Convenience methods for specific shared components
    private boolean hasCalloutFactory(Path srcDir) {
        return hasSharedComponent(srcDir, "AnnotationBasedColumnCalloutFactory", "IColumnCalloutFactory");
    }

    private boolean hasPluginActivator(Path srcDir) {
        return hasSharedComponent(srcDir, "Incremental2PackActivator");
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

    // ========================================================================
    // Methods for adding modules to existing multi-module projects
    // ========================================================================

    /**
     * Adds a new plugin module to an existing multi-module project.
     *
     * @param rootDir the multi-module project root directory
     * @param pluginId the full plugin ID (e.g., org.example.myplugin.newplugin)
     * @param descriptor optional descriptor with settings (version, vendor, etc.)
     */
    public void addPluginModuleToProject(Path rootDir, String pluginId, PluginDescriptor descriptor) {
        System.out.println();
        System.out.println("Adding plugin module: " + pluginId);
        System.out.println();

        try {
            Path pluginDir = rootDir.resolve(pluginId);
            if (Files.exists(pluginDir)) {
                System.err.println("Error: Directory '" + pluginDir + "' already exists.");
                return;
            }

            // Build template data
            Map<String, Object> data = buildModuleData(rootDir, pluginId, descriptor);

            // Create plugin module
            createPluginModule(pluginDir, descriptor, data);

            // Update root pom.xml to include new module
            updateRootPomModules(rootDir, pluginId);

            // Update category.xml if p2 module exists
            updateCategoryXml(rootDir, pluginId, false);

            System.out.println();
            System.out.println("Plugin module added successfully!");
            System.out.println();
            System.out.println("Next steps:");
            System.out.println("  1. Refresh Maven project in Eclipse");
            System.out.println("  2. The module will be included in the build automatically");
            System.out.println();
        } catch (IOException e) {
            System.err.println("Error adding plugin module: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Adds a fragment module to an existing multi-module project.
     *
     * @param rootDir the multi-module project root directory
     * @param fragmentHost the fragment host bundle (e.g., org.adempiere.ui.zk)
     * @param descriptor optional descriptor with settings
     */
    public void addFragmentModuleToProject(Path rootDir, String fragmentHost, PluginDescriptor descriptor) {
        String fragmentId = descriptor.getPluginId() + ".fragment";
        System.out.println();
        System.out.println("Adding fragment module: " + fragmentId);
        System.out.println("Fragment host: " + fragmentHost);
        System.out.println();

        try {
            Path fragmentDir = rootDir.resolve(fragmentId);
            if (Files.exists(fragmentDir)) {
                System.err.println("Error: Directory '" + fragmentDir + "' already exists.");
                return;
            }

            // Build template data
            Map<String, Object> data = buildModuleData(rootDir, fragmentId, descriptor);
            data.put("fragmentHost", fragmentHost);

            // Create fragment module
            createFragmentModule(fragmentDir, descriptor, data);

            // Update root pom.xml to include new module
            updateRootPomModules(rootDir, fragmentId);

            // Update category.xml if p2 module exists
            updateCategoryXml(rootDir, fragmentId, false);

            // Update feature.xml if feature module exists
            updateFeatureXml(rootDir, fragmentId, true);

            System.out.println();
            System.out.println("Fragment module added successfully!");
            System.out.println();
        } catch (IOException e) {
            System.err.println("Error adding fragment module: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Adds a feature module to an existing multi-module project.
     *
     * @param rootDir the multi-module project root directory
     * @param descriptor optional descriptor with settings
     */
    public void addFeatureModuleToProject(Path rootDir, PluginDescriptor descriptor) {
        String featureId = descriptor.getPluginId() + ".feature";
        System.out.println();
        System.out.println("Adding feature module: " + featureId);
        System.out.println();

        try {
            Path featureDir = rootDir.resolve(featureId);
            if (Files.exists(featureDir)) {
                System.err.println("Error: Directory '" + featureDir + "' already exists.");
                return;
            }

            // Build template data
            Map<String, Object> data = buildModuleData(rootDir, featureId, descriptor);
            data.put("withFragment", false);  // Will be updated if fragment exists

            // Check for existing fragment
            Path fragmentDir = rootDir.resolve(descriptor.getPluginId() + ".fragment");
            if (Files.exists(fragmentDir)) {
                data.put("withFragment", true);
            }

            // Create feature module
            createFeatureModule(featureDir, descriptor, data);

            // Update root pom.xml to include new module
            updateRootPomModules(rootDir, featureId);

            // Update category.xml if p2 module exists - use feature instead of individual plugins
            updateCategoryXmlForFeature(rootDir, featureId);

            System.out.println();
            System.out.println("Feature module added successfully!");
            System.out.println();
            System.out.println("The feature groups your plugins for easier installation.");
            System.out.println();
        } catch (IOException e) {
            System.err.println("Error adding feature module: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Build template data for adding a module to an existing project.
     */
    private Map<String, Object> buildModuleData(Path rootDir, String moduleId, PluginDescriptor descriptor) {
        Map<String, Object> data = buildPluginData(descriptor);

        // Override with module-specific data
        data.put("pluginId", descriptor.getPluginId());
        data.put("basePluginId", descriptor.getBasePluginId());
        data.put("groupId", descriptor.getGroupId());
        data.put("withFragment", descriptor.isWithFragment());
        data.put("withFeature", descriptor.isWithFeature());
        data.put("withTest", descriptor.isWithTest());
        data.put("fragmentHost", descriptor.getFragmentHost());
        data.put("categoryName", descriptor.getPluginId().replace('.', '-'));

        return data;
    }

    /**
     * Updates the root pom.xml to add a new module entry.
     */
    private void updateRootPomModules(Path rootDir, String moduleName) throws IOException {
        Path pomFile = rootDir.resolve("pom.xml");
        if (!Files.exists(pomFile)) {
            System.err.println("Warning: Root pom.xml not found, skipping module registration");
            return;
        }

        String content = Files.readString(pomFile);

        // Check if module already exists
        if (content.contains("<module>" + moduleName + "</module>")) {
            System.out.println("  Module already registered in pom.xml");
            return;
        }

        // Find the closing </modules> tag and insert before it
        int modulesEnd = content.indexOf("</modules>");
        if (modulesEnd == -1) {
            System.err.println("Warning: Could not find </modules> in pom.xml");
            return;
        }

        // Find indentation from previous module line
        String indent = "\t\t";  // Default
        int lastModule = content.lastIndexOf("<module>", modulesEnd);
        if (lastModule != -1) {
            int lineStart = content.lastIndexOf('\n', lastModule);
            if (lineStart != -1) {
                indent = content.substring(lineStart + 1, lastModule);
            }
        }

        String newModule = indent + "<module>" + moduleName + "</module>\n";
        String newContent = content.substring(0, modulesEnd) + newModule + content.substring(modulesEnd);

        Files.writeString(pomFile, newContent);
        System.out.println("  Updated: " + pomFile);
    }

    /**
     * Updates category.xml to include a new plugin or fragment.
     */
    private void updateCategoryXml(Path rootDir, String bundleId, boolean isFragment) throws IOException {
        // Find p2 module directory
        Path p2Dir = findP2Module(rootDir);
        if (p2Dir == null) {
            return;  // No p2 module, nothing to update
        }

        Path categoryFile = p2Dir.resolve("category.xml");
        if (!Files.exists(categoryFile)) {
            return;
        }

        String content = Files.readString(categoryFile);

        // Check if bundle already in category
        if (content.contains("id=\"" + bundleId + "\"")) {
            return;
        }

        // Find the category-def closing tag to insert before it
        int categoryDefEnd = content.indexOf("</category-def>");
        if (categoryDefEnd == -1) {
            return;
        }

        // Find the last bundle entry to determine indentation
        String bundleEntry = String.format(
                "      <bundle id=\"%s\" version=\"0.0.0\">\n" +
                "         <category name=\"%s\"/>\n" +
                "      </bundle>\n",
                bundleId, extractCategoryName(content)
        );

        // Insert before </category-def>
        int insertPos = content.lastIndexOf("</bundle>", categoryDefEnd);
        if (insertPos != -1) {
            insertPos = content.indexOf('\n', insertPos) + 1;
        } else {
            insertPos = content.indexOf('\n', content.indexOf("<category-def")) + 1;
        }

        String newContent = content.substring(0, insertPos) + bundleEntry + content.substring(insertPos);
        Files.writeString(categoryFile, newContent);
        System.out.println("  Updated: " + categoryFile);
    }

    /**
     * Updates category.xml to use a feature instead of individual bundles.
     */
    private void updateCategoryXmlForFeature(Path rootDir, String featureId) throws IOException {
        Path p2Dir = findP2Module(rootDir);
        if (p2Dir == null) {
            return;
        }

        Path categoryFile = p2Dir.resolve("category.xml");
        if (!Files.exists(categoryFile)) {
            return;
        }

        String content = Files.readString(categoryFile);
        String categoryName = extractCategoryName(content);

        // Add iu (installable unit) for feature if not present
        if (!content.contains("id=\"" + featureId + "\"")) {
            String featureEntry = String.format(
                    "   <iu id=\"%s.feature.group\" version=\"0.0.0\">\n" +
                    "      <category name=\"%s\"/>\n" +
                    "   </iu>\n",
                    featureId.replace(".feature", ""), categoryName
            );

            int categoryDefStart = content.indexOf("<category-def");
            if (categoryDefStart != -1) {
                String newContent = content.substring(0, categoryDefStart) + featureEntry + content.substring(categoryDefStart);
                Files.writeString(categoryFile, newContent);
                System.out.println("  Updated: " + categoryFile);
            }
        }
    }

    /**
     * Updates feature.xml to include a new plugin or fragment.
     */
    private void updateFeatureXml(Path rootDir, String bundleId, boolean isFragment) throws IOException {
        // Find feature module directory
        String baseId = extractBaseProjectId(rootDir);
        Path featureDir = rootDir.resolve(baseId + ".feature");
        if (!Files.exists(featureDir)) {
            return;
        }

        Path featureFile = featureDir.resolve("feature.xml");
        if (!Files.exists(featureFile)) {
            return;
        }

        String content = Files.readString(featureFile);

        // Check if bundle already in feature
        if (content.contains("id=\"" + bundleId + "\"")) {
            return;
        }

        // Find closing </feature> tag
        int featureEnd = content.indexOf("</feature>");
        if (featureEnd == -1) {
            return;
        }

        String pluginEntry;
        if (isFragment) {
            pluginEntry = String.format(
                    "   <plugin\n" +
                    "         id=\"%s\"\n" +
                    "         download-size=\"0\"\n" +
                    "         install-size=\"0\"\n" +
                    "         version=\"0.0.0\"\n" +
                    "         fragment=\"true\"\n" +
                    "         unpack=\"false\"/>\n",
                    bundleId
            );
        } else {
            pluginEntry = String.format(
                    "   <plugin\n" +
                    "         id=\"%s\"\n" +
                    "         download-size=\"0\"\n" +
                    "         install-size=\"0\"\n" +
                    "         version=\"0.0.0\"\n" +
                    "         unpack=\"false\"/>\n",
                    bundleId
            );
        }

        String newContent = content.substring(0, featureEnd) + pluginEntry + content.substring(featureEnd);
        Files.writeString(featureFile, newContent);
        System.out.println("  Updated: " + featureFile);
    }

    /**
     * Finds the p2 module directory.
     */
    private Path findP2Module(Path rootDir) {
        try (var stream = Files.list(rootDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().endsWith(".p2"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Extracts the category name from category.xml content.
     */
    private String extractCategoryName(String content) {
        Pattern pattern = Pattern.compile("<category-def\\s+name=\"([^\"]+)\"");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "default";
    }

    /**
     * Extracts the base project ID from root directory name or pom.
     */
    private String extractBaseProjectId(Path rootDir) {
        return rootDir.getFileName().toString();
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

            // Only create Activator if none exists (shared by process-mapped and jasper-report)
            if (!hasPluginActivator(srcDir)) {
                String pluginBaseName = toPascalCase(extractPluginName(pluginId));
                data.put("className", pluginBaseName + "Activator");
                templateRenderer.render("jasper-report/Activator.java", data,
                        srcDir.resolve(pluginBaseName + "Activator.java"));
            } else {
                System.out.println("  Using existing Activator (shared by process-mapped and jasper-report)");
            }

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
