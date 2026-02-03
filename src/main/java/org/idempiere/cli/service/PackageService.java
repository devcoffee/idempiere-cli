package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@ApplicationScoped
public class PackageService {

    @Inject
    ProcessRunner processRunner;

    public void packageZip(Path jarFile, String pluginId, String pluginVersion, Path pluginDir, Path outputDir) {
        try {
            Files.createDirectories(outputDir);

            String zipName = pluginId + "-" + pluginVersion + ".zip";
            Path zipPath = outputDir.resolve(zipName);

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
                // Add the jar
                addToZip(zos, jarFile.getFileName().toString(), jarFile);

                // Add MANIFEST.MF
                Path manifest = pluginDir.resolve("META-INF/MANIFEST.MF");
                if (Files.exists(manifest)) {
                    addToZip(zos, "META-INF/MANIFEST.MF", manifest);
                }

                // Add plugin.xml
                Path pluginXml = pluginDir.resolve("plugin.xml");
                if (Files.exists(pluginXml)) {
                    addToZip(zos, "plugin.xml", pluginXml);
                }

                // Add install instructions
                String readme = "Installation Instructions\n" +
                        "========================\n\n" +
                        "Plugin: " + pluginId + "\n" +
                        "Version: " + pluginVersion + "\n\n" +
                        "1. Copy the .jar file to your iDempiere plugins/ directory\n" +
                        "2. Restart iDempiere\n\n" +
                        "Or use hot deploy:\n" +
                        "  idempiere-cli deploy --target /path/to/idempiere --hot\n";
                zos.putNextEntry(new ZipEntry("README.txt"));
                zos.write(readme.getBytes());
                zos.closeEntry();
            }

            System.out.println("  Created: " + zipPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("  Error creating zip: " + e.getMessage());
        }
    }

    public void packageP2(Path pluginDir, Path outputDir) {
        System.out.println("  Generating p2 update site...");

        // Detect Maven
        String mvnCmd = "mvn";
        Path mvnw = pluginDir.resolve("mvnw");
        if (Files.exists(mvnw)) {
            if (!Files.isExecutable(mvnw)) {
                try {
                    mvnw.toFile().setExecutable(true);
                } catch (Exception ignored) {
                }
            }
            if (Files.isExecutable(mvnw)) {
                mvnCmd = "./mvnw";
            }
        } else if (Files.exists(pluginDir.resolve("mvnw.cmd"))) {
            mvnCmd = "mvnw.cmd";
        }

        ProcessRunner.RunResult result = processRunner.runInDir(pluginDir,
                mvnCmd, "tycho-p2-repository:assemble-repository");

        if (result.exitCode() != 0) {
            System.err.println("  Error: p2 repository generation failed.");
            System.err.println("  Ensure the plugin has a proper Tycho build configuration.");
            return;
        }

        Path repoDir = pluginDir.resolve("target/repository");
        if (Files.exists(repoDir)) {
            try {
                Files.createDirectories(outputDir);
                copyDirectory(repoDir, outputDir.resolve("repository"));
                System.out.println("  Created p2 repository at: " + outputDir.resolve("repository").toAbsolutePath());
            } catch (IOException e) {
                System.err.println("  Error copying p2 repository: " + e.getMessage());
            }
        } else {
            System.err.println("  Error: target/repository not found after build.");
        }
    }

    private void addToZip(ZipOutputStream zos, String entryName, Path file) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        Files.copy(file, zos);
        zos.closeEntry();
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(src -> {
            Path dest = target.resolve(source.relativize(src));
            try {
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(src, dest);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
