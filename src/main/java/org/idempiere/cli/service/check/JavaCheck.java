package org.idempiere.cli.service.check;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.service.ProcessRunner;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks for Java 17+ installation.
 */
@ApplicationScoped
public class JavaCheck implements EnvironmentCheck {

    private static final Pattern VERSION_PATTERN = Pattern.compile("version \"(\\d+)");
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    @Inject
    ProcessRunner processRunner;

    @Override
    public String toolName() {
        return "Java";
    }

    @Override
    public CheckResult check() {
        ProcessRunner.RunResult result = processRunner.run("java", "-version");
        if (result.exitCode() < 0 || result.output() == null) {
            String msg = "Not found";
            return new CheckResult(toolName(), CheckResult.Status.FAIL, msg);
        }

        Matcher matcher = VERSION_PATTERN.matcher(result.output());
        if (matcher.find()) {
            int majorVersion = Integer.parseInt(matcher.group(1));
            if (majorVersion >= 17) {
                String msg = "Version " + majorVersion + " detected";
                return new CheckResult(toolName(), CheckResult.Status.OK, msg);
            } else {
                String msg = "Version " + majorVersion + " found, but 17+ is required";
                return new CheckResult(toolName(), CheckResult.Status.FAIL, msg);
            }
        }

        String msg = "Found but could not determine version";
        return new CheckResult(toolName(), CheckResult.Status.WARN, msg);
    }

    @Override
    public FixSuggestion getFixSuggestion(String os) {
        return FixSuggestion.builder()
                .brew("openjdk@21")
                .apt("openjdk-21-jdk")
                .dnf("java-21-openjdk-devel")
                .pacman("jdk-openjdk")
                .zypper("java-21-openjdk-devel")
                .winget("EclipseAdoptium.Temurin.21.JDK")
                .url("https://adoptium.net/")
                .build();
    }
}
