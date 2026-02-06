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
 * Generates Callout components for iDempiere plugins.
 *
 * <p>Creates a Callout class with the @Callout annotation and a CalloutFactory
 * that uses AnnotationBasedColumnCalloutFactory to discover callouts via package scanning.
 */
@ApplicationScoped
public class CalloutGenerator implements ComponentGenerator {

    @Inject
    TemplateRenderer templateRenderer;

    @Inject
    ManifestService manifestService;

    @Override
    public String type() {
        return "callout";
    }

    @Override
    public void generate(Path srcDir, Path baseDir, Map<String, Object> data) throws IOException {
        String pluginId = (String) data.get("pluginId");
        String baseName = toPascalCase(extractPluginName(pluginId));
        String className = baseName + "Callout";

        Map<String, Object> templateData = new HashMap<>(data);
        templateData.put("className", className);

        templateRenderer.render("callout/Callout.java", templateData, srcDir.resolve(className + ".java"));
        templateRenderer.render("callout/CalloutFactory.java", templateData, srcDir.resolve(className + "Factory.java"));
    }

    @Override
    public void addToExisting(Path srcDir, Path pluginDir, Map<String, Object> data) throws IOException {
        String name = (String) data.get("className");
        String pluginId = (String) data.get("pluginId");

        templateRenderer.render("callout/Callout.java", data, srcDir.resolve(name + ".java"));

        // Only create factory if none exists (one factory handles all callouts in package)
        if (!hasCalloutFactory(srcDir)) {
            String pluginBaseName = toPascalCase(extractPluginName(pluginId));
            Map<String, Object> factoryData = new HashMap<>(data);
            factoryData.put("className", pluginBaseName + "Callout");
            templateRenderer.render("callout/CalloutFactory.java", factoryData,
                    srcDir.resolve(pluginBaseName + "CalloutFactory.java"));
        } else {
            System.out.println("  Using existing CalloutFactory (scans package for all @Callout classes)");
        }

        manifestService.addRequiredBundles(pluginDir, type());
    }
}
