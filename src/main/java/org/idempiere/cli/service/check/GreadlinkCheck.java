package org.idempiere.cli.service.check;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.service.ProcessRunner;

/**
 * Checks for greadlink (coreutils) on macOS.
 * Only applicable on macOS, skipped on other platforms.
 */
@ApplicationScoped
public class GreadlinkCheck implements EnvironmentCheck {

    @Inject
    ProcessRunner processRunner;

    @Override
    public String toolName() {
        return "greadlink";
    }

    @Override
    public boolean isApplicable() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    @Override
    public CheckResult check() {
        ProcessRunner.RunResult result = processRunner.run("greadlink", "--version");
        if (result.exitCode() != 0 || result.output() == null) {
            return new CheckResult(toolName(), CheckResult.Status.FAIL,
                    "Not found (required on macOS for database import)");
        }
        return new CheckResult(toolName(), CheckResult.Status.OK, "Found (coreutils)");
    }

    @Override
    public FixSuggestion getFixSuggestion(String os) {
        if (!os.contains("mac")) {
            return null;
        }
        return FixSuggestion.builder()
                .brew("coreutils")
                .build();
    }
}
