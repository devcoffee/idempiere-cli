package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Executes external processes with various output modes (live, quiet, captured).
 *
 * <p>Supports configurable timeout via {@code idempiere.process.timeout} property (in seconds).
 * Default timeout is 300 seconds (5 minutes). Use 0 for no timeout.
 */
@ApplicationScoped
public class ProcessRunner {

    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    @Inject
    SessionLogger sessionLogger;

    @ConfigProperty(name = "idempiere.process.timeout", defaultValue = "300")
    int defaultTimeoutSeconds;

    public record RunResult(int exitCode, String output) {
        public boolean isSuccess() {
            return exitCode == 0;
        }

        public boolean isTimeout() {
            return exitCode == -2;
        }
    }

    public RunResult run(String... command) {
        return runInDir(null, command);
    }

    /**
     * On Windows, wrap the command with "cmd /c" to ensure .cmd/.bat files are resolved.
     * This is needed because ProcessBuilder doesn't automatically resolve extensions on Windows.
     */
    private String[] wrapCommandForWindows(String... command) {
        if (!IS_WINDOWS || command.length == 0) {
            return command;
        }
        // Create new array: cmd, /c, original command...
        String[] wrapped = new String[command.length + 2];
        wrapped[0] = "cmd";
        wrapped[1] = "/c";
        System.arraycopy(command, 0, wrapped, 2, command.length);
        return wrapped;
    }

    public RunResult runInDir(Path workDir, String... command) {
        return runInDirWithTimeout(workDir, defaultTimeoutSeconds, command);
    }

    public RunResult runInDirWithTimeout(Path workDir, int timeoutSeconds, String... command) {
        long startTime = System.currentTimeMillis();
        sessionLogger.logCommand(workDir, command);

        try {
            String[] effectiveCommand = wrapCommandForWindows(command);
            ProcessBuilder pb = new ProcessBuilder(effectiveCommand);
            pb.redirectErrorStream(true);
            if (workDir != null) {
                pb.directory(workDir.toFile());
            }
            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            // Read output in a separate thread to avoid blocking
            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (IOException e) {
                    // ignore
                }
            });
            reader.start();

            boolean completed;
            if (timeoutSeconds > 0) {
                completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            } else {
                process.waitFor();
                completed = true;
            }

            if (!completed) {
                process.destroyForcibly();
                reader.join(1000);
                sessionLogger.logCommandResult(-2, System.currentTimeMillis() - startTime);
                return new RunResult(-2, output + "\n[TIMEOUT after " + timeoutSeconds + "s]");
            }

