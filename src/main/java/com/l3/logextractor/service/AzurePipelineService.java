package com.l3.logextractor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.l3.logextractor.config.AzureConfig;
import com.l3.logextractor.model.LogExtractionRequest;
import com.l3.logextractor.model.PipelineRunResult;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Service class for interacting with Azure DevOps Pipeline API
 */
public class AzurePipelineService {

    private final AzureConfig config;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;

    public AzurePipelineService(AzureConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.httpClient = HttpClients.createDefault();
    }

    /**
     * Trigger the pipeline with flight number and incident date
     */
    public PipelineRunResult triggerPipeline(LogExtractionRequest request, Consumer<String> logCallback) {
        try {
            logCallback.accept(String.format("Starting pipeline for flight %s on %s",
                request.getFlightNumber(), request.getIncidentDate().format(DateTimeFormatter.ISO_LOCAL_DATE)));

            // Create pipeline run request body
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> templateParameters = new HashMap<>();

            templateParameters.put("flight_number", request.getFlightNumber());
            templateParameters.put("log_date", request.getIncidentDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
            templateParameters.put("target_env", request.getEnvironment());

            requestBody.put("templateParameters", templateParameters);

            // Specify the branch to run the pipeline on
            Map<String, Object> resources = new HashMap<>();
            Map<String, Object> repositories = new HashMap<>();
            Map<String, Object> self = new HashMap<>();
            self.put("refName", "refs/heads/" + config.getBranch());
            repositories.put("self", self);
            resources.put("repositories", repositories);
            requestBody.put("resources", resources);

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            logCallback.accept("Sending request to Azure Pipeline API...");
            logCallback.accept("Request URL: " + config.getPipelineUrl() + "?api-version=7.0");
            logCallback.accept("Request Body: " + jsonBody);

            // Create HTTP POST request
            HttpPost postRequest = new HttpPost(config.getPipelineUrl() + "?api-version=7.0");
            postRequest.setHeader("Content-Type", "application/json");
            postRequest.setHeader("Authorization", "Basic " + getEncodedAuth());
            postRequest.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));

            // Execute request
            ClassicHttpResponse response = httpClient.execute(postRequest);
            HttpEntity entity = response.getEntity();
            String responseBody = EntityUtils.toString(entity, StandardCharsets.UTF_8);

            logCallback.accept("Response received from Azure Pipeline API");
            logCallback.accept("Response Status: " + response.getCode());

            if (response.getCode() >= 200 && response.getCode() < 300) {
                // Parse successful response
                JsonNode jsonResponse = objectMapper.readTree(responseBody);
                PipelineRunResult result = new PipelineRunResult();
                result.setRunId(jsonResponse.get("id").asText());
                result.setStatus(jsonResponse.get("state").asText());
                result.setResult(jsonResponse.has("result") ? jsonResponse.get("result").asText() : "none");
                result.setCreatedDate(LocalDateTime.parse(jsonResponse.get("createdDate").asText().substring(0, 19)));

                logCallback.accept(String.format("Pipeline run started successfully. Run ID: %s", result.getRunId()));
                return result;
            } else {
                String errorMessage = getDetailedErrorMessage(response.getCode(), responseBody);
                logCallback.accept(errorMessage);

                PipelineRunResult result = new PipelineRunResult();
                result.setStatus("failed");
                result.setResult("failed");
                result.setErrorMessage(errorMessage);
                return result;
            }

        } catch (Exception e) {
            logCallback.accept("Error triggering pipeline: " + e.getMessage());
            PipelineRunResult result = new PipelineRunResult();
            result.setStatus("failed");
            result.setResult("failed");
            result.setErrorMessage("Exception occurred: " + e.getMessage());
            return result;
        }
    }

    /**
     * Check the status of a running pipeline
     */
    public PipelineRunResult checkPipelineStatus(String runId, Consumer<String> logCallback) {
        try {
            String statusUrl = String.format("%s/%s?api-version=7.0", config.getPipelineUrl(), runId);

            HttpGet getRequest = new HttpGet(statusUrl);
            getRequest.setHeader("Authorization", "Basic " + getEncodedAuth());

            ClassicHttpResponse response = httpClient.execute(getRequest);
            HttpEntity entity = response.getEntity();
            String responseBody = EntityUtils.toString(entity, StandardCharsets.UTF_8);

            if (response.getCode() >= 200 && response.getCode() < 300) {
                JsonNode jsonResponse = objectMapper.readTree(responseBody);
                PipelineRunResult result = new PipelineRunResult();
                result.setRunId(runId);
                result.setStatus(jsonResponse.get("state").asText());
                result.setResult(jsonResponse.has("result") ? jsonResponse.get("result").asText() : "none");
                result.setCreatedDate(LocalDateTime.parse(jsonResponse.get("createdDate").asText().substring(0, 19)));

                if (jsonResponse.has("finishedDate")) {
                    result.setFinishedDate(LocalDateTime.parse(jsonResponse.get("finishedDate").asText().substring(0, 19)));
                }

                return result;
            } else {
                String errorMessage = getDetailedErrorMessage(response.getCode(), responseBody);
                logCallback.accept(String.format("Failed to check pipeline status: %s", errorMessage));
                PipelineRunResult result = new PipelineRunResult();
                result.setRunId(runId);
                result.setStatus("failed");
                result.setResult("failed");
                result.setErrorMessage("Status check failed: " + errorMessage);
                return result;
            }

        } catch (Exception e) {
            logCallback.accept("Error checking pipeline status: " + e.getMessage());
            PipelineRunResult result = new PipelineRunResult();
            result.setRunId(runId);
            result.setStatus("failed");
            result.setResult("failed");
            result.setErrorMessage("Exception occurred: " + e.getMessage());
            return result;
        }
    }

    /**
     * Get download URL for build artifacts
     */
    public String getBuildArtifactsUrl(String runId) {
        return String.format("%s/%s/%s/_apis/build/builds/%s/artifacts?api-version=7.0",
            config.getBaseUrl(), config.getOrganization(), config.getProject(), runId);
    }

    private String getDetailedErrorMessage(int statusCode, String responseBody) {
        switch (statusCode) {
            case 401:
                return String.format("Authentication failed (Status: %d). Please check:\n" +
                    "1. Personal Access Token is valid and not expired\n" +
                    "2. Token has 'Build (read and execute)' permissions\n" +
                    "3. Organization and project names are correct\n" +
                    "Response: %s", statusCode, responseBody);
            case 403:
                return String.format("Access forbidden (Status: %d). The token may not have sufficient permissions.\n" +
                    "Required permissions: 'Build (read and execute)' and 'Release (read, write, execute and manage)'\n" +
                    "Response: %s", statusCode, responseBody);
            case 404:
                return String.format("Pipeline not found (Status: %d). Please verify:\n" +
                    "1. Organization: %s\n" +
                    "2. Project: %s\n" +
                    "3. Pipeline ID: %s\n" +
                    "Response: %s", statusCode, config.getOrganization(), config.getProject(), config.getPipelineId(), responseBody);
            case 400:
                return getDetailed400ErrorMessage(responseBody);
            default:
                return String.format("Pipeline trigger failed (Status: %d). Response: %s", statusCode, responseBody);
        }
    }

    private String getDetailed400ErrorMessage(String responseBody) {
        // Check if this is a PipelineValidationException for missing YAML file
        if (responseBody.contains("PipelineValidationException") && responseBody.contains("not found in repository")) {
            String errorMessage = "Pipeline Configuration Error (Status: 400).\n\n";

            // Extract the missing file path from the error message
            if (responseBody.contains("File /azure-pipelines/flight-log-search.yml not found")) {
                errorMessage += "ISSUE: The pipeline is looking for a YAML file that doesn't exist:\n";
                errorMessage += "• Missing file: /azure-pipelines/flight-log-search.yml\n";
                errorMessage += "• Repository: prdcts_api_pnr_ansible_poc\n";
                errorMessage += "• Branch: " + config.getBranch() + "\n\n";

                errorMessage += "SOLUTION: Please check with your DevOps team:\n";
                errorMessage += "1. Verify the correct pipeline ID for log extraction\n";
                errorMessage += "2. Ensure the YAML file exists in the repository\n";
                errorMessage += "3. Confirm the pipeline is configured for the correct branch\n";
                errorMessage += "4. Check if the pipeline path should be different\n\n";

                errorMessage += "ALTERNATIVE: If using a different pipeline, update the Pipeline ID in configuration.\n";
                errorMessage += "Current Pipeline ID: " + config.getPipelineId() + "\n\n";
            } else {
                // Generic pipeline validation error
                errorMessage += "ISSUE: Pipeline YAML configuration problem.\n";
                errorMessage += "The pipeline definition could not be loaded or validated.\n\n";

                errorMessage += "SOLUTION: Please verify:\n";
                errorMessage += "1. Pipeline ID is correct: " + config.getPipelineId() + "\n";
                errorMessage += "2. Pipeline YAML file exists in the repository\n";
                errorMessage += "3. YAML syntax is valid\n";
                errorMessage += "4. Pipeline is not disabled\n\n";
            }

            errorMessage += "Full Response: " + responseBody;
            return errorMessage;
        }

        // Check for parameter validation issues
        if (responseBody.contains("parameter") || responseBody.contains("template")) {
            return String.format("Bad request (Status: 400). Pipeline parameter validation failed.\n" +
                "This could be due to:\n" +
                "1. Missing required template parameters\n" +
                "2. Invalid parameter values\n" +
                "3. Parameter type mismatch\n\n" +
                "Sent parameters: flight_number, log_date\n\n" +
                "Response: %s", responseBody);
        }

        // Generic 400 error
        return String.format("Bad request (Status: 400). The request parameters may be invalid.\n" +
            "Check flight number and date format.\n" +
            "Response: %s", responseBody);
    }

    private String getEncodedAuth() {
        String auth = ":" + config.getPersonalAccessToken();
        return Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }

    public void close() throws IOException {
        httpClient.close();
    }
}
