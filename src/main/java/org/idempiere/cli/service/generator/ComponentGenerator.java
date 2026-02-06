package org.idempiere.cli.service.generator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Strategy interface for generating iDempiere plugin components.
 *
 * <p>Implementations handle specific component types (callout, process, event-handler, etc.)
 * and are discovered via CDI injection using {@code Instance<ComponentGenerator>}.
 *
 * <h2>Usage</h2>
 * <pre>
 * &#64;Inject
 * Instance&lt;ComponentGenerator&gt; generators;
 *
 * // Find generator by type
 * generators.stream()
 *     .filter(g -&gt; g.type().equals("callout"))
 *     .findFirst()
 *     .ifPresent(g -&gt; g.generate(srcDir, baseDir, data));
 * </pre>
 *
 * @see org.idempiere.cli.service.ScaffoldService
 */
public interface ComponentGenerator {

    /**
     * Returns the component type identifier.
     * This matches the feature name used in PluginDescriptor (e.g., "callout", "process").
     *
     * @return the component type (e.g., "callout", "event-handler", "process")
     */
    String type();

    /**
     * Generates component files for a new plugin project.
     *
     * <p>Called during plugin scaffolding when the component is enabled as a feature.
     *
     * @param srcDir  the source directory where Java files should be created
     * @param baseDir the plugin root directory (for non-Java files like .zul, .jrxml)
     * @param data    template data including pluginId, className, etc.
     * @throws IOException if file generation fails
     */
    void generate(Path srcDir, Path baseDir, Map<String, Object> data) throws IOException;

    /**
     * Adds the component to an existing plugin project.
     *
     * <p>Called via "idempiere-cli add &lt;type&gt;" commands. May need to check for
     * existing shared infrastructure (e.g., CalloutFactory, Activator).
     *
     * @param srcDir    the source directory where Java files should be created
     * @param pluginDir the plugin root directory
     * @param data      template data including pluginId, className, etc.
     * @throws IOException if file generation fails
     */
    void addToExisting(Path srcDir, Path pluginDir, Map<String, Object> data) throws IOException;
}
