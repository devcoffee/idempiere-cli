package org.idempiere.cli.util;

/**
 * Standardized troubleshooting output for actionable CLI error messages.
 */
public final class Troubleshooting {

    private Troubleshooting() {
    }

    /**
     * Prints an actionable "How to resolve" block to stderr.
     */
    public static void printHowToResolve(String... steps) {
        if (steps == null || steps.length == 0) {
            return;
        }

        boolean printedAny = false;
        for (String step : steps) {
            if (step == null || step.isBlank()) {
                continue;
            }
            if (!printedAny) {
                System.err.println("How to resolve:");
                printedAny = true;
            }
            System.err.println("  - " + step);
        }

        if (printedAny) {
            System.err.println();
        }
    }
}
