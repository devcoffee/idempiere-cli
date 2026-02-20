package org.idempiere.cli.commands;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusMainTest
class DeployCommandMultiModuleRootTest {

    private static final Path ROOT_DIR = Path.of("/tmp/idempiere-cli-test-deploy-mm-root");
    private static final Path BASE_DIR = ROOT_DIR.resolve("org.test.mm.base");
    private static final Path TARGET_HOME = Path.of("/tmp/idempiere-cli-test-deploy-mm-target");

    @BeforeEach
    void setup() throws IOException {
        deleteDir(ROOT_DIR);
        deleteDir(TARGET_HOME);

        Files.createDirectories(BASE_DIR.resolve("META-INF"));
        Files.createDirectories(BASE_DIR.resolve("target"));
        Files.createDirectories(TARGET_HOME.resolve("plugins"));

        Files.writeString(ROOT_DIR.resolve("pom.xml"),
                "<project>\n" +
                "  <packaging>pom</packaging>\n" +
                "  <modules>\n" +
                "    <module>org.test.mm.base</module>\n" +
                "  </modules>\n" +
                "</project>\n");

        Files.writeString(BASE_DIR.resolve("META-INF/MANIFEST.MF"),
                "Bundle-SymbolicName: org.test.mm.base\n" +
                        "Bundle-Version: 1.0.0.qualifier\n");
        Files.writeString(BASE_DIR.resolve("target/org.test.mm.base-1.0.0.jar"), "dummy");
    }

    @AfterEach
    void cleanup() throws IOException {
        deleteDir(ROOT_DIR);
        deleteDir(TARGET_HOME);
    }

    @Test
    @Launch(value = {
            "deploy",
            "--dir=/tmp/idempiere-cli-test-deploy-mm-root",
            "--target=/tmp/idempiere-cli-test-deploy-mm-target"
    }, exitCode = 0)
    void testDeployFromMultiModuleRootResolvesBaseModule(LaunchResult result) {
        assertEquals(0, result.exitCode());
        assertTrue(result.getOutput().contains("Resolved plugin module:"));
        assertTrue(Files.exists(TARGET_HOME.resolve("plugins/org.test.mm.base-1.0.0.jar")));
    }

    private void deleteDir(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
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
