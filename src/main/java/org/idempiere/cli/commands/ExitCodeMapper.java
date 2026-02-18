package org.idempiere.cli.commands;

import org.idempiere.cli.service.ScaffoldResult;

/**
 * Maps structured scaffold errors to process exit codes.
 */
public final class ExitCodeMapper {

    private ExitCodeMapper() {
    }

    public static final int SUCCESS = 0;
    public static final int VALIDATION_ERROR = 1;
    public static final int IO_ERROR = 2;
    public static final int STATE_ERROR = 3;

    public static int fromScaffold(ScaffoldResult result) {
        if (result.success()) {
            return SUCCESS;
        }
        if (result.errorCode() == null) {
            return VALIDATION_ERROR;
        }
        return switch (result.errorCode()) {
            case "IO_ERROR", "GENERATION_FAILED" -> IO_ERROR;
            case "DIRECTORY_EXISTS" -> STATE_ERROR;
            case "UNKNOWN_COMPONENT_TYPE" -> VALIDATION_ERROR;
            default -> VALIDATION_ERROR;
        };
    }
}
