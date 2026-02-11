package org.idempiere.cli.service.ai;

/**
 * Response from an AI generation request.
 */
public record AiResponse(boolean success, String content, String error) {

    public static AiResponse ok(String content) {
        return new AiResponse(true, content, null);
    }

    public static AiResponse fail(String error) {
        return new AiResponse(false, null, error);
    }
}
