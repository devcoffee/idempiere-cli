package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.model.PluginDescriptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Creates new iDempiere plugin projects and adds components to existing plugins.
 *
 * <h2>Shared Components</h2>
 * <p>Some components share infrastructure classes that only need to exist once per plugin:</p>
 * <ul>
 *   <li><b>CalloutFactory</b> - One {@code AnnotationBasedColumnCalloutFactory} per plugin handles
 *       all callouts. Additional callouts are automatically discovered via package scanning.</li>
 *   <li><b>Activator</b> - One {@code Incremental2PackActivator} per plugin is shared between
 *       process-mapped and jasper-report components for 2Pack support.</li>
 * </ul>
 *
 * <p>When adding components, this service detects existing shared infrastructure by scanning
 * file contents (not names), ensuring compatibility with custom-named classes.</p>
 *
 * <h2>File Protection</h2>
 * <p>Existing files are never overwritten. When a file already exists, it is skipped with
 * a warning message.</p>
 */
@ApplicationScoped
public class ScaffoldService {

    @Inject
    TemplateRenderer templateRenderer;

    @Inject
    MavenWrapperService mavenWrapperService;

    @Inject
    AddComponentService addComponentService;

    @Inject
    ScaffoldTemplateDataFactory scaffoldTemplateDataFactory;

    @Inject
    ScaffoldModuleWriterService scaffoldModuleWriterService;

    @Inject
    ScaffoldModuleManagementService scaffoldModuleManagementService;

    @Inject
    ScaffoldProjectAuxFilesService scaffoldProjectAuxFilesService;

    public ScaffoldResult createPlugin(PluginDescriptor descriptor) {
        if (descriptor.isMultiModule()) {
            return createMultiModulePlugin(descriptor);
        } else {
            return createStandalonePlugin(descriptor);
        }
    }

