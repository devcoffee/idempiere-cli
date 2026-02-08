package org.idempiere.cli.service.check;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.service.ProcessRunner;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks for PostgreSQL client (psql) installation.
 */
@ApplicationScoped
public class PostgresCheck implements EnvironmentCheck {

    private static final Pattern VERSION_PATTERN = Pattern.compile("psql \\(PostgreSQL\\) (\\S+)");
    private static final int RECOMMENDED_VERSION = 16;

    @Inject
    ProcessRunner processRunner;

    @Override
    public String toolName() {
        return "PostgreSQL";
    }

    @Override
    public CheckResult check() {
        ProcessRunner.RunResult result = processRunner.run("psql", "--version");
        if (result.exitCode() != 0 || result.output() == null) {
            String msg = "psql client not found (required for database import)";
            return new CheckResult(toolName(), CheckResult.Status.FAIL, msg);
        }

        Matcher matcher = VERSION_PATTERN.matcher(result.output());
        if (matcher.find()) {
            String versionStr = matcher.group(1);
            int majorVersion = 0;
            try {
                String majorStr = versionStr.split("\\.")[0];
                majorVersion = Integer.parseInt(majorStr);
            } catch (NumberFormatException e) {
                // Ignore, will treat as unknown version
            }

            if (majorVersion > 0 && majorVersion < RECOMMENDED_VERSION) {
                String msg = "psql version " + versionStr + " (outdated, recommend " + RECOMMENDED_VERSION + ")";
                return new CheckResult(toolName(), CheckResult.Status.WARN, msg);
            }

            String msg = "psql version " + versionStr + " detected";
            return new CheckResult(toolName(), CheckResult.Status.OK, msg);
        }

        String msg = "psql found";
        return new CheckResult(toolName(), CheckResult.Status.OK, msg);
    }

    @Override
    public FixSuggestion getFixSuggestion(String os) {
        return FixSuggestion.builder()
                .brew("postgresql@16")
                .apt("postgresql-client-16")
                .dnf("postgresql16")
                .pacman("postgresql-libs-16")
                .zypper("postgresql16")
                .winget("PostgreSQL.PostgreSQL.16")
                .url("https://www.postgresql.org/download/")
                .build();
    }
}
