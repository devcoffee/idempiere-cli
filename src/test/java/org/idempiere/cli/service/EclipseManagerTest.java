package org.idempiere.cli.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EclipseManagerTest {

    private Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("eclipse-manager-test-");
    }

    @AfterEach
    void cleanup() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    @Test
    void testDetectJavaHomeReturnsBundledJre() throws IOException {
        EclipseManager manager = new EclipseManager();

        Path jreDir = tempDir.resolve("plugins")
                .resolve("org.eclipse.justj.openjdk.hotspot.jre.full.test")
                .resolve("jre");
        Files.createDirectories(jreDir);

        Path detected = manager.detectJavaHome(tempDir);
        assertEquals(jreDir, detected);
    }

    @Test
    void testDetectJavaHomeReturnsNullWhenNoBundledJre() {
        EclipseManager manager = new EclipseManager();
        assertNull(manager.detectJavaHome(tempDir));
    }
}
