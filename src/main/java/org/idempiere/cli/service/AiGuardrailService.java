package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.idempiere.cli.model.GeneratedCode;
import org.idempiere.cli.util.PluginUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@ApplicationScoped
public class AiGuardrailService {

    private static final ConcurrentHashMap<Path, Optional<ClasspathIndex>> CLASSPATH_INDEX_CACHE = new ConcurrentHashMap<>();
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s+([\\w.]+)(\\.\\*)?\\s*;");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern TYPE_PATTERN = Pattern.compile("(?m)^\\s*(?:public\\s+)?(?:abstract\\s+|final\\s+|sealed\\s+|non-sealed\\s+)?(?:class|interface|enum|record)\\s+([A-Za-z_][A-Za-z0-9_]*)\\b");
    private static final Pattern FQCN_PATTERN = Pattern.compile("\\b(?:[a-z_][a-z0-9_]*\\.)+[A-Z][A-Za-z0-9_$]*\\b");
    private static final List<String> CRITICAL_PREFIXES = List.of("org.idempiere.", "org.compiere.", "org.adempiere.");
    private static final List<String> JDK_PREFIXES = List.of("java.", "jdk.", "sun.", "com.sun.", "org.w3c.", "org.xml.", "org.ietf.");

    private record ClasspathIndex(Set<String> classes, Set<String> packages) {}

    public List<String> validateGeneratedCode(GeneratedCode code, String pluginId) {
        return validateGeneratedCode(code, pluginId, null);
    }

    public List<String> validateGeneratedCode(GeneratedCode code, String pluginId, Path pluginDir) {
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

    public boolean hasBlockingIssue(List<String> issues) {
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
