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
 * Generates Event Handler components for iDempiere plugins.
 *
 * <p>Creates an EventHandler (delegate) class and an EventManager that registers
 * the handler via OSGi declarative services.
 */
@ApplicationScoped
public class EventHandlerGenerator implements ComponentGenerator {

    @Inject
    TemplateRenderer templateRenderer;

    @Inject
    ManifestService manifestService;

    @Override
    public String type() {
        return "event-handler";
    }

    @Override
    public void generate(Path srcDir, Path baseDir, Map<String, Object> data) throws IOException {
        String pluginId = (String) data.get("pluginId");
        String baseName = toPascalCase(extractPluginName(pluginId));

        Map<String, Object> templateData = new HashMap<>(data);

        // EventHandler (delegate)
        String delegateClassName = baseName + "EventDelegate";
        templateData.put("className", delegateClassName);
        templateRenderer.render("event-handler/EventHandler.java", templateData, srcDir.resolve(delegateClassName + ".java"));

        // EventManager
        String managerName = baseName + "Event";
        templateData.put("className", managerName);
        templateRenderer.render("event-handler/EventManager.java", templateData, srcDir.resolve(managerName + "Manager.java"));
    }

    @Override
    public void addToExisting(Path srcDir, Path pluginDir, Map<String, Object> data) throws IOException {
        String name = (String) data.get("className");

        templateRenderer.render("event-handler/EventHandler.java", data, srcDir.resolve(name + ".java"));
        templateRenderer.render("event-handler/EventManager.java", data, srcDir.resolve(name + "Manager.java"));

        manifestService.addRequiredBundles(pluginDir, type());
    }
}
