package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creates auxiliary scaffold files like .gitignore and Eclipse .project.
 */
@ApplicationScoped
public class ScaffoldProjectAuxFilesService {

    public void generateMavenJvmConfig(Path rootDir) throws IOException {
        Path mvnDir = rootDir.resolve(".mvn");
        Files.createDirectories(mvnDir);
        writeFileAndReport(mvnDir.resolve("jvm.config"),
                "-Djdk.xml.maxGeneralEntitySizeLimit=0\n" +
                        "-Djdk.xml.totalEntitySizeLimit=0\n");
    }

    public void generateGitignore(Path dir) throws IOException {
        Path gitignore = dir.resolve(".gitignore");
        writeFileAndReport(gitignore,
                "# Build output\n" +
                        "target/\n" +
                        "bin/\n" +
                        "\n" +
                        "# Eclipse IDE\n" +
                        ".settings/\n" +
                        ".classpath\n" +
                        "\n" +
                        "# IntelliJ IDEA\n" +
                        ".idea/\n" +
                        "*.iml\n" +
                        "\n" +
                        "# OS files\n" +
                        ".DS_Store\n" +
                        "Thumbs.db\n");
    }

    public void generateEclipseProject(Path moduleDir, String projectName) throws IOException {
        Path projectFile = moduleDir.resolve(".project");
        writeFileAndReport(projectFile,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<projectDescription>\n" +
                        "\t<name>" + projectName + "</name>\n" +
                        "\t<comment></comment>\n" +
                        "\t<projects></projects>\n" +
                        "\t<buildSpec>\n" +
                        "\t\t<buildCommand>\n" +
                        "\t\t\t<name>org.eclipse.jdt.core.javabuilder</name>\n" +
                        "\t\t\t<arguments></arguments>\n" +
                        "\t\t</buildCommand>\n" +
                        "\t\t<buildCommand>\n" +
                        "\t\t\t<name>org.eclipse.pde.ManifestBuilder</name>\n" +
                        "\t\t\t<arguments></arguments>\n" +
                        "\t\t</buildCommand>\n" +
                        "\t\t<buildCommand>\n" +
                        "\t\t\t<name>org.eclipse.pde.SchemaBuilder</name>\n" +
                        "\t\t\t<arguments></arguments>\n" +
                        "\t\t</buildCommand>\n" +
                        "\t</buildSpec>\n" +
                        "\t<natures>\n" +
                        "\t\t<nature>org.eclipse.pde.PluginNature</nature>\n" +
                        "\t\t<nature>org.eclipse.jdt.core.javanature</nature>\n" +
                        "\t</natures>\n" +
                        "</projectDescription>\n");
    }

    private void writeFileAndReport(Path file, String content) throws IOException {
        Files.writeString(file, content);
        System.out.println("  Created: " + file);
    }
}
