package org.idempiere.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import org.idempiere.cli.service.DoctorService;
import org.idempiere.cli.service.DoctorService.CheckEntry;
import org.idempiere.cli.service.DoctorService.EnvironmentResult;
import org.idempiere.cli.service.DoctorService.PluginCheckResult;
import org.idempiere.cli.service.check.CheckResult;
import org.idempiere.cli.service.check.EnvironmentCheck;
import org.idempiere.cli.util.CliOutput;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates the development environment and plugin structure.
 *
 * <p>Performs comprehensive checks for:
 * <ul>
 *   <li>Java JDK (version and JAVA_HOME)</li>
 *   <li>Maven installation</li>
 *   <li>Git installation</li>
 *   <li>Docker (optional, for database containers)</li>
 *   <li>PostgreSQL client tools</li>
 * </ul>
 *
 * <h2>Plugin Validation</h2>
 * <p>When used with {@code --dir}, validates plugin structure:
 * <ul>
 *   <li>MANIFEST.MF syntax and required headers</li>
 *   <li>build.properties configuration</li>
 *   <li>Require-Bundle consistency</li>
 * </ul>
 *
 * <h2>Auto-fix</h2>
 * <p>Use {@code --fix} to automatically install missing tools using
 * the system package manager (Homebrew on macOS, apt on Linux).
 *
 * @see DoctorService
 */
@Command(
        name = "doctor",
        description = "Check required tools and environment prerequisites",
        mixinStandardHelpOptions = true
)
public class DoctorCommand implements Runnable {

    @Option(names = {"--fix"}, description = "Attempt to auto-fix missing dependencies")
    boolean fix;

    @Option(names = {"--fix-optional"}, description = "Also install optional tools (e.g. Docker) when using --fix")
    boolean fixOptional;

    @Option(names = {"--dir"}, description = "Validate plugin structure in the given directory")
    String dir;

    @Option(names = {"--json"}, description = "Output results as JSON")
    boolean json;

    @Inject
    DoctorService doctorService;

    @Override
    public void run() {
        if (dir != null) {
            if (json) {
                printPluginJson(doctorService.checkPluginData(Path.of(dir)));
            } else {
                runPluginCheck(Path.of(dir));
            }
        } else if (json) {
            printEnvironmentJson(doctorService.checkEnvironmentData());
        } else {
            // --fix-optional implies --fix
            if (fixOptional) fix = true;
            runEnvironmentCheck();
        }
    }

    private void runEnvironmentCheck() {
        System.out.println();
        System.out.println("iDempiere CLI - Environment Check");
        System.out.println("==================================");
        System.out.println();

        EnvironmentResult result = doctorService.checkEnvironmentData();

        for (CheckEntry entry : result.entries()) {
            printResult(entry.result());
        }

        System.out.println();
        System.out.println("----------------------------------");
        System.out.printf("Results: %d passed, %d warnings, %d failed%n",
                result.passed(), result.warnings(), result.failed());

        List<CheckResult> results = result.entries().stream().map(CheckEntry::result).toList();
        boolean dockerNotOk = results.stream().anyMatch(r -> r.tool().equals("Docker") && !r.isOk());
        boolean postgresOutdated = results.stream().anyMatch(r -> r.tool().equals("PostgreSQL") && r.isWarn());
        boolean hasFixableIssues = result.failed() > 0 || (fixOptional && dockerNotOk);

        if (fix && hasFixableIssues) {
            doctorService.runAutoFix(result.entries(), fixOptional);
        } else if (result.failed() > 0 || dockerNotOk || postgresOutdated) {
            printFixSuggestions(result.entries());
        }

        if (result.failed() == 0) {
            System.out.println();
            if (result.warnings() > 0) {
                System.out.println("Environment is functional but has warnings. Consider addressing them.");
            } else {
                System.out.println("All checks passed! Your environment is ready.");
            }
            System.out.println("Run 'idempiere-cli setup-dev-env' to bootstrap your development environment.");
        }

        System.out.println();
    }

    private void runPluginCheck(Path pluginDir) {
        System.out.println();
        System.out.println("iDempiere CLI - Plugin Validation");
        System.out.println("==================================");
        System.out.println();

        if (!Files.exists(pluginDir)) {
            System.err.println("  Error: Directory '" + pluginDir + "' does not exist.");
            return;
        }

        PluginCheckResult result = doctorService.checkPluginData(pluginDir);

        for (CheckResult cr : result.results()) {
            printResult(cr);
        }

        System.out.println();
        System.out.println("----------------------------------");
        System.out.printf("Results: %d passed, %d warnings, %d failed%n",
                result.passed(), result.warnings(), result.failed());
        System.out.println();
    }

    private void printResult(CheckResult result) {
        String line = String.format("%-15s %s", result.tool(), result.message());
        String output = switch (result.status()) {
            case OK -> CliOutput.ok(line);
            case WARN -> CliOutput.warn(line);
            case FAIL -> CliOutput.fail(line);
        };
        System.out.println("  " + output);
    }

