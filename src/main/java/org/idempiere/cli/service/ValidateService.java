package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.idempiere.cli.util.CliOutput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Validates iDempiere plugin structure and configuration.
 *
 * <p>Performs comprehensive checks including:
 * <ul>
 *   <li>MANIFEST.MF syntax and required OSGi headers</li>
 *   <li>build.properties configuration</li>
 *   <li>pom.xml Tycho/Maven configuration</li>
 *   <li>OSGI-INF service component XML files</li>
 *   <li>plugin.xml extensions</li>
 *   <li>Java source file basic validation</li>
 * </ul>
 *
 * <p>Returns structured validation results suitable for CI/CD pipelines.
 */
@ApplicationScoped
public class ValidateService {

    private static final Pattern BUNDLE_SYMBOLIC_NAME_PATTERN =
            Pattern.compile("Bundle-SymbolicName:\\s*([^;\\n]+)");

    public enum Severity {
        ERROR, WARNING, INFO
    }

    public record ValidationIssue(Severity severity, String file, String message) {
        @Override
        public String toString() {
            String content = String.format("[%s] %s: %s", severity, file, message);
            return "  " + switch (severity) {
                case ERROR -> CliOutput.err(content);
                case WARNING -> CliOutput.warn(content);
                case INFO -> CliOutput.info(content);
            };
        }
    }

    public record ValidationResult(
            Path pluginDir,
            String pluginId,
            List<ValidationIssue> issues,
            int errors,
            int warnings
    ) {
        public boolean isValid() {
            return errors == 0;
        }
    }

    /**
     * Validates a plugin directory.
     *
     * @param pluginDir Path to the plugin directory
     * @return ValidationResult with all found issues
     */
    public ValidationResult validate(Path pluginDir) {
        List<ValidationIssue> issues = new ArrayList<>();
        String pluginId = "unknown";

        if (!Files.exists(pluginDir)) {
            issues.add(new ValidationIssue(Severity.ERROR, pluginDir.toString(),
                    "Directory does not exist"));
            return buildResult(pluginDir, pluginId, issues);
        }

        if (!Files.isDirectory(pluginDir)) {
            issues.add(new ValidationIssue(Severity.ERROR, pluginDir.toString(),
                    "Not a directory"));
            return buildResult(pluginDir, pluginId, issues);
        }

        // Extract plugin ID from MANIFEST.MF
        pluginId = extractPluginId(pluginDir);

        // Run all validations
        validateManifest(pluginDir, issues);
        validateBuildProperties(pluginDir, issues);
        validatePomXml(pluginDir, issues);
        validateOsgiInf(pluginDir, issues);
        validatePluginXml(pluginDir, issues);
        validateJavaSources(pluginDir, issues);

        return buildResult(pluginDir, pluginId, issues);
    }

    private ValidationResult buildResult(Path pluginDir, String pluginId, List<ValidationIssue> issues) {
        int errors = (int) issues.stream().filter(i -> i.severity == Severity.ERROR).count();
        int warnings = (int) issues.stream().filter(i -> i.severity == Severity.WARNING).count();
        return new ValidationResult(pluginDir, pluginId, issues, errors, warnings);
    }

    private String extractPluginId(Path pluginDir) {
        Path manifest = pluginDir.resolve("META-INF/MANIFEST.MF");
        if (Files.exists(manifest)) {
            try {
                String content = Files.readString(manifest);
                Matcher m = BUNDLE_SYMBOLIC_NAME_PATTERN.matcher(content);
                if (m.find()) {
                    return m.group(1).trim();
                }
            } catch (IOException ignored) {
            }
        }
        return pluginDir.getFileName().toString();
    }

