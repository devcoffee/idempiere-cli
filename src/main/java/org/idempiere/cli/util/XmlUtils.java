package org.idempiere.cli.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Utility class for XML manipulation using DOM.
 */
public final class XmlUtils {

    private XmlUtils() {
    }

    /**
     * Loads an XML document from a file with XXE protection.
     */
    public static Document load(Path file) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Protection against XXE (XML External Entity) attacks
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(file.toFile());
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse XML: " + file, e);
        }
    }

    /**
     * Saves XML with clean formatting.
     * Use for CREATING new files (templates, new modules).
     */
    public static void save(Document doc, Path file) throws IOException {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            // Remove extra whitespace before saving
            doc.normalize();
            removeEmptyTextNodes(doc.getDocumentElement());

            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(file.toFile());
            transformer.transform(source, result);
        } catch (TransformerException e) {
            throw new IOException("Failed to save XML: " + file, e);
        }
    }

    /**
     * Saves XML preserving original formatting as much as possible.
     * Use for UPDATING existing files (pom.xml, category.xml, feature.xml).
     */
    public static void savePreservingFormat(Document doc, Path file) throws IOException {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            // Do NOT set INDENT - preserves original formatting
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(file.toFile());
            transformer.transform(source, result);
        } catch (TransformerException e) {
            throw new IOException("Failed to save XML: " + file, e);
        }
    }

    /**
     * Finds the first element matching the given tag name.
     */
    public static Optional<Element> findElement(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return Optional.of((Element) nodes.item(0));
        }
        return Optional.empty();
    }

    /**
     * Finds all elements matching the given tag name.
     */
    public static NodeList findElements(Document doc, String tagName) {
        return doc.getElementsByTagName(tagName);
    }

    /**
     * Checks if an element with the given attribute value exists.
     */
    public static boolean hasElementWithAttribute(Document doc, String tagName, String attrName, String attrValue) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            if (attrValue.equals(el.getAttribute(attrName))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a module element with the given text content exists.
     */
    public static boolean hasModuleWithName(Document doc, String moduleName) {
        NodeList modules = doc.getElementsByTagName("module");
        for (int i = 0; i < modules.getLength(); i++) {
            if (moduleName.equals(modules.item(i).getTextContent().trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds a module element to the modules section.
     */
    public static void addModule(Document doc, String moduleName) {
        Optional<Element> modulesOpt = findElement(doc, "modules");
        if (modulesOpt.isEmpty()) {
            return;
        }

        Element modules = modulesOpt.get();
        Element newModule = doc.createElement("module");
        newModule.setTextContent(moduleName);
        modules.appendChild(newModule);
    }

    /**
     * Creates a bundle element for category.xml.
     */
    public static Element createBundleElement(Document doc, String bundleId, String categoryName) {
        Element bundle = doc.createElement("bundle");
        bundle.setAttribute("id", bundleId);
        bundle.setAttribute("version", "0.0.0");

        Element category = doc.createElement("category");
        category.setAttribute("name", categoryName);
        bundle.appendChild(category);

        return bundle;
    }

    /**
     * Creates an iu (installable unit) element for category.xml.
     */
    public static Element createIuElement(Document doc, String featureId, String categoryName) {
        Element iu = doc.createElement("iu");
        iu.setAttribute("id", featureId + ".feature.group");
        iu.setAttribute("version", "0.0.0");

        Element category = doc.createElement("category");
        category.setAttribute("name", categoryName);
        iu.appendChild(category);

        return iu;
    }

    /**
     * Creates a plugin element for feature.xml.
     */
    public static Element createPluginElement(Document doc, String pluginId, boolean isFragment) {
        Element plugin = doc.createElement("plugin");
        plugin.setAttribute("id", pluginId);
        plugin.setAttribute("download-size", "0");
        plugin.setAttribute("install-size", "0");
        plugin.setAttribute("version", "0.0.0");
        if (isFragment) {
            plugin.setAttribute("fragment", "true");
        }
        plugin.setAttribute("unpack", "false");
        return plugin;
    }

    /**
     * Gets the value of a named attribute from the first matching element.
     */
    public static Optional<String> getAttributeValue(Document doc, String tagName, String attrName) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            Element el = (Element) nodes.item(0);
            String value = el.getAttribute(attrName);
            if (!value.isEmpty()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /**
     * Inserts an element before the first occurrence of a tag.
     */
    public static void insertBefore(Document doc, Element newElement, String beforeTagName) {
        Optional<Element> target = findElement(doc, beforeTagName);
        if (target.isPresent()) {
            target.get().getParentNode().insertBefore(newElement, target.get());
        }
    }

    /**
     * Removes empty text nodes (whitespace only) from the document.
     */
    private static void removeEmptyTextNodes(Node node) {
        NodeList children = node.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().trim().isEmpty()) {
                node.removeChild(child);
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                removeEmptyTextNodes(child);
            }
        }
    }
}
