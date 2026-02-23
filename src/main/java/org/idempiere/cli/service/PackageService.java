package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Packages plugins for distribution (ZIP or P2 update site).
 */
@ApplicationScoped
public class PackageService {

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
                        "Option 1: Using idempiere-cli\n" +
                        "  idempiere-cli deploy --target /path/to/idempiere\n" +
                        "  idempiere-cli deploy --target /path/to/idempiere --hot\n\n" +
                        "Option 2: Copy to plugins folder\n" +
                        "  Copy the .jar to <idempiere>/plugins/ and restart\n\n" +
                        "Option 3: OSGi Console (hot deploy)\n" +
                        "  telnet localhost 12612\n" +
                        "  install file:/path/to/" + jarFile.getFileName() + "\n" +
                        "  start <bundle-id>\n\n" +
                        "Option 4: WebUI\n" +
                        "  System Admin > General Rules > System Rules > OSGi Management\n\n" +
                        "See: https://wiki.idempiere.org/en/Developing_Plug-Ins_-_Get_your_Plug-In_running\n";
                zos.putNextEntry(new ZipEntry("README.txt"));
                zos.write(readme.getBytes());
                zos.closeEntry();
            }

            System.out.println("  Created: " + zipPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("  Error creating zip: " + e.getMessage());
        }
    }

    /**
     * Packages a p2 update site from a multi-module project.
     * Copies the Tycho-generated repository to the output directory.
     *
     * @param p2Repository the path to the Tycho-generated repository (e.g., *.p2/target/repository)
     * @param outputDir the output directory
     */
    public void packageP2MultiModule(Path p2Repository, Path outputDir) {
        System.out.println("  Copying p2 update site from: " + p2Repository);

        try {
            Path repoDir = outputDir.resolve("repository");
            Files.createDirectories(repoDir);

            // Copy entire repository structure
            copyDirectory(p2Repository, repoDir);

            System.out.println("  Created p2 repository at: " + repoDir.toAbsolutePath());
            System.out.println();
            System.out.println("  To use this update site:");
            System.out.println();
            System.out.println("  1. Host on a web server, or");
            System.out.println("  2. Use locally with file:// URL:");
            System.out.println("     file://" + repoDir.toAbsolutePath());
            System.out.println();
            System.out.println("  To deploy directly to iDempiere:");
            System.out.println("    idempiere-cli deploy --target /path/to/idempiere");
            System.out.println();
            System.out.println("  See: https://wiki.idempiere.org/en/Developing_Plug-Ins_-_Get_your_Plug-In_running");
        } catch (IOException e) {
            System.err.println("  Error creating p2 repository: " + e.getMessage());
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(sourcePath -> {
            try {
                Path targetPath = target.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.copy(sourcePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy " + sourcePath, e);
            }
        });
    }

    private void addToZip(ZipOutputStream zos, String entryName, Path file) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        Files.copy(file, zos);
        zos.closeEntry();
    }
}