    private void validateManifest(Path pluginDir, List<ValidationIssue> issues) {
        Path manifest = pluginDir.resolve("META-INF/MANIFEST.MF");

        if (!Files.exists(manifest)) {
            issues.add(new ValidationIssue(Severity.ERROR, "META-INF/MANIFEST.MF",
                    "File not found - required for OSGi bundle"));
            return;
        }

        try {
            String content = Files.readString(manifest);

            // Check required headers
            checkManifestHeader(content, "Manifest-Version", true, issues);
            checkManifestHeader(content, "Bundle-ManifestVersion", true, issues);
            checkManifestHeader(content, "Bundle-SymbolicName", true, issues);
            checkManifestHeader(content, "Bundle-Version", true, issues);
            checkManifestHeader(content, "Bundle-Name", false, issues);
            checkManifestHeader(content, "Bundle-Vendor", false, issues);

            // Check execution environment
            if (!content.contains("Bundle-RequiredExecutionEnvironment")) {
                issues.add(new ValidationIssue(Severity.ERROR, "META-INF/MANIFEST.MF",
                        "Missing Bundle-RequiredExecutionEnvironment (e.g., JavaSE-17)"));
            }

            // Check for Require-Bundle or Fragment-Host
            if (!content.contains("Require-Bundle") && !content.contains("Fragment-Host")) {
                issues.add(new ValidationIssue(Severity.ERROR, "META-INF/MANIFEST.MF",
                        "Missing Require-Bundle or Fragment-Host - plugin has no dependencies"));
            }

            // Check for org.adempiere.base dependency
            if (content.contains("Require-Bundle") && !content.contains("org.adempiere.base")) {
                issues.add(new ValidationIssue(Severity.WARNING, "META-INF/MANIFEST.MF",
                        "org.adempiere.base not in Require-Bundle - most plugins need this"));
            }

            // Check line endings (should not have Windows CRLF)
            if (content.contains("\r\n")) {
                issues.add(new ValidationIssue(Severity.WARNING, "META-INF/MANIFEST.MF",
                        "Contains Windows line endings (CRLF) - may cause issues"));
            }

            // Check for trailing whitespace in continuation lines
            if (content.matches("(?m)^\\s+.*\\s+$")) {
                issues.add(new ValidationIssue(Severity.WARNING, "META-INF/MANIFEST.MF",
                        "Contains trailing whitespace - may cause parsing issues"));
            }

            // Validate bundle version format
            Pattern versionPattern = Pattern.compile("Bundle-Version:\\s*(\\d+\\.\\d+\\.\\d+)");
            Matcher vm = versionPattern.matcher(content);
            if (vm.find()) {
                String version = vm.group(1);
                if (!version.matches("\\d+\\.\\d+\\.\\d+")) {
                    issues.add(new ValidationIssue(Severity.ERROR, "META-INF/MANIFEST.MF",
                            "Invalid Bundle-Version format: " + version + " (expected: major.minor.micro)"));
                }
            }

        } catch (IOException e) {
            issues.add(new ValidationIssue(Severity.ERROR, "META-INF/MANIFEST.MF",
                    "Cannot read file: " + e.getMessage()));
        }
    }

    private void checkManifestHeader(String content, String header, boolean required, List<ValidationIssue> issues) {
        if (!content.contains(header + ":")) {
            Severity severity = required ? Severity.ERROR : Severity.WARNING;
            issues.add(new ValidationIssue(severity, "META-INF/MANIFEST.MF",
                    "Missing " + header + (required ? " (required)" : " (recommended)")));
        }
    }

    private void validateBuildProperties(Path pluginDir, List<ValidationIssue> issues) {
        Path buildProps = pluginDir.resolve("build.properties");

        if (!Files.exists(buildProps)) {
            issues.add(new ValidationIssue(Severity.ERROR, "build.properties",
                    "File not found - required for Tycho build"));
            return;
        }

        try {
            String content = Files.readString(buildProps);

            // Check required entries
            if (!content.contains("source..")) {
                issues.add(new ValidationIssue(Severity.ERROR, "build.properties",
                        "Missing 'source..' entry - specifies source folders"));
            }

            if (!content.contains("output..")) {
                issues.add(new ValidationIssue(Severity.ERROR, "build.properties",
                        "Missing 'output..' entry - specifies output folder"));
            }

            if (!content.contains("bin.includes")) {
                issues.add(new ValidationIssue(Severity.ERROR, "build.properties",
                        "Missing 'bin.includes' entry - specifies files to include in bundle"));
            } else {
                // Check bin.includes has META-INF/
                if (!content.contains("META-INF/")) {
                    issues.add(new ValidationIssue(Severity.WARNING, "build.properties",
                            "bin.includes should contain META-INF/"));
                }
            }

            // Check for common mistakes - source.. should reference src/
            if (content.contains("src/") && !content.matches("(?s).*source\\.\\.\\s*=\\s*src/.*")) {
                issues.add(new ValidationIssue(Severity.WARNING, "build.properties",
                        "Has 'src/' reference but source.. may not be configured correctly"));
            }

        } catch (IOException e) {
            issues.add(new ValidationIssue(Severity.ERROR, "build.properties",
                    "Cannot read file: " + e.getMessage()));
        }
    }

