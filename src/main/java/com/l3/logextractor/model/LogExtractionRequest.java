package com.l3.logextractor.model;

import java.time.LocalDateTime;

/**
 * Model class representing a log extraction request
 */
public class LogExtractionRequest {
    private String flightNumber;
    private LocalDateTime incidentDate;
    private String requestId;
    private LocalDateTime requestTime;
    private String environment;

    public LogExtractionRequest() {
        this.requestId = generateRequestId();
        this.requestTime = LocalDateTime.now();
        this.environment = "azure_ci2"; // Default environment
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

    @Override
    public String toString() {
        return String.format("LogExtractionRequest{flightNumber='%s', incidentDate=%s, environment='%s', requestId='%s'}",
            flightNumber, incidentDate, environment, requestId);
    }
}
