package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.VersionProvider;
import org.idempiere.cli.service.ProcessRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Self-upgrade command to update the CLI to the latest version.
 *
 * <p>Downloads the latest release from GitHub and replaces the current binary.
 * Supports Linux, macOS, and Windows platforms.
 *
 * <h2>Example Usage</h2>
 * <pre>
 * # Check for updates
 * idempiere-cli upgrade --check
 *
 * # Upgrade to latest version
 * idempiere-cli upgrade
 *
 * # Upgrade to specific version
 * idempiere-cli upgrade --version=1.2.0
 * </pre>
 *
 * @see <a href="https://github.com/devcoffee/idempiere-cli/releases">GitHub Releases</a>
 */
@Command(
        name = "upgrade",
        description = "Upgrade idempiere-cli to the latest version",
        mixinStandardHelpOptions = true
)
public class UpgradeCommand implements Runnable {

    private static final String GITHUB_API = "https://api.github.com/repos/devcoffee/idempiere-cli/releases/latest";
    private static final String GITHUB_RELEASES = "https://github.com/devcoffee/idempiere-cli/releases/download";

    @Option(names = {"--check"}, description = "Only check for updates, don't install")
    boolean checkOnly;

    @Option(names = {"--version", "-v"}, description = "Upgrade to specific version (e.g., 1.2.0)")
    String targetVersion;

    @Option(names = {"--force", "-f"}, description = "Force upgrade even if already on latest version")
    boolean force;

    @Inject
    ProcessRunner processRunner;

