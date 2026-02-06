package org.idempiere.cli.service.check;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.service.ProcessRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks for Docker installation (optional).
 */
@ApplicationScoped
public class DockerCheck implements EnvironmentCheck {

    private static final Pattern VERSION_PATTERN = Pattern.compile("Docker version (\\S+)");
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    @Inject
    ProcessRunner processRunner;

    @Override
    public String toolName() {
        return "Docker";
    }

    @Override
    public boolean isRequired() {
        return false; // Docker is optional
    }

    @Override
    public CheckResult check() {
        ProcessRunner.RunResult result = processRunner.run("docker", "--version");
        if (result.exitCode() < 0 || result.output() == null) {
            String msg = "Not found (optional)";
            return new CheckResult(toolName(), CheckResult.Status.WARN, msg);
        }

        String version = "Found";
        Matcher matcher = VERSION_PATTERN.matcher(result.output());
        if (matcher.find()) {
            version = "Version " + matcher.group(1);
        }

        // Check if daemon is running
        ProcessRunner.RunResult infoResult = processRunner.run("docker", "info");
        if (!infoResult.isSuccess()) {
            if (IS_WINDOWS && !isDockerDesktopInstalled()) {
                String msg = "CLI found (possibly from WSL) but Docker Desktop is not installed (optional)";
                return new CheckResult(toolName(), CheckResult.Status.WARN, msg);
            }
            String msg = version + " installed, but daemon is not running";
            return new CheckResult(toolName(), CheckResult.Status.WARN, msg);
        }

        String msg = version + " detected, daemon running";
        return new CheckResult(toolName(), CheckResult.Status.OK, msg);
    }

    private boolean isDockerDesktopInstalled() {
        String programFiles = System.getenv("ProgramFiles");
        if (programFiles == null) programFiles = "C:\\Program Files";
        return Files.exists(Path.of(programFiles, "Docker", "Docker", "Docker Desktop.exe"));
    }

    @Override
    public FixSuggestion getFixSuggestion(String os) {
        return FixSuggestion.builder()
                .brewCask("docker")
                .apt("docker.io")
                .dnf("docker")
                .pacman("docker")
                .zypper("docker")
                .winget("Docker.DockerDesktop")
                .url("https://www.docker.com/products/docker-desktop")
                .build();
    }
}
