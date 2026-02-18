package org.idempiere.cli.service;

import java.nio.file.Path;

/**
 * Structured result for scaffolding operations.
 */
public record ScaffoldResult(
        boolean success,
        String errorCode,
        String errorMessage,
        Path createdPath
) {

    public static ScaffoldResult ok(Path createdPath) {
        return new ScaffoldResult(true, null, null, createdPath);
    }

    public static ScaffoldResult ok() {
        return ok(null);
    }

    public static ScaffoldResult error(String errorCode, String errorMessage) {
        return new ScaffoldResult(false, errorCode, errorMessage, null);
    }
}
