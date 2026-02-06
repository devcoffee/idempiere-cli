package org.idempiere.cli.service.generator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.service.ManifestService;
import org.idempiere.cli.service.TemplateRenderer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.idempiere.cli.service.generator.GeneratorUtils.*;

/**
 * Generates Base Test components for iDempiere plugins.
 *
 * <p>Creates a test class extending AbstractTestCase for plugin testing.
 */
@ApplicationScoped
public class BaseTestGenerator implements ComponentGenerator {

    @Inject
    TemplateRenderer templateRenderer;

    @Inject
    ManifestService manifestService;

    @Override
    public String type() {
        return "base-test";
    }

    @Override
    public void generate(Path srcDir, Path baseDir, Map<String, Object> data) throws IOException {
        String pluginId = (String) data.get("pluginId");
        String baseName = toPascalCase(extractPluginName(pluginId));
        String pluginName = extractPluginName(pluginId);

        Map<String, Object> templateData = new HashMap<>(data);
        templateData.put("className", baseName + "Test");
        templateData.put("pluginName", pluginName);

        templateRenderer.render("test/PluginTest.java", templateData, srcDir.resolve(baseName + "Test.java"));
    }

    @Override
    public void addToExisting(Path srcDir, Path pluginDir, Map<String, Object> data) throws IOException {
        String name = (String) data.get("className");
        String pluginId = (String) data.get("pluginId");

        Map<String, Object> templateData = new HashMap<>(data);
        templateData.put("pluginName", extractPluginName(pluginId));

        templateRenderer.render("test/PluginTest.java", templateData, srcDir.resolve(name + ".java"));

        manifestService.addRequiredBundles(pluginDir, type());
    }
}
