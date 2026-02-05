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

    public void packageP2(Path pluginDir, Path outputDir, Path jarFile) {
        System.out.println("  Generating p2 update site...");

        Path targetDir = pluginDir.resolve("target");
        Path p2Content = targetDir.resolve("p2content.xml");
        Path p2Artifacts = targetDir.resolve("p2artifacts.xml");

        // Check if Tycho generated p2 metadata
        if (!Files.exists(p2Content) || !Files.exists(p2Artifacts)) {
            System.err.println("  Error: p2 metadata not found in target/");
            System.err.println("  Make sure to build the plugin first: idempiere-cli build");
            return;
        }

        try {
            // Create p2 repository structure
            Path repoDir = outputDir.resolve("repository");
            Path pluginsDir = repoDir.resolve("plugins");
            Files.createDirectories(pluginsDir);

            // Copy the plugin jar
            Files.copy(jarFile, pluginsDir.resolve(jarFile.getFileName()),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // Copy p2 metadata as content.xml and artifacts.xml
            Files.copy(p2Content, repoDir.resolve("content.xml"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.copy(p2Artifacts, repoDir.resolve("artifacts.xml"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            System.out.println("  Created p2 repository at: " + repoDir.toAbsolutePath());
            System.out.println();
            System.out.println("  To deploy the plugin:");
            System.out.println();
            System.out.println("  Using idempiere-cli:");
            System.out.println("    idempiere-cli deploy --target /path/to/idempiere");
            System.out.println();
            System.out.println("  Or copy directly:");
            System.out.println("    cp " + pluginsDir.resolve(jarFile.getFileName()) + " /path/to/idempiere/plugins/");
            System.out.println();
            System.out.println("  See: https://wiki.idempiere.org/en/Developing_Plug-Ins_-_Get_your_Plug-In_running");
        } catch (IOException e) {
            System.err.println("  Error creating p2 repository: " + e.getMessage());
        }
    }

    private void addToZip(ZipOutputStream zos, String entryName, Path file) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        Files.copy(file, zos);
        zos.closeEntry();
    }
}
