package org.idempiere.cli.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PlatformVersion.
 */
class PlatformVersionTest {

    @Test
    void testVersion12() {
        PlatformVersion v12 = PlatformVersion.of(12);

        assertEquals(12, v12.major());
        assertEquals(17, v12.javaRelease());
        assertEquals("JavaSE-17", v12.javaSeVersion());
        assertEquals("4.0.4", v12.tychoVersion());
        assertEquals("12.0.0", v12.bundleVersion());
        assertEquals("release-12", v12.branch());
    }

    @Test
    void testVersion13() {
        PlatformVersion v13 = PlatformVersion.of(13);

        assertEquals(13, v13.major());
        assertEquals(21, v13.javaRelease());
        assertEquals("JavaSE-21", v13.javaSeVersion());
        assertEquals("4.0.8", v13.tychoVersion());
        assertEquals("13.0.0", v13.bundleVersion());
        assertEquals("master", v13.branch());
    }

    @Test
    void testUnsupportedVersion() {
        assertThrows(IllegalArgumentException.class, () -> PlatformVersion.of(10));
        assertThrows(IllegalArgumentException.class, () -> PlatformVersion.of(99));
    }

    @Test
    void testFromBranchMaster() {
        PlatformVersion v = PlatformVersion.fromBranch("master");
        assertEquals(PlatformVersion.latest(), v);
        assertEquals(13, v.major());
    }

    @Test
    void testFromBranchMain() {
        PlatformVersion v = PlatformVersion.fromBranch("main");
        assertEquals(PlatformVersion.latest(), v);
    }

    @Test
    void testFromBranchRelease12() {
        PlatformVersion v = PlatformVersion.fromBranch("release-12");
        assertEquals(12, v.major());
    }

    @Test
    void testFromBranchUnknown() {
        // Unknown branch should default to stable
        PlatformVersion v = PlatformVersion.fromBranch("unknown-branch");
        assertEquals(PlatformVersion.stable(), v);
    }

    @Test
    void testLatest() {
        PlatformVersion latest = PlatformVersion.latest();
        assertEquals(13, latest.major());
    }

    @Test
    void testStable() {
        PlatformVersion stable = PlatformVersion.stable();
        assertEquals(12, stable.major());
    }

    @Test
    void testSupported() {
        List<PlatformVersion> supported = PlatformVersion.supported();
        assertNotNull(supported);
        assertEquals(2, supported.size());
        assertTrue(supported.stream().anyMatch(v -> v.major() == 12));
        assertTrue(supported.stream().anyMatch(v -> v.major() == 13));
    }

    @Test
    void testEclipseRepoUrl() {
        PlatformVersion v12 = PlatformVersion.of(12);
        assertTrue(v12.eclipseRepoUrl().contains("eclipse.org"));
        assertTrue(v12.eclipseRepoUrl().contains("2023-09"));

        PlatformVersion v13 = PlatformVersion.of(13);
        assertTrue(v13.eclipseRepoUrl().contains("2024-09"));
    }
}