    /**
     * Creates a multi-module project structure:
     * - root pom.xml (aggregator)
     * - parent module (with Tycho config)
     * - base plugin module
     * - test module (optional)
     * - p2 site module
     * - fragment module (optional)
     * - feature module (optional)
     */
    private ScaffoldResult createMultiModulePlugin(PluginDescriptor descriptor) {
        Path rootDir;
        if (descriptor.getOutputDir() != null) {
            rootDir = descriptor.getOutputDir().resolve(descriptor.getProjectName());
        } else {
            rootDir = Path.of(descriptor.getProjectName());
        }

        if (Files.exists(rootDir)) {
            return directoryExistsError(rootDir);
        }

        System.out.println();
        System.out.println("Creating iDempiere multi-module project: " + descriptor.getPluginId());
        System.out.println("==========================================");
        System.out.println();

        try {
            // Create root directory
            Files.createDirectories(rootDir);

            // Build template data
            Map<String, Object> data = scaffoldTemplateDataFactory.buildMultiModuleData(descriptor);

            // Create root pom.xml
            templateRenderer.render("multi-module/root-pom.xml", data, rootDir.resolve("pom.xml"));

            // Create parent module
            Path parentDir = rootDir.resolve(descriptor.getPluginId() + ".parent");
            Files.createDirectories(parentDir);
            templateRenderer.render("multi-module/parent-pom.xml", data, parentDir.resolve("pom.xml"));

            // Create base plugin module
            Path pluginDir = rootDir.resolve(descriptor.getBasePluginId());
            scaffoldModuleWriterService.createPluginModule(pluginDir, descriptor, data);

            // Create test module (if enabled)
            if (descriptor.isWithTest()) {
                Path testDir = rootDir.resolve(descriptor.getBasePluginId() + ".test");
                scaffoldModuleWriterService.createTestModule(testDir, descriptor, data);
            }

            // Create fragment module (if enabled)
            if (descriptor.isWithFragment()) {
                Path fragmentDir = rootDir.resolve(descriptor.getPluginId() + ".fragment");
                scaffoldModuleWriterService.createFragmentModule(fragmentDir, descriptor, data);
            }

            // Create feature module (if enabled)
            if (descriptor.isWithFeature()) {
                Path featureDir = rootDir.resolve(descriptor.getPluginId() + ".feature");
                scaffoldModuleWriterService.createFeatureModule(featureDir, descriptor, data);
            }

            // Create p2 site module
            Path p2Dir = rootDir.resolve(descriptor.getPluginId() + ".p2");
            scaffoldModuleWriterService.createP2Module(p2Dir, descriptor, data);

            // Create .mvn/jvm.config at root
            scaffoldProjectAuxFilesService.generateMavenJvmConfig(rootDir);

            // Add Maven Wrapper
            if (mavenWrapperService.addWrapper(rootDir)) {
                System.out.println("  Created: mvnw, mvnw.cmd (Maven Wrapper)");
            }

            // Generate .gitignore
            scaffoldProjectAuxFilesService.generateGitignore(rootDir);

            // Generate Eclipse .project files (if enabled)
            if (descriptor.isWithEclipseProject()) {
                scaffoldProjectAuxFilesService.generateEclipseProject(pluginDir, descriptor.getBasePluginId());
                if (descriptor.isWithTest()) {
                    scaffoldProjectAuxFilesService.generateEclipseProject(rootDir.resolve(descriptor.getBasePluginId() + ".test"),
                            descriptor.getBasePluginId() + ".test");
                }
                if (descriptor.isWithFragment()) {
                    scaffoldProjectAuxFilesService.generateEclipseProject(rootDir.resolve(descriptor.getPluginId() + ".fragment"),
                            descriptor.getPluginId() + ".fragment");
                }
            }

            System.out.println();
            System.out.println("Multi-module project created successfully!");
            System.out.println();
            System.out.println("Structure:");
            System.out.println("  " + descriptor.getProjectName() + "/");
            System.out.println("  ├── " + descriptor.getPluginId() + ".parent/    (Maven parent)");
            System.out.println("  ├── " + descriptor.getBasePluginId() + "/    (Main plugin)");
            if (descriptor.isWithTest()) {
                System.out.println("  ├── " + descriptor.getBasePluginId() + ".test/    (Tests)");
            }
            if (descriptor.isWithFragment()) {
                System.out.println("  ├── " + descriptor.getPluginId() + ".fragment/    (Fragment)");
            }
            if (descriptor.isWithFeature()) {
                System.out.println("  ├── " + descriptor.getPluginId() + ".feature/    (Feature)");
            }
            System.out.println("  └── " + descriptor.getPluginId() + ".p2/    (P2 repository)");
            System.out.println();
            System.out.println("Next steps:");
            System.out.println("  1. cd " + descriptor.getProjectName());
            if (descriptor.isWithEclipseProject()) {
                System.out.println("  2. Import in Eclipse: File > Import > Existing Projects into Workspace");
                System.out.println("     Or run: idempiere-cli import-workspace --dir=" + descriptor.getProjectName());
            } else {
                System.out.println("  2. Import in Eclipse: File > Import > Maven > Existing Maven Projects");
            }
            System.out.println("  3. Select this directory as root and click Finish");
            System.out.println();
            System.out.println("To build:");
            System.out.println("  ./mvnw verify    (or mvnw.cmd on Windows)");
            System.out.println();
            System.out.println("To package for distribution:");
            System.out.println("  ./mvnw verify    (JAR will be in p2/target/repository/)");
            System.out.println();
            System.out.println("Tip: Use 'idempiere-cli add <component>' for AI-powered code generation.");
            System.out.println();
            return ScaffoldResult.ok(rootDir);
        } catch (IOException e) {
            return ioError("Error creating project", e, true);
        }
    }

