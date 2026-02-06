package org.idempiere.cli.service.generator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.service.ManifestService;
import org.idempiere.cli.service.TemplateRenderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.idempiere.cli.service.generator.GeneratorUtils.*;

/**
 * Generates Jasper Report components for iDempiere plugins.
 *
 * <p>Creates an Activator for 2Pack registration and a sample .jrxml report file.
 * The Activator is shared with process-mapped components.
 */
@ApplicationScoped
public class JasperReportGenerator implements ComponentGenerator {

    @Inject
    TemplateRenderer templateRenderer;

    @Inject
    ManifestService manifestService;

    @Override
    public String type() {
        return "jasper-report";
    }

    @Override
    public void generate(Path srcDir, Path baseDir, Map<String, Object> data) throws IOException {
        String pluginId = (String) data.get("pluginId");
        String baseName = toPascalCase(extractPluginName(pluginId));
        String pluginName = extractPluginName(pluginId);

        Map<String, Object> templateData = new HashMap<>(data);
        templateData.put("pluginName", pluginName);

        // Only create Activator if not already present (may be created by process-mapped)
        Boolean hasProcessMapped = (Boolean) data.get("hasProcessMapped");
        if (hasProcessMapped == null || !hasProcessMapped) {
            templateData.put("className", baseName + "Activator");
            templateRenderer.render("jasper-report/Activator.java", templateData, srcDir.resolve(baseName + "Activator.java"));
        }

        // Create reports folder and sample .jrxml
        Path reportsDir = baseDir.resolve("reports");
        Files.createDirectories(reportsDir);
        templateData.put("className", baseName + "Report");
        templateRenderer.render("jasper-report/SampleReport.jrxml", templateData, reportsDir.resolve(baseName + "Report.jrxml"));
    }

    @Override
    public void addToExisting(Path srcDir, Path pluginDir, Map<String, Object> data) throws IOException {
        String name = (String) data.get("className");
        String pluginId = (String) data.get("pluginId");

        Map<String, Object> templateData = new HashMap<>(data);
        templateData.put("pluginName", extractPluginName(pluginId));

        // Only create Activator if none exists (shared by process-mapped and jasper-report)
        if (!hasPluginActivator(srcDir)) {
            String pluginBaseName = toPascalCase(extractPluginName(pluginId));
            templateData.put("className", pluginBaseName + "Activator");
            templateRenderer.render("jasper-report/Activator.java", templateData,
                    srcDir.resolve(pluginBaseName + "Activator.java"));
        } else {
            System.out.println("  Using existing Activator (shared by process-mapped and jasper-report)");
        }

        // Create reports folder and .jrxml file
        Path reportsDir = pluginDir.resolve("reports");
        Files.createDirectories(reportsDir);
        templateData.put("className", name);
        templateRenderer.render("jasper-report/SampleReport.jrxml", templateData, reportsDir.resolve(name + ".jrxml"));

        manifestService.addRequiredBundles(pluginDir, type());
    }
}
