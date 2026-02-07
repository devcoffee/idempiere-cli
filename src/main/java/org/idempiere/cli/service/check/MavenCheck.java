package org.idempiere.cli.service.check;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.service.ProcessRunner;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks for Apache Maven installation.
 *
 * <p>Maven is optional because projects can use Maven Wrapper (mvnw) instead.
 * The wrapper is the recommended approach for consistent builds.
 */
@ApplicationScoped
public class MavenCheck implements EnvironmentCheck {

    private static final Pattern VERSION_PATTERN = Pattern.compile("Apache Maven (\\S+)");
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    @Inject
    ProcessRunner processRunner;

    @Override
    public String toolName() {
        return "Maven";
    }

    @Override
    public boolean isRequired() {
        return false; // Optional: projects can use Maven Wrapper (mvnw) instead
    }

    @Override
    public CheckResult check() {
        // On Windows, use mvn.cmd explicitly to avoid issues with mvn.exe launcher
        String mvnCmd = IS_WINDOWS ? "mvn.cmd" : "mvn";
        ProcessRunner.RunResult result = processRunner.run(mvnCmd, "-version");
        if (result.exitCode() < 0 || result.output() == null) {
            String msg = "Not found (optional: projects use mvnw wrapper)";
            return new CheckResult(toolName(), CheckResult.Status.WARN, msg);
        }

        Matcher matcher = VERSION_PATTERN.matcher(result.output());
        if (matcher.find()) {
            String msg = "Version " + matcher.group(1) + " detected";
            return new CheckResult(toolName(), CheckResult.Status.OK, msg);
        }

        String msg = "Found";
        return new CheckResult(toolName(), CheckResult.Status.OK, msg);
    }

    @Override
    public FixSuggestion getFixSuggestion(String os) {
        return FixSuggestion.builder()
                .sdkman("maven")  // Recommended: version manager
                .brew("maven")
                .apt("maven")
                .dnf("maven")
                .pacman("maven")
                .zypper("maven")
                .url("https://sdkman.io/")
                .build();
    }
}
