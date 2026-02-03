package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.model.SetupConfig;
import org.idempiere.cli.util.CliDefaults;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Scanner;

@ApplicationScoped
public class SourceManager {

    private static final String JYTHON_VERSION = CliDefaults.JYTHON_VERSION;
    private static final String JYTHON_URL = CliDefaults.JYTHON_MAVEN_BASE_URL
            + JYTHON_VERSION + "/jython-standalone-" + JYTHON_VERSION + ".jar";
    private static final String REST_REPO_URL = CliDefaults.IDEMPIERE_REST_REPO_URL;

    @Inject
    ProcessRunner processRunner;

    @Inject
    SessionLogger sessionLogger;

    public boolean cloneOrUpdate(SetupConfig config) {
        Path sourceDir = config.getSourceDir();

        if (Files.exists(sourceDir.resolve(".git"))) {
            System.out.println("  iDempiere source found at: " + sourceDir.toAbsolutePath());
            return updateSource(config);
        }

        if (Files.exists(sourceDir) && !isEmptyDir(sourceDir)) {
            sessionLogger.logError("Directory " + sourceDir.toAbsolutePath() + " exists but is not a git repository");
            System.out.println("  Directory " + sourceDir.toAbsolutePath() + " exists but is not a git repository.");
            System.out.println("  Skipping source clone.");
            return false;
        }

        if (!config.isNonInteractive()) {
            System.out.println("  iDempiere source not found at: " + sourceDir.toAbsolutePath());
            System.out.print("  Clone from " + config.getRepositoryUrl() + "? [Y/n] ");
            @SuppressWarnings("resource")
            Scanner scanner = new Scanner(System.in); // Do not close: would close System.in
            String answer = scanner.nextLine().trim();
            if (answer.equalsIgnoreCase("n") || answer.equalsIgnoreCase("no")) {
                System.out.println("  Skipping source clone.");
                return false;
            }
        }

        return cloneSource(config);
    }

    private boolean cloneSource(SetupConfig config) {
        System.out.println("  Cloning " + config.getRepositoryUrl() + " (branch: " + config.getBranch() + ")...");

        int exitCode = processRunner.runLive(
                "git", "clone", "--branch", config.getBranch(),
                "--depth", "1",
                config.getRepositoryUrl(),
                config.getSourceDir().toString()
        );

        if (exitCode != 0) {
            sessionLogger.logError("Failed to clone iDempiere repository (exit code: " + exitCode + ")");
            System.err.println("  Failed to clone iDempiere repository.");
            return false;
        }

        if (config.isIncludeRest()) {
            return cloneRest(config);
        }

        return true;
    }

    private boolean updateSource(SetupConfig config) {
        System.out.println("  Updating iDempiere source...");

        Path sourceDir = config.getSourceDir();

        int exitCode = processRunner.runLiveInDir(sourceDir, "git", "fetch", "--all");
        if (exitCode != 0) {
            sessionLogger.logError("Failed to fetch from remote (exit code: " + exitCode + ")");
            System.err.println("  Failed to fetch from remote.");
            return false;
        }

        exitCode = processRunner.runLiveInDir(sourceDir, "git", "checkout", config.getBranch());
        if (exitCode != 0) {
            sessionLogger.logError("Failed to checkout branch: " + config.getBranch() + " (exit code: " + exitCode + ")");
            System.err.println("  Failed to checkout branch: " + config.getBranch());
            return false;
        }

        exitCode = processRunner.runLiveInDir(sourceDir, "git", "pull");
        if (exitCode != 0) {
            sessionLogger.logError("Failed to pull latest changes (exit code: " + exitCode + ")");
            System.err.println("  Failed to pull latest changes.");
            return false;
        }

        if (config.isIncludeRest()) {
            return cloneRest(config);
        }

        return true;
    }