    @Override
    public void run() {
        System.out.println();
        System.out.println("iDempiere CLI Upgrade");
        System.out.println("=====================");
        System.out.println();
        System.out.println("  Current version: " + VersionProvider.getApplicationVersion());

        try {
            String latestVersion = targetVersion != null ? targetVersion : fetchLatestVersion();
            if (latestVersion == null) {
                System.err.println("  Could not determine latest version.");
                return;
            }

            System.out.println("  Latest version:  " + latestVersion);
            System.out.println();

            if (!force && isUpToDate(VersionProvider.getApplicationVersion(), latestVersion)) {
                System.out.println("  Already up to date!");
                return;
            }

            if (checkOnly) {
                System.out.println("  Update available: " + VersionProvider.getApplicationVersion() + " -> " + latestVersion);
                System.out.println("  Run 'idempiere-cli upgrade' to install.");
                return;
            }

            // Detect platform and architecture
            String binaryName = detectBinaryName();
            if (binaryName == null) {
                System.err.println("  Unsupported platform.");
                return;
            }

            System.out.println("  Downloading " + binaryName + "...");

            String downloadUrl = GITHUB_RELEASES + "/v" + latestVersion + "/" + binaryName;
            Path tempFile = downloadBinary(downloadUrl);
            if (tempFile == null) {
                System.err.println("  Download failed.");
                return;
            }

            // Find current binary location
            Path currentBinary = findCurrentBinary();
            if (currentBinary == null) {
                System.err.println("  Could not determine current binary location.");
                System.err.println("  Downloaded file is at: " + tempFile);
                return;
            }

            // Backup and replace
            Path backupPath = currentBinary.resolveSibling(currentBinary.getFileName() + ".bak");
            System.out.println("  Backing up current binary to " + backupPath.getFileName() + "...");

            try {
                // On Windows, a running .exe can't be overwritten but CAN be renamed.
                // So: rename current -> .bak, then move new -> original name.
                Files.deleteIfExists(backupPath);
                Files.move(currentBinary, backupPath);
                Files.move(tempFile, currentBinary);

                // Make executable on Unix systems
                if (!isWindows()) {
                    Files.setPosixFilePermissions(currentBinary, Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE,
                            PosixFilePermission.GROUP_READ,
                            PosixFilePermission.GROUP_EXECUTE,
                            PosixFilePermission.OTHERS_READ,
                            PosixFilePermission.OTHERS_EXECUTE
                    ));
                }

                System.out.println();
                System.out.println("  Upgrade successful!");
                System.out.println("  Run 'idempiere-cli --version' to verify.");

            } catch (IOException e) {
                // Restore from backup if the move failed mid-way
                if (!Files.exists(currentBinary) && Files.exists(backupPath)) {
                    try { Files.move(backupPath, currentBinary); } catch (IOException ignored) {}
                }
                System.err.println("  Failed to replace binary: " + e.getMessage());
                System.err.println("  You may need to run with sudo/administrator privileges.");
                System.err.println("  Or manually copy from: " + tempFile);
            }

        } catch (Exception e) {
            System.err.println("  Error during upgrade: " + e.getMessage());
        }
    }

    private String fetchLatestVersion() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API))
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "idempiere-cli")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("  GitHub API returned status " + response.statusCode());
                return null;
            }

            // Simple JSON parsing for "tag_name": "v1.2.3"
            Pattern pattern = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([^\"]+)\"");
            Matcher matcher = pattern.matcher(response.body());
            if (matcher.find()) {
                return matcher.group(1);
            }

        } catch (Exception e) {
            System.err.println("  Failed to fetch latest version: " + e.getMessage());
        }
        return null;
    }

    private Path downloadBinary(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "idempiere-cli")
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                System.err.println("  Download failed with status " + response.statusCode());
                return null;
            }

            Path tempFile = Files.createTempFile("idempiere-cli-", isWindows() ? ".exe" : "");
            try (InputStream in = response.body()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            return tempFile;

        } catch (Exception e) {
            System.err.println("  Download error: " + e.getMessage());
            return null;
        }
    }

    private Path findCurrentBinary() {
        // Try to find the current binary location
        // 1. Check if running from a known location (GraalVM native image)
        String binaryPath = System.getProperty("org.graalvm.nativeimage.imagelocation");
        if (binaryPath != null) {
            return Path.of(binaryPath);
        }

        // 2. Try ProcessHandle to get current process command
        try {
            var command = ProcessHandle.current().info().command();
            if (command.isPresent()) {
                Path cmdPath = Path.of(command.get());
                if (Files.exists(cmdPath) && cmdPath.getFileName().toString().contains("idempiere-cli")) {
                    return cmdPath;
                }
            }
        } catch (Exception ignored) {
            // ProcessHandle may not work in all environments
        }

        // 3. Check current working directory
        String binaryName = isWindows() ? "idempiere-cli.exe" : "idempiere-cli";
        Path cwdBinary = Path.of(System.getProperty("user.dir"), binaryName);
        if (Files.exists(cwdBinary)) {
            return cwdBinary;
        }

        // 4. Try common installation paths
        String[] commonPaths = {
                "/usr/local/bin/idempiere-cli",
                System.getProperty("user.home") + "/.local/bin/idempiere-cli",
                System.getProperty("user.home") + "/bin/idempiere-cli",
                "C:\\Program Files\\idempiere-cli\\idempiere-cli.exe",
                System.getProperty("user.home") + "\\AppData\\Local\\idempiere-cli\\idempiere-cli.exe"
        };

        for (String path : commonPaths) {
            Path p = Path.of(path);
            if (Files.exists(p)) {
                return p;
            }
        }

        // 5. Try 'which' command on Unix or 'where' on Windows
        if (isWindows()) {
            ProcessRunner.RunResult result = processRunner.run("where.exe", "idempiere-cli");
            if (result.isSuccess() && !result.output().isBlank()) {
                // 'where' may return multiple paths, take the first one
                String firstPath = result.output().trim().split("\\r?\\n")[0];
                return Path.of(firstPath);
            }
            // Also try with .exe extension
            result = processRunner.run("where.exe", "idempiere-cli.exe");
            if (result.isSuccess() && !result.output().isBlank()) {
                String firstPath = result.output().trim().split("\\r?\\n")[0];
                return Path.of(firstPath);
            }
        } else {
            ProcessRunner.RunResult result = processRunner.run("which", "idempiere-cli");
            if (result.isSuccess() && !result.output().isBlank()) {
                return Path.of(result.output().trim());
            }
        }

        return null;
    }

    private String detectBinaryName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();

        String osName;
        if (os.contains("linux")) {
            osName = "linux";
        } else if (os.contains("mac") || os.contains("darwin")) {
            osName = "darwin";
        } else if (os.contains("win")) {
            osName = "windows";
        } else {
            return null;
        }

        String archName;
        if (arch.contains("amd64") || arch.contains("x86_64")) {
            archName = "amd64";
        } else if (arch.contains("aarch64") || arch.contains("arm64")) {
            archName = "arm64";
        } else {
            return null;
        }

        String extension = osName.equals("windows") ? ".exe" : "";
        return "idempiere-cli-" + osName + "-" + archName + extension;
    }

    private boolean isUpToDate(String current, String latest) {
        // Simple version comparison - assumes semver format
        if (current.contains("SNAPSHOT")) {
            return false; // SNAPSHOT is never up-to-date
        }
        return current.equals(latest);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
