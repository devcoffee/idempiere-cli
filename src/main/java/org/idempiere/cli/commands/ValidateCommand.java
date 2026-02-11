package org.idempiere.cli.commands;

import jakarta.inject.Inject;
import org.idempiere.cli.service.ValidateService;
import org.idempiere.cli.service.ValidateService.Severity;
import org.idempiere.cli.service.ValidateService.ValidationIssue;
import org.idempiere.cli.service.ValidateService.ValidationResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Validates an iDempiere plugin structure before deployment.
 *
 * <p>Performs comprehensive validation of plugin artifacts:
 * <ul>
 *   <li>META-INF/MANIFEST.MF - OSGi bundle headers and syntax</li>
 *   <li>build.properties - Tycho build configuration</li>
 *   <li>pom.xml - Maven/Tycho configuration</li>
 *   <li>OSGI-INF/*.xml - Service component descriptors</li>
 *   <li>plugin.xml - Eclipse extension points</li>
 *   <li>Java sources - Package structure and basic checks</li>
 * </ul>
 *
 * <h2>Exit Codes</h2>
 * <ul>
 *   <li>0 - Validation passed (no errors, warnings allowed)</li>
 *   <li>1 - Validation failed (one or more errors)</li>
 *   <li>2 - Validation passed with warnings (when --strict)</li>
 * </ul>
 *
 * <h2>CI/CD Integration</h2>
 * <pre>
 * # Fail build on any error
 * idempiere-cli validate ./my-plugin
 *
 * # Fail build on warnings too
 * idempiere-cli validate --strict ./my-plugin
 *
 * # Quiet mode for scripts
 * idempiere-cli validate --quiet ./my-plugin && echo "OK"
 * </pre>
 *
 * @see ValidateService#validate(Path)
 */
@Command(
        name = "validate",
        description = "Validate plugin structure and configuration",
        mixinStandardHelpOptions = true
)
public class ValidateCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Plugin directory to validate", defaultValue = ".")
    Path pluginDir;

    @Option(names = {"--strict"}, description = "Treat warnings as errors")
    boolean strict;

    @Option(names = {"-q", "--quiet"}, description = "Only output errors (no info/warnings)")
    boolean quiet;

    @Option(names = {"--json"}, description = "Output results as JSON")
    boolean json;

    @Inject
    ValidateService validateService;

    @Override
    public Integer call() {
        ValidationResult result = validateService.validate(pluginDir.toAbsolutePath().normalize());

        if (json) {
            return outputJson(result);
        }

        return outputText(result);
    }

    private Integer outputText(ValidationResult result) {
        if (!quiet) {
            System.out.println();
            System.out.println("iDempiere Plugin Validation");
            System.out.println("===========================");
            System.out.println();
            System.out.println("Plugin: " + result.pluginId());
            System.out.println("Path:   " + result.pluginDir());
            System.out.println();
        }

        // Output issues
        for (ValidationIssue issue : result.issues()) {
            if (quiet && issue.severity() != Severity.ERROR) {
                continue;
            }
            System.out.println(issue);
        }

        if (!quiet) {
            System.out.println();
            System.out.println("---------------------------");
            System.out.printf("Errors: %d, Warnings: %d%n", result.errors(), result.warnings());
            System.out.println();
        }

        // Determine exit code
        if (result.errors() > 0) {
            if (!quiet) {
                System.out.println("Validation FAILED");
            }
            return 1;
        }

        if (strict && result.warnings() > 0) {
            if (!quiet) {
                System.out.println("Validation FAILED (strict mode - warnings treated as errors)");
            }
            return 2;
        }

        if (!quiet) {
            System.out.println("Validation PASSED" + (result.warnings() > 0 ? " (with warnings)" : ""));
        }
        return 0;
    }

    private Integer outputJson(ValidationResult result) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode root = mapper.createObjectNode();
            root.put("pluginId", result.pluginId());
            root.put("path", result.pluginDir().toString());
            root.put("valid", result.isValid());
            root.put("errors", result.errors());
            root.put("warnings", result.warnings());

            com.fasterxml.jackson.databind.node.ArrayNode issues = root.putArray("issues");
            for (ValidationIssue issue : result.issues()) {
                com.fasterxml.jackson.databind.node.ObjectNode node = issues.addObject();
                node.put("severity", issue.severity().name());
                node.put("file", issue.file());
                node.put("message", issue.message());
            }

            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (Exception e) {
            System.err.println("{\"error\": \"Failed to serialize JSON\"}");
        }

        if (result.errors() > 0) {
            return 1;
        }
        if (strict && result.warnings() > 0) {
            return 2;
        }
        return 0;
    }
}
