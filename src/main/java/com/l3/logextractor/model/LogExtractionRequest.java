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

    public LogExtractionRequest() {
        this.requestId = generateRequestId();
        this.requestTime = LocalDateTime.now();
    }

    public LogExtractionRequest(String flightNumber, LocalDateTime incidentDate) {
        this();
        this.flightNumber = flightNumber;
        this.incidentDate = incidentDate;
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

    @Override
    public String toString() {
        return String.format("LogExtractionRequest{flightNumber='%s', incidentDate=%s, requestId='%s'}",
            flightNumber, incidentDate, requestId);
    }
}
