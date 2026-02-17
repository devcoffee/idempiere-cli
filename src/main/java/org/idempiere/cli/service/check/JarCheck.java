package org.idempiere.cli.service.check;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.service.ProcessRunner;

/**
 * Checks for jar command (part of JDK, required for database seed extraction).
 */
@ApplicationScoped
public class JarCheck implements EnvironmentCheck {

    @Inject
    ProcessRunner processRunner;

    @Override
    public String toolName() {
        return "jar";
    }

    @Override
    public CheckResult check() {
        ProcessRunner.RunResult result = processRunner.run("jar", "--version");
        if (result.exitCode() != 0 || result.output() == null) {
            String msg = "Not found (required for database seed extraction)";
            return new CheckResult(toolName(), CheckResult.Status.FAIL, msg);
        }
        String msg = "Found (part of JDK)";
        return new CheckResult(toolName(), CheckResult.Status.OK, msg);
    }

    @Override
    public FixSuggestion getFixSuggestion(String os) {
        // jar comes with JDK — same fix as Java.
        // No system package fallbacks — fail explicitly if SDKMAN fails.
        return FixSuggestion.builder()
                .sdkman("java 21-tem")
                .winget("EclipseAdoptium.Temurin.21.JDK")
                .url("https://adoptium.net/")
                .build();
    }
}
