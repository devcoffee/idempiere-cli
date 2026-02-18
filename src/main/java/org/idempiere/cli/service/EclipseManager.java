package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.model.SetupConfig;
import org.idempiere.cli.util.CliDefaults;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Downloads and configures Eclipse IDE for iDempiere development.
 */
@ApplicationScoped
public class EclipseManager {

    private static final String ECLIPSE_VERSION = CliDefaults.ECLIPSE_VERSION;
    private static final String ECLIPSE_RELEASE = CliDefaults.ECLIPSE_RELEASE;
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

    @Inject
    SessionLogger sessionLogger;

    public boolean detectOrInstall(SetupConfig config) {
        Path eclipseDir = config.getEclipseDir();
        Path eclipseExe = getEclipseExecutable(eclipseDir);

        if (Files.exists(eclipseExe)) {
            sessionLogger.logInfo("Eclipse found at " + eclipseDir.toAbsolutePath());
            System.out.println("  Eclipse found at: " + eclipseDir.toAbsolutePath());
            return true;
        }

        sessionLogger.logInfo("Eclipse not found, downloading Eclipse JEE " + ECLIPSE_VERSION);
        System.out.println("  Eclipse not found at: " + eclipseDir.toAbsolutePath());
        System.out.println("  Downloading Eclipse JEE " + ECLIPSE_VERSION + "...");

        return downloadAndExtract(config);
    }

    public boolean installPlugins(SetupConfig config) {
        Path eclipseExe = getEclipseExecutable(config.getEclipseDir());
        if (!Files.exists(eclipseExe)) {
            sessionLogger.logError("Eclipse executable not found at: " + eclipseExe.toAbsolutePath());
            System.err.println("  Eclipse executable not found at: " + eclipseExe.toAbsolutePath());
            return false;
        }

        Path eclipseDir = config.getEclipseDir();
        String destination = getEclipseDestination(eclipseDir).toAbsolutePath().toString();
        Path sourceDir = config.getSourceDir();

        // Detect bundled JRE for -vm flag (same as setup-ws.sh)
        String vmArg = detectVmArg(eclipseDir);

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
        ProcessRunner.RunResult result = runEclipseCommand(
                eclipseExe,
                vm,
                "-nosplash",
                "-data", dataDir.toAbsolutePath().toString(),
                "-application", "org.eclipse.equinox.p2.director",
                "-repository", repository,
                "-destination", destination,
                "-installIU", installIUs
        );

        // Only show output on failure
        if (!result.isSuccess()) {
            // Log error and full output to session log
            sessionLogger.logError("P2 Director failed (exit code: " + result.exitCode() + ")");
            sessionLogger.logCommandOutput("p2-director", result.output());
            System.err.println("  P2 Director failed. See session log for details.");
            // Show last 30 lines as summary on screen
            System.err.println("  Last 30 lines:");
            printLastLines(result.output(), 30);
        }
        return result.exitCode();
    }

