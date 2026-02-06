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
 * Generates REST Extension components for iDempiere plugins.
 *
 * <p>Creates a ResourceExtension class and a Resource class for extending
 * the iDempiere REST API.
 */
@ApplicationScoped
public class RestExtensionGenerator implements ComponentGenerator {

    @Inject
    TemplateRenderer templateRenderer;

    @Inject
    ManifestService manifestService;

    @Override
    public String type() {
        return "rest-extension";
    }

    @Override
    public void generate(Path srcDir, Path baseDir, Map<String, Object> data) throws IOException {
        String pluginId = (String) data.get("pluginId");
        String baseName = toPascalCase(extractPluginName(pluginId));

        Map<String, Object> templateData = new HashMap<>(data);
        templateData.put("className", baseName);
        templateData.put("resourcePath", extractPluginName(pluginId).toLowerCase());

        templateRenderer.render("rest-extension/ResourceExtension.java", templateData,
                srcDir.resolve(baseName + "ResourceExtension.java"));
        templateRenderer.render("rest-extension/Resource.java", templateData,
                srcDir.resolve(baseName + "Resource.java"));
    }

    @Override
    public void addToExisting(Path srcDir, Path pluginDir, Map<String, Object> data) throws IOException {
        String name = (String) data.get("className");
        String resourcePath = (String) data.get("resourcePath");

        if (resourcePath == null) {
            resourcePath = name.toLowerCase();
            data = new HashMap<>(data);
            data.put("resourcePath", resourcePath);
        }

        templateRenderer.render("rest-extension/ResourceExtension.java", data,
                srcDir.resolve(name + "ResourceExtension.java"));
        templateRenderer.render("rest-extension/Resource.java", data,
                srcDir.resolve(name + "Resource.java"));

        manifestService.addRequiredBundles(pluginDir, type());
    }
}
