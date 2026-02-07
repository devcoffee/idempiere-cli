package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/**
 * Manages Maven Wrapper installation for iDempiere plugins.
 *
 * <p>Provides functionality to add the Maven Wrapper (mvnw) to projects,
 * ensuring consistent Maven versions across different development environments.
 */
@ApplicationScoped
public class MavenWrapperService {

    private static final String WRAPPER_RESOURCE_PATH = "maven-wrapper/";
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    /**
     * Adds Maven Wrapper to the specified project directory.
     *
     * @param projectDir the project root directory
     * @return true if wrapper was added successfully
     */
    public boolean addWrapper(Path projectDir) {
        try {
            // Create .mvn/wrapper directory
            Path wrapperDir = projectDir.resolve(".mvn/wrapper");
            Files.createDirectories(wrapperDir);

            // Copy wrapper files
            copyResource("mvnw", projectDir.resolve("mvnw"));
            copyResource("mvnw.cmd", projectDir.resolve("mvnw.cmd"));
            copyResource("maven-wrapper.properties", wrapperDir.resolve("maven-wrapper.properties"));

            // Make mvnw executable on Unix systems
            if (!IS_WINDOWS) {
                makeExecutable(projectDir.resolve("mvnw"));
            }

            return true;
        } catch (IOException e) {
            System.err.println("Failed to add Maven Wrapper: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the project already has Maven Wrapper installed.
     *
     * @param projectDir the project root directory
     * @return true if mvnw or mvnw.cmd exists
     */
    public boolean hasWrapper(Path projectDir) {
        return Files.exists(projectDir.resolve("mvnw"))
            || Files.exists(projectDir.resolve("mvnw.cmd"));
    }

    /**
     * Updates the Maven version in an existing wrapper installation.
     *
     * @param projectDir the project root directory
     * @param mavenVersion the Maven version to use (e.g., "3.9.9")
     * @return true if update was successful
     */
    public boolean updateMavenVersion(Path projectDir, String mavenVersion) {
        Path propertiesFile = projectDir.resolve(".mvn/wrapper/maven-wrapper.properties");
        if (!Files.exists(propertiesFile)) {
            System.err.println("Maven Wrapper not found. Run 'add mvnw' first.");
            return false;
        }

        try {
            String content = Files.readString(propertiesFile);
            String updated = content.replaceAll(
                "apache-maven-[0-9.]+",
                "apache-maven-" + mavenVersion
            );
            Files.writeString(propertiesFile, updated);
            return true;
        } catch (IOException e) {
            System.err.println("Failed to update Maven version: " + e.getMessage());
            return false;
        }
    }

    private void copyResource(String resourceName, Path targetPath) throws IOException {
        String resourcePath = WRAPPER_RESOURCE_PATH + resourceName;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void makeExecutable(Path file) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, perms);
        } catch (IOException | UnsupportedOperationException e) {
            // Best effort - may fail on some filesystems
        }
    }
}
