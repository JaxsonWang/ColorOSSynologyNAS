package com.jaxson.coloros.synologynas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public final class ModuleMetadataTest {
    @Test
    public void declaresModernApi102EntryAndGalleryOnlyScope() throws IOException {
        Properties moduleProperties = loadProperties("META-INF/xposed/module.prop");

        assertEquals("101", moduleProperties.getProperty("minApiVersion"));
        assertEquals("102", moduleProperties.getProperty("targetApiVersion"));
        assertEquals("true", moduleProperties.getProperty("staticScope"));
        assertEquals(
                "com.jaxson.coloros.synologynas.ColorOsSynologyNasModule",
                loadText("META-INF/xposed/java_init.list")
        );
        assertEquals("com.coloros.gallery3d", loadText("META-INF/xposed/scope.list"));
    }

    private static Properties loadProperties(String resourceName) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = resource(resourceName)) {
            properties.load(input);
        }
        return properties;
    }

    private static String loadText(String resourceName) throws IOException {
        try (InputStream input = resource(resourceName)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }

    private static InputStream resource(String resourceName) {
        InputStream input = ModuleMetadataTest.class.getClassLoader()
                .getResourceAsStream(resourceName);
        assertNotNull("Missing resource: " + resourceName, input);
        return input;
    }
}

