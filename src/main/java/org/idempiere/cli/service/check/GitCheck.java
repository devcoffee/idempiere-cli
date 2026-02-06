package org.idempiere.cli.service.check;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.service.ProcessRunner;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks for Git installation.
 */
@ApplicationScoped
public class GitCheck implements EnvironmentCheck {

    private static final Pattern VERSION_PATTERN = Pattern.compile("git version (\\S+)");

    @Inject
    ProcessRunner processRunner;

    @Override
    public String toolName() {
        return "Git";
    }

    @Override
    public CheckResult check() {
        ProcessRunner.RunResult result = processRunner.run("git", "--version");
        if (result.exitCode() < 0 || result.output() == null) {
            String msg = "Not found";
            return new CheckResult(toolName(), CheckResult.Status.FAIL, msg);
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
                .brew("git")
                .apt("git")
                .dnf("git")
                .pacman("git")
                .zypper("git")
                .winget("Git.Git")
                .url("https://git-scm.com/downloads")
                .build();
    }
}
