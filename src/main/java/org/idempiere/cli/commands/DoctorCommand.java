package org.idempiere.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import org.idempiere.cli.model.CliConfig;
import org.idempiere.cli.service.CliConfigService;
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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Option(names = {"--fix-optional"}, arity = "0..1", fallbackValue = "",
            description = "Install optional tools. Without value: interactive. Values: all, docker, maven (comma-separated)")
    String fixOptional;

    @Option(names = {"--dir"}, description = "Validate plugin structure in the given directory")
    String dir;

    @Option(names = {"--java"}, defaultValue = "21-tem",
            description = "Java version to install. SDKMAN id on macOS/Linux (e.g. 21-tem, 21-graal), " +
                    "winget package id on Windows (default: ${DEFAULT-VALUE})")
    String javaVersion;

    @Option(names = {"--json"}, description = "Output results as JSON")
    boolean json;

    @Inject
    DoctorService doctorService;

    @Inject
    CliConfigService configService;

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
            if (fixOptional != null) fix = true;
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

        printConfigStatus();

        List<CheckResult> results = result.entries().stream().map(CheckEntry::result).toList();
        boolean dockerNotOk = results.stream().anyMatch(r -> r.tool().equals("Docker") && !r.isOk());
        boolean postgresOutdated = results.stream().anyMatch(r -> r.tool().equals("PostgreSQL") && r.isWarn());

        // Resolve which optional tools to fix
        Set<String> optionalFilter = resolveOptionalFilter(result.entries());
        boolean hasFixableIssues = result.failed() > 0 || (optionalFilter != null && !optionalFilter.isEmpty());

        if (fix && hasFixableIssues) {
            doctorService.runAutoFix(result.entries(), optionalFilter, javaVersion);
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

    private void printConfigStatus() {
        System.out.println();
        System.out.println("Configuration:");

        boolean hasGlobal = configService.hasGlobalConfig();
        Path projectConfig = configService.findConfigInHierarchy();

        if (!hasGlobal && projectConfig == null) {
            System.out.println("  " + CliOutput.warn("No config file found"));
            System.out.println("    AI-powered features are disabled.");
            System.out.println("    Run: idempiere-cli config init");
            return;
        }

        if (projectConfig != null) {
            System.out.println("  " + CliOutput.ok("Project config: " + projectConfig));
        }
        if (hasGlobal) {
            System.out.println("  " + CliOutput.ok("Global config:  " + configService.getGlobalConfigPath()));
        }

        CliConfig config = configService.loadConfig();
        if (config.getAi().isEnabled()) {
            String apiKeyEnv = config.getAi().getApiKeyEnv();
            boolean hasKey = apiKeyEnv != null && System.getenv(apiKeyEnv) != null;
            if (hasKey) {
                System.out.println("  " + CliOutput.ok("AI provider:    " + config.getAi().getProvider()));
            } else {
                System.out.println("  " + CliOutput.warn("AI provider:    " + config.getAi().getProvider()
                        + " ($" + apiKeyEnv + " not set)"));
            }
        } else {
            System.out.println("  " + CliOutput.warn("AI provider:    not configured"));
            System.out.println("    Run: idempiere-cli config init");
        }
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

            // Override default Java version with --java value
            if (sdkmanPackages.removeIf(pkg -> pkg.startsWith("java "))) {
                sdkmanPackages.add("java " + javaVersion);
            }

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
            // Override default Java winget package with --java value
            if (wingetPackages.removeIf(pkg -> pkg.contains("Temurin") && pkg.contains("JDK"))) {
                wingetPackages.add(javaVersion);
            }

            System.out.println();
            System.out.println("  --fix will install via winget:");
            for (String pkg : wingetPackages) {
                System.out.println("    winget install " + pkg);
            }
        }

        System.out.println();
        System.out.println("  Run: idempiere-cli doctor --fix");

        // Collect optional warnings for suggestions
        List<CheckEntry> optionalWarnings = entries.stream()
                .filter(e -> e.result().isWarn() && !e.check().isRequired())
                .toList();

        if (!optionalWarnings.isEmpty()) {
            System.out.println();
            System.out.println("Optional tools:");
            for (CheckEntry entry : optionalWarnings) {
                String toolName = entry.result().tool();
                System.out.printf("  %-12s %s%n", toolName, entry.result().message());
            }
            System.out.println();
            String toolNames = optionalWarnings.stream()
                    .map(e -> e.result().tool().toLowerCase())
                    .collect(Collectors.joining(","));
            System.out.println("  Install all:      idempiere-cli doctor --fix-optional=all");
            System.out.println("  Choose specific:  idempiere-cli doctor --fix-optional=" + toolNames);
            System.out.println("  Interactive:      idempiere-cli doctor --fix-optional");
        }

        CheckEntry dockerEntry = entries.stream()
                .filter(e -> e.result().tool().equals("Docker") && !e.result().isOk())
                .findFirst().orElse(null);

        if (dockerEntry != null) {
            boolean daemonNotRunning = dockerEntry.result().message() != null
                    && dockerEntry.result().message().contains("daemon is not running");

            if (daemonNotRunning) {
                System.out.println();
                System.out.println("Docker is installed but the daemon is not running.");
                if (os.contains("mac")) {
                    System.out.println("  Start Docker Desktop: open -a Docker");
                } else if (os.contains("linux")) {
                    System.out.println("  Start Docker: sudo systemctl start docker");
                } else if (os.contains("win")) {
                    System.out.println("  Start Docker Desktop from the Start menu");
                }
            }
            System.out.println();
            System.out.println("  With Docker, use: idempiere-cli setup-dev-env --with-docker");
        }

        System.out.println();
    }

    /**
     * Resolves which optional tools to fix based on --fix-optional value.
     * @return null if no optional fix requested, empty set if none selected, or set of tool names
     */
    private Set<String> resolveOptionalFilter(List<CheckEntry> entries) {
        if (fixOptional == null) {
            return null; // --fix-optional not used
        }

        // Collect optional tools that have warnings
        List<CheckEntry> optionalWarnings = entries.stream()
                .filter(e -> e.result().isWarn() && !e.check().isRequired())
                .toList();

        if (optionalWarnings.isEmpty()) {
            return Set.of(); // nothing to fix
        }

        if (fixOptional.equalsIgnoreCase("all")) {
            // --fix-optional=all → fix all optional
            return optionalWarnings.stream()
                    .map(e -> e.result().tool().toLowerCase())
                    .collect(Collectors.toSet());
        }

        if (!fixOptional.isEmpty()) {
            // --fix-optional=docker,maven → filter specific tools
            Set<String> requested = Arrays.stream(fixOptional.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());

            // Validate tool names
            Set<String> validNames = optionalWarnings.stream()
                    .map(e -> e.result().tool().toLowerCase())
                    .collect(Collectors.toSet());

            Set<String> invalid = requested.stream()
                    .filter(r -> !validNames.contains(r))
                    .collect(Collectors.toSet());

            if (!invalid.isEmpty()) {
                System.out.println();
                System.out.println("Unknown optional tool(s): " + String.join(", ", invalid));
                System.out.println("Available: " + String.join(", ", validNames));
                return Set.of();
            }

            return requested;
        }

        // --fix-optional (no value) → interactive mode
        System.out.println();
        System.out.println("Optional tools available to install:");
        System.out.println();
        for (int i = 0; i < optionalWarnings.size(); i++) {
            CheckEntry entry = optionalWarnings.get(i);
            System.out.printf("  %d. %-12s %s%n", i + 1,
                    entry.result().tool(), entry.result().message());
        }
        System.out.println("  a. All of the above");
        System.out.println();
        System.out.print("Select tools to install (e.g. 1,2 or a for all) [a]: ");

        @SuppressWarnings("resource")
        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine().trim();

        if (input.isEmpty() || input.equalsIgnoreCase("a")) {
            return optionalWarnings.stream()
                    .map(e -> e.result().tool().toLowerCase())
                    .collect(Collectors.toSet());
        }

        Set<String> selected = new LinkedHashSet<>();
        for (String part : input.split(",")) {
            try {
                int idx = Integer.parseInt(part.trim()) - 1;
                if (idx >= 0 && idx < optionalWarnings.size()) {
                    selected.add(optionalWarnings.get(idx).result().tool().toLowerCase());
                }
            } catch (NumberFormatException e) {
                // Try as tool name
                String name = part.trim().toLowerCase();
                if (optionalWarnings.stream().anyMatch(w -> w.result().tool().equalsIgnoreCase(name))) {
                    selected.add(name);
                }
            }
        }

        return selected;
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

            // Config status
            ObjectNode configNode = root.putObject("config");
            configNode.put("globalConfigFound", configService.hasGlobalConfig());
            configNode.put("projectConfigFound", configService.findConfigInHierarchy() != null);
            CliConfig config = configService.loadConfig();
            configNode.put("aiEnabled", config.getAi().isEnabled());
            configNode.put("aiProvider", config.getAi().getProvider());

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