    public boolean setupWorkspace(SetupConfig config) {
        Path sourceDir = config.getSourceDir();
        Path eclipseDir = config.getEclipseDir();

        try {
            // Count projects for info (top-level + nested repos like idempiere-rest)
            int projectCount = 0;
            try (var dirs = Files.list(sourceDir)) {
                for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                    if (Files.exists(dir.resolve(".project"))) {
                        projectCount++;
                    } else {
                        // Nested repository — count its children
                        try (var children = Files.list(dir)) {
                            projectCount += (int) children.filter(Files::isDirectory)
                                    .filter(d -> Files.exists(d.resolve(".project")))
                                    .count();
                        }
                    }
                }
            }

            sessionLogger.logInfo("Found " + projectCount + " Eclipse projects in source directory");
            System.out.println("  Found " + projectCount + " Eclipse projects in source directory.");

            // Configure default workspace in Eclipse preferences
            configureDefaultWorkspace(config, sourceDir);

            // Copy workspace setup scripts to lib directory (where Jython is)
            Path libDir = sourceDir.resolve("lib");
            if (!Files.exists(libDir)) {
                Files.createDirectories(libDir);
            }
            copyResourceToFile("eclipse/setup-ws.xml", libDir.resolve("setup-ws.xml"));
            copyResourceToFile("eclipse/loadtargetplatform.xml", libDir.resolve("loadtargetplatform.xml"));

            // Run Eclipse antRunner to import projects (same as hengsin/idempiere-dev-setup)
            Path eclipseExe = getEclipseExecutable(eclipseDir);
            if (!Files.exists(eclipseExe)) {
                sessionLogger.logError("Eclipse executable not found at: " + eclipseExe.toAbsolutePath());
                System.err.println("  Eclipse executable not found at: " + eclipseExe.toAbsolutePath());
                return false;
            }

            String vmArg = detectVmArg(eclipseDir);

            // Step 1: Import projects using setup-ws.xml
            System.out.println("  Importing projects into workspace...");
            boolean importOk = runAntRunner(eclipseExe, vmArg, sourceDir,
                    libDir.resolve("setup-ws.xml"), sourceDir);
            if (!importOk) {
                System.err.println("  Warning: Project import may have had issues. Check Eclipse manually.");
            }

            // Step 2: Load target platform using loadtargetplatform.xml
            System.out.println("  Loading target platform (this may take a while)...");
            boolean targetOk = runAntRunner(eclipseExe, vmArg, sourceDir,
                    libDir.resolve("loadtargetplatform.xml"), sourceDir);
            if (!targetOk) {
                System.err.println("  Warning: Target platform loading may have had issues. Check Eclipse manually.");
            }

            System.out.println("  Workspace configured at: " + sourceDir.toAbsolutePath());
            return importOk && targetOk;
        } catch (IOException e) {
            sessionLogger.logError("Failed to configure workspace: " + e.getMessage());
            System.err.println("  Failed to configure workspace: " + e.getMessage());
            return false;
        }
    }

    private void copyResourceToFile(String resourcePath, Path targetPath) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean runAntRunner(Path eclipseExe, String vm, Path dataDir, Path buildFile, Path idempiereDir) {
        String idempiereProperty = "-Didempiere=" + idempiereDir.toAbsolutePath().toString();
        ProcessRunner.RunResult result = runEclipseCommand(
                eclipseExe,
                vm,
                "-nosplash",
                "-data", dataDir.toAbsolutePath().toString(),
                "-application", "org.eclipse.ant.core.antRunner",
                "-buildfile", buildFile.toAbsolutePath().toString(),
                idempiereProperty
        );

        // Only show output on failure
        if (!result.isSuccess()) {
            // Log full output to session log
            sessionLogger.logCommandOutput("ant-runner", result.output());
            System.err.println("  Ant Runner failed. See session log for details.");
            // Show last 30 lines as summary on screen
            System.err.println("  Last 30 lines:");
            printLastLines(result.output(), 30);
        }
        return result.isSuccess();
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
            // Eclipse .prefs uses Java Properties format where backslashes are escape characters.
            // On Windows, paths must use forward slashes or escaped backslashes.
            String workspacePath = sourceDir.toAbsolutePath().toString().replace("\\", "/");
            String content = "MAX_RECENT_WORKSPACES=10\n"
                    + "RECENT_WORKSPACES=" + workspacePath + "\n"
                    + "RECENT_WORKSPACES_PROTOCOL=3\n"
                    + "SHOW_RECENT_WORKSPACES=false\n"
                    + "SHOW_WORKSPACE_SELECTION_DIALOG=true\n"
                    + "eclipse.preferences.version=1\n";
            Files.writeString(idePrefs, content);
        }
    }

    /**
     * Imports plugin projects (with .project files) into an Eclipse workspace.
     *
     * @param eclipseDir  Eclipse installation directory
     * @param workspaceDir Eclipse workspace directory (used as -data)
     * @param pluginDir   Plugin project directory to import
     * @return true if import succeeded
     */
    public boolean importProject(Path eclipseDir, Path workspaceDir, Path pluginDir) {
        Path eclipseExe = getEclipseExecutable(eclipseDir);
        if (!Files.exists(eclipseExe)) {
            sessionLogger.logError("Eclipse executable not found at: " + eclipseExe.toAbsolutePath());
            System.err.println("Error: Eclipse executable not found at: " + eclipseExe.toAbsolutePath());
            return false;
        }

        try {
            // Copy import script to a temp location alongside Jython
            Path libDir = workspaceDir.resolve("lib");
            if (!Files.exists(libDir)) {
                Files.createDirectories(libDir);
            }
            copyResourceToFile("eclipse/import-project.xml", libDir.resolve("import-project.xml"));

            String vmArg = detectVmArg(eclipseDir);

            System.out.println("  Importing projects into workspace...");
            boolean success = runAntRunnerWithProperty(eclipseExe, vmArg, workspaceDir,
                    libDir.resolve("import-project.xml"),
                    "pluginPath", pluginDir.toAbsolutePath().toString());

            if (success) {
                System.out.println("  Projects imported successfully.");
            } else {
                System.err.println("  Warning: Import may have had issues. Check Eclipse manually.");
            }
            return success;
        } catch (IOException e) {
            sessionLogger.logError("Failed to import projects: " + e.getMessage());
            System.err.println("Error: Failed to import projects: " + e.getMessage());
            return false;
        }
    }

    private boolean runAntRunnerWithProperty(Path eclipseExe, String vm, Path dataDir,
                                              Path buildFile, String propName, String propValue) {
        String property = "-D" + propName + "=" + propValue;

        ProcessRunner.RunResult result = runEclipseCommand(
                eclipseExe,
                vm,
                "-nosplash",
                "-data", dataDir.toAbsolutePath().toString(),
                "-application", "org.eclipse.ant.core.antRunner",
                "-buildfile", buildFile.toAbsolutePath().toString(),
                property
        );

        if (!result.isSuccess()) {
            sessionLogger.logCommandOutput("ant-runner-import", result.output());
            System.err.println("  Ant Runner failed. See session log for details.");
            printLastLines(result.output(), 30);
        }
        return result.isSuccess();
    }

    private String detectVmArg(Path eclipseDir) {
        Path javaHome = detectJavaHome(eclipseDir);
        if (javaHome == null) {
            return null;
        }
        Path javaBin = javaHome.resolve("bin/java");
        if (!Files.exists(javaBin)) {
            return null;
        }
        return javaBin.toAbsolutePath().toString();
    }

    private ProcessRunner.RunResult runEclipseCommand(Path eclipseExe, String vm, String... args) {
        List<String> command = new ArrayList<>();
        command.add(eclipseExe.toString());
        if (vm != null && !vm.isBlank()) {
            command.add("-vm");
            command.add(vm);
        }
        for (String arg : args) {
            command.add(arg);
        }
        return processRunner.runQuiet(command.toArray(new String[0]));
    }

    private void printLastLines(String output, int maxLines) {
        if (output == null || output.isEmpty()) {
            return;
        }
        String[] lines = output.split("\n");
        int start = Math.max(0, lines.length - maxLines);
        for (int i = start; i < lines.length; i++) {
            System.err.println("    " + lines[i]);
        }
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
            String archSuffix = arch.contains("aarch64") || arch.contains("arm") ? "aarch64" : "x86_64";
            fileName = "eclipse-jee-" + ECLIPSE_VERSION + "-" + ECLIPSE_RELEASE + "-linux-gtk-" + archSuffix + ".tar.gz";
        } else if (os.contains("mac")) {
            String archSuffix = arch.contains("aarch64") || arch.contains("arm") ? "aarch64" : "x86_64";
            fileName = "eclipse-jee-" + ECLIPSE_VERSION + "-" + ECLIPSE_RELEASE + "-macosx-cocoa-" + archSuffix + ".tar.gz";
        } else if (os.contains("win")) {
            fileName = "eclipse-jee-" + ECLIPSE_VERSION + "-" + ECLIPSE_RELEASE + "-win32-x86_64.zip";
        } else {
            sessionLogger.logError("Unsupported operating system: " + os);
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
                sessionLogger.logError("Failed to download Eclipse (exit code: " + exitCode + ")");
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
                sessionLogger.logError("Failed to extract Eclipse (exit code: " + exitCode + ")");
                System.err.println("  Failed to extract Eclipse.");
                return false;
            }

            // Clean up archive
            Files.deleteIfExists(archiveFile);

            System.out.println("  Eclipse installed at: " + config.getEclipseDir().toAbsolutePath());
            return true;
        } catch (IOException e) {
            sessionLogger.logError("Failed to download/extract Eclipse: " + e.getMessage());
            System.err.println("  Failed to download/extract Eclipse: " + e.getMessage());
            return false;
        }
    }
}
