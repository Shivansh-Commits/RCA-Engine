package com.l3.logparser.config;

import com.l3.common.util.PropertiesUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Advanced configuration for EDIFACT message pattern matching and segment codes
 * Allows users to customize how messages are detected and parsed
 */
public class AdvancedParserConfig {

    // API Configuration
    private ApiPatternConfig apiConfig;

    // PNR Configuration (for future implementation)
    private PnrPatternConfig pnrConfig;

    public AdvancedParserConfig() {
        loadDefaultConfiguration();
        loadFromFile();
    }

    /**
     * Load default hardcoded values from application.properties
     */
    private void loadDefaultConfiguration() {
        this.apiConfig = new ApiPatternConfig();
        this.pnrConfig = new PnrPatternConfig();

        // Load default API patterns from properties
        apiConfig.loadDefaults();

        // PNR patterns will be added later
        pnrConfig.loadDefaults();
    }

    /**
     * Reset configuration to default values only (without loading from file)
     */
    public void resetToDefaults() {
        loadDefaultConfiguration();
    }

    /**
     * Load configuration from user config file
     */
    public void loadFromFile() {
        try {
            Path configFile = getConfigFilePath();
            if (!Files.exists(configFile)) {
                return; // Use default values if file doesn't exist
            }

            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(configFile.toFile())) {
                props.load(fis);
            }

            // Load API configuration
            apiConfig.loadFromProperties(props);

            // Load PNR configuration (when implemented)
            // pnrConfig.loadFromProperties(props);

        } catch (Exception e) {
            System.err.println("Warning: Could not load advanced parser configuration: " + e.getMessage());
            // Continue with default values
        }
    }

    /**
     * Save configuration to properties file
     */
    public boolean saveToFile() {
        try {
            Path configFile = getConfigFilePath();

            // Create directory if it doesn't exist
            Files.createDirectories(configFile.getParent());

            Properties props = new Properties();

            // Save API configuration
            apiConfig.saveToProperties(props);

            // Save PNR configuration (when implemented)
            // pnrConfig.saveToProperties(props);

            // Save to file with comments
            try (FileOutputStream fos = new FileOutputStream(configFile.toFile())) {
                props.store(fos, "L3 Engine - Advanced Parser Configuration\nGenerated on: " + new java.util.Date());
            }

            return true;

        } catch (Exception e) {
            System.err.println("Error: Could not save advanced parser configuration: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get the path to the configuration file
     */
    private Path getConfigFilePath() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".l3engine", "parser-config.properties");
    }

    /**
     * Check if configuration file exists
     */
    public boolean configFileExists() {
        return Files.exists(getConfigFilePath());
    }

    // Getters and Setters
    public ApiPatternConfig getApiConfig() {
        return apiConfig;
    }

    public void setApiConfig(ApiPatternConfig apiConfig) {
        this.apiConfig = apiConfig;
    }

    public PnrPatternConfig getPnrConfig() {
        return pnrConfig;
    }

    public void setPnrConfig(PnrPatternConfig pnrConfig) {
        this.pnrConfig = pnrConfig;
    }
}
