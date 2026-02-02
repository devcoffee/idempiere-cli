package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        Path targetDir = pluginDir.resolve("target");
        if (!Files.exists(targetDir)) {
            return Optional.empty();
        }
        try (var stream = Files.list(targetDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().endsWith("-sources.jar"))
                    .filter(p -> !p.getFileName().toString().equals("classes.jar"))
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private String detectMvnCommand(Path dir) {
        // Check for wrapper in plugin directory
        Path mvnw = dir.resolve("mvnw");
        if (Files.exists(mvnw)) {
            return mvnw.toAbsolutePath().toString();
        }
        Path mvnwCmd = dir.resolve("mvnw.cmd");
        if (Files.exists(mvnwCmd)) {
            return mvnwCmd.toAbsolutePath().toString();
        }
        // Fallback to system Maven
        if (processRunner.isAvailable("mvn")) {
            return "mvn";
        }
        return null;
    }
}
