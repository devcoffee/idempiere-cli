package org.idempiere.cli.service;

import org.idempiere.cli.model.GeneratedCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiResponseParserServiceTest {

    private final AiResponseParserService service = new AiResponseParserService();

    @Test
    void testParseFromMarkdownFence() {
        String response = """
                Here is your result:
                ```json
                {
                  "files": [
                    {"path": "src/Test.java", "content": "class Test {}"}
                  ]
                }
                ```
                """;

        GeneratedCode code = service.parse(response);
        assertNotNull(code);
        assertEquals(1, code.getFiles().size());
        assertEquals("src/Test.java", code.getFiles().get(0).getPath());
    }

    @Test
    void testParseInvalidReturnsNullAndError() {
        AiResponseParserService.ParseResult result = service.parseDetailed("not json");

        assertNull(result.code());
        assertNotNull(result.errorMessage());
        assertTrue(result.errorMessage().startsWith("Invalid JSON"));
    }
}
