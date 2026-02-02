package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

@ApplicationScoped
public class ProcessRunner {

    public record RunResult(int exitCode, String output) {
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    public RunResult run(String... command) {
        return runInDir(null, command);
    }

    public RunResult runInDir(Path workDir, String... command) {
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
            return new RunResult(exitCode, output.toString());
        } catch (Exception e) {
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
            return process.waitFor();
        } catch (Exception e) {
            System.err.println("Error executing: " + String.join(" ", command));
            System.err.println("  " + e.getMessage());
            return -1;
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
            return new RunResult(exitCode, output.toString());
        } catch (Exception e) {
            return new RunResult(-1, e.getMessage());
        }
    }
}
