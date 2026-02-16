package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates iDempiere server distribution packages (ZIP) from built source.
 *
 * <p>Produces distribution zips compatible with Jenkins/SourceForge format
 * and the install-idempiere Ansible playbook.
 */
@ApplicationScoped
public class DistService {

    private static final String PRODUCTS_RELATIVE_PATH =
            "org.idempiere.p2/target/products/org.adempiere.server.product";

    @Inject
    ProcessRunner processRunner;

    @Inject
    SessionLogger sessionLogger;

    record PlatformDist(String os, String ws, String arch) {
        String zipSuffix() {
            return ws + "." + os + "." + arch;
        }

        String dirPrefix() {
            return "idempiere." + zipSuffix();
        }

        Path productPath() {
            return Path.of(os, ws, arch);
        }

        String displayName() {
            return os + "/" + ws + "/" + arch;
        }
    }

    public boolean createDistribution(Path sourceDir, Path outputDir, String version,
                                       boolean skipBuild, boolean clean) {
        System.out.println();
        System.out.println("iDempiere Server Distribution");
        System.out.println("=============================");
        System.out.println();

        // Validate source directory
        if (!isIdempiereSource(sourceDir)) {
            System.err.println("Error: Not an iDempiere source directory: " + sourceDir.toAbsolutePath());
            System.err.println("Expected to find pom.xml and org.idempiere.p2/ in the directory.");
            return false;
        }

        // Auto-detect version if not specified
        if (version == null || version.isBlank()) {
            version = detectVersion(sourceDir);
        }

        System.out.println("  Source:  " + sourceDir.toAbsolutePath());
        System.out.println("  Output:  " + outputDir.toAbsolutePath());
        System.out.println("  Version: " + version);
        System.out.println();

        // Build if needed
        if (!skipBuild) {
            String goal = clean ? "clean verify" : "verify";
            System.out.print("  Building iDempiere (mvn " + goal + ")...");
            if (!buildSource(sourceDir, clean)) {
                return false;
            }
            System.out.println("  Build completed.");
            System.out.println();
        }

        // Discover platforms
        Path productsBase = sourceDir.resolve(PRODUCTS_RELATIVE_PATH);
        if (!Files.isDirectory(productsBase)) {
            System.err.println("Error: Products directory not found: " + productsBase);
            System.err.println("Make sure the build completed successfully (mvn verify).");
            return false;
        }

        List<PlatformDist> platforms = discoverPlatforms(productsBase);
        if (platforms.isEmpty()) {
            System.err.println("Error: No platform builds found in " + productsBase);
            return false;
        }

        System.out.println("  Found " + platforms.size() + " platform(s):");
        for (PlatformDist p : platforms) {
            System.out.println("    - " + p.displayName());
        }
        System.out.println();

        // Create output directory
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            System.err.println("Error: Cannot create output directory: " + e.getMessage());
            return false;
        }

        // Create zips
        List<Path> createdFiles = new ArrayList<>();
        for (PlatformDist platform : platforms) {
            System.out.print("  Packaging " + platform.displayName() + "...");
            Path zipFile = createPlatformZip(productsBase, outputDir, platform, version);
            if (zipFile != null) {
                createdFiles.add(zipFile);
                System.out.println(" done.");
            } else {
                System.err.println(" failed.");
            }
        }

        if (createdFiles.isEmpty()) {
            System.err.println("Error: No distribution packages were created.");
            return false;
        }

        // Generate checksums
        System.out.println();
        Path checksumFile = generateChecksums(outputDir, createdFiles);

        // Summary
        System.out.println();
        System.out.println("Distribution packages created:");
        System.out.println();
        for (Path file : createdFiles) {
            try {
                long sizeMb = Files.size(file) / (1024 * 1024);
                System.out.println("  " + file.getFileName() + " (" + sizeMb + " MB)");
            } catch (IOException e) {
                System.out.println("  " + file.getFileName());
            }
        }
        if (checksumFile != null) {
            System.out.println("  " + checksumFile.getFileName());
        }
        System.out.println();
        System.out.println("Output directory: " + outputDir.toAbsolutePath());
        System.out.println();

