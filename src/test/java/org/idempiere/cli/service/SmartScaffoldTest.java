package org.idempiere.cli.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.idempiere.cli.model.GeneratedCode;
import org.idempiere.cli.model.ProjectContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SmartScaffoldTest {

    @Inject
    ScaffoldService scaffoldService;

    @Test
    void testParseAiResponseValidJson() {
        String json = """
                {
                  "files": [
                    {"path": "src/org/example/MyCallout.java", "content": "package org.example;\\npublic class MyCallout {}"}
                  ],
                  "manifest_additions": ["org.compiere.model"],
                  "build_properties_additions": []
                }
                """;

        GeneratedCode code = scaffoldService.parseAiResponse(json);
        assertNotNull(code);
        assertEquals(1, code.getFiles().size());
        assertEquals("src/org/example/MyCallout.java", code.getFiles().get(0).getPath());
        assertTrue(code.getFiles().get(0).getContent().contains("MyCallout"));
        assertEquals(1, code.getManifestAdditions().size());
    }

    @Test
    void testParseAiResponseWithMarkdownFences() {
        String json = """
                ```json
                {
                  "files": [
                    {"path": "src/Test.java", "content": "class Test {}"}
                  ]
                }
                ```
                """;

        GeneratedCode code = scaffoldService.parseAiResponse(json);
        assertNotNull(code);
        assertEquals(1, code.getFiles().size());
    }

    @Test
    void testParseAiResponseInvalidJson() {
        GeneratedCode code = scaffoldService.parseAiResponse("this is not json");
        assertNull(code);
    }

    @Test
    void testParseAiResponseEmpty() {
        GeneratedCode code = scaffoldService.parseAiResponse("");
        assertNull(code);
    }

    @Test
    void testValidateGeneratedCodeClean() {
        GeneratedCode code = new GeneratedCode();
        GeneratedCode.GeneratedFile file = new GeneratedCode.GeneratedFile(
                "src/org/example/plugin/MyCallout.java",
                "package org.example.plugin;\npublic class MyCallout {}"
        );
        code.setFiles(List.of(file));

        List<String> issues = scaffoldService.validateGeneratedCode(code, "org.example.plugin");
        assertTrue(issues.isEmpty());
    }

    @Test
    void testValidateGeneratedCodePathTraversal() {
        GeneratedCode code = new GeneratedCode();
        GeneratedCode.GeneratedFile file = new GeneratedCode.GeneratedFile(
                "../../../etc/passwd",
                "malicious"
        );
        code.setFiles(List.of(file));

        List<String> issues = scaffoldService.validateGeneratedCode(code, "org.example");
        assertFalse(issues.isEmpty());
        assertTrue(issues.get(0).contains("Path traversal"));
    }

    @Test
    void testValidateGeneratedCodeWrongPackage() {
        GeneratedCode code = new GeneratedCode();
        GeneratedCode.GeneratedFile file = new GeneratedCode.GeneratedFile(
                "src/org/wrong/MyClass.java",
                "package org.wrong;\npublic class MyClass {}"
        );
        code.setFiles(List.of(file));

        List<String> issues = scaffoldService.validateGeneratedCode(code, "org.example.plugin");
        assertFalse(issues.isEmpty());
        assertTrue(issues.get(0).contains("Unexpected package"));
    }

    @Test
    void testValidateGeneratedCodeEmptyContent() {
        GeneratedCode code = new GeneratedCode();
        GeneratedCode.GeneratedFile file = new GeneratedCode.GeneratedFile(
                "resources/empty.xml",
                ""
        );
        code.setFiles(List.of(file));

        List<String> issues = scaffoldService.validateGeneratedCode(code, "org.example");
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(i -> i.contains("Empty content")));
    }

    @Test
    void testBuildAiPrompt() {
        ProjectContext ctx = ProjectContext.builder()
                .pluginId("org.example.myplugin")
                .basePackage("org.example.myplugin")
                .version("1.0.0")
                .hasActivator(true)
                .hasCalloutFactory(false)
                .usesAnnotationPattern(true)
                .existingClasses(List.of("MyProcess", "MyCallout"))
                .build();

        String prompt = scaffoldService.buildAiPrompt(
                "# Generate a callout",
                ctx, "callout", "OrderValidator",
                Map.of("tableName", "C_Order")
        );

        assertTrue(prompt.contains("org.example.myplugin"));
        assertTrue(prompt.contains("OrderValidator"));
        assertTrue(prompt.contains("callout"));
        assertTrue(prompt.contains("Generate a callout"));
        assertTrue(prompt.contains("Has Activator: true"));
        assertTrue(prompt.contains("MyProcess"));
        assertTrue(prompt.contains("tableName"));
        assertTrue(prompt.contains("JSON"));
    }
}
