package org.idempiere.cli.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PluginDescriptor.
 */
class PluginDescriptorTest {

    @Test
    void testDefaultConstructor() {
        PluginDescriptor descriptor = new PluginDescriptor();

        assertEquals("1.0.0.qualifier", descriptor.getVersion());
        assertEquals("", descriptor.getVendor());
        assertNotNull(descriptor.getFeatures());
        assertTrue(descriptor.getFeatures().isEmpty());
        assertEquals(PlatformVersion.stable(), descriptor.getPlatformVersion());
    }

    @Test
    void testConstructorWithPluginId() {
        PluginDescriptor descriptor = new PluginDescriptor("org.idempiere.myplugin");

        assertEquals("org.idempiere.myplugin", descriptor.getPluginId());
        assertEquals("myplugin", descriptor.getPluginName());
    }

    @Test
    void testConstructorWithSimplePluginId() {
        PluginDescriptor descriptor = new PluginDescriptor("myplugin");

        assertEquals("myplugin", descriptor.getPluginId());
        assertEquals("myplugin", descriptor.getPluginName());
    }

    @Test
    void testSetPluginId() {
        PluginDescriptor descriptor = new PluginDescriptor();
        descriptor.setPluginId("com.example.plugin");

        assertEquals("com.example.plugin", descriptor.getPluginId());
    }

    @Test
    void testSetPluginName() {
        PluginDescriptor descriptor = new PluginDescriptor();
        descriptor.setPluginName("MyCustomPlugin");

        assertEquals("MyCustomPlugin", descriptor.getPluginName());
    }

    @Test
    void testSetVersion() {
        PluginDescriptor descriptor = new PluginDescriptor();
        descriptor.setVersion("2.0.0");

        assertEquals("2.0.0", descriptor.getVersion());
    }

    @Test
    void testSetVendor() {
        PluginDescriptor descriptor = new PluginDescriptor();
        descriptor.setVendor("My Company");

        assertEquals("My Company", descriptor.getVendor());
    }

    @Test
    void testAddFeature() {
        PluginDescriptor descriptor = new PluginDescriptor();
        descriptor.addFeature("callout");
        descriptor.addFeature("process");

        assertTrue(descriptor.hasFeature("callout"));
        assertTrue(descriptor.hasFeature("process"));
        assertFalse(descriptor.hasFeature("form"));
    }

    @Test
    void testSetFeatures() {
        PluginDescriptor descriptor = new PluginDescriptor();
        descriptor.setFeatures(Set.of("a", "b", "c"));

        assertEquals(3, descriptor.getFeatures().size());
        assertTrue(descriptor.hasFeature("a"));
        assertTrue(descriptor.hasFeature("b"));
        assertTrue(descriptor.hasFeature("c"));
    }

    @Test
    void testSetPlatformVersion() {
        PluginDescriptor descriptor = new PluginDescriptor();
        descriptor.setPlatformVersion(PlatformVersion.of(13));

        assertEquals(13, descriptor.getPlatformVersion().major());
    }

    @Test
    void testGetPackagePath() {
        PluginDescriptor descriptor = new PluginDescriptor("org.idempiere.myplugin");

        assertEquals("org/idempiere/myplugin", descriptor.getPackagePath());
    }

    @Test
    void testSetOutputDir() {
        PluginDescriptor descriptor = new PluginDescriptor();
        Path outputDir = Path.of("/tmp/output");
        descriptor.setOutputDir(outputDir);

        assertEquals(outputDir, descriptor.getOutputDir());
    }
}