    private void validatePomXml(Path pluginDir, List<ValidationIssue> issues) {
        Path pom = pluginDir.resolve("pom.xml");

        if (!Files.exists(pom)) {
            issues.add(new ValidationIssue(Severity.ERROR, "pom.xml",
                    "File not found - required for Maven/Tycho build"));
            return;
        }

        try {
            String content = Files.readString(pom);

            // Check packaging type
            if (!content.contains("<packaging>eclipse-plugin</packaging>") &&
                !content.contains("<packaging>bundle</packaging>")) {
                issues.add(new ValidationIssue(Severity.ERROR, "pom.xml",
                        "Missing or invalid packaging - should be 'eclipse-plugin' or 'bundle'"));
            }

            // Check for Tycho plugin
            if (!content.contains("tycho-maven-plugin") && !content.contains("tycho.version")) {
                issues.add(new ValidationIssue(Severity.WARNING, "pom.xml",
                        "Tycho Maven plugin not found - required for OSGi builds"));
            }

            // Check artifactId matches Bundle-SymbolicName
            Pattern artifactPattern = Pattern.compile("<artifactId>([^<]+)</artifactId>");
            Matcher am = artifactPattern.matcher(content);
            if (am.find()) {
                String artifactId = am.group(1);
                String pluginId = extractPluginId(pluginDir);
                if (!artifactId.equals(pluginId) && !pluginId.equals("unknown")) {
                    issues.add(new ValidationIssue(Severity.WARNING, "pom.xml",
                            "artifactId '" + artifactId + "' doesn't match Bundle-SymbolicName '" + pluginId + "'"));
                }
            }

        } catch (IOException e) {
            issues.add(new ValidationIssue(Severity.ERROR, "pom.xml",
                    "Cannot read file: " + e.getMessage()));
        }
    }

    private void validateOsgiInf(Path pluginDir, List<ValidationIssue> issues) {
        Path osgiInf = pluginDir.resolve("OSGI-INF");

        if (!Files.exists(osgiInf)) {
            // Not an error - some plugins don't have declarative services
            issues.add(new ValidationIssue(Severity.INFO, "OSGI-INF",
                    "Directory not found - OK if not using declarative services"));
            return;
        }

        if (!Files.isDirectory(osgiInf)) {
            issues.add(new ValidationIssue(Severity.ERROR, "OSGI-INF",
                    "Exists but is not a directory"));
            return;
        }

        // Check for XML files
        try (Stream<Path> xmlFiles = Files.list(osgiInf).filter(p -> p.toString().endsWith(".xml"))) {
            List<Path> files = xmlFiles.toList();

            if (files.isEmpty()) {
                issues.add(new ValidationIssue(Severity.INFO, "OSGI-INF",
                        "No service component XML files found"));
            } else {
                // Validate each XML file
                for (Path xmlFile : files) {
                    validateOsgiXml(xmlFile, issues);
                }
            }
        } catch (IOException e) {
            issues.add(new ValidationIssue(Severity.ERROR, "OSGI-INF",
                    "Cannot list directory: " + e.getMessage()));
        }

        // Check Service-Component header in MANIFEST.MF
        Path manifest = pluginDir.resolve("META-INF/MANIFEST.MF");
        if (Files.exists(manifest)) {
            try {
                String content = Files.readString(manifest);
                if (!content.contains("Service-Component:")) {
                    issues.add(new ValidationIssue(Severity.WARNING, "META-INF/MANIFEST.MF",
                            "OSGI-INF exists but no Service-Component header in MANIFEST.MF"));
                }
            } catch (IOException ignored) {
            }
        }
    }

    private void validateOsgiXml(Path xmlFile, List<ValidationIssue> issues) {
        String fileName = "OSGI-INF/" + xmlFile.getFileName();

        try {
            String content = Files.readString(xmlFile);

            // Basic XML validation
            if (!content.trim().startsWith("<?xml") && !content.trim().startsWith("<")) {
                issues.add(new ValidationIssue(Severity.ERROR, fileName,
                        "Invalid XML - doesn't start with XML declaration or root element"));
                return;
            }

            // Check for scr namespace
            if (!content.contains("scr") && !content.contains("component")) {
                issues.add(new ValidationIssue(Severity.WARNING, fileName,
                        "Doesn't appear to be a valid SCR component descriptor"));
            }

            // Check implementation class exists
            Pattern implPattern = Pattern.compile("class=\"([^\"]+)\"");
            Matcher m = implPattern.matcher(content);
            if (m.find()) {
                String className = m.group(1);
                // Convert class name to path
                String classPath = className.replace('.', '/') + ".java";
                Path srcPath = xmlFile.getParent().getParent().resolve("src").resolve(classPath);
                if (!Files.exists(srcPath)) {
                    issues.add(new ValidationIssue(Severity.WARNING, fileName,
                            "Implementation class not found: " + className));
                }
            }

        } catch (IOException e) {
            issues.add(new ValidationIssue(Severity.ERROR, fileName,
                    "Cannot read file: " + e.getMessage()));
        }
    }

