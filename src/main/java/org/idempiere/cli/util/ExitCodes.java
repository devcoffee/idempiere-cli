package org.idempiere.cli.util;

/**
 * Process exit code contract used by idempiere-cli commands.
 */
public final class ExitCodes {

    private ExitCodes() {
    }

    public static final int SUCCESS = 0;
    public static final int VALIDATION_ERROR = 1;
    public static final int IO_ERROR = 2;
    public static final int STATE_ERROR = 3;
}
