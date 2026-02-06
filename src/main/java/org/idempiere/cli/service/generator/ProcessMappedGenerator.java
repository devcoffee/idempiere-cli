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
 * Generates Process Mapped components for iDempiere plugins.
 *
 * <p>Creates a Process class and an Activator for 2Pack process mapping.
 * The Activator uses Incremental2PackActivator for bundle activation.
 */
@ApplicationScoped
public class ProcessMappedGenerator implements ComponentGenerator {

    @Inject
    TemplateRenderer templateRenderer;

    @Inject
    ManifestService manifestService;

    @Override
    public String type() {
        return "process-mapped";
    }

    @Override
    public void generate(Path srcDir, Path baseDir, Map<String, Object> data) throws IOException {
        String pluginId = (String) data.get("pluginId");
        String baseName = toPascalCase(extractPluginName(pluginId));

        Map<String, Object> templateData = new HashMap<>(data);

        // Process class
        templateData.put("className", baseName + "Process");
        templateRenderer.render("process/Process.java", templateData, srcDir.resolve(baseName + "Process.java"));

        // Activator class
        templateData.put("className", baseName + "Activator");
        templateRenderer.render("process-mapped/Activator.java", templateData, srcDir.resolve(baseName + "Activator.java"));
    }

    @Override
    public void addToExisting(Path srcDir, Path pluginDir, Map<String, Object> data) throws IOException {
        String name = (String) data.get("className");
        String pluginId = (String) data.get("pluginId");

        // Process template expects className to be the full class name
        Map<String, Object> processData = new HashMap<>(data);
        processData.put("className", name + "Process");
        templateRenderer.render("process/Process.java", processData, srcDir.resolve(name + "Process.java"));

        // Only create Activator if none exists (shared by process-mapped and jasper-report)
        if (!hasPluginActivator(srcDir)) {
            String pluginBaseName = toPascalCase(extractPluginName(pluginId));
            Map<String, Object> activatorData = new HashMap<>(data);
            activatorData.put("className", pluginBaseName + "Activator");
            templateRenderer.render("process-mapped/Activator.java", activatorData,
                    srcDir.resolve(pluginBaseName + "Activator.java"));
        } else {
            System.out.println("  Using existing Activator (shared by process-mapped and jasper-report)");
        }

        manifestService.addRequiredBundles(pluginDir, type());
    }
}
