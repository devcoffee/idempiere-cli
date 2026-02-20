package org.idempiere.cli.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.idempiere.cli.util.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles XML updates for multi-module scaffold maintenance.
 */
@ApplicationScoped
public class ScaffoldXmlUpdateService {

    public void updateRootPomModules(Path rootDir, String moduleName) throws IOException {
        Path pomFile = rootDir.resolve("pom.xml");
        if (!Files.exists(pomFile)) {
            System.err.println("Warning: Root pom.xml not found, skipping module registration");
            return;
        }

        Document doc = XmlUtils.load(pomFile);

        if (XmlUtils.hasModuleWithName(doc, moduleName)) {
            System.out.println("  Module already registered in pom.xml");
            return;
        }

        XmlUtils.addModule(doc, moduleName);
        XmlUtils.savePreservingFormat(doc, pomFile);
        System.out.println("  Updated: " + pomFile);
    }

    public void updateCategoryXml(Path rootDir, String bundleId, boolean isFragment) throws IOException {
        Path p2Dir = findP2Module(rootDir);
        if (p2Dir == null) {
            return;
        }

        Path categoryFile = p2Dir.resolve("category.xml");
        if (!Files.exists(categoryFile)) {
            return;
        }

        Document doc = XmlUtils.load(categoryFile);

        if (XmlUtils.hasElementWithAttribute(doc, "bundle", "id", bundleId)) {
            return;
        }

        String categoryName = XmlUtils.getAttributeValue(doc, "category-def", "name").orElse("default");
        Element bundleElement = XmlUtils.createBundleElement(doc, bundleId, categoryName);
        XmlUtils.findElement(doc, "site").ifPresent(site -> site.appendChild(bundleElement));

        XmlUtils.savePreservingFormat(doc, categoryFile);
        System.out.println("  Updated: " + categoryFile);
    }

    public void updateCategoryXmlForFeature(Path rootDir, String featureId) throws IOException {
        Path p2Dir = findP2Module(rootDir);
        if (p2Dir == null) {
            return;
        }

        Path categoryFile = p2Dir.resolve("category.xml");
        if (!Files.exists(categoryFile)) {
            return;
        }

        Document doc = XmlUtils.load(categoryFile);
        String baseFeatureId = featureId.replace(".feature", "");

        if (XmlUtils.hasElementWithAttribute(doc, "iu", "id", baseFeatureId + ".feature.group")) {
            return;
        }

        String categoryName = XmlUtils.getAttributeValue(doc, "category-def", "name").orElse("default");
        Element iuElement = XmlUtils.createIuElement(doc, baseFeatureId, categoryName);
        XmlUtils.insertBefore(doc, iuElement, "category-def");

        XmlUtils.savePreservingFormat(doc, categoryFile);
        System.out.println("  Updated: " + categoryFile);
    }

    public void updateFeatureXml(Path rootDir, String bundleId, boolean isFragment) throws IOException {
        String baseId = extractBaseProjectId(rootDir);
        Path featureDir = rootDir.resolve(baseId + ".feature");
        if (!Files.exists(featureDir)) {
            return;
        }

        Path featureFile = featureDir.resolve("feature.xml");
        if (!Files.exists(featureFile)) {
            return;
        }

        Document doc = XmlUtils.load(featureFile);

        if (XmlUtils.hasElementWithAttribute(doc, "plugin", "id", bundleId)) {
            return;
        }

        Element pluginElement = XmlUtils.createPluginElement(doc, bundleId, isFragment);
        XmlUtils.findElement(doc, "feature").ifPresent(feature -> feature.appendChild(pluginElement));

        XmlUtils.savePreservingFormat(doc, featureFile);
        System.out.println("  Updated: " + featureFile);
    }

    private Path findP2Module(Path rootDir) {
        try (var stream = Files.list(rootDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().endsWith(".p2"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private String extractBaseProjectId(Path rootDir) {
        return rootDir.getFileName().toString();
    }
}
