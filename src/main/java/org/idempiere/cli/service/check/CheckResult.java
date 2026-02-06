package org.idempiere.cli.service.check;

/**
 * Result of an environment or plugin check.
 */
public record CheckResult(String tool, Status status, String message) {

    public enum Status {
        OK, WARN, FAIL
    }

    public boolean isOk() {
        return status == Status.OK;
    }

    public boolean isFail() {
        return status == Status.FAIL;
    }

    public boolean isWarn() {
        return status == Status.WARN;
    }
}
