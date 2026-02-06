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
 * Generates Facts Validator components for iDempiere plugins.
 *
 * <p>Creates a FactsValidator class for validating accounting facts.
 * Also ensures an EventManager exists for registration.
 */
@ApplicationScoped
public class FactsValidatorGenerator implements ComponentGenerator {

    @Inject
    TemplateRenderer templateRenderer;

    @Inject
    ManifestService manifestService;

    @Override
    public String type() {
        return "facts-validator";
    }

    @Override
    public void generate(Path srcDir, Path baseDir, Map<String, Object> data) throws IOException {
        String pluginId = (String) data.get("pluginId");
        String baseName = toPascalCase(extractPluginName(pluginId));
        String className = baseName + "FactsValidator";

        Map<String, Object> templateData = new HashMap<>(data);
        templateData.put("className", className);

        templateRenderer.render("facts-validator/FactsValidator.java", templateData, srcDir.resolve(className + ".java"));

        // FactsValidator requires EventManager if not already present via event-handler
        // Check data for hasEventHandler flag or just always create if needed
        Boolean hasEventHandler = (Boolean) data.get("hasEventHandler");
        if (hasEventHandler == null || !hasEventHandler) {
            String managerName = baseName + "Event";
            templateData.put("className", managerName);
            templateRenderer.render("event-handler/EventManager.java", templateData, srcDir.resolve(managerName + "Manager.java"));
        }
    }

    @Override
    public void addToExisting(Path srcDir, Path pluginDir, Map<String, Object> data) throws IOException {
        String name = (String) data.get("className");
        String pluginId = (String) data.get("pluginId");

        templateRenderer.render("facts-validator/FactsValidator.java", data, srcDir.resolve(name + ".java"));

        // Ensure EventManager exists
        if (!hasEventManager(srcDir)) {
            Map<String, Object> managerData = new HashMap<>(data);
            managerData.put("className", name);
            templateRenderer.render("event-handler/EventManager.java", managerData,
                    srcDir.resolve(name + "Manager.java"));
        }

        manifestService.addRequiredBundles(pluginDir, type());
    }
}
