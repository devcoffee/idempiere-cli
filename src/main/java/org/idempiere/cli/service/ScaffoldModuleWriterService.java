package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.model.PluginDescriptor;
import org.idempiere.cli.service.generator.GeneratorUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Writes multi-module scaffold modules and their base files.
 */
@ApplicationScoped
public class ScaffoldModuleWriterService {

    @Inject
    TemplateRenderer templateRenderer;

    @Inject
    DescriptorComponentGenerationService descriptorComponentGenerationService;

    public void createPluginModule(Path pluginDir, PluginDescriptor descriptor, Map<String, Object> data) throws IOException {
        Files.createDirectories(pluginDir.resolve("META-INF"));
        Files.createDirectories(pluginDir.resolve("OSGI-INF"));
        Files.createDirectories(pluginDir.resolve("src").resolve(descriptor.getBasePackagePath()));

        templateRenderer.render("multi-module/plugin-pom.xml", data, pluginDir.resolve("pom.xml"));

        // MANIFEST uses basePluginId as Bundle-SymbolicName (must match Java package)
        Map<String, Object> manifestData = new HashMap<>(data);
        manifestData.put("pluginId", descriptor.getBasePluginId());
        templateRenderer.render("plugin/MANIFEST.MF", manifestData, pluginDir.resolve("META-INF/MANIFEST.MF"));

        templateRenderer.render("plugin/plugin.xml", data, pluginDir.resolve("plugin.xml"));

        writeFileAndReport(pluginDir.resolve("build.properties"),
                "source.. = src/\n" +
                        "output.. = bin/\n" +
                        "bin.includes = META-INF/,\\\n" +
                        "               OSGI-INF/,\\\n" +
                        "               .,\\\n" +
                        "               plugin.xml\n");

        // Generate component files (callout, process, etc.) in the plugin
        ScaffoldResult componentResult = descriptorComponentGenerationService.generateComponentFiles(pluginDir, descriptor);
        if (!componentResult.success()) {
            throw new IOException(componentResult.errorMessage());
        }
    }

    public void createTestModule(Path testDir, PluginDescriptor descriptor, Map<String, Object> data) throws IOException {
        Files.createDirectories(testDir.resolve("META-INF"));
        Files.createDirectories(testDir.resolve("src").resolve(descriptor.getBasePackagePath()));

        templateRenderer.render("multi-module/test-pom.xml", data, testDir.resolve("pom.xml"));
        templateRenderer.render("multi-module/test-MANIFEST.MF", data, testDir.resolve("META-INF/MANIFEST.MF"));

        writeFileAndReport(testDir.resolve("build.properties"),
                "source.. = src/\n" +
                        "output.. = bin/\n" +
                        "bin.includes = META-INF/,\\\n" +
                        "               .\n");

        Path srcDir = testDir.resolve("src").resolve(descriptor.getBasePackagePath());
        String baseName = GeneratorUtils.toPascalCase(descriptor.getPluginName());
        Map<String, Object> testData = new HashMap<>(data);
        testData.put("className", baseName + "Test");
        testData.put("pluginName", descriptor.getPluginName());
        testData.put("pluginId", descriptor.getBasePluginId());
        templateRenderer.render("test/PluginTest.java", testData, srcDir.resolve(baseName + "Test.java"));
    }

    public void createFragmentModule(Path fragmentDir, PluginDescriptor descriptor, Map<String, Object> data) throws IOException {
        Files.createDirectories(fragmentDir.resolve("META-INF"));
        Files.createDirectories(fragmentDir.resolve("src"));

        templateRenderer.render("fragment/pom.xml", data, fragmentDir.resolve("pom.xml"));
        templateRenderer.render("fragment/MANIFEST.MF", data, fragmentDir.resolve("META-INF/MANIFEST.MF"));

        writeFileAndReport(fragmentDir.resolve("build.properties"),
                "source.. = src/\n" +
                        "output.. = bin/\n" +
                        "bin.includes = META-INF/,\\\n" +
                        "               .\n");
    }

    public void createFeatureModule(Path featureDir, PluginDescriptor descriptor, Map<String, Object> data) throws IOException {
        Files.createDirectories(featureDir);

        templateRenderer.render("feature/pom.xml", data, featureDir.resolve("pom.xml"));
        templateRenderer.render("feature/feature.xml", data, featureDir.resolve("feature.xml"));

        writeFileAndReport(featureDir.resolve("build.properties"),
                "bin.includes = feature.xml\n");
    }

    public void createP2Module(Path p2Dir, PluginDescriptor descriptor, Map<String, Object> data) throws IOException {
        Files.createDirectories(p2Dir);
        templateRenderer.render("multi-module/p2-pom.xml", data, p2Dir.resolve("pom.xml"));
        templateRenderer.render("multi-module/category.xml", data, p2Dir.resolve("category.xml"));
    }

    public void createStandaloneStructure(Path baseDir, PluginDescriptor descriptor) throws IOException {
        Files.createDirectories(baseDir.resolve("META-INF"));
        Files.createDirectories(baseDir.resolve("OSGI-INF"));
        Files.createDirectories(baseDir.resolve("src").resolve(descriptor.getPackagePath()));
        Files.createDirectories(baseDir.resolve(".mvn"));
    }

    public void createStandaloneFiles(Path baseDir, Map<String, Object> data) throws IOException {
        templateRenderer.render("plugin/pom.xml", data, baseDir.resolve("pom.xml"));
        templateRenderer.render("plugin/MANIFEST.MF", data, baseDir.resolve("META-INF/MANIFEST.MF"));
        templateRenderer.render("plugin/plugin.xml", data, baseDir.resolve("plugin.xml"));

        writeFileAndReport(baseDir.resolve("build.properties"),
                "source.. = src/\n" +
                        "output.. = bin/\n" +
                        "bin.includes = META-INF/,\\\n" +
                        "               OSGI-INF/,\\\n" +
                        "               .,\\\n" +
                        "               plugin.xml\n");

        // Increase XML parser limits for large p2 repositories.
        writeFileAndReport(baseDir.resolve(".mvn/jvm.config"),
                "-Djdk.xml.maxGeneralEntitySizeLimit=0\n" +
                        "-Djdk.xml.totalEntitySizeLimit=0\n");
    }

    public void generateStandaloneComponents(Path baseDir, PluginDescriptor descriptor) throws IOException {
        ScaffoldResult componentResult = descriptorComponentGenerationService.generateComponentFiles(baseDir, descriptor);
        if (!componentResult.success()) {
            throw new IOException(componentResult.errorMessage());
        }
    }

    private void writeFileAndReport(Path file, String content) throws IOException {
        Files.writeString(file, content);
        System.out.println("  Created: " + file);
    }
}
