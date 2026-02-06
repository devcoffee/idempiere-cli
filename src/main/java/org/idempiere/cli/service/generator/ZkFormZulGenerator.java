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
 * Generates ZK Form with ZUL components for iDempiere plugins.
 *
 * <p>Creates a Form class, a FormController class, and a .zul file.
 * The ZUL file uses ZK markup and is copied directly (not processed by Qute).
 */
@ApplicationScoped
public class ZkFormZulGenerator implements ComponentGenerator {

    @Inject
    TemplateRenderer templateRenderer;

    @Inject
    ManifestService manifestService;

    @Override
    public String type() {
        return "zk-form-zul";
    }

    @Override
    public void generate(Path srcDir, Path baseDir, Map<String, Object> data) throws IOException {
        String pluginId = (String) data.get("pluginId");
        String baseName = toPascalCase(extractPluginName(pluginId));
        String pluginName = extractPluginName(pluginId);

        Map<String, Object> templateData = new HashMap<>(data);
        templateData.put("pluginName", pluginName);

        // Form class
        templateData.put("className", baseName + "ZulForm");
        templateRenderer.render("zk-form-zul/Form.java", templateData, srcDir.resolve(baseName + "ZulForm.java"));

        // Controller class
        templateData.put("className", baseName + "ZulFormController");
        templateData.put("formClassName", baseName + "ZulForm");
        templateRenderer.render("zk-form-zul/FormController.java", templateData, srcDir.resolve(baseName + "ZulFormController.java"));

        // ZUL file (copy without Qute processing due to ${} syntax conflict)
        Path webDir = baseDir.resolve("src/web");
        Files.createDirectories(webDir);
        templateRenderer.copyResource("zk-form-zul/form.zul", webDir.resolve("form.zul"));
    }

    @Override
    public void addToExisting(Path srcDir, Path pluginDir, Map<String, Object> data) throws IOException {
        String name = (String) data.get("className");
        String pluginId = (String) data.get("pluginId");

        Map<String, Object> templateData = new HashMap<>(data);
        templateData.put("pluginName", extractPluginName(pluginId));

        // Form class
        templateData.put("className", name);
        templateRenderer.render("zk-form-zul/Form.java", templateData, srcDir.resolve(name + ".java"));

        // Controller class
        templateData.put("className", name + "Controller");
        templateData.put("formClassName", name);
        templateRenderer.render("zk-form-zul/FormController.java", templateData, srcDir.resolve(name + "Controller.java"));

        // ZUL file
        Path webDir = pluginDir.resolve("src/web");
        Files.createDirectories(webDir);
        String zulFileName = name.toLowerCase() + ".zul";
        templateRenderer.copyResource("zk-form-zul/form.zul", webDir.resolve(zulFileName));

        manifestService.addRequiredBundles(pluginDir, type());
    }
}
