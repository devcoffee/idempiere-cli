package org.idempiere.cli.integration;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.idempiere.cli.model.PlatformVersion;
import org.idempiere.cli.model.PluginDescriptor;
import org.idempiere.cli.service.ProcessRunner;
import org.idempiere.cli.service.ScaffoldService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end test for plugin lifecycle: init → build → verify compilation.
 *
 * <p>This test creates real plugins and builds them using Maven with a local
 * iDempiere p2 repository. Requires a pre-built iDempiere installation.
 */
@QuarkusTest
class PluginBuildE2ETest {

    private static final Path P2_REPOSITORY = Path.of("/Users/muriloht/workspace/idempiere/idempiere/org.idempiere.p2/target/repository");

    @Inject
    ScaffoldService scaffoldService;

    @Inject
    ProcessRunner processRunner;

    private Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("e2e-build-");
    }

    @AfterEach
    void cleanup() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            deleteDir(tempDir);
        }
    }

    private void deleteDir(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean isP2RepositoryAvailable() {
        return Files.exists(P2_REPOSITORY) && Files.exists(P2_REPOSITORY.resolve("artifacts.jar"));
    }

    private boolean isMavenAvailable() {
        ProcessRunner.RunResult result = processRunner.run("mvn", "--version");
        return result.exitCode() == 0;
    }

    private PluginDescriptor createDescriptor(String pluginId) {
        PluginDescriptor descriptor = new PluginDescriptor(pluginId);
        descriptor.setVersion("1.0.0.qualifier");
        descriptor.setVendor("E2E Build Test");
        descriptor.setPlatformVersion(PlatformVersion.of(13));
        descriptor.setOutputDir(tempDir);
        descriptor.setMultiModule(false);
        return descriptor;
    }

    private String getP2RepositoryUrl() {
        return P2_REPOSITORY.toUri().toString();
    }

    private ProcessRunner.RunResult runMavenCompile(Path pluginDir) {
        String repoUrl = getP2RepositoryUrl();
        return processRunner.runInDir(
                pluginDir,
                "mvn", "compile", "-B", "-q",
                "-Didempiere.core.repository.url=" + repoUrl
        );
    }

    @Test
    void testBasicPlugin_compiles_successfully() throws IOException {
        assumeTrue(isP2RepositoryAvailable(), "iDempiere p2 repository not available");
        assumeTrue(isMavenAvailable(), "Maven not available");

        String pluginId = "org.e2e.build.basic";
        PluginDescriptor descriptor = createDescriptor(pluginId);
        scaffoldService.createPlugin(descriptor);

        Path pluginDir = tempDir.resolve(pluginId);
        ProcessRunner.RunResult result = runMavenCompile(pluginDir);

        assertEquals(0, result.exitCode(),
                "Maven compile should succeed. Output: " + result.output());

        // Verify target directory was created
        assertTrue(Files.exists(pluginDir.resolve("target/classes")),
                "target/classes should exist after compilation");
    }

    @Test
    void testPluginWithCallout_compiles_successfully() throws IOException {
        assumeTrue(isP2RepositoryAvailable(), "iDempiere p2 repository not available");
        assumeTrue(isMavenAvailable(), "Maven not available");

        String pluginId = "org.e2e.build.callout";
        PluginDescriptor descriptor = createDescriptor(pluginId);
        descriptor.addFeature("callout");
        scaffoldService.createPlugin(descriptor);

        Path pluginDir = tempDir.resolve(pluginId);
        ProcessRunner.RunResult result = runMavenCompile(pluginDir);

        assertEquals(0, result.exitCode(),
                "Maven compile should succeed. Output: " + result.output());

        // Verify callout class was compiled
        Path classesDir = pluginDir.resolve("target/classes/org/e2e/build/callout");
        assertTrue(Files.exists(classesDir.resolve("CalloutCallout.class")),
                "CalloutCallout.class should exist");
        assertTrue(Files.exists(classesDir.resolve("CalloutCalloutFactory.class")),
                "CalloutCalloutFactory.class should exist");
    }

    @Test
    void testPluginWithProcess_compiles_successfully() throws IOException {
        assumeTrue(isP2RepositoryAvailable(), "iDempiere p2 repository not available");
        assumeTrue(isMavenAvailable(), "Maven not available");

        String pluginId = "org.e2e.build.process";
        PluginDescriptor descriptor = createDescriptor(pluginId);
        descriptor.addFeature("process");
        scaffoldService.createPlugin(descriptor);

        Path pluginDir = tempDir.resolve(pluginId);
        ProcessRunner.RunResult result = runMavenCompile(pluginDir);

        assertEquals(0, result.exitCode(),
                "Maven compile should succeed. Output: " + result.output());

        Path classesDir = pluginDir.resolve("target/classes/org/e2e/build/process");
        assertTrue(Files.exists(classesDir.resolve("ProcessProcess.class")),
                "ProcessProcess.class should exist");
    }

    @Test
    void testPluginWithEventHandler_compiles_successfully() throws IOException {
        assumeTrue(isP2RepositoryAvailable(), "iDempiere p2 repository not available");
        assumeTrue(isMavenAvailable(), "Maven not available");

        String pluginId = "org.e2e.build.event";
        PluginDescriptor descriptor = createDescriptor(pluginId);
        descriptor.addFeature("event-handler");
        scaffoldService.createPlugin(descriptor);

        Path pluginDir = tempDir.resolve(pluginId);
        ProcessRunner.RunResult result = runMavenCompile(pluginDir);

        assertEquals(0, result.exitCode(),
                "Maven compile should succeed. Output: " + result.output());

        Path classesDir = pluginDir.resolve("target/classes/org/e2e/build/event");
        assertTrue(Files.exists(classesDir.resolve("EventEventDelegate.class")),
                "EventEventDelegate.class should exist");
        assertTrue(Files.exists(classesDir.resolve("EventEventManager.class")),
                "EventEventManager.class should exist");
    }

    @Test
    void testPluginWithAllFeatures_compiles_successfully() throws IOException {
        assumeTrue(isP2RepositoryAvailable(), "iDempiere p2 repository not available");
        assumeTrue(isMavenAvailable(), "Maven not available");

        String pluginId = "org.e2e.build.full";
        PluginDescriptor descriptor = createDescriptor(pluginId);
        descriptor.addFeature("callout");
        descriptor.addFeature("process");
        descriptor.addFeature("event-handler");
        scaffoldService.createPlugin(descriptor);

        Path pluginDir = tempDir.resolve(pluginId);
        ProcessRunner.RunResult result = runMavenCompile(pluginDir);

        assertEquals(0, result.exitCode(),
                "Maven compile should succeed. Output: " + result.output());

        Path classesDir = pluginDir.resolve("target/classes/org/e2e/build/full");
        assertTrue(Files.exists(classesDir), "Classes directory should exist");

        // Count compiled classes
        long classCount = Files.list(classesDir)
                .filter(p -> p.toString().endsWith(".class"))
                .count();

        assertTrue(classCount >= 5, "Should have at least 5 compiled classes, found: " + classCount);
    }
}
