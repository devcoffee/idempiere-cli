package org.idempiere.cli.commands;

import org.idempiere.cli.service.ScaffoldResult;
import org.idempiere.cli.util.ExitCodes;

/**
 * Maps structured scaffold errors to process exit codes.
 */
public final class ExitCodeMapper {

    private ExitCodeMapper() {
    }

    public static int fromScaffold(ScaffoldResult result) {
        if (result.success()) {
            return ExitCodes.SUCCESS;
        }
        if (result.errorCode() == null) {
            return ExitCodes.VALIDATION_ERROR;
        }
        return switch (result.errorCode()) {
            case "IO_ERROR", "GENERATION_FAILED" -> ExitCodes.IO_ERROR;
            case "DIRECTORY_EXISTS" -> ExitCodes.STATE_ERROR;
            case "UNKNOWN_COMPONENT_TYPE" -> ExitCodes.VALIDATION_ERROR;
            default -> ExitCodes.VALIDATION_ERROR;
        };
    }
}
