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
 * Generates Process components for iDempiere plugins.
 *
 * <p>Creates a Process class extending SvrProcess and a ProcessFactory
 * that implements IProcessFactory for OSGi registration.
 */
@ApplicationScoped
public class ProcessGenerator implements ComponentGenerator {

    @Inject
    TemplateRenderer templateRenderer;

    @Inject
    ManifestService manifestService;

    @Override
    public String type() {
        return "process";
    }

    @Override
    public void generate(Path srcDir, Path baseDir, Map<String, Object> data) throws IOException {
        String pluginId = (String) data.get("pluginId");
        String baseName = toPascalCase(extractPluginName(pluginId));
        String className = baseName + "Process";

        Map<String, Object> templateData = new HashMap<>(data);
        templateData.put("className", className);

        templateRenderer.render("process/Process.java", templateData, srcDir.resolve(className + ".java"));
        templateRenderer.render("process/ProcessFactory.java", templateData, srcDir.resolve(className + "Factory.java"));
    }

    @Override
    public void addToExisting(Path srcDir, Path pluginDir, Map<String, Object> data) throws IOException {
        String name = (String) data.get("className");

        templateRenderer.render("process/Process.java", data, srcDir.resolve(name + ".java"));
        templateRenderer.render("process/ProcessFactory.java", data, srcDir.resolve(name + "Factory.java"));

        manifestService.addRequiredBundles(pluginDir, type());
    }
}
