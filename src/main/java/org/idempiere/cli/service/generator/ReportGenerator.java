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
 * Generates Report components for iDempiere plugins.
 *
 * <p>Creates a Report class that extends ProcessReportDelegate.
 */
@ApplicationScoped
public class ReportGenerator implements ComponentGenerator {

    @Inject
    TemplateRenderer templateRenderer;

    @Inject
    ManifestService manifestService;

    @Override
    public String type() {
        return "report";
    }

    @Override
    public void generate(Path srcDir, Path baseDir, Map<String, Object> data) throws IOException {
        String pluginId = (String) data.get("pluginId");
        String baseName = toPascalCase(extractPluginName(pluginId));
        String className = baseName + "Report";

        Map<String, Object> templateData = new HashMap<>(data);
        templateData.put("className", className);

        templateRenderer.render("report/Report.java", templateData, srcDir.resolve(className + ".java"));
    }

    @Override
    public void addToExisting(Path srcDir, Path pluginDir, Map<String, Object> data) throws IOException {
        String name = (String) data.get("className");

        templateRenderer.render("report/Report.java", data, srcDir.resolve(name + ".java"));

        manifestService.addRequiredBundles(pluginDir, type());
    }
}
