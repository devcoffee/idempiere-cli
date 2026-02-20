package org.idempiere.cli.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.idempiere.cli.model.GeneratedCode;
import org.idempiere.cli.model.ProjectContext;
import org.idempiere.cli.service.ai.AiClient;
import org.idempiere.cli.service.ai.AiClientFactory;
import org.idempiere.cli.service.ai.AiResponse;
import org.idempiere.cli.util.PluginUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * AI-powered code generation service.
 * Orchestrates skill loading, project analysis, AI client calls, and validation.
 * Returns empty if AI is not configured or generation fails.
 */
@ApplicationScoped
public class SmartScaffoldService {

    @Inject
    AiClientFactory aiClientFactory;

    @Inject
    SkillManager skillManager;

    @Inject
    ProjectAnalyzer projectAnalyzer;

    @Inject
    SessionLogger sessionLogger;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ConcurrentHashMap<Path, Optional<ClasspathIndex>> CLASSPATH_INDEX_CACHE = new ConcurrentHashMap<>();
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s+([\\w.]+)(\\.\\*)?\\s*;");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern TYPE_PATTERN = Pattern.compile("(?m)^\\s*(?:public\\s+)?(?:abstract\\s+|final\\s+|sealed\\s+|non-sealed\\s+)?(?:class|interface|enum|record)\\s+([A-Za-z_][A-Za-z0-9_]*)\\b");
    private static final Pattern FQCN_PATTERN = Pattern.compile("\\b(?:[a-z_][a-z0-9_]*\\.)+[A-Z][A-Za-z0-9_$]*\\b");
    private static final List<String> CRITICAL_PREFIXES = List.of("org.idempiere.", "org.compiere.", "org.adempiere.");
    private static final List<String> JDK_PREFIXES = List.of("java.", "jdk.", "sun.", "com.sun.", "org.w3c.", "org.xml.", "org.ietf.");
    private static final int MAX_ISSUES_TO_PRINT = 10;

    private record ParseResult(GeneratedCode code, String errorMessage) {
        static ParseResult success(GeneratedCode code) {
            return new ParseResult(code, null);
        }

        static ParseResult failure(String errorMessage) {
            return new ParseResult(null, errorMessage);
        }
    }

    private record ClasspathIndex(Set<String> classes, Set<String> packages) {}

    /**
     * Tries AI generation for a component. Returns the generated code if successful, empty otherwise.
     */
    public Optional<GeneratedCode> generate(String type, String name, Path pluginDir,
                                             String pluginId, Map<String, Object> extraData) {
        Optional<AiClient> client = aiClientFactory.getClient();
        if (client.isEmpty()) {
            return Optional.empty();
        }

        ProjectContext ctx = projectAnalyzer.analyze(pluginDir);
        return tryAiGeneration(type, name, pluginDir, pluginId, ctx, extraData, client.get());
    }

    private Optional<GeneratedCode> tryAiGeneration(String type, String name, Path pluginDir,
                                                     String pluginId, ProjectContext ctx,
                                                     Map<String, Object> extraData, AiClient client) {
        Optional<String> skill = skillManager.loadSkill(type);

        // If no skill file and no user prompt, skip AI generation
        boolean hasUserPrompt = extraData != null && extraData.get("prompt") instanceof String p && !p.isBlank();
        if (skill.isEmpty() && !hasUserPrompt) {
            return Optional.empty();
        }

        String prompt = buildAiPrompt(skill.orElse(null), ctx, type, name, extraData);
        sessionLogger.logCommandOutput("ai-prompt", prompt);

        System.out.println("  Generating with AI (" + client.providerName() + ")...");
        AiResponse response = client.generate(prompt);

        if (!response.success()) {
            sessionLogger.logError("AI generation failed: " + response.error());
            System.err.println("  AI generation failed: " + response.error());
            return Optional.empty();
        }

        sessionLogger.logCommandOutput("ai-response", response.content());

        ParseResult parsed = parseAiResponseDetailed(response.content());
        GeneratedCode code = parsed.code();
        if (code == null || code.getFiles().isEmpty()) {
            sessionLogger.logError("AI parse failed: " + parsed.errorMessage());
            sessionLogger.logCommandOutput("ai-response-raw", response.content());
            System.err.println("  Failed to parse AI response. Falling back to template generation.");
            System.err.println("  See log for details: " + currentLogPathHint());
            return Optional.empty();
        }

        List<String> issues = validateGeneratedCode(code, pluginId, pluginDir);
        if (!issues.isEmpty()) {
            System.err.println("  AI output validation warnings:");
            issues.stream().limit(MAX_ISSUES_TO_PRINT).forEach(i -> System.err.println("    - " + i));
            if (issues.size() > MAX_ISSUES_TO_PRINT) {
                System.err.println("    - ... and " + (issues.size() - MAX_ISSUES_TO_PRINT) + " more");
            }
            sessionLogger.logCommandOutput("ai-validation-issues", String.join("\n", issues));
        }

        if (hasBlockingIssue(issues)) {
            sessionLogger.logError("AI output rejected by classpath guardrails.");
            System.err.println("  AI output incompatible with current target platform. Falling back to template generation.");
            System.err.println("  See log for details: " + currentLogPathHint());
            return Optional.empty();
        }

        try {
            code.writeTo(pluginDir);
            System.out.println("  Generated with AI");
            return Optional.of(code);
        } catch (IOException e) {
            sessionLogger.logError("Failed to write AI-generated files: " + e.getMessage());
            System.err.println("  Failed to write AI-generated files: " + e.getMessage());
            return Optional.empty();
        }
    }

