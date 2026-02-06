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
 * Generates WListbox Editor Form components for iDempiere plugins.
 *
 * <p>Creates a form with editable WListbox for inline data editing.
 */
@ApplicationScoped
public class WListboxEditorGenerator implements ComponentGenerator {

    @Inject
    TemplateRenderer templateRenderer;

    @Inject
    ManifestService manifestService;

    @Override
    public String type() {
        return "wlistbox-editor";
    }

    @Override
    public void generate(Path srcDir, Path baseDir, Map<String, Object> data) throws IOException {
        String pluginId = (String) data.get("pluginId");
        String baseName = toPascalCase(extractPluginName(pluginId));

        Map<String, Object> templateData = new HashMap<>(data);
        templateData.put("className", baseName + "WListboxEditorForm");

        templateRenderer.render("wlistbox-editor/WListboxEditorForm.java", templateData, srcDir.resolve(baseName + "WListboxEditorForm.java"));
    }

    @Override
    public void addToExisting(Path srcDir, Path pluginDir, Map<String, Object> data) throws IOException {
        String name = (String) data.get("className");

        Map<String, Object> templateData = new HashMap<>(data);
        templateData.put("className", name + "Form");

        templateRenderer.render("wlistbox-editor/WListboxEditorForm.java", templateData, srcDir.resolve(name + "Form.java"));

        manifestService.addRequiredBundles(pluginDir, type());
    }
}
