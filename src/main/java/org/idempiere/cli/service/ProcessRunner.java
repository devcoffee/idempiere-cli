package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;

/**
 * Executes external processes with various output modes (live, quiet, captured).
 */
@ApplicationScoped
public class ProcessRunner {

    @Inject
    SessionLogger sessionLogger;

    public record RunResult(int exitCode, String output) {
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    public RunResult run(String... command) {
        return runInDir(null, command);
    }

    public RunResult runInDir(Path workDir, String... command) {
        long startTime = System.currentTimeMillis();
        sessionLogger.logCommand(workDir, command);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            if (workDir != null) {
                pb.directory(workDir.toFile());
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

    public int runLive(String... command) {
        return runLiveInDir(null, command);
    }

    public int runLiveInDir(Path workDir, String... command) {
        return runLiveInDir(workDir, null, command);
    }

    public int runLiveInDir(Path workDir, Map<String, String> env, String... command) {
        long startTime = System.currentTimeMillis();
        sessionLogger.logCommand(workDir, command);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.inheritIO();
            if (workDir != null) {
                pb.directory(workDir.toFile());
            }
            if (env != null) {
                pb.environment().putAll(env);
            }
            Process process = pb.start();
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
     * Run a command with live output, sending input to stdin.
     * Useful for scripts that have interactive prompts like "Press enter to continue..."
     */
    public int runLiveInDirWithInput(Path workDir, Map<String, String> env, String input, String... command) {
        long startTime = System.currentTimeMillis();
        sessionLogger.logCommand(workDir, command);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
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
        return runQuietInDir(null, command);
    }

    public RunResult runQuietInDirWithEnv(Path workDir, Map<String, String> env, String... command) {
        return runQuietInternal(workDir, env, null, command);
    }

    public RunResult runQuietInDirWithEnvAndInput(Path workDir, Map<String, String> env, String input, String... command) {
        return runQuietInternal(workDir, env, input, command);
    }

    public RunResult runQuietInDir(Path workDir, String... command) {
        return runQuietInternal(workDir, null, null, command);
    }

    private RunResult runQuietInternal(Path workDir, Map<String, String> env, String input, String... command) {
        long startTime = System.currentTimeMillis();
        sessionLogger.logCommand(workDir, command);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
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
            ProcessBuilder pb = new ProcessBuilder(command);
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
