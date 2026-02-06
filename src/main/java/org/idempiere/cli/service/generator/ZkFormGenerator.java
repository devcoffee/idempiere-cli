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
 * Generates ZK Form components for iDempiere plugins.
 *
 * <p>Creates a Form class extending ADForm and a FormFactory
 * for OSGi registration.
 */
@ApplicationScoped
public class ZkFormGenerator implements ComponentGenerator {

    @Inject
    TemplateRenderer templateRenderer;

    @Inject
    ManifestService manifestService;

    @Override
    public String type() {
        return "zk-form";
    }

    @Override
    public void generate(Path srcDir, Path baseDir, Map<String, Object> data) throws IOException {
        String pluginId = (String) data.get("pluginId");
        String baseName = toPascalCase(extractPluginName(pluginId));
        String className = baseName + "Form";

        Map<String, Object> templateData = new HashMap<>(data);
        templateData.put("className", className);

        templateRenderer.render("zk-form/ZkForm.java", templateData, srcDir.resolve(className + ".java"));
        templateRenderer.render("zk-form/FormFactory.java", templateData, srcDir.resolve(className + "Factory.java"));
    }

    @Override
    public void addToExisting(Path srcDir, Path pluginDir, Map<String, Object> data) throws IOException {
        String name = (String) data.get("className");

        templateRenderer.render("zk-form/ZkForm.java", data, srcDir.resolve(name + ".java"));
        templateRenderer.render("zk-form/FormFactory.java", data, srcDir.resolve(name + "Factory.java"));

        manifestService.addRequiredBundles(pluginDir, type());
    }
}
