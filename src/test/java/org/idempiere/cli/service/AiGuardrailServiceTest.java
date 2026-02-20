package org.idempiere.cli.service;

import org.idempiere.cli.model.GeneratedCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiGuardrailServiceTest {

    private final AiGuardrailService service = new AiGuardrailService();

    @Test
    void testValidatePathTraversal() {
        GeneratedCode code = new GeneratedCode();
        code.setFiles(List.of(new GeneratedCode.GeneratedFile("../../../etc/passwd", "malicious")));

        List<String> issues = service.validateGeneratedCode(code, "org.example");
        assertTrue(issues.stream().anyMatch(i -> i.contains("Path traversal")));
    }

    @Test
    void testValidateBlocksUnknownCriticalImport(@TempDir Path tempDir) throws IOException {
        Path pluginDir = setupPluginWithP2Repo(tempDir);

        GeneratedCode code = new GeneratedCode();
        code.setFiles(List.of(new GeneratedCode.GeneratedFile(
                "src/org/example/plugin/MyProcess.java",
                "package org.example.plugin;\n" +
                        "import org.compiere.process.SvrProcess;\n" +
                        "import org.idempiere.callout.annotation.Callout;\n" +
                        "public class MyProcess extends SvrProcess {}"
        )));

        List<String> issues = service.validateGeneratedCode(code, "org.example.plugin", pluginDir);
        assertTrue(issues.stream().anyMatch(i -> i.startsWith("BLOCKER: Unresolved import: org.idempiere.callout.annotation.Callout")));
        assertTrue(service.hasBlockingIssue(issues));
    }

    @Test
    void testValidateAllowsKnownCriticalImport(@TempDir Path tempDir) throws IOException {
        Path pluginDir = setupPluginWithP2Repo(tempDir);

        GeneratedCode code = new GeneratedCode();
        code.setFiles(List.of(new GeneratedCode.GeneratedFile(
                "src/org/example/plugin/MyProcess.java",
                "package org.example.plugin;\n" +
                        "import org.compiere.process.SvrProcess;\n" +
                        "public class MyProcess extends SvrProcess {}"
        )));

        List<String> issues = service.validateGeneratedCode(code, "org.example.plugin", pluginDir);
        assertFalse(service.hasBlockingIssue(issues));
    }

    private Path setupPluginWithP2Repo(Path tempDir) throws IOException {
        Path workspace = tempDir.resolve("ws");
        Path pluginDir = workspace.resolve("myplugin/org.example.plugin");
        Files.createDirectories(pluginDir.resolve("src"));

        Path repo = workspace.resolve("idempiere/org.idempiere.p2/target/repository");
        Path plugins = repo.resolve("plugins");
        Files.createDirectories(plugins);
        Files.writeString(repo.resolve("content.xml"), "<repository/>");
        createJarWithClasses(plugins.resolve("core.jar"), List.of(
                "org/compiere/process/SvrProcess.class",
                "org/compiere/model/MBPartner.class"
        ));

        return pluginDir;
    }

    private void createJarWithClasses(Path jar, List<String> classEntries) throws IOException {
        try (OutputStream os = Files.newOutputStream(jar);
             JarOutputStream jos = new JarOutputStream(os)) {
            for (String entry : classEntries) {
                jos.putNextEntry(new JarEntry(entry));
                jos.closeEntry();
            }
        }
    }
}
