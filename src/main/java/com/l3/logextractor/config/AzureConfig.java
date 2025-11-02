package com.l3.logextractor.config;

/**
 * Configuration class for Azure Pipeline API integration
 */
public class AzureConfig {
    private String baseUrl;
    private String organization;
    private String project;
    private String pipelineId;
    private String personalAccessToken;
    private String branch;

    public AzureConfig() {
        // Default configuration - can be loaded from properties file
        this.baseUrl = "https://dev.azure.com";
        this.organization = "SITA-PSE";
        this.project = "Borders";
        this.pipelineId = "";
        this.personalAccessToken = "";
        this.branch = "";
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

    public String getPipelineUrl() {
        return String.format("%s/%s/%s/_apis/pipelines/%s/runs",
            baseUrl, organization, project, pipelineId);
    }
}