            reader.join();
            int exitCode = process.exitValue();
            sessionLogger.logCommandResult(exitCode, System.currentTimeMillis() - startTime);
            return new RunResult(exitCode, output.toString());
        } catch (Exception e) {
            sessionLogger.logCommandResult(-1, System.currentTimeMillis() - startTime);
            return new RunResult(-1, e.getMessage());
        }
    }

    public int runLive(String... command) {
        return runLiveInDir(null, command);
    }

    /**
     * Run a command with live output and no timeout.
     * Useful for long-running installations like package managers.
     */
    public int runLiveNoTimeout(String... command) {
        return runLiveInDirWithTimeout(null, null, 0, command);
    }

    public int runLiveInDir(Path workDir, String... command) {
        return runLiveInDir(workDir, null, command);
    }

    public int runLiveInDir(Path workDir, Map<String, String> env, String... command) {
        return runLiveInDirWithTimeout(workDir, env, defaultTimeoutSeconds, command);
    }

    public int runLiveInDirWithTimeout(Path workDir, Map<String, String> env, int timeoutSeconds, String... command) {
        long startTime = System.currentTimeMillis();
        sessionLogger.logCommand(workDir, command);

        try {
            String[] effectiveCommand = wrapCommandForWindows(command);
            ProcessBuilder pb = new ProcessBuilder(effectiveCommand);
            pb.inheritIO();
            if (workDir != null) {
                pb.directory(workDir.toFile());
            }
            if (env != null) {
                pb.environment().putAll(env);
            }
            Process process = pb.start();

            boolean completed;
            if (timeoutSeconds > 0) {
                completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            } else {
                process.waitFor();
                completed = true;
            }

            if (!completed) {
                process.destroyForcibly();
                System.err.println("\n[TIMEOUT after " + timeoutSeconds + "s]");
                sessionLogger.logCommandResult(-2, System.currentTimeMillis() - startTime);
                return -2;
            }

            int exitCode = process.exitValue();
            sessionLogger.logCommandResult(exitCode, System.currentTimeMillis() - startTime);
            return exitCode;
        } catch (Exception e) {
            System.err.println("Error executing: " + String.join(" ", command));
            System.err.println("  " + e.getMessage());
            sessionLogger.logCommandResult(-1, System.currentTimeMillis() - startTime);
            return -1;
        }
    }

    /**
     * Run a command with live output, sending input to stdin.
     * Useful for scripts that have interactive prompts like "Press enter to continue..."
     */
    public int runLiveInDirWithInput(Path workDir, Map<String, String> env, String input, String... command) {
        long startTime = System.currentTimeMillis();
        sessionLogger.logCommand(workDir, command);

        try {
            String[] effectiveCommand = wrapCommandForWindows(command);
            ProcessBuilder pb = new ProcessBuilder(effectiveCommand);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            if (workDir != null) {
                pb.directory(workDir.toFile());
            }
            if (env != null) {
                pb.environment().putAll(env);
            }
            Process process = pb.start();

            // Write input to stdin in a separate thread
            if (input != null) {
                Thread inputThread = new Thread(() -> {
                    try (OutputStream os = process.getOutputStream()) {
                        os.write(input.getBytes());
                        os.flush();
                    } catch (IOException e) {
                        // Ignore - process may have ended
                    }
                });
                inputThread.start();
            }

            int exitCode = process.waitFor();
            sessionLogger.logCommandResult(exitCode, System.currentTimeMillis() - startTime);
            return exitCode;
        } catch (Exception e) {
            System.err.println("Error executing: " + String.join(" ", command));
            System.err.println("  " + e.getMessage());
            sessionLogger.logCommandResult(-1, System.currentTimeMillis() - startTime);
            return -1;
        }
    }

    /**
     * Run a command quietly - captures output and only shows on failure.
     * Shows a simple progress indicator while running.
     */
    public RunResult runQuiet(String... command) {
        return runQuietInDirWithTimeout(null, 0, command);
    }

    /**
     * Run a command quietly with an explicit timeout (seconds).
     * Use timeoutSeconds <= 0 for no timeout.
     */
    public RunResult runQuietWithTimeout(int timeoutSeconds, String... command) {
        return runQuietInDirWithTimeout(null, timeoutSeconds, command);
    }

    public RunResult runQuietInDirWithEnv(Path workDir, Map<String, String> env, String... command) {
        return runQuietInternal(workDir, env, null, 0, command);
    }

    public RunResult runQuietInDirWithEnvAndInput(Path workDir, Map<String, String> env, String input, String... command) {
        return runQuietInternal(workDir, env, input, 0, command);
    }

    public RunResult runQuietInDir(Path workDir, String... command) {
        return runQuietInDirWithTimeout(workDir, 0, command);
    }

    /**
     * Run a command quietly in a directory with an explicit timeout (seconds).
     * Use timeoutSeconds <= 0 for no timeout.
     */
    public RunResult runQuietInDirWithTimeout(Path workDir, int timeoutSeconds, String... command) {
        return runQuietInternal(workDir, null, null, timeoutSeconds, command);
    }

    private RunResult runQuietInternal(Path workDir, Map<String, String> env, String input,
                                       int timeoutSeconds, String... command) {
        long startTime = System.currentTimeMillis();
        sessionLogger.logCommand(workDir, command);

        try {
            String[] effectiveCommand = wrapCommandForWindows(command);
            ProcessBuilder pb = new ProcessBuilder(effectiveCommand);
            pb.redirectErrorStream(true);
            if (workDir != null) {
                pb.directory(workDir.toFile());
            }
            if (env != null) {
                pb.environment().putAll(env);
            }
            Process process = pb.start();

            // Write input to stdin if provided
            if (input != null) {
                Thread inputThread = new Thread(() -> {
                    try (OutputStream os = process.getOutputStream()) {
                        os.write(input.getBytes());
                        os.flush();
                    } catch (IOException e) {
                        // Ignore - process may have ended
                    }
                });
                inputThread.start();
            }

            StringBuilder output = new StringBuilder();
            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (IOException e) {
                    // ignore
                }
            });
            reader.start();

            // Show progress dots while waiting
            while (process.isAlive()) {
                System.out.print(".");
                System.out.flush();

                if (timeoutSeconds > 0 && System.currentTimeMillis() - startTime >= timeoutSeconds * 1000L) {
                    process.destroyForcibly();
                    reader.join(1000);
                    System.out.println();
                    sessionLogger.logCommandResult(-2, System.currentTimeMillis() - startTime);
                    return new RunResult(-2, output + "\n[TIMEOUT after " + timeoutSeconds + "s]");
                }

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.println();

            reader.join();
            int exitCode = process.waitFor();
            sessionLogger.logCommandResult(exitCode, System.currentTimeMillis() - startTime);
            return new RunResult(exitCode, output.toString());
        } catch (Exception e) {
            System.out.println();
            sessionLogger.logCommandResult(-1, System.currentTimeMillis() - startTime);
            return new RunResult(-1, e.getMessage());
        }
    }

    /**
     * Returns the default timeout in seconds configured via {@code idempiere.process.timeout}.
     */
    public int getDefaultTimeoutSeconds() {
        return defaultTimeoutSeconds;
    }

    public boolean isAvailable(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("which", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return true;
            }
            // Fallback for Windows
            pb = new ProcessBuilder("where", command);
            pb.redirectErrorStream(true);
            process = pb.start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public RunResult runWithEnv(Map<String, String> env, String... command) {
        return runWithEnvInDir(null, env, command);
    }

    public RunResult runWithEnvInDir(Path workDir, Map<String, String> env, String... command) {
        long startTime = System.currentTimeMillis();
        sessionLogger.logCommand(workDir, command);

        try {
            String[] effectiveCommand = wrapCommandForWindows(command);
            ProcessBuilder pb = new ProcessBuilder(effectiveCommand);
            pb.redirectErrorStream(true);
            if (workDir != null) {
                pb.directory(workDir.toFile());
            }
            if (env != null) {
                pb.environment().putAll(env);
            }
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();
            sessionLogger.logCommandResult(exitCode, System.currentTimeMillis() - startTime);
            return new RunResult(exitCode, output.toString());
        } catch (Exception e) {
            sessionLogger.logCommandResult(-1, System.currentTimeMillis() - startTime);
            return new RunResult(-1, e.getMessage());
        }
    }
}
