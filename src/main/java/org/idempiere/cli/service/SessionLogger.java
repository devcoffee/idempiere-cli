package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Session logger for audit and troubleshooting purposes.
 * Creates a log file per CLI session in ~/.idempiere-cli/logs/
 */
@ApplicationScoped
public class SessionLogger {

    private static final DateTimeFormatter FILE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");
    private static final DateTimeFormatter LOG_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Path LOGS_DIR = Path.of(System.getProperty("user.home"), ".idempiere-cli", "logs");

    private volatile Path sessionLogFile;
    private volatile LocalDateTime sessionStart;
    private final AtomicInteger stepCounter = new AtomicInteger(0);
    private volatile boolean initialized = false;

    /**
     * Start a new logging session.
     *
     * @param command The command being executed (e.g., "setup-dev-env --with-docker")
     */
    public void startSession(String command) {
        this.sessionStart = LocalDateTime.now();
        this.stepCounter.set(0);
        this.initialized = true;

        try {
            Files.createDirectories(LOGS_DIR);

            String filename = "session-" + sessionStart.format(FILE_FORMAT) + ".log";
            this.sessionLogFile = LOGS_DIR.resolve(filename);

            // Write session header
            StringBuilder header = new StringBuilder();
            header.append("=== iDempiere CLI Session ===\n");
            header.append("Started: ").append(sessionStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
            header.append("Command: ").append(command).append("\n");
            header.append("Working Dir: ").append(System.getProperty("user.dir")).append("\n");
            header.append("OS: ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version"));
            header.append(", Java ").append(System.getProperty("java.version")).append("\n");
            header.append("User: ").append(System.getProperty("user.name")).append("\n");
            header.append("\n");

            Files.writeString(sessionLogFile, header.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // Update latest.log symlink
            updateLatestSymlink();

        } catch (IOException e) {
            // Non-critical - just disable logging for this session
            this.sessionLogFile = null;
            System.err.println("Warning: Could not create session log: " + e.getMessage());
        }
    }

    /**
     * Log the start of a step (e.g., "Setting up iDempiere source code")
     */
    public void logStep(int current, int total, String description) {
        if (!initialized || sessionLogFile == null) return;

        stepCounter.set(current);
        String entry = String.format("[%s] Step %d/%d: %s%n",
                LocalDateTime.now().format(LOG_FORMAT), current, total, description);
        appendToLog(entry);
    }

    /**
     * Log a command execution start.
     */
    public void logCommand(String... command) {
        if (!initialized || sessionLogFile == null) return;

        String entry = String.format("[%s] > %s%n",
                LocalDateTime.now().format(LOG_FORMAT), String.join(" ", command));
        appendToLog(entry);
    }

    /**
     * Log a command execution start with working directory.
     */
    public void logCommand(Path workDir, String... command) {
        if (!initialized || sessionLogFile == null) return;

        String entry = String.format("[%s] > [%s] %s%n",
                LocalDateTime.now().format(LOG_FORMAT),
                workDir != null ? workDir.toString() : ".",
                String.join(" ", command));
        appendToLog(entry);
    }

    /**
     * Log a command result.
     */
    public void logCommandResult(int exitCode, long durationMs) {
        if (!initialized || sessionLogFile == null) return;

        String duration = formatDuration(durationMs);
        String entry = String.format("[%s] < exit=%d (%s)%n",
                LocalDateTime.now().format(LOG_FORMAT), exitCode, duration);
        appendToLog(entry);
    }

    /**
     * Log command output (for failed commands or important outputs).
     * The output is indented and wrapped in markers for easy identification.
     */
    public void logCommandOutput(String label, String output) {
        if (!initialized || sessionLogFile == null) return;

        StringBuilder entry = new StringBuilder();
        entry.append(String.format("[%s] --- %s output start ---%n", LocalDateTime.now().format(LOG_FORMAT), label));

        // Indent each line of output
        if (output != null && !output.isEmpty()) {
            for (String line : output.split("\n")) {
                entry.append("    ").append(line).append("\n");
            }
        }

        entry.append(String.format("[%s] --- %s output end ---%n", LocalDateTime.now().format(LOG_FORMAT), label));
        appendToLog(entry.toString());
    }

    /**
     * Log an informational message.
     */
    public void logInfo(String message) {
        if (!initialized || sessionLogFile == null) return;

        String entry = String.format("[%s] %s%n",
                LocalDateTime.now().format(LOG_FORMAT), message);
        appendToLog(entry);
    }

    /**
     * Log an error message.
     */
    public void logError(String message) {
        if (!initialized || sessionLogFile == null) return;

        String entry = String.format("[%s] ERROR: %s%n",
                LocalDateTime.now().format(LOG_FORMAT), message);
        appendToLog(entry);
    }

    /**
     * Log step result.
     */
    public void logStepResult(boolean success, String component) {
        if (!initialized || sessionLogFile == null) return;

        String status = success ? "OK" : "FAILED";
        String entry = String.format("[%s] %s: %s%n",
                LocalDateTime.now().format(LOG_FORMAT), component, status);
        appendToLog(entry);
    }

    /**
     * End the session and write summary.
     */
    public void endSession(boolean success) {
        if (!initialized || sessionLogFile == null) return;

        LocalDateTime now = LocalDateTime.now();
        Duration duration = Duration.between(sessionStart, now);

        StringBuilder summary = new StringBuilder();
        summary.append("\n");
        summary.append("=== Session ").append(success ? "completed successfully" : "completed with errors").append(" ===\n");
        summary.append("Ended: ").append(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        summary.append("Duration: ").append(formatDuration(duration.toMillis())).append("\n");

        appendToLog(summary.toString());

        // Print log location to user
        System.out.println();
        System.out.println("Session log: " + sessionLogFile.toAbsolutePath());
    }

    /**
     * Get the current session log file path.
     */
    public Path getSessionLogFile() {
        return sessionLogFile;
    }

    /**
     * Check if logger is active.
     */
    public boolean isActive() {
        return initialized && sessionLogFile != null;
    }

    private void appendToLog(String content) {
        if (sessionLogFile == null) return;

        try {
            Files.writeString(sessionLogFile, content, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Ignore - non-critical
        }
    }

    private void updateLatestSymlink() {
        Path latestLink = LOGS_DIR.resolve("latest.log");
        try {
            Files.deleteIfExists(latestLink);
            Files.createSymbolicLink(latestLink, sessionLogFile.getFileName());
        } catch (IOException | UnsupportedOperationException e) {
            // Symlinks may not be supported on all systems - ignore
        }
    }

    private String formatDuration(long ms) {
        if (ms < 1000) {
            return ms + "ms";
        } else if (ms < 60000) {
            return String.format("%.1fs", ms / 1000.0);
        } else {
            long minutes = ms / 60000;
            long seconds = (ms % 60000) / 1000;
            return String.format("%dm%02ds", minutes, seconds);
        }
    }

    /**
     * Clean old log files, keeping only the most recent N files.
     */
    public void cleanOldLogs(int keepCount) {
        try {
            if (!Files.exists(LOGS_DIR)) return;

            var logFiles = Files.list(LOGS_DIR)
                    .filter(p -> p.getFileName().toString().startsWith("session-"))
                    .filter(p -> p.getFileName().toString().endsWith(".log"))
                    .sorted((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .toList();

            if (logFiles.size() > keepCount) {
                for (int i = keepCount; i < logFiles.size(); i++) {
                    Files.deleteIfExists(logFiles.get(i));
                }
            }
        } catch (IOException e) {
            // Ignore - non-critical
        }
    }
}
