package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.util.PluginUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds iDempiere plugins using Maven/Tycho.
 */
@ApplicationScoped
public class BuildService {

    @Inject
    ProcessRunner processRunner;

    public boolean build(Path pluginDir, Path idempiereHome, boolean clean, boolean skipTests) {
        String mvnCmd = detectMvnCommand(pluginDir);
        if (mvnCmd == null) {
            System.err.println("  Maven not found. Install Maven or add a Maven wrapper (mvnw) to your plugin.");
            return false;
        }

        List<String> args = new ArrayList<>();
        args.add(mvnCmd);
        if (clean) {
            args.add("clean");
        }
        args.add("verify");

        // Activate standalone build profile with iDempiere as p2 repository
        if (idempiereHome != null) {
            args.add("-Pstandalone-build");
            args.add("-Didempiere.home=" + idempiereHome.toAbsolutePath());
        }

        if (skipTests) {
            args.add("-DskipTests");
        }

        System.out.println("  Building plugin...");
        int exitCode = processRunner.runLiveInDir(pluginDir, args.toArray(new String[0]));

        if (exitCode != 0) {
            System.err.println("  Build failed with exit code " + exitCode);
            return false;
        }

        Optional<Path> jar = findBuiltJar(pluginDir);
        if (jar.isPresent()) {
            System.out.println();
            System.out.println("  Build successful: " + jar.get());
        } else {
            System.out.println();
            System.out.println("  Build completed but no .jar found in target/");
        }

        return true;
    }

    public Optional<Path> findBuiltJar(Path pluginDir) {
        return PluginUtils.findBuiltJar(pluginDir);
    }

    private String detectMvnCommand(Path dir) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

        // On Windows, check for .cmd wrapper first (Unix mvnw is a bash script)
        Path mvnwCmd = dir.resolve("mvnw.cmd");
        if (isWindows && Files.exists(mvnwCmd)) {
            return mvnwCmd.toAbsolutePath().toString();
        }

        // Check for Unix wrapper
        if (!isWindows) {
            Path mvnw = dir.resolve("mvnw");
            if (Files.exists(mvnw)) {
                if (!Files.isExecutable(mvnw)) {
                    try {
                        mvnw.toFile().setExecutable(true);
                    } catch (Exception ignored) {
                        // Fall through
                    }
                }
                if (Files.isExecutable(mvnw)) {
                    return mvnw.toAbsolutePath().toString();
                }
            }
        }

        // Check for .cmd even on non-Windows (unlikely but safe)
        if (!isWindows && Files.exists(mvnwCmd)) {
            return mvnwCmd.toAbsolutePath().toString();
        }

        // Fallback to system Maven
        // On Windows, use mvn.cmd explicitly to avoid issues with mvn.exe launcher
        if (isWindows) {
            if (processRunner.isAvailable("mvn.cmd")) {
                return "mvn.cmd";
            }
        } else {
            if (processRunner.isAvailable("mvn")) {
                return "mvn";
            }
        }
        return null;
    }
}
