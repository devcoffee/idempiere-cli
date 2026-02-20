package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.idempiere.cli.model.PluginDescriptor;
import org.idempiere.cli.service.generator.ComponentGenerator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Generates descriptor-selected components during scaffold creation.
 */
@ApplicationScoped
public class DescriptorComponentGenerationService {

    @Inject
    Instance<ComponentGenerator> generators;

    public ScaffoldResult generateComponentFiles(Path baseDir, PluginDescriptor descriptor) {
        Path srcDir;
        String pluginIdForPackage;
        if (descriptor.isMultiModule()) {
            srcDir = baseDir.resolve("src").resolve(descriptor.getBasePackagePath());
            pluginIdForPackage = descriptor.getBasePluginId();
        } else {
            srcDir = baseDir.resolve("src").resolve(descriptor.getPackagePath());
            pluginIdForPackage = descriptor.getPluginId();
        }

        Map<String, Object> data = new HashMap<>();
        data.put("pluginId", pluginIdForPackage);

        // Track features for cross-generator dependencies
        data.put("hasEventHandler", descriptor.hasFeature("event-handler"));
        data.put("hasProcessMapped", descriptor.hasFeature("process-mapped"));
        AtomicBoolean generationFailed = new AtomicBoolean(false);
        AtomicReference<String> firstError = new AtomicReference<>();

        // Generate components for each feature in the descriptor
        for (String feature : descriptor.getFeatures()) {
            findGenerator(feature).ifPresent(generator -> {
                generateWithErrorCapture(generator, srcDir, baseDir, data,
                        feature, generationFailed, firstError);
            });
        }

        // For standalone plugins with test feature (special case - uses base-test generator)
        if (!descriptor.isMultiModule() && descriptor.hasFeature("test")) {
            findGenerator("base-test").ifPresent(generator -> {
                generateWithErrorCapture(generator, srcDir, baseDir, data,
                        "test", generationFailed, firstError);
            });
        }

        if (generationFailed.get()) {
            String message = firstError.get() != null
                    ? firstError.get()
                    : "Failed to generate one or more selected components.";
            return ScaffoldResult.error("GENERATION_FAILED", message);
        }

        return ScaffoldResult.ok(baseDir);
    }

    private Optional<ComponentGenerator> findGenerator(String type) {
        return generators.stream()
                .filter(g -> g.type().equals(type))
                .findFirst();
    }

    private void generateWithErrorCapture(ComponentGenerator generator, Path srcDir, Path baseDir,
                                          Map<String, Object> data, String label,
                                          AtomicBoolean generationFailed,
                                          AtomicReference<String> firstError) {
        try {
            generator.generate(srcDir, baseDir, data);
        } catch (IOException e) {
            String message = "Error generating " + label + ": " + e.getMessage();
            System.err.println(message);
            generationFailed.set(true);
            firstError.compareAndSet(null, message);
        }
    }
}
