package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.model.PluginDescriptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Orchestrates adding modules to existing multi-module projects.
 */
@ApplicationScoped
public class ScaffoldModuleManagementService {

    @Inject
    ScaffoldTemplateDataFactory scaffoldTemplateDataFactory;

    @Inject
    ScaffoldModuleWriterService scaffoldModuleWriterService;

    @Inject
    ScaffoldXmlUpdateService scaffoldXmlUpdateService;

    @Inject
    ScaffoldErrorService scaffoldErrorService;

    public ScaffoldResult addPluginModuleToProject(Path rootDir, String pluginId, PluginDescriptor descriptor) {
        System.out.println();
        System.out.println("Adding plugin module: " + pluginId);
        System.out.println();

        try {
            Path pluginDir = rootDir.resolve(pluginId);
            if (Files.exists(pluginDir)) {
                return scaffoldErrorService.directoryExistsError(pluginDir);
            }

            Map<String, Object> data = buildModuleData(descriptor);
            scaffoldModuleWriterService.createPluginModule(pluginDir, descriptor, data);
            scaffoldXmlUpdateService.updateRootPomModules(rootDir, pluginId);
            scaffoldXmlUpdateService.updateCategoryXml(rootDir, pluginId, false);

            System.out.println();
            System.out.println("Plugin module added successfully!");
            System.out.println();
            System.out.println("Next steps:");
            System.out.println("  1. Refresh Maven project in Eclipse");
            System.out.println("  2. The module will be included in the build automatically");
            System.out.println();
            return ScaffoldResult.ok(pluginDir);
        } catch (IOException e) {
            return scaffoldErrorService.ioError("Error adding plugin module", e, true);
        }
    }

    public ScaffoldResult addFragmentModuleToProject(Path rootDir, String fragmentHost, PluginDescriptor descriptor) {
        String fragmentId = descriptor.getPluginId() + ".fragment";
        System.out.println();
        System.out.println("Adding fragment module: " + fragmentId);
        System.out.println("Fragment host: " + fragmentHost);
        System.out.println();

        try {
            Path fragmentDir = rootDir.resolve(fragmentId);
            if (Files.exists(fragmentDir)) {
                return scaffoldErrorService.directoryExistsError(fragmentDir);
            }

            Map<String, Object> data = buildModuleData(descriptor);
            data.put("fragmentHost", fragmentHost);

            scaffoldModuleWriterService.createFragmentModule(fragmentDir, descriptor, data);
            scaffoldXmlUpdateService.updateRootPomModules(rootDir, fragmentId);
            scaffoldXmlUpdateService.updateCategoryXml(rootDir, fragmentId, false);
            scaffoldXmlUpdateService.updateFeatureXml(rootDir, fragmentId, true);

            System.out.println();
            System.out.println("Fragment module added successfully!");
            System.out.println();
            return ScaffoldResult.ok(fragmentDir);
        } catch (IOException e) {
            return scaffoldErrorService.ioError("Error adding fragment module", e, true);
        }
    }

    public ScaffoldResult addFeatureModuleToProject(Path rootDir, PluginDescriptor descriptor) {
        String featureId = descriptor.getPluginId() + ".feature";
        System.out.println();
        System.out.println("Adding feature module: " + featureId);
        System.out.println();

        try {
            Path featureDir = rootDir.resolve(featureId);
            if (Files.exists(featureDir)) {
                return scaffoldErrorService.directoryExistsError(featureDir);
            }

            Map<String, Object> data = buildModuleData(descriptor);
            data.put("withFragment", false);

            Path fragmentDir = rootDir.resolve(descriptor.getPluginId() + ".fragment");
            if (Files.exists(fragmentDir)) {
                data.put("withFragment", true);
            }

            scaffoldModuleWriterService.createFeatureModule(featureDir, descriptor, data);
            scaffoldXmlUpdateService.updateRootPomModules(rootDir, featureId);
            scaffoldXmlUpdateService.updateCategoryXmlForFeature(rootDir, featureId);

            System.out.println();
            System.out.println("Feature module added successfully!");
            System.out.println();
            System.out.println("The feature groups your plugins for easier installation.");
            System.out.println();
            return ScaffoldResult.ok(featureDir);
        } catch (IOException e) {
            return scaffoldErrorService.ioError("Error adding feature module", e, true);
        }
    }

    private Map<String, Object> buildModuleData(PluginDescriptor descriptor) {
        Map<String, Object> data = scaffoldTemplateDataFactory.buildPluginData(descriptor);
        data.put("pluginId", descriptor.getPluginId());
        data.put("basePluginId", descriptor.getBasePluginId());
        data.put("groupId", descriptor.getGroupId());
        data.put("withFragment", descriptor.isWithFragment());
        data.put("withFeature", descriptor.isWithFeature());
        data.put("withTest", descriptor.isWithTest());
        data.put("fragmentHost", descriptor.getFragmentHost());
        data.put("categoryName", descriptor.getPluginId().replace('.', '-'));
        return data;
    }

}
