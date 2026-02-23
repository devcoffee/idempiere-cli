package org.idempiere.cli.service;

/**
 * Abstraction for validating embedded AI provider availability from core commands.
 * Core builds can bind this to a no-op implementation, while experimental builds
 * can provide a real provider validation implementation.
 */
public interface AiStatusService {

    ValidationResult validateConfiguredProvider();

    record ValidationResult(boolean supported, boolean available, boolean passed, String errorMessage) {
        public static ValidationResult unsupported(String errorMessage) {
            return new ValidationResult(false, false, false, errorMessage);
        }

        public static ValidationResult unavailable(String errorMessage) {
            return new ValidationResult(true, false, false, errorMessage);
        }

        public static ValidationResult ok() {
            return new ValidationResult(true, true, true, null);
        }

        public static ValidationResult failure(String errorMessage) {
            return new ValidationResult(true, true, false, errorMessage);
        }
    }
}
