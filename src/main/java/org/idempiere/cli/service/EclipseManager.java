package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.model.SetupConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class EclipseManager {

    private static final String ECLIPSE_VERSION = "2025-09";
    private static final String ECLIPSE_RELEASE = "R";
    private static final String ECLIPSE_MIRROR = "https://download.eclipse.org/technology/epp/downloads/release/"
            + ECLIPSE_VERSION + "/" + ECLIPSE_RELEASE + "/";

    // Pinned repository versions matching hengsin/idempiere-dev-setup setup-ws.sh
    private static final String XTEXT_REPO = "https://download.eclipse.org/modeling/tmf/xtext/updates/releases/2.35.0";
    private static final String MWE_REPO = "https://download.eclipse.org/modeling/emft/mwe/updates/releases/2.18.0/";
    private static final String EMF_REPO = "https://download.eclipse.org/modeling/emf/emf/builds/release/2.38.0";
    private static final String TPD_REPO = "https://download.eclipse.org/cbi/updates/tpd/nightly/latest";
    private static final String LSP4E_REPO = "https://download.eclipse.org/lsp4e/releases/latest/";
    private static final String COPILOT_REPO = "https://azuredownloads-g3ahgwb5b8bkbxhd.b01.azurefd.net/github-copilot/";

    @Inject
    ProcessRunner processRunner;

    public boolean detectOrInstall(SetupConfig config) {
        Path eclipseDir = config.getEclipseDir();
        Path eclipseExe = getEclipseExecutable(eclipseDir);

        if (Files.exists(eclipseExe)) {
            System.out.println("  Eclipse found at: " + eclipseDir.toAbsolutePath());
            return true;
        }

        System.out.println("  Eclipse not found at: " + eclipseDir.toAbsolutePath());
        System.out.println("  Downloading Eclipse JEE " + ECLIPSE_VERSION + "...");

        return downloadAndExtract(config);
    }

    public boolean installPlugins(SetupConfig config) {
        Path eclipseExe = getEclipseExecutable(config.getEclipseDir());
        if (!Files.exists(eclipseExe)) {
            System.err.println("  Eclipse executable not found at: " + eclipseExe.toAbsolutePath());
            return false;
        }

        Path eclipseDir = config.getEclipseDir();
        String destination = getEclipseDestination(eclipseDir).toAbsolutePath().toString();
        Path sourceDir = config.getSourceDir();

        // Detect bundled JRE for -vm flag (same as setup-ws.sh)
        Path javaHome = detectJavaHome(eclipseDir);
        String vmArg = null;
        if (javaHome != null) {
            Path javaBin = javaHome.resolve("bin/java");
            if (Files.exists(javaBin)) {
                vmArg = javaBin.toAbsolutePath().toString();
            }
        }

        boolean success = true;

        // Install XText Runtime — replicates setup-ws.sh:
        // ./eclipse -vm "$ECLIPSE_JRE/bin/java" -nosplash -data "$IDEMPIERE_SOURCE_FOLDER"
        //   -application org.eclipse.equinox.p2.director
        //   -repository $XTEXT_RUNTIME_REPOSITORY,$MWE_REPOSITORY,$EMF_REPOSITORY
        //   -destination "$DESTINATION"
        //   -installIU "org.eclipse.xtext.runtime.feature.group,org.eclipse.xtext.ui.feature.group,
        //               org.eclipse.emf.mwe2.runtime,org.eclipse.emf.codegen.ecore.xtext,
        //               org.eclipse.emf.ecore.xcore.feature.group"
        System.out.println("  Installing XText Runtime...");
        String xtextRepos = XTEXT_REPO + "," + MWE_REPO + "," + EMF_REPO;
        String xtextIUs = "org.eclipse.xtext.runtime.feature.group,"
                + "org.eclipse.xtext.ui.feature.group,"
                + "org.eclipse.emf.mwe2.runtime,"
                + "org.eclipse.emf.codegen.ecore.xtext,"
                + "org.eclipse.emf.ecore.xcore.feature.group";

        int exitCode = runP2Director(eclipseExe, vmArg, sourceDir, destination, xtextRepos, xtextIUs);
        if (exitCode != 0) {
            System.err.println("  Warning: Failed to install XText plugins.");
            success = false;
        }

        // Install CBI Target Platform DSL Editor — replicates setup-ws.sh:
        // ./eclipse -vm ... -nosplash -data "$IDEMPIERE_SOURCE_FOLDER"
        //   -application org.eclipse.equinox.p2.director
        //   -repository $TARGETPLATFORM_DSL_REPOSITORY -destination "$DESTINATION"
        //   -installIU org.eclipse.cbi.targetplatform.feature.feature.group
        System.out.println("  Installing CBI Target Platform DSL Editor...");
        exitCode = runP2Director(eclipseExe, vmArg, sourceDir, destination,
                TPD_REPO, "org.eclipse.cbi.targetplatform.feature.feature.group");
        if (exitCode != 0) {
            System.err.println("  Warning: Failed to install Target Platform DSL plugin.");
            success = false;
        }

        // Install Copilot (optional) — replicates setup-ws.sh:
        // ./eclipse -vm ... -nosplash -data "$IDEMPIERE_SOURCE_FOLDER"
        //   -application org.eclipse.equinox.p2.director
        //   -repository $LSP4_REPOSITORY,$COPILOT_REPOSITORY -destination "$DESTINATION"
        //   -installIU "com.microsoft.copilot.eclipse.feature.feature.group"
        if (config.isInstallCopilot()) {
            System.out.println("  Installing GitHub Copilot plugin...");
            exitCode = runP2Director(eclipseExe, vmArg, sourceDir, destination,
                    LSP4E_REPO + "," + COPILOT_REPO,
                    "com.microsoft.copilot.eclipse.feature.feature.group");
            if (exitCode != 0) {
                System.err.println("  Warning: Failed to install Copilot plugin.");
            }
        }

        return success;
    }

    private int runP2Director(Path eclipseExe, String vm, Path dataDir, String destination,
                              String repository, String installIUs) {
        if (vm != null) {
            return processRunner.runLive(
                    eclipseExe.toString(),
                    "-vm", vm,
                    "-nosplash",
                    "-data", dataDir.toAbsolutePath().toString(),
                    "-application", "org.eclipse.equinox.p2.director",
                    "-repository", repository,
                    "-destination", destination,
                    "-installIU", installIUs
            );
        } else {
            return processRunner.runLive(
                    eclipseExe.toString(),
                    "-nosplash",
                    "-data", dataDir.toAbsolutePath().toString(),
                    "-application", "org.eclipse.equinox.p2.director",
                    "-repository", repository,
                    "-destination", destination,
                    "-installIU", installIUs
            );
        }
    }

    public boolean setupWorkspace(SetupConfig config) {
        Path sourceDir = config.getSourceDir();
        Path workspaceDir = config.getEclipseDir().resolve("workspace");

        try {
            Files.createDirectories(workspaceDir);
        } catch (IOException e) {
            System.err.println("  Failed to create workspace directory: " + e.getMessage());
            return false;
        }

        // Link projects into workspace metadata so Eclipse auto-discovers them on first launch.
        // This avoids the need for CDT headlessbuild (not in Eclipse JEE) or interactive import.
        System.out.println("  Linking iDempiere projects into Eclipse workspace...");

        try {
            Path projectsDir = workspaceDir.resolve(".metadata/.plugins/org.eclipse.core.resources/.projects");
            Files.createDirectories(projectsDir);

            int linked = 0;
            try (var dirs = Files.list(sourceDir)) {
                var projects = dirs.filter(Files::isDirectory)
                        .filter(d -> Files.exists(d.resolve(".project")))
                        .toList();

                for (Path projectDir : projects) {
                    String projectName = projectDir.getFileName().toString();
                    Path projectMetaDir = projectsDir.resolve(projectName);
                    Files.createDirectories(projectMetaDir);

                    Path locationFile = projectMetaDir.resolve(".location");
                    writeProjectLocation(locationFile, projectDir.toAbsolutePath().toString());
                    linked++;
                }
            }

            System.out.println("  Linked " + linked + " projects into workspace.");

            // Configure default workspace in Eclipse preferences (same as setup.sh)
            configureDefaultWorkspace(config, sourceDir);

            System.out.println("  Workspace configured at: " + workspaceDir.toAbsolutePath());
            return true;
        } catch (IOException e) {
            System.err.println("  Failed to configure workspace: " + e.getMessage());
            return false;
        }
    }

    private void configureDefaultWorkspace(SetupConfig config, Path sourceDir) throws IOException {
        // Replicate setup.sh IDE preference configuration:
        // IDE_PREFERENCE=$ECLIPSE/configuration/.settings/org.eclipse.ui.ide.prefs
        Path settingsDir = config.getEclipseDir().resolve("configuration/.settings");
        // On macOS the configuration is inside Eclipse.app
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            settingsDir = config.getEclipseDir().resolve("Eclipse.app/Contents/Eclipse/configuration/.settings");
        }
        Files.createDirectories(settingsDir);

        Path idePrefs = settingsDir.resolve("org.eclipse.ui.ide.prefs");
        if (!Files.exists(idePrefs)) {
            String content = "MAX_RECENT_WORKSPACES=10\n"
                    + "RECENT_WORKSPACES=" + sourceDir.toAbsolutePath() + "\n"
                    + "RECENT_WORKSPACES_PROTOCOL=3\n"
                    + "SHOW_RECENT_WORKSPACES=false\n"
                    + "SHOW_WORKSPACE_SELECTION_DIALOG=true\n"
                    + "eclipse.preferences.version=1\n";
            Files.writeString(idePrefs, content);
        }
    }

    private void writeProjectLocation(Path locationFile, String projectPath) throws IOException {
        String uri = "URI//" + Path.of(projectPath).toUri().toString();
        byte[] uriBytes = uri.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        // File header (magic bytes for Eclipse .location)
        bos.write(new byte[]{0x40, (byte) 0xB1, (byte) 0x8B, (byte) 0x81, 0x23, (byte) 0xBC, 0x00, 0x14,
                0x1A, 0x25, (byte) 0x96, (byte) 0xE7, (byte) 0xA3, (byte) 0x93, (byte) 0xBE, 0x1E});
        // URI length (2 bytes big-endian)
        bos.write((uriBytes.length >> 8) & 0xFF);
        bos.write(uriBytes.length & 0xFF);
        // URI string
        bos.write(uriBytes);
        // Trailer
        bos.write(new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});

        Files.write(locationFile, bos.toByteArray());
    }

    public Path detectJavaHome(Path eclipseDir) {
        // Look for bundled JustJ JRE in Eclipse plugins
        Path pluginsDir = getEclipsePluginsDir(eclipseDir);
        if (pluginsDir == null || !Files.exists(pluginsDir)) {
            return null;
        }

        try (var stream = Files.list(pluginsDir)) {
            Path justjPlugin = stream
                    .filter(p -> p.getFileName().toString().startsWith("org.eclipse.justj.openjdk.hotspot.jre.full"))
                    .filter(Files::isDirectory)
                    .findFirst()
                    .orElse(null);

            if (justjPlugin != null) {
                Path jre = justjPlugin.resolve("jre");
                if (Files.exists(jre)) {
                    return jre;
                }
            }
        } catch (IOException e) {
            // ignore
        }

        return null;
    }

    public Path getEclipseExecutable(Path eclipseDir) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            Path macApp = eclipseDir.resolve("Eclipse.app/Contents/MacOS/eclipse");
            if (Files.exists(macApp)) return macApp;
            return eclipseDir.resolve("eclipse");
        } else if (os.contains("win")) {
            return eclipseDir.resolve("eclipse.exe");
        }
        return eclipseDir.resolve("eclipse");
    }

    private Path getEclipseDestination(Path eclipseDir) {
        // On macOS, the Eclipse installation root is inside Eclipse.app/Contents/Eclipse
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            Path macDest = eclipseDir.resolve("Eclipse.app/Contents/Eclipse");
            if (Files.exists(macDest)) return macDest;
        }
        return eclipseDir;
    }

    private Path getEclipsePluginsDir(Path eclipseDir) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            Path macPlugins = eclipseDir.resolve("Eclipse.app/Contents/Eclipse/plugins");
            if (Files.exists(macPlugins)) return macPlugins;
        }
        Path plugins = eclipseDir.resolve("plugins");
        if (Files.exists(plugins)) return plugins;
        return null;
    }

    private boolean downloadAndExtract(SetupConfig config) {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();

        String fileName;
        if (os.contains("linux")) {
            fileName = "eclipse-jee-" + ECLIPSE_VERSION + "-" + ECLIPSE_RELEASE + "-linux-gtk-x86_64.tar.gz";
        } else if (os.contains("mac")) {
            String archSuffix = arch.contains("aarch64") || arch.contains("arm") ? "aarch64" : "x86_64";
            fileName = "eclipse-jee-" + ECLIPSE_VERSION + "-" + ECLIPSE_RELEASE + "-macosx-cocoa-" + archSuffix + ".tar.gz";
        } else if (os.contains("win")) {
            fileName = "eclipse-jee-" + ECLIPSE_VERSION + "-" + ECLIPSE_RELEASE + "-win32-x86_64.zip";
        } else {
            System.err.println("  Unsupported operating system: " + os);
            return false;
        }

        String downloadUrl = ECLIPSE_MIRROR + fileName;
        Path downloadDir = config.getEclipseDir().getParent();
        if (downloadDir == null) downloadDir = Path.of(".");
        Path archiveFile = downloadDir.resolve(fileName);

        try {
            Files.createDirectories(downloadDir);

            // Download using wget (same as setup.sh) or curl as fallback
            System.out.println("  Downloading: " + fileName);
            int exitCode;
            if (processRunner.isAvailable("wget")) {
                exitCode = processRunner.runLive("wget", "-q", "--show-progress", downloadUrl, "-O", archiveFile.toString());
            } else {
                exitCode = processRunner.runLive("curl", "-L", "-o", archiveFile.toString(), "-#", downloadUrl);
            }

            if (exitCode != 0) {
                System.err.println("  Failed to download Eclipse.");
                return false;
            }

            // Extract
            // On Linux, tar.gz contains eclipse/ subdirectory → extract to parent dir
            // On macOS, tar.gz contains Eclipse.app directly → extract to eclipseDir
            // On Windows, zip contains eclipse/ subdirectory → extract to parent dir
            System.out.println("  Extracting Eclipse...");
            Files.createDirectories(config.getEclipseDir());

            if (fileName.endsWith(".tar.gz")) {
                String extractTarget = os.contains("mac")
                        ? config.getEclipseDir().toString()
                        : downloadDir.toString();
                exitCode = processRunner.runLive(
                        "tar", "xzf", archiveFile.toString(),
                        "-C", extractTarget
                );
            } else if (fileName.endsWith(".zip")) {
                exitCode = processRunner.runLive(
                        "unzip", "-q", archiveFile.toString(),
                        "-d", downloadDir.toString()
                );
            }

            if (exitCode != 0) {
                System.err.println("  Failed to extract Eclipse.");
                return false;
            }

            // Clean up archive
            Files.deleteIfExists(archiveFile);

            System.out.println("  Eclipse installed at: " + config.getEclipseDir().toAbsolutePath());
            return true;
        } catch (IOException e) {
            System.err.println("  Failed to download/extract Eclipse: " + e.getMessage());
            return false;
        }
    }
}