    private void printFixSuggestions(List<CheckEntry> entries) {
        String os = System.getProperty("os.name", "").toLowerCase();

        List<CheckEntry> failed = entries.stream()
                .filter(e -> e.result().isFail())
                .toList();

        List<CheckEntry> warned = entries.stream()
                .filter(e -> e.result().isWarn())
                .toList();

        if (failed.isEmpty() && warned.isEmpty()) return;

        System.out.println();
        System.out.println("Fix Suggestions");
        System.out.println("---------------");

        Set<String> sdkmanPackages = new LinkedHashSet<>();
        Set<String> brewPackages = new LinkedHashSet<>();
        Set<String> brewCasks = new LinkedHashSet<>();
        Set<String> aptPackages = new LinkedHashSet<>();
        Set<String> dnfPackages = new LinkedHashSet<>();
        Set<String> pacmanPackages = new LinkedHashSet<>();
        Set<String> wingetPackages = new LinkedHashSet<>();
        Set<String> manualUrls = new LinkedHashSet<>();

        for (CheckEntry entry : failed) {
            EnvironmentCheck.FixSuggestion fix = entry.check().getFixSuggestion(os);
            if (fix == null) continue;

            if (fix.sdkmanPackage() != null) sdkmanPackages.add(fix.sdkmanPackage());
            if (fix.brewPackage() != null) brewPackages.add(fix.brewPackage());
            if (fix.brewCask() != null) brewCasks.add(fix.brewCask());
            if (fix.aptPackage() != null) aptPackages.add(fix.aptPackage());
            if (fix.dnfPackage() != null) dnfPackages.add(fix.dnfPackage());
            if (fix.pacmanPackage() != null) pacmanPackages.add(fix.pacmanPackage());
            if (fix.wingetPackage() != null) wingetPackages.add(fix.wingetPackage());
            if (fix.manualUrl() != null) manualUrls.add(entry.check().toolName() + ": " + fix.manualUrl());
        }

        if (!os.contains("win") && !sdkmanPackages.isEmpty()) {
            removeJavaMavenPackages(brewPackages);
            removeJavaMavenPackages(aptPackages);
            removeJavaMavenPackages(dnfPackages);
            removeJavaMavenPackages(pacmanPackages);

            System.out.println();
            System.out.println("  " + CliOutput.tip("--fix will install via SDKMAN (recommended for Java/Maven):"));
            for (String pkg : sdkmanPackages) {
                System.out.println("    sdk install " + pkg);
            }
        }

        if (os.contains("mac") && (!brewPackages.isEmpty() || !brewCasks.isEmpty())) {
            System.out.println();
            System.out.println("  --fix will install via Homebrew:");
            if (!brewPackages.isEmpty()) {
                System.out.println("    brew install " + String.join(" ", brewPackages));
            }
            for (String cask : brewCasks) {
                System.out.println("    brew install --cask " + cask);
            }
        } else if (os.contains("linux") && !aptPackages.isEmpty()) {
            System.out.println();
            System.out.println("  --fix will install via apt:");
            System.out.println("    sudo apt install " + String.join(" ", aptPackages));
        } else if (os.contains("win") && !wingetPackages.isEmpty()) {
            System.out.println();
            System.out.println("  --fix will install via winget:");
            for (String pkg : wingetPackages) {
                System.out.println("    winget install " + pkg);
            }
        }

        System.out.println();
        System.out.println("  Run: idempiere-cli doctor --fix");

        CheckEntry dockerEntry = entries.stream()
                .filter(e -> e.result().tool().equals("Docker") && !e.result().isOk())
                .findFirst().orElse(null);

        if (dockerEntry != null) {
            System.out.println();
            boolean daemonNotRunning = dockerEntry.result().message() != null
                    && dockerEntry.result().message().contains("daemon is not running");

            if (daemonNotRunning) {
                System.out.println("Docker is installed but the daemon is not running.");
                if (os.contains("mac")) {
                    System.out.println("  Start Docker Desktop: open -a Docker");
                } else if (os.contains("linux")) {
                    System.out.println("  Start Docker: sudo systemctl start docker");
                } else if (os.contains("win")) {
                    System.out.println("  Start Docker Desktop from the Start menu");
                }
            } else {
                System.out.println("Optional: Docker (for containerized PostgreSQL)");
                System.out.println("  Run: idempiere-cli doctor --fix-optional");
            }
            System.out.println();
            System.out.println("  With Docker, use: idempiere-cli setup-dev-env --with-docker");
        }

        System.out.println();
    }

    private void removeJavaMavenPackages(Set<String> packages) {
        packages.removeIf(pkg ->
                pkg.contains("java") ||
                pkg.contains("jdk") ||
                pkg.contains("openjdk") ||
                pkg.contains("maven") ||
                pkg.contains("temurin"));
    }

    private void printEnvironmentJson(EnvironmentResult result) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.createObjectNode();
            root.put("passed", result.passed());
            root.put("warnings", result.warnings());
            root.put("failed", result.failed());

            ArrayNode checks = root.putArray("checks");
            for (var entry : result.entries()) {
                CheckResult cr = entry.result();
                ObjectNode node = checks.addObject();
                node.put("tool", cr.tool());
                node.put("status", cr.status().name());
                node.put("message", cr.message());
            }

            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (Exception e) {
            System.err.println("{\"error\": \"Failed to serialize JSON\"}");
        }
    }

    private void printPluginJson(PluginCheckResult result) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.createObjectNode();
            root.put("pluginDir", result.pluginDir().toString());
            root.put("passed", result.passed());
            root.put("warnings", result.warnings());
            root.put("failed", result.failed());

            ArrayNode checks = root.putArray("checks");
            for (CheckResult cr : result.results()) {
                ObjectNode node = checks.addObject();
                node.put("tool", cr.tool());
                node.put("status", cr.status().name());
                node.put("message", cr.message());
            }

            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (Exception e) {
            System.err.println("{\"error\": \"Failed to serialize JSON\"}");
        }
    }
}
