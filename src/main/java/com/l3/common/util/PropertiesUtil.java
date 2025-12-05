package com.l3.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Utility class for reading application properties
 */
public class PropertiesUtil {
    private static final Properties properties = new Properties();
    private static boolean loaded = false;

    static {
        loadProperties();
    }

    /**
     * Load properties from application.properties file
     */
    private static void loadProperties() {
        if (loaded) return;

        try (InputStream input = PropertiesUtil.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input != null) {
                properties.load(input);
                loaded = true;
            } else {
                System.err.println("Warning: application.properties file not found");
            }
        } catch (IOException e) {
            System.err.println("Error loading application.properties: " + e.getMessage());
        }
    }

    /**
     * Get a property value
     * @param key the property key
     * @return the property value or null if not found
     */
    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

    /**
     * Get a property value with default
     * @param key the property key
     * @param defaultValue the default value if key not found
     * @return the property value or default value
     */
    public static String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Get a comma-separated property as a list
     * @param key the property key
     * @return list of values or empty list if not found
     */
    public static List<String> getPropertyAsList(String key) {
        String value = getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return Arrays.asList();
        }
        return Arrays.asList(value.split(","));
    }
}
