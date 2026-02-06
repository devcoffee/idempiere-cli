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
    public CheckResult check() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("mac")) {
            // greadlink is only needed on macOS
            return new CheckResult(toolName(), CheckResult.Status.OK, "N/A");
        }

        ProcessRunner.RunResult result = processRunner.run("greadlink", "--version");
        if (result.exitCode() < 0 || result.output() == null) {
            String msg = "Not found (required on macOS for database import)";
            return new CheckResult(toolName(), CheckResult.Status.FAIL, msg);
        }

        String msg = "Found (coreutils)";
        return new CheckResult(toolName(), CheckResult.Status.OK, msg);
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