    /** Built-in component descriptions used when no SKILL.md is available. */
    static final Map<String, String> COMPONENT_DESCRIPTIONS = Map.ofEntries(
            Map.entry("callout", "An iDempiere column-level callout implementing IColumnCallout. "
                    + "Use @Callout(tableName, columnName) annotation for registration. "
                    + "The existing CalloutFactory scans the package for all @Callout classes automatically."),
            Map.entry("process", "An iDempiere server-side process extending SvrProcess. "
                    + "Use @Process annotation with its own AnnotationBasedProcessFactory."),
            Map.entry("process-mapped", "An iDempiere process using MappedProcessFactory (2Pack compatible). "
                    + "Extends SvrProcess, registered via MappedProcessFactory in Activator."),
            Map.entry("event-handler", "An iDempiere model event handler using @EventDelegate annotation. "
                    + "Handles lifecycle events like BeforeNew, AfterChange on model objects."),
            Map.entry("zk-form", "A ZK programmatic form extending ADForm for iDempiere UI."),
            Map.entry("zk-form-zul", "A ZUL-based form with separate .zul layout file and Controller class."),
            Map.entry("listbox-group", "A form with grouped/collapsible Listbox using GroupsModel."),
            Map.entry("wlistbox-editor", "A form with custom WListbox column editors."),
            Map.entry("report", "An iDempiere report process extending SvrProcess."),
            Map.entry("jasper-report", "A Jasper report with Activator and sample .jrxml template."),
            Map.entry("window-validator", "An iDempiere window-level event validator."),
            Map.entry("rest-extension", "A REST API resource extension using JAX-RS annotations."),
            Map.entry("facts-validator", "An iDempiere accounting facts validator."),
            Map.entry("base-test", "A JUnit test class using AbstractTestCase (iDempiere test infrastructure).")
    );

    String buildAiPrompt(String skill, ProjectContext ctx, String type,
                          String name, Map<String, Object> extraData) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are generating an iDempiere plugin component.\n\n");

        if (skill != null) {
            prompt.append("## Skill Instructions\n");
            prompt.append(skill).append("\n\n");
        } else {
            prompt.append("## Component Type\n");
            prompt.append(COMPONENT_DESCRIPTIONS.getOrDefault(type,
                    "An iDempiere " + type + " component.")).append("\n\n");
        }

        prompt.append("## Project Context\n");
        prompt.append("- Plugin ID: ").append(ctx.getPluginId()).append("\n");
        prompt.append("- Base package: ").append(ctx.getBasePackage()).append("\n");
        if (ctx.getPlatformVersion() != null) {
            prompt.append("- Platform version: iDempiere ").append(ctx.getPlatformVersion().major()).append("\n");
        }
        if (!ctx.getExistingClasses().isEmpty()) {
            prompt.append("- Existing classes: ").append(ctx.getExistingClasses()).append("\n");
        }
        prompt.append("- Uses annotation pattern: ").append(ctx.usesAnnotationPattern()).append("\n");
        prompt.append("- Has Activator: ").append(ctx.hasActivator()).append("\n");
        prompt.append("- Has CalloutFactory: ").append(ctx.hasCalloutFactory()).append("\n");
        prompt.append("- Has EventManager: ").append(ctx.hasEventManager()).append("\n");

