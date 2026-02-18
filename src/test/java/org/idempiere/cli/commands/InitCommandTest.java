package org.idempiere.cli.commands;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusMainTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InitCommandTest {

    // Directory names now use the last segment of pluginId (projectName default)
    private static final String TEST_PLUGIN_DIR = "myplugin";
    private static final String TEST_PLUGIN_V13_DIR = "pluginv13";

    @BeforeEach
    void setup() throws IOException {
        deleteDir(Path.of(TEST_PLUGIN_DIR));
        deleteDir(Path.of(TEST_PLUGIN_V13_DIR));
    }

    @AfterEach
    void cleanup() throws IOException {
        deleteDir(Path.of(TEST_PLUGIN_DIR));
        deleteDir(Path.of(TEST_PLUGIN_V13_DIR));
    }

    private void deleteDir(Path dir) throws IOException {
        if (Files.exists(dir)) {
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
    }

    @Test
    @Order(1)
    @Launch({"init", "org.test.myplugin", "--standalone"})
    void testInitBasicPlugin(LaunchResult result) throws IOException {
        assertEquals(0, result.exitCode());

        Path pluginDir = Path.of(TEST_PLUGIN_DIR);
        assertTrue(Files.exists(pluginDir.resolve("pom.xml")));
        assertTrue(Files.exists(pluginDir.resolve("META-INF/MANIFEST.MF")));
        assertTrue(Files.exists(pluginDir.resolve("plugin.xml")));
        assertTrue(Files.exists(pluginDir.resolve("build.properties")));
        assertTrue(Files.exists(pluginDir.resolve("OSGI-INF")));
        assertTrue(Files.exists(pluginDir.resolve(".gitignore")));
        assertTrue(Files.exists(pluginDir.resolve(".project")));

        // Default platform version is 13 (latest) → Java 21
        String pom = Files.readString(pluginDir.resolve("pom.xml"));
        assertTrue(pom.contains("<maven.compiler.release>21</maven.compiler.release>"));
        assertTrue(pom.contains("<tycho.version>4.0.8</tycho.version>"));

        String manifest = Files.readString(pluginDir.resolve("META-INF/MANIFEST.MF"));
        assertTrue(manifest.contains("Bundle-RequiredExecutionEnvironment: JavaSE-21"));
        assertTrue(manifest.contains("bundle-version=\"13.0.0\""));
    }

    @Test
    @Order(2)
    @Launch({"init", "org.test.myplugin", "--standalone", "--with-callout", "--with-process"})
    void testInitPluginWithFeatures(LaunchResult result) {
        assertEquals(0, result.exitCode());

        Path pluginDir = Path.of(TEST_PLUGIN_DIR);
        Path srcDir = pluginDir.resolve("src/org/test/myplugin");

        assertTrue(Files.exists(srcDir.resolve("MypluginCallout.java")));
        assertTrue(Files.exists(srcDir.resolve("MypluginCalloutFactory.java")));
        assertTrue(Files.exists(srcDir.resolve("MypluginProcess.java")));
    }

    @Test
    @Order(3)
    @Launch({"init", "org.test.pluginv13", "--standalone", "--idempiere-version=12"})
    void testInitWithPlatformVersion12(LaunchResult result) throws IOException {
        assertEquals(0, result.exitCode());

        Path pluginDir = Path.of(TEST_PLUGIN_V13_DIR);
        String pom = Files.readString(pluginDir.resolve("pom.xml"));
        assertTrue(pom.contains("<maven.compiler.release>17</maven.compiler.release>"));
        assertTrue(pom.contains("<tycho.version>4.0.4</tycho.version>"));

        String manifest = Files.readString(pluginDir.resolve("META-INF/MANIFEST.MF"));
        assertTrue(manifest.contains("Bundle-RequiredExecutionEnvironment: JavaSE-17"));
        assertTrue(manifest.contains("bundle-version=\"12.0.0\""));
    }

    @Test
    @Order(4)
    @Launch(value = {"init", "--help"}, exitCode = 2)
    void testInitHelp(LaunchResult result) {
        String output = result.getOutput() + result.getErrorOutput();
        assertTrue(output.contains("--standalone"));
        assertTrue(output.contains("--with-fragment"));
        assertTrue(output.contains("--with-feature"));
        assertTrue(output.contains("--with-callout"));
        assertTrue(output.contains("--with-event-handler"));
        assertTrue(output.contains("--with-process"));
        assertTrue(output.contains("--with-zk-form"));
        assertTrue(output.contains("--with-report"));
        assertTrue(output.contains("--idempiere-version"));
        assertTrue(output.contains("--name"));
        assertTrue(output.contains("eclipse-project"));
    }
}
