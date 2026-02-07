package org.idempiere.cli.service.check;

/**
 * Strategy interface for environment checks.
 *
 * <p>Implementations verify the presence and configuration of development tools
 * (Java, Maven, Git, Docker, etc.) and are discovered via CDI.
 */
public interface EnvironmentCheck {

    /**
     * Returns the tool name for display.
     */
    String toolName();

    /**
     * Whether this check is required or optional.
     * Optional checks (like Docker) show warnings instead of failures.
     */
    default boolean isRequired() {
        return true;
    }

    /**
     * Whether this check is applicable to the current platform.
     * Checks that return false are silently skipped.
     */
    default boolean isApplicable() {
        return true;
    }

    /**
     * Performs the environment check.
     *
     * @return the check result with status and message
     */
    CheckResult check();

    /**
     * Returns fix instructions for this tool on the current platform.
     *
     * @param os the operating system name (lowercase)
     * @return package names or install commands, or null if no auto-fix available
     */
    default FixSuggestion getFixSuggestion(String os) {
        return null;
    }

    /**
     * Fix suggestion for a specific platform.
     */
    record FixSuggestion(
            String brewPackage,      // macOS Homebrew package
            String brewCask,         // macOS Homebrew cask (for GUI apps)
            String aptPackage,       // Debian/Ubuntu apt package
            String dnfPackage,       // Fedora/RHEL dnf package
            String pacmanPackage,    // Arch pacman package
            String zypperPackage,    // openSUSE zypper package
            String wingetPackage,    // Windows winget package ID
            String sdkmanPackage,    // SDKMAN! package (cross-platform, recommended for Java/Maven)
            String manualUrl         // Manual download URL
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String brewPackage;
            private String brewCask;
            private String aptPackage;
            private String dnfPackage;
            private String pacmanPackage;
            private String zypperPackage;
            private String wingetPackage;
            private String sdkmanPackage;
            private String manualUrl;

            public Builder brew(String pkg) { this.brewPackage = pkg; return this; }
            public Builder brewCask(String pkg) { this.brewCask = pkg; return this; }
            public Builder apt(String pkg) { this.aptPackage = pkg; return this; }
            public Builder dnf(String pkg) { this.dnfPackage = pkg; return this; }
            public Builder pacman(String pkg) { this.pacmanPackage = pkg; return this; }
            public Builder zypper(String pkg) { this.zypperPackage = pkg; return this; }
            public Builder winget(String pkg) { this.wingetPackage = pkg; return this; }
            public Builder sdkman(String pkg) { this.sdkmanPackage = pkg; return this; }
            public Builder url(String url) { this.manualUrl = url; return this; }

            public FixSuggestion build() {
                return new FixSuggestion(brewPackage, brewCask, aptPackage, dnfPackage,
                        pacmanPackage, zypperPackage, wingetPackage, sdkmanPackage, manualUrl);
            }
        }
    }
}
