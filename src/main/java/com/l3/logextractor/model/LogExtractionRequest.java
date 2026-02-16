package com.l3.logextractor.model;

import com.l3.common.util.PropertiesUtil;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Model class representing a log extraction request
 */
public class LogExtractionRequest {
    private String flightNumber;
    private LocalDateTime incidentDate;
    private String requestId;
    private LocalDateTime requestTime;
    private String environment;
    private List<String> logFiles;

    public LogExtractionRequest() {
        this.requestId = generateRequestId();
        this.requestTime = LocalDateTime.now();
        this.logFiles = new ArrayList<>();

        // Get default environment from application.properties
        List<String> environments = PropertiesUtil.getPropertyAsList("azure.environments");
        this.environment = environments.isEmpty() ? "azure_ci2" : environments.get(0);
    }

    public LogExtractionRequest(String flightNumber, LocalDateTime incidentDate) {
        this();
        this.flightNumber = flightNumber;
        this.incidentDate = incidentDate;
    }

    public LogExtractionRequest(String flightNumber, LocalDateTime incidentDate, String environment) {
        this();
        this.flightNumber = flightNumber;
        this.incidentDate = incidentDate;
        this.environment = environment;
    }

    public LogExtractionRequest(String flightNumber, LocalDateTime incidentDate, String environment, List<String> logFiles) {
        this();
        this.flightNumber = flightNumber;
        this.incidentDate = incidentDate;
        this.environment = environment;
        this.logFiles = new ArrayList<>(logFiles);
    }

    private String generateRequestId() {
        return "REQ_" + System.currentTimeMillis();
    }

    // Getters and Setters
    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }

    public LocalDateTime getIncidentDate() { return incidentDate; }
    public void setIncidentDate(LocalDateTime incidentDate) { this.incidentDate = incidentDate; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public LocalDateTime getRequestTime() { return requestTime; }
    public void setRequestTime(LocalDateTime requestTime) { this.requestTime = requestTime; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public List<String> getLogFiles() { return logFiles; }
    public void setLogFiles(List<String> logFiles) { this.logFiles = new ArrayList<>(logFiles); }

    @Override
    public String toString() {
        return String.format("LogExtractionRequest{flightNumber='%s', incidentDate=%s, environment='%s', logFiles=%s, requestId='%s'}",
            flightNumber, incidentDate, environment, logFiles, requestId);
    }
}
