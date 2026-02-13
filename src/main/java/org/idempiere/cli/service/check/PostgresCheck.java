package org.idempiere.cli.service.check;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.service.ProcessRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Checks for PostgreSQL client (psql) installation.
 */
@ApplicationScoped
public class PostgresCheck implements EnvironmentCheck {

    private static final Pattern VERSION_PATTERN = Pattern.compile("psql \\(PostgreSQL\\) (\\S+)");
    private static final int RECOMMENDED_VERSION = 16;
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

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
            // On Windows, try well-known installation paths
            if (IS_WINDOWS) {
                String winPsql = findPsqlOnWindows();
                if (winPsql != null) {
                    result = processRunner.run(winPsql, "--version");
                    if (result.exitCode() == 0 && result.output() != null) {
                        String binDir = Path.of(winPsql).getParent().toString();
                        return parseVersion(result.output(),
                                " (not in PATH, run: setx PATH \"%PATH%;" + binDir + "\")",
                                CheckResult.Status.WARN);
                    }
                }
            }
            String msg = "psql client not found (required for database import)";
            return new CheckResult(toolName(), CheckResult.Status.FAIL, msg);
        }

        return parseVersion(result.output(), "", null);
    }

    private CheckResult parseVersion(String output, String suffix, CheckResult.Status forceStatus) {
        Matcher matcher = VERSION_PATTERN.matcher(output);
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
                String msg = "psql version " + versionStr + " (outdated, recommend " + RECOMMENDED_VERSION + ")" + suffix;
                return new CheckResult(toolName(), CheckResult.Status.WARN, msg);
            }

            CheckResult.Status status = forceStatus != null ? forceStatus : CheckResult.Status.OK;
            String msg = "psql version " + versionStr + " detected" + suffix;
            return new CheckResult(toolName(), status, msg);
        }

        CheckResult.Status status = forceStatus != null ? forceStatus : CheckResult.Status.OK;
        String msg = "psql found" + suffix;
        return new CheckResult(toolName(), status, msg);
    }

    /**
     * Searches well-known Windows installation paths for psql.exe.
     * PostgreSQL installer puts binaries in C:\Program Files\PostgreSQL\{version}\bin\.
     *
     * @return full path to psql.exe if found, null otherwise
     */
    public String findPsqlOnWindows() {
        String programFiles = System.getenv("ProgramFiles");
        if (programFiles == null) programFiles = "C:\\Program Files";

        Path pgBase = Path.of(programFiles, "PostgreSQL");
        if (!Files.isDirectory(pgBase)) {
            return null;
        }

        try (Stream<Path> versions = Files.list(pgBase)) {
            return versions
                    .filter(Files::isDirectory)
                    .map(dir -> dir.resolve("bin").resolve("psql.exe"))
                    .filter(Files::exists)
                    .map(Path::toString)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
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
