package com.l3.logparser.config;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

/**
 * Advanced configuration for EDIFACT message pattern matching and segment codes
 * Allows users to customize how messages are detected and parsed
 */
public class AdvancedParserConfig {

    // API Configuration
    private ApiPatternConfig apiConfig;

    // PNR Configuration (for future implementation)
    private PnrPatternConfig pnrConfig;
    
    // Progress callback for UI logging
    private Consumer<String> progressCallback;
    
    // Debug mode flag
    private boolean debugMode = false;

    public AdvancedParserConfig() {
        loadDefaultConfiguration();
        loadFromFile();
    }
    
    /**
     * Set progress callback for UI logging
     * This will propagate to all sub-configs
     */
    public void setProgressCallback(Consumer<String> callback) {
        this.progressCallback = callback;
        if (pnrConfig != null) {
            pnrConfig.setProgressCallback(callback);
        }
    }
    
    /**
     * Enable or disable debug mode for detailed logging
     */
    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
        if (pnrConfig != null) {
            pnrConfig.setDebugMode(debugMode);
        }
    }
    
    /**
     * Get debug mode status
     */
    public boolean isDebugMode() {
        return debugMode;
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
     * Reload configuration from file
     * Useful when callback or debug mode is set after construction
     */
    public void reload() {
        loadFromFile();
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

            // Set progress callback and debug mode for PNR config before loading
            if (pnrConfig != null) {
                pnrConfig.setProgressCallback(progressCallback);
                pnrConfig.setDebugMode(debugMode);
            }

            // Load API configuration
            apiConfig.loadFromProperties(props);

            // Load PNR configuration
            pnrConfig.loadFromProperties(props);

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

            // Save PNR configuration
            pnrConfig.saveToProperties(props);

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