    private void validatePluginXml(Path pluginDir, List<ValidationIssue> issues) {
        Path pluginXml = pluginDir.resolve("plugin.xml");

        if (!Files.exists(pluginXml)) {
            // Not required
            issues.add(new ValidationIssue(Severity.INFO, "plugin.xml",
                    "Not found - OK if not using Eclipse extensions"));
            return;
        }

        try {
            String content = Files.readString(pluginXml);

            // Basic XML validation
            if (!content.trim().startsWith("<?xml") && !content.trim().startsWith("<")) {
                issues.add(new ValidationIssue(Severity.ERROR, "plugin.xml",
                        "Invalid XML format"));
                return;
            }

            // Check for extension points
            if (!content.contains("<extension") && !content.contains("<extension-point")) {
                issues.add(new ValidationIssue(Severity.WARNING, "plugin.xml",
                        "No extensions defined - file may be unnecessary"));
            }

        } catch (IOException e) {
            issues.add(new ValidationIssue(Severity.ERROR, "plugin.xml",
                    "Cannot read file: " + e.getMessage()));
        }
    }

    private void validateJavaSources(Path pluginDir, List<ValidationIssue> issues) {
        Path srcDir = pluginDir.resolve("src");

        if (!Files.exists(srcDir)) {
            issues.add(new ValidationIssue(Severity.WARNING, "src",
                    "Source directory not found"));
            return;
        }

        try (Stream<Path> javaFiles = Files.walk(srcDir)
                .filter(p -> p.toString().endsWith(".java"))) {

            List<Path> files = javaFiles.toList();

            if (files.isEmpty()) {
                issues.add(new ValidationIssue(Severity.WARNING, "src",
                        "No Java source files found"));
                return;
            }

            // Check each Java file for basic issues
            Set<String> packageNames = new HashSet<>();
            for (Path javaFile : files) {
                validateJavaFile(javaFile, srcDir, packageNames, issues);
            }

            // Validate package structure matches plugin ID
            String pluginId = extractPluginId(pluginDir);
            if (!pluginId.equals("unknown")) {
                boolean hasMatchingPackage = packageNames.stream()
                        .anyMatch(pkg -> pkg.startsWith(pluginId) || pluginId.startsWith(pkg));
                if (!hasMatchingPackage && !packageNames.isEmpty()) {
                    issues.add(new ValidationIssue(Severity.WARNING, "src",
                            "No package matches plugin ID '" + pluginId + "'"));
                }
            }

        } catch (IOException e) {
            issues.add(new ValidationIssue(Severity.ERROR, "src",
                    "Cannot scan directory: " + e.getMessage()));
        }
    }

    private void validateJavaFile(Path javaFile, Path srcDir, Set<String> packageNames, List<ValidationIssue> issues) {
        String relativePath = srcDir.relativize(javaFile).toString();

        try {
            String content = Files.readString(javaFile);

            // Extract package name
            Pattern packagePattern = Pattern.compile("package\\s+([\\w.]+)\\s*;");
            Matcher pm = packagePattern.matcher(content);
            if (pm.find()) {
                String packageName = pm.group(1);
                packageNames.add(packageName);

                // Verify package matches directory structure
                String expectedPath = packageName.replace('.', '/');
                String actualPath = relativePath.substring(0, relativePath.lastIndexOf('/'));
                if (!actualPath.equals(expectedPath)) {
                    issues.add(new ValidationIssue(Severity.ERROR, relativePath,
                            "Package '" + packageName + "' doesn't match directory structure"));
                }
            } else {
                issues.add(new ValidationIssue(Severity.WARNING, relativePath,
                        "No package declaration found"));
            }

            // Check for common issues
            if (content.contains("System.out.println") || content.contains("System.err.println")) {
                issues.add(new ValidationIssue(Severity.INFO, relativePath,
                        "Contains System.out/err - consider using CLogger"));
            }

            // Check class declaration exists
            if (!content.contains("class ") && !content.contains("interface ") &&
                !content.contains("enum ") && !content.contains("record ")) {
                issues.add(new ValidationIssue(Severity.WARNING, relativePath,
                        "No class/interface/enum/record declaration found"));
            }

        } catch (IOException e) {
            issues.add(new ValidationIssue(Severity.ERROR, relativePath,
                    "Cannot read file: " + e.getMessage()));
        }
    }
}
