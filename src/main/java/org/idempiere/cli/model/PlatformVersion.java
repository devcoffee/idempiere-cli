package org.idempiere.cli.model;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps an iDempiere major version to all derived platform configuration values.
 * The iDempiere project maintains compatibility with the latest stable release
 * and the development branch (master).
 */
public record PlatformVersion(
        int major,
        int javaRelease,
        String javaSeVersion,
        String tychoVersion,
        String bundleVersion,
        String branch,
        String eclipseRepoUrl
) {

    private static final PlatformVersion V12 = new PlatformVersion(12, 17, "JavaSE-17", "4.0.4", "12.0.0", "release-12",
            "https://download.eclipse.org/releases/2023-09/");
    private static final PlatformVersion V13 = new PlatformVersion(13, 21, "JavaSE-21", "4.0.8", "13.0.0", "master",
            "https://download.eclipse.org/releases/2024-09/");

    private static final List<PlatformVersion> SUPPORTED = List.of(V12, V13);

    /**
     * Returns the PlatformVersion for a given major version number.
     */
    public static PlatformVersion of(int major) {
        for (PlatformVersion pv : SUPPORTED) {
            if (pv.major == major) return pv;
        }
        throw new IllegalArgumentException(
                "Unsupported iDempiere version: " + major + ". Supported versions: " +
                        SUPPORTED.stream().map(pv -> String.valueOf(pv.major)).toList());
    }

    /**
     * Derives the PlatformVersion from a git branch name.
     * "master" maps to the latest (dev) version.
     * "release-12" maps to version 12, etc.
     */
    public static PlatformVersion fromBranch(String branch) {
        if ("master".equals(branch) || "main".equals(branch)) {
            return latest();
        }
        Matcher m = Pattern.compile("release-(\\d+)").matcher(branch);
        if (m.find()) {
            return of(Integer.parseInt(m.group(1)));
        }
        return stable();
    }

    /**
     * Returns the latest version (master/development branch).
     */
    public static PlatformVersion latest() {
        return V13;
    }

    /**
     * Returns the latest stable release.
     */
    public static PlatformVersion stable() {
        return V12;
    }

    /**
     * Returns all supported platform versions.
     */
    public static List<PlatformVersion> supported() {
        return SUPPORTED;
    }
}