    /**
     * Creates a standalone single plugin (original behavior).
     */
    private ScaffoldResult createStandalonePlugin(PluginDescriptor descriptor) {
        Path baseDir;
        if (descriptor.getOutputDir() != null) {
            baseDir = descriptor.getOutputDir().resolve(descriptor.getProjectName());
        } else {
            baseDir = Path.of(descriptor.getProjectName());
        }

        if (Files.exists(baseDir)) {
            return directoryExistsError(baseDir);
        }

        System.out.println();
        System.out.println("Creating iDempiere plugin: " + descriptor.getPluginId());
        System.out.println("==========================================");
        System.out.println();

        try {
            scaffoldModuleWriterService.createStandaloneStructure(baseDir, descriptor);
            Map<String, Object> data = scaffoldTemplateDataFactory.buildPluginData(descriptor);
            scaffoldModuleWriterService.createStandaloneFiles(baseDir, data);
            scaffoldModuleWriterService.generateStandaloneComponents(baseDir, descriptor);

            // Add Maven Wrapper
            if (mavenWrapperService.addWrapper(baseDir)) {
                System.out.println("  Created: mvnw, mvnw.cmd (Maven Wrapper)");
            }

            // Generate .gitignore
            scaffoldProjectAuxFilesService.generateGitignore(baseDir);

            // Generate Eclipse .project file (if enabled)
            if (descriptor.isWithEclipseProject()) {
                scaffoldProjectAuxFilesService.generateEclipseProject(baseDir, descriptor.getPluginId());
            }

            System.out.println();
            System.out.println("Plugin created successfully!");
            System.out.println();
            System.out.println("Next steps:");
            System.out.println("  1. cd " + descriptor.getProjectName());
            if (descriptor.isWithEclipseProject()) {
                System.out.println("  2. Import in Eclipse: File > Import > Existing Projects into Workspace");
                System.out.println("     Or run: idempiere-cli import-workspace --dir=" + descriptor.getProjectName());
            } else {
                System.out.println("  2. Import in Eclipse: File > Import > Maven > Existing Maven Projects");
            }
            System.out.println("  3. Select this directory as root and click Finish");
            System.out.println();
            System.out.println("To build:");
            System.out.println("  ./mvnw verify    (or mvnw.cmd on Windows)");
            System.out.println();
            System.out.println("Tip: Use 'idempiere-cli add <component>' for AI-powered code generation.");
            System.out.println();
            return ScaffoldResult.ok(baseDir);
        } catch (IOException e) {
            return ioError("Error creating plugin", e, false);
        }
    }

    /**
     * Add a component to an existing plugin using the registered generator.
     */
    public ScaffoldResult addComponent(String type, String name, Path pluginDir, String pluginId) {
        return addComponentService.addComponent(type, name, pluginDir, pluginId, null);
    }

    /**
     * Add a component to an existing plugin with additional data.
     * Tries AI generation first (if configured), then falls back to templates.
     *
     * @param type      the component type (e.g., "zk-form-zul", "jasper-report")
     * @param name      the component class name
     * @param pluginDir the plugin directory
     * @param pluginId  the plugin bundle ID
     * @param extraData additional data for the generator (e.g., resourcePath for REST)
     */
    public ScaffoldResult addComponent(String type, String name, Path pluginDir, String pluginId, Map<String, Object> extraData) {
        return addComponentService.addComponent(type, name, pluginDir, pluginId, extraData);
    }

    // ========================================================================
    // Methods for adding modules to existing multi-module projects
    // ========================================================================

    /**
     * Adds a new plugin module to an existing multi-module project.
     *
     * @param rootDir the multi-module project root directory
     * @param pluginId the full plugin ID (e.g., org.example.myplugin.newplugin)
     * @param descriptor optional descriptor with settings (version, vendor, etc.)
     */
    public ScaffoldResult addPluginModuleToProject(Path rootDir, String pluginId, PluginDescriptor descriptor) {
        return scaffoldModuleManagementService.addPluginModuleToProject(rootDir, pluginId, descriptor);
    }

    /**
     * Adds a fragment module to an existing multi-module project.
     *
     * @param rootDir the multi-module project root directory
     * @param fragmentHost the fragment host bundle (e.g., org.adempiere.ui.zk)
     * @param descriptor optional descriptor with settings
     */
    public ScaffoldResult addFragmentModuleToProject(Path rootDir, String fragmentHost, PluginDescriptor descriptor) {
        return scaffoldModuleManagementService.addFragmentModuleToProject(rootDir, fragmentHost, descriptor);
    }

    /**
     * Adds a feature module to an existing multi-module project.
     *
     * @param rootDir the multi-module project root directory
     * @param descriptor optional descriptor with settings
     */
    public ScaffoldResult addFeatureModuleToProject(Path rootDir, PluginDescriptor descriptor) {
        return scaffoldModuleManagementService.addFeatureModuleToProject(rootDir, descriptor);
    }

    private ScaffoldResult directoryExistsError(Path dir) {
        String message = "Directory '" + dir + "' already exists.";
        System.err.println("Error: " + message);
        return ScaffoldResult.error("DIRECTORY_EXISTS", message);
    }

    private ScaffoldResult ioError(String context, IOException e, boolean withStackTrace) {
        String message = context + ": " + e.getMessage();
        System.err.println(message);
        if (withStackTrace) {
            e.printStackTrace();
        }
        return ScaffoldResult.error("IO_ERROR", message);
    }

}