        if (ctx.getManifestContent() != null) {
            prompt.append("\n## Current MANIFEST.MF\n```\n").append(ctx.getManifestContent()).append("\n```\n");
        }

        prompt.append("\n## Task\n");
        prompt.append("Generate a ").append(type).append(" named ").append(name).append(".\n");

        if (extraData != null) {
            String userPrompt = (String) extraData.get("prompt");
            if (userPrompt != null && !userPrompt.isBlank()) {
                prompt.append("\n## User Instructions\n");
                prompt.append(userPrompt).append("\n");
            }
            // Pass remaining extraData (excluding "prompt") as additional parameters
            Map<String, Object> params = new java.util.HashMap<>(extraData);
            params.remove("prompt");
            if (!params.isEmpty()) {
                prompt.append("Additional parameters: ").append(params).append("\n");
            }
        }

        prompt.append("""

                ## Output Format
                Respond with ONLY a JSON object (no markdown fences, no explanation):
                {
                  "files": [
                    {"path": "relative/path/from/plugin/root/File.java", "content": "full file content"}
                  ],
                  "manifest_additions": ["Import-Package lines to add"],
                  "build_properties_additions": ["lines to add to build.properties"]
                }

                IMPORTANT:
                - Paths are relative to the plugin root directory
                - Include full file content, not snippets
                - Use the exact package based on the Plugin ID
                - Follow the naming conventions visible in existing classes
                """);

