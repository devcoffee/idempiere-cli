package org.idempiere.cli.util;

import picocli.CommandLine.Help.Ansi;

/**
 * Utility class for consistent colored CLI output.
 * Uses Picocli's Ansi.AUTO for automatic terminal detection.
 */
public final class CliOutput {

    private CliOutput() {}

    /** Format success message: green [OK] */
    public static String ok(String message) {
        return Ansi.AUTO.string("@|green [OK]|@ " + message);
    }

    /** Format failure message: red [FAIL] */
    public static String fail(String message) {
        return Ansi.AUTO.string("@|red [FAIL]|@ " + message);
    }

    /** Format warning message: yellow [WARN] */
    public static String warn(String message) {
        return Ansi.AUTO.string("@|yellow [WARN]|@ " + message);
    }

    /** Format tip message: cyan [TIP] */
    public static String tip(String message) {
        return Ansi.AUTO.string("@|cyan [TIP]|@ " + message);
    }

    /** Format info message: blue [INFO] */
    public static String info(String message) {
        return Ansi.AUTO.string("@|blue [INFO]|@ " + message);
    }

    /** Format error message: red [ERR] */
    public static String err(String message) {
        return Ansi.AUTO.string("@|red [ERR]|@ " + message);
    }

    /** Bold text */
    public static String bold(String text) {
        return Ansi.AUTO.string("@|bold " + text + "|@");
    }

    /** Green text */
    public static String green(String text) {
        return Ansi.AUTO.string("@|green " + text + "|@");
    }

    /** Red text */
    public static String red(String text) {
        return Ansi.AUTO.string("@|red " + text + "|@");
    }

    /** Yellow text */
    public static String yellow(String text) {
        return Ansi.AUTO.string("@|yellow " + text + "|@");
    }

    /** Cyan text */
    public static String cyan(String text) {
        return Ansi.AUTO.string("@|cyan " + text + "|@");
    }
}
