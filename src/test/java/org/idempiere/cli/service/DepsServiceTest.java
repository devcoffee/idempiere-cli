package org.idempiere.cli.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class DepsServiceTest {

    @Inject
    DepsService depsService;

    private Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("deps-test-");
    }

    @AfterEach
    void cleanup() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    @Test
    void testRestPackageUsesSpecificBundleMapping() throws IOException {
        writeManifest("org.idempiere.rest.api");
        writeJava(
                "src/org/test/deps/RestUsage.java",
                "package org.test.deps;\n" +
                "import org.idempiere.rest.api.model.ErrorResponse;\n" +
                "public class RestUsage {}\n"
        );

        DepsService.DepsResult result = depsService.analyzeData(tempDir);

        assertTrue(result.requiredBundles().contains("org.idempiere.rest.api"));
        assertFalse(result.requiredBundles().contains("org.adempiere.base"));
        assertTrue(result.missingBundles().isEmpty());
    }

    @Test
    void testUnmappedImportsAreReported() throws IOException {
        writeManifest("org.adempiere.base");
        writeJava(
                "src/org/test/deps/UnmappedUsage.java",
                "package org.test.deps;\n" +
                "import org.example.custom.ExternalType;\n" +
                "public class UnmappedUsage {}\n"
        );

        DepsService.DepsResult result = depsService.analyzeData(tempDir);

        assertTrue(result.unmappedImports().contains("org.example.custom.ExternalType"));
    }

    @Test
    void testUiImportsRequireUiBundle() throws IOException {
        writeManifest("org.adempiere.base");
        writeJava(
                "src/org/test/deps/UiUsage.java",
                "package org.test.deps;\n" +
                "import org.compiere.model.PO;\n" +
                "import org.adempiere.webui.component.Window;\n" +
                "public class UiUsage {}\n"
        );

        DepsService.DepsResult result = depsService.analyzeData(tempDir);

        assertTrue(result.requiredBundles().contains("org.adempiere.base"));
        assertTrue(result.requiredBundles().contains("org.adempiere.ui.zk"));
        assertTrue(result.missingBundles().contains("org.adempiere.ui.zk"));
        assertFalse(result.missingBundles().contains("org.adempiere.base"));
    }

    private void writeManifest(String requireBundle) throws IOException {
        Files.createDirectories(tempDir.resolve("META-INF"));
        Files.writeString(
                tempDir.resolve("META-INF/MANIFEST.MF"),
                "Manifest-Version: 1.0\n" +
                "Bundle-ManifestVersion: 2\n" +
                "Bundle-SymbolicName: org.test.deps\n" +
                "Bundle-Version: 1.0.0\n" +
                "Bundle-RequiredExecutionEnvironment: JavaSE-17\n" +
                "Require-Bundle: " + requireBundle + "\n"
        );
    }

    private void writeJava(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