        return prompt.toString();
    }

    GeneratedCode parseAiResponse(String raw) {
        return parseAiResponseDetailed(raw).code();
    }

    private ParseResult parseAiResponseDetailed(String raw) {
        if (raw == null || raw.isBlank()) {
            return ParseResult.failure("AI response is empty");
        }

        // 1. Try raw as-is
        ParseResult result = tryParseJson(raw.strip(), "raw response");
        if (result.code() != null) return result;
        String lastError = result.errorMessage();

        // 2. Try extracting from markdown code fences (```json ... ``` or ``` ... ```)
        java.util.regex.Matcher fenceMatcher = java.util.regex.Pattern
                .compile("```(?:json)?\\s*\\n?(\\{.*?\\})\\s*```", java.util.regex.Pattern.DOTALL)
                .matcher(raw);
        if (fenceMatcher.find()) {
            result = tryParseJson(fenceMatcher.group(1).strip(), "markdown code fence");
            if (result.code() != null) return result;
            lastError = result.errorMessage();
        }

        // 3. Try extracting the outermost { ... } block
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            result = tryParseJson(raw.substring(start, end + 1).strip(), "outer JSON block");
            if (result.code() != null) return result;
            lastError = result.errorMessage();
        }

        return ParseResult.failure(lastError != null ? lastError : "No parseable JSON object found in AI response");
    }

    private ParseResult tryParseJson(String json, String source) {
        try {
            GeneratedCode code = objectMapper.readValue(json, GeneratedCode.class);
            if (code != null && code.getFiles() != null && !code.getFiles().isEmpty()) {
                return ParseResult.success(code);
            }
            return ParseResult.failure("Parsed " + source + " but files array is empty");
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            message = message.replace('\n', ' ').replace('\r', ' ');
            return ParseResult.failure("Invalid JSON in " + source + ": " + message);
        }
    }

    private String currentLogPathHint() {
        Path sessionLog = sessionLogger.getSessionLogFile();
        if (sessionLog != null) {
            return sessionLog.toAbsolutePath().toString();
        }
        return "~/.idempiere-cli/logs/latest.log";
    }

    List<String> validateGeneratedCode(GeneratedCode code, String pluginId) {
        return validateGeneratedCode(code, pluginId, null);
    }

    List<String> validateGeneratedCode(GeneratedCode code, String pluginId, Path pluginDir) {
        List<String> issues = new ArrayList<>();
        Set<String> generatedClasses = new HashSet<>();
        Set<String> generatedPackages = new HashSet<>();
        collectGeneratedTypes(code, generatedClasses, generatedPackages);
        Optional<ClasspathIndex> classpathIndex = resolveClasspathIndex(pluginDir);

        for (GeneratedCode.GeneratedFile file : code.getFiles()) {
            if (file.getPath() != null && file.getPath().contains("..")) {
                issues.add("Path traversal detected: " + file.getPath());
            }

            if (file.getPath() != null && file.getPath().endsWith(".java")) {
                if (file.getContent() != null && !file.getContent().contains("package " + pluginId)) {
                    issues.add("Unexpected package in " + file.getPath());
                }
                if (classpathIndex.isPresent()) {
                    validateJavaFileAgainstClasspath(
                            file, pluginDir, classpathIndex.get(), generatedClasses, generatedPackages, issues);
                } else if (pluginDir != null) {
                    issues.add("WARN: Could not resolve iDempiere target platform (org.idempiere.p2/target/repository).");
                }
            }

            if (file.getContent() == null || file.getContent().isBlank()) {
                issues.add("Empty content for " + file.getPath());
            }
        }

        return issues;
    }

    private boolean hasBlockingIssue(List<String> issues) {
        return issues.stream().anyMatch(i -> i.startsWith("BLOCKER:"));
    }

    private void validateJavaFileAgainstClasspath(GeneratedCode.GeneratedFile file,
                                                  Path pluginDir,
                                                  ClasspathIndex classpathIndex,
                                                  Set<String> generatedClasses,
                                                  Set<String> generatedPackages,
                                                  List<String> issues) {
        String content = file.getContent();
        if (content == null) {
            return;
        }

        Matcher importMatcher = IMPORT_PATTERN.matcher(content);
        while (importMatcher.find()) {
            String imported = importMatcher.group(1);
            boolean wildcard = importMatcher.group(2) != null;
            if (wildcard) {
                String pkg = imported;
                if (!isResolvablePackage(pkg, pluginDir, classpathIndex, generatedPackages)) {
                    addUnresolvedIssue(issues, "Unresolved wildcard import package: " + pkg + " in " + file.getPath(), pkg);
                }
            } else if (!isResolvableClass(imported, pluginDir, classpathIndex, generatedClasses)) {
                addUnresolvedIssue(issues, "Unresolved import: " + imported + " in " + file.getPath(), imported);
            }
        }

        Matcher fqcnMatcher = FQCN_PATTERN.matcher(content);
        while (fqcnMatcher.find()) {
            String fqcn = fqcnMatcher.group();
            if (isInImportOrPackageLine(content, fqcn)) {
                continue;
            }
            if (!isResolvableClass(fqcn, pluginDir, classpathIndex, generatedClasses)) {
                addUnresolvedIssue(issues, "Unresolved class reference: " + fqcn + " in " + file.getPath(), fqcn);
            }
        }
    }

    private boolean isInImportOrPackageLine(String content, String fqcn) {
        return content.contains("import " + fqcn + ";") || content.contains("package " + fqcn + ";");
    }

    private void addUnresolvedIssue(List<String> issues, String message, String symbol) {
        if (isCriticalSymbol(symbol)) {
            issues.add("BLOCKER: " + message);
        } else {
            issues.add("WARN: " + message);
        }
    }

    private boolean isCriticalSymbol(String symbol) {
        return CRITICAL_PREFIXES.stream().anyMatch(symbol::startsWith);
    }

    private boolean isResolvableClass(String fqcn, Path pluginDir, ClasspathIndex classpathIndex, Set<String> generatedClasses) {
        if (isJdkClass(fqcn)) {
            return true;
        }
        if (generatedClasses.contains(fqcn)) {
            return true;
        }
        if (classpathIndex.classes().contains(fqcn)) {
            return true;
        }
        return hasLocalSourceClass(pluginDir, fqcn);
    }

    private boolean isResolvablePackage(String pkg, Path pluginDir, ClasspathIndex classpathIndex, Set<String> generatedPackages) {
        if (isJdkClass(pkg)) {
            return true;
        }
        if (generatedPackages.contains(pkg)) {
            return true;
        }
        if (classpathIndex.packages().contains(pkg)) {
            return true;
        }
        return hasLocalSourcePackage(pluginDir, pkg);
    }

    private boolean isJdkClass(String symbol) {
        return JDK_PREFIXES.stream().anyMatch(symbol::startsWith);
    }

    private boolean hasLocalSourceClass(Path pluginDir, String fqcn) {
        if (pluginDir == null || fqcn == null || fqcn.isBlank()) {
            return false;
        }
        Path source = pluginDir.resolve("src").resolve(fqcn.replace('.', '/') + ".java");
        return java.nio.file.Files.exists(source);
    }

    private boolean hasLocalSourcePackage(Path pluginDir, String pkg) {
        if (pluginDir == null || pkg == null || pkg.isBlank()) {
            return false;
        }
        Path sourceDir = pluginDir.resolve("src").resolve(pkg.replace('.', '/'));
        return java.nio.file.Files.isDirectory(sourceDir);
    }

    private void collectGeneratedTypes(GeneratedCode code, Set<String> generatedClasses, Set<String> generatedPackages) {
        for (GeneratedCode.GeneratedFile file : code.getFiles()) {
            if (file.getPath() == null || !file.getPath().endsWith(".java") || file.getContent() == null) {
                continue;
            }
            String content = file.getContent();
            Matcher packageMatcher = PACKAGE_PATTERN.matcher(content);
            String pkg = packageMatcher.find() ? packageMatcher.group(1) : null;
            if (pkg != null) {
                generatedPackages.add(pkg);
            }
            Matcher typeMatcher = TYPE_PATTERN.matcher(content);
            if (pkg != null && typeMatcher.find()) {
                generatedClasses.add(pkg + "." + typeMatcher.group(1));
            }
        }
    }

    private Optional<ClasspathIndex> resolveClasspathIndex(Path pluginDir) {
        Optional<Path> repo = findP2Repository(pluginDir);
        if (repo.isEmpty()) {
            return Optional.empty();
        }
        Path key = repo.get().toAbsolutePath().normalize();
        return CLASSPATH_INDEX_CACHE.computeIfAbsent(key, this::buildClasspathIndexSafely);
    }

    private Optional<ClasspathIndex> buildClasspathIndexSafely(Path p2Repo) {
        try {
            return Optional.of(buildClasspathIndex(p2Repo));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private ClasspathIndex buildClasspathIndex(Path p2Repo) throws IOException {
        Set<String> classes = new HashSet<>();
        Set<String> packages = new HashSet<>();

        Path pluginsDir = p2Repo.resolve("plugins");
        Path scanRoot = java.nio.file.Files.isDirectory(pluginsDir) ? pluginsDir : p2Repo;

        try (Stream<Path> stream = java.nio.file.Files.walk(scanRoot)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .forEach(jar -> indexJar(jar, classes, packages));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }

        return new ClasspathIndex(Set.copyOf(classes), Set.copyOf(packages));
    }

    private void indexJar(Path jarPath, Set<String> classes, Set<String> packages) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .forEach(entry -> {
                        String className = entry.getName().replace('/', '.');
                        className = className.substring(0, className.length() - ".class".length());
                        if ("module-info".equals(className) || className.endsWith(".package-info")) {
                            return;
                        }
                        int inner = className.indexOf('$');
                        if (inner > 0) {
                            className = className.substring(0, inner);
                        }
                        classes.add(className);
                        int dot = className.lastIndexOf('.');
                        if (dot > 0) {
                            packages.add(className.substring(0, dot));
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Optional<Path> findP2Repository(Path pluginDir) {
        // 1) Explicit IDEMPIERE_HOME env (product dir or source dir)
        String idempiereHome = System.getenv("IDEMPIERE_HOME");
        if (idempiereHome != null && !idempiereHome.isBlank()) {
            Optional<Path> fromEnv = PluginUtils.findP2Repository(Path.of(idempiereHome));
            if (fromEnv.isPresent()) {
                return fromEnv;
            }
        }

        if (pluginDir == null) {
            return Optional.empty();
        }

        // 2) Walk up and search for sibling idempiere source with built p2 repository
        Path current = pluginDir.toAbsolutePath().normalize();
        while (current != null) {
            Path siblingIdempiere = current.resolve("idempiere");
            Optional<Path> siblingRepo = PluginUtils.findP2Repository(siblingIdempiere);
            if (siblingRepo.isPresent()) {
                return siblingRepo;
            }
            Optional<Path> currentRepo = PluginUtils.findP2Repository(current);
            if (currentRepo.isPresent()) {
                return currentRepo;
            }
            current = current.getParent();
        }

        return Optional.empty();
    }
}
