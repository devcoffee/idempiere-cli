package org.idempiere.cli.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Utility helpers for consistent JSON command output.
 */
public final class JsonOutput {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonOutput() {
    }

    /**
     * Prints a standard JSON error object to stdout and returns exit code 1.
     */
    public static Integer printError(String code, String message) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            ObjectNode error = root.putObject("error");
            error.put("code", code);
            error.put("message", message);
            System.out.println(MAPPER.writeValueAsString(root));
        } catch (Exception ignored) {
            // Last-resort fallback to keep JSON contract for scripts.
            System.out.println("{\"error\":{\"code\":\"INTERNAL_ERROR\",\"message\":\"Failed to serialize JSON error\"}}");
        }
        return 1;
    }
}
