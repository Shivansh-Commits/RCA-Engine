package com.l3.logextractor.config;

import com.l3.common.util.PropertiesUtil;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

/**
 * Configuration class for Azure Pipeline API integration with persistence support
 */
public class AzureConfig {
    private String baseUrl;
    private String organization;
    private String project;
    private String pipelineId;
    private String personalAccessToken;
    private String branch;
    private String environment;

    public AzureConfig() {
        // Set default values first
        setDefaultValues();
        // Try to load existing configuration
        loadFromFile();
    }

    private void setDefaultValues() {
        this.baseUrl = "https://dev.azure.com";
        this.organization = "SITA-PSE";
        this.project = "Borders";
        this.pipelineId = "";
        this.personalAccessToken = "";
        this.branch = "";

        // Get default environment from application.properties
        List<String> environments = PropertiesUtil.getPropertyAsList("azure.environments");
        this.environment = environments.isEmpty() ? "azure_ci2" : environments.get(0);
    }

    /**
     * Load configuration from properties file
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

            // Load configuration values
            this.baseUrl = props.getProperty("azure.baseUrl", this.baseUrl);
            this.organization = props.getProperty("azure.organization", this.organization);
            this.project = props.getProperty("azure.project", this.project);
            this.pipelineId = props.getProperty("azure.pipelineId", this.pipelineId);
            this.branch = props.getProperty("azure.branch", this.branch);
            this.environment = props.getProperty("azure.environment", this.environment);

            // Decrypt personal access token
            String encryptedToken = props.getProperty("azure.personalAccessToken", "");
            this.personalAccessToken = encryptedToken.isEmpty() ? "" : decryptToken(encryptedToken);

        } catch (Exception e) {
            System.err.println("Warning: Could not load Azure configuration: " + e.getMessage());
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
            props.setProperty("azure.baseUrl", baseUrl != null ? baseUrl : "");
            props.setProperty("azure.organization", organization != null ? organization : "");
            props.setProperty("azure.project", project != null ? project : "");
            props.setProperty("azure.pipelineId", pipelineId != null ? pipelineId : "");
            props.setProperty("azure.branch", branch != null ? branch : "");
            // Get default environment from application.properties
            List<String> environments = PropertiesUtil.getPropertyAsList("azure.environments");
            String defaultEnvironment = environments.isEmpty() ? "azure_ci2" : environments.get(0);
            props.setProperty("azure.environment", environment != null ? environment : defaultEnvironment);

            // Encrypt personal access token before storing
            String encryptedToken = personalAccessToken != null && !personalAccessToken.isEmpty()
                ? encryptToken(personalAccessToken) : "";
            props.setProperty("azure.personalAccessToken", encryptedToken);

            // Save to file with comments
            try (FileOutputStream fos = new FileOutputStream(configFile.toFile())) {
                props.store(fos, "L3 Engine - Azure DevOps Configuration\nGenerated on: " + new java.util.Date());
            }

            return true;

        } catch (Exception e) {
            System.err.println("Error: Could not save Azure configuration: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get the path to the configuration file
     */
    private Path getConfigFilePath() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".l3engine", "azure-config.properties");
    }

    /**
     * Simple encryption for personal access token (Base64 encoding)
     * Note: This is not high security, just basic obfuscation
     */
    private String encryptToken(String token) {
        try {
            return Base64.getEncoder().encodeToString(token.getBytes("UTF-8"));
        } catch (Exception e) {
            return token; // Return original if encryption fails
        }
    }

    /**
     * Decrypt personal access token
     */
    private String decryptToken(String encryptedToken) {
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedToken);
            return new String(decodedBytes, "UTF-8");
        } catch (Exception e) {
            return encryptedToken; // Return original if decryption fails
        }
    }

    /**
     * Check if configuration file exists
     */
    public boolean configFileExists() {
        return Files.exists(getConfigFilePath());
    }

    /**
     * Get configuration file location for display
     */
    public String getConfigFileLocation() {
        return getConfigFilePath().toString();
    }

    // Getters and Setters
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }

    public String getProject() { return project; }
    public void setProject(String project) { this.project = project; }

    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }

    public String getPersonalAccessToken() { return personalAccessToken; }
    public void setPersonalAccessToken(String personalAccessToken) { this.personalAccessToken = personalAccessToken; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getPipelineUrl() {
        return String.format("%s/%s/%s/_apis/pipelines/%s/runs",
            baseUrl, organization, project, pipelineId);
    }
}
