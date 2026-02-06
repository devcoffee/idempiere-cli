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
 * Generates Listbox Group components for iDempiere plugins.
 *
 * <p>Creates a ListboxGroupForm, a GroupModel, and a GroupRenderer class
 * for grouped data display in ZK listbox.
 */
@ApplicationScoped
public class ListboxGroupGenerator implements ComponentGenerator {

    @Inject
    TemplateRenderer templateRenderer;

    @Inject
    ManifestService manifestService;

    @Override
    public String type() {
        return "listbox-group";
    }

    @Override
    public void generate(Path srcDir, Path baseDir, Map<String, Object> data) throws IOException {
        String pluginId = (String) data.get("pluginId");
        String baseName = toPascalCase(extractPluginName(pluginId));

        Map<String, Object> templateData = new HashMap<>(data);

        // Form class
        templateData.put("className", baseName + "ListboxGroupForm");
        templateData.put("modelClassName", baseName + "GroupModel");
        templateData.put("rendererClassName", baseName + "GroupRenderer");
        templateRenderer.render("listbox-group/ListboxGroupForm.java", templateData, srcDir.resolve(baseName + "ListboxGroupForm.java"));

        // Model class
        templateData.put("className", baseName + "GroupModel");
        templateRenderer.render("listbox-group/GroupModel.java", templateData, srcDir.resolve(baseName + "GroupModel.java"));

        // Renderer class
        templateData.put("className", baseName + "GroupRenderer");
        templateRenderer.render("listbox-group/GroupRenderer.java", templateData, srcDir.resolve(baseName + "GroupRenderer.java"));
    }

    @Override
    public void addToExisting(Path srcDir, Path pluginDir, Map<String, Object> data) throws IOException {
        String name = (String) data.get("className");

        Map<String, Object> templateData = new HashMap<>(data);

        // Form class
        templateData.put("className", name + "Form");
        templateData.put("modelClassName", name + "GroupModel");
        templateData.put("rendererClassName", name + "GroupRenderer");
        templateRenderer.render("listbox-group/ListboxGroupForm.java", templateData, srcDir.resolve(name + "Form.java"));

        // Model class
        templateData.put("className", name + "GroupModel");
        templateRenderer.render("listbox-group/GroupModel.java", templateData, srcDir.resolve(name + "GroupModel.java"));

        // Renderer class
        templateData.put("className", name + "GroupRenderer");
        templateRenderer.render("listbox-group/GroupRenderer.java", templateData, srcDir.resolve(name + "GroupRenderer.java"));

        manifestService.addRequiredBundles(pluginDir, type());
    }
}