    private boolean cloneRest(SetupConfig config) {
        Path restDir = config.getSourceDir().resolve("idempiere-rest");

        if (Files.exists(restDir.resolve(".git"))) {
            System.out.println("  Updating idempiere-rest...");
            int exitCode = processRunner.runLiveInDir(restDir, "git", "pull");
            return exitCode == 0;
        }

        System.out.println("  Cloning idempiere-rest...");
        int exitCode = processRunner.runLive(
                "git", "clone", "--depth", "1",
                REST_REPO_URL,
                restDir.toString()
        );

        if (exitCode != 0) {
            System.err.println("  Failed to clone idempiere-rest. Continuing without REST support.");
            return true;
        }

        return true;
    }

    public boolean buildSource(SetupConfig config) {
        Path sourceDir = config.getSourceDir();

        if (!Files.exists(sourceDir.resolve("pom.xml"))) {
            sessionLogger.logError("No pom.xml found in " + sourceDir.toAbsolutePath());
            System.err.println("  No pom.xml found in " + sourceDir.toAbsolutePath());
            return false;
        }

        System.out.println("  Running Maven build (mvn verify)...");
        System.out.println("  This may take several minutes on first build.");
        System.out.print("  ");

        String mvnCmd = detectMvnCommand(sourceDir);

        // Full reactor build from root, same as hengsin/idempiere-dev-setup setup.sh:
        //   cd "$IDEMPIERE_SOURCE_FOLDER" && ./mvnw verify
        ProcessRunner.RunResult result = processRunner.runQuietInDir(sourceDir, mvnCmd, "verify");

        if (!result.isSuccess()) {
            // Log error and full output to session log
            sessionLogger.logError("Maven build failed (exit code: " + result.exitCode() + ")");
            sessionLogger.logCommandOutput("maven-build", result.output());
            System.err.println("  Maven build failed. See session log for details.");
            // Show last 30 lines as summary on screen
            System.err.println("  Last 30 lines:");
            String[] lines = result.output().split("\n");
            int start = Math.max(0, lines.length - 30);
            for (int i = start; i < lines.length; i++) {
                System.err.println("    " + lines[i]);
            }
            return false;
        }

        return true;
    }

    public boolean downloadJython(SetupConfig config) {
        Path sourceDir = config.getSourceDir();
        Path libDir = sourceDir.resolve("lib");
        Path jythonJar = libDir.resolve("jython-standalone-" + JYTHON_VERSION + ".jar");

        if (Files.exists(jythonJar)) {
            sessionLogger.logInfo("Jython " + JYTHON_VERSION + " already downloaded at " + jythonJar);
            System.out.println("  Jython " + JYTHON_VERSION + " already downloaded.");
            return true;
        }

        sessionLogger.logInfo("Downloading Jython " + JYTHON_VERSION + " from " + JYTHON_URL);
        System.out.println("  Downloading Jython " + JYTHON_VERSION + "...");

        long startTime = System.currentTimeMillis();
        try {
            Files.createDirectories(libDir);
            try (InputStream in = URI.create(JYTHON_URL).toURL().openStream()) {
                Files.copy(in, jythonJar, StandardCopyOption.REPLACE_EXISTING);
            }
            long duration = System.currentTimeMillis() - startTime;
            sessionLogger.logInfo("Jython downloaded successfully (" + duration + "ms)");
            System.out.println("  Downloaded: " + jythonJar);
            return true;
        } catch (IOException e) {
            sessionLogger.logError("Failed to download Jython: " + e.getMessage());
            System.err.println("  Failed to download Jython: " + e.getMessage());
            return false;
        }
    }

    private String detectMvnCommand(Path dir) {
        // Check for Maven wrapper first
        Path mvnw = dir.resolve("mvnw");
        if (Files.exists(mvnw)) {
            // Make executable if needed (common issue after git clone)
            if (!Files.isExecutable(mvnw)) {
                try {
                    mvnw.toFile().setExecutable(true);
                } catch (Exception ignored) {
                    // Fall through to mvn
                }
            }
            if (Files.isExecutable(mvnw)) {
                return "./mvnw";
            }
        }
        // Check for Windows wrapper
        Path mvnwCmd = dir.resolve("mvnw.cmd");
        if (Files.exists(mvnwCmd)) {
            return "mvnw.cmd";
        }
        return "mvn";
    }

    private boolean isEmptyDir(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream.findFirst().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }
}