        return true;
    }

    boolean isIdempiereSource(Path dir) {
        return Files.exists(dir.resolve("pom.xml"))
                && Files.isDirectory(dir.resolve("org.idempiere.p2"));
    }

    List<PlatformDist> discoverPlatforms(Path productsBase) {
        List<PlatformDist> platforms = new ArrayList<>();

        // Structure: productsBase/<os>/<ws>/<arch>
        try (Stream<Path> osStream = Files.list(productsBase)) {
            osStream.filter(Files::isDirectory).forEach(osDir -> {
                try (Stream<Path> wsStream = Files.list(osDir)) {
                    wsStream.filter(Files::isDirectory).forEach(wsDir -> {
                        try (Stream<Path> archStream = Files.list(wsDir)) {
                            archStream.filter(Files::isDirectory)
                                    .filter(this::containsFiles)
                                    .forEach(archDir -> {
                                        String os = osDir.getFileName().toString();
                                        String ws = wsDir.getFileName().toString();
                                        String arch = archDir.getFileName().toString();
                                        platforms.add(new PlatformDist(os, ws, arch));
                                    });
                        } catch (IOException e) {
                            // skip
                        }
                    });
                } catch (IOException e) {
                    // skip
                }
            });
        } catch (IOException e) {
            // skip
        }

        return platforms;
    }

    private boolean containsFiles(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.findFirst().isPresent();
        } catch (IOException e) {
            return false;
        }
    }

    Path createPlatformZip(Path productsBase, Path outputDir, PlatformDist platform, String version) {
        Path platformDir = productsBase.resolve(platform.productPath());
        if (!Files.isDirectory(platformDir)) {
            return null;
        }

        String zipName = "idempiereServer" + version + "." + platform.zipSuffix() + ".zip";
        Path zipFile = outputDir.resolve(zipName);
        String zipRootDir = platform.dirPrefix() + "/idempiere-server";

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            Files.walk(platformDir).forEach(path -> {
                try {
                    Path relativePath = platformDir.relativize(path);
                    String entryName = zipRootDir + "/" + relativePath.toString().replace("\\", "/");

                    if (Files.isDirectory(path)) {
                        // Add directory entry (ensures empty dirs are preserved)
                        if (!relativePath.toString().isEmpty()) {
                            zos.putNextEntry(new ZipEntry(entryName + "/"));
                            zos.closeEntry();
                        }
                    } else {
                        zos.putNextEntry(new ZipEntry(entryName));
                        Files.copy(path, zos);
                        zos.closeEntry();
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to add to zip: " + path, e);
                }
            });

            return zipFile;
        } catch (Exception e) {
            System.err.println("  Error creating zip: " + e.getMessage());
            // Clean up partial file
            try {
                Files.deleteIfExists(zipFile);
            } catch (IOException ignored) {
            }
            return null;
        }
    }

    private Path generateChecksums(Path outputDir, List<Path> files) {
        Path checksumFile = outputDir.resolve("checksums.txt");
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();

            for (Path file : files) {
                byte[] fileBytes = Files.readAllBytes(file);
                byte[] hash = sha256.digest(fileBytes);
                sha256.reset();
                String hex = HexFormat.of().formatHex(hash);
                sb.append(hex).append("  ").append(file.getFileName()).append("\n");
            }

            Files.writeString(checksumFile, sb.toString());
            System.out.println("  Checksums written to " + checksumFile.getFileName());
            return checksumFile;
        } catch (NoSuchAlgorithmException | IOException e) {
            System.err.println("  Warning: Could not generate checksums: " + e.getMessage());
            return null;
        }
    }

    String detectVersion(Path sourceDir) {
        Path pomFile = sourceDir.resolve("pom.xml");
        if (!Files.exists(pomFile)) {
            return "dev";
        }

        try {
            String content = Files.readString(pomFile);
            // Match the first <version> tag (project version, not parent)
            Pattern p = Pattern.compile("<version>([^<]+)</version>");
            Matcher m = p.matcher(content);
            if (m.find()) {
                String ver = m.group(1);
                // Extract major version: "12.0.0-SNAPSHOT" -> "12"
                if (ver.contains(".")) {
                    return ver.split("\\.")[0];
                }
                return ver;
            }
        } catch (IOException e) {
            // ignore
        }
        return "dev";
    }

    private boolean buildSource(Path sourceDir, boolean clean) {
        String mvnCmd = detectMvnCommand(sourceDir);

        ProcessRunner.RunResult result;
        if (clean) {
            result = processRunner.runQuietInDir(sourceDir, mvnCmd, "clean", "verify");
        } else {
            result = processRunner.runQuietInDir(sourceDir, mvnCmd, "verify");
        }

        if (!result.isSuccess()) {
            sessionLogger.logError("Maven build failed (exit code: " + result.exitCode() + ")");
            sessionLogger.logCommandOutput("dist-build", result.output());
            System.err.println("  Build failed. See session log for details.");
            System.err.println("  Last 20 lines:");
            String[] lines = result.output().split("\n");
            int start = Math.max(0, lines.length - 20);
            for (int i = start; i < lines.length; i++) {
                System.err.println("    " + lines[i]);
            }
            return false;
        }

        return true;
    }

    private String detectMvnCommand(Path dir) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

        Path mvnwCmd = dir.resolve("mvnw.cmd");
        if (isWindows && Files.exists(mvnwCmd)) {
            return mvnwCmd.toAbsolutePath().toString();
        }

        if (!isWindows) {
            Path mvnw = dir.resolve("mvnw");
            if (Files.exists(mvnw)) {
                if (!Files.isExecutable(mvnw)) {
                    try {
                        mvnw.toFile().setExecutable(true);
                    } catch (Exception ignored) {
                    }
                }
                if (Files.isExecutable(mvnw)) {
                    return "./mvnw";
                }
            }
        }

        return isWindows ? "mvn.cmd" : "mvn";
    }
}
