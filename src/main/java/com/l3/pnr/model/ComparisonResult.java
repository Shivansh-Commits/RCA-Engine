package com.l3.pnr.model;

import java.util.List;

/**
 * Represents the result of comparing PNRGOV files
 */
public class ComparisonResult {
    
    private final PnrData inputData;
    private final PnrData outputData;
    private final FlightComparison flightComparison;
    
    // Passenger comparison results
    private final java.util.Set<String> processedPassengerKeys;
    private final java.util.Set<String> droppedPassengerKeys;
    private final java.util.Set<String> addedPassengerKeys;
    
    // PNR comparison results
    private final java.util.Set<String> processedPnrKeys;
    private final java.util.Set<String> droppedPnrKeys;
    private final java.util.Set<String> addedPnrKeys;
    
    // Duplicate passengers within input data
    private final java.util.Set<String> duplicatePassengerKeys;
    
    private final long processingTimeMs;
    private final PnrgovConfig config;
    
    public ComparisonResult(PnrData inputData, PnrData outputData, FlightComparison flightComparison,
                           java.util.Set<String> processedPassengerKeys, java.util.Set<String> droppedPassengerKeys, 
                           java.util.Set<String> addedPassengerKeys,
                           java.util.Set<String> processedPnrKeys, java.util.Set<String> droppedPnrKeys, 
                           java.util.Set<String> addedPnrKeys,
                           java.util.Set<String> duplicatePassengerKeys,
                           long processingTimeMs, PnrgovConfig config) {
        this.inputData = inputData;
        this.outputData = outputData;
        this.flightComparison = flightComparison;
        this.processedPassengerKeys = processedPassengerKeys;
        this.droppedPassengerKeys = droppedPassengerKeys;
        this.addedPassengerKeys = addedPassengerKeys;
        this.processedPnrKeys = processedPnrKeys;
        this.droppedPnrKeys = droppedPnrKeys;
        this.addedPnrKeys = addedPnrKeys;
        this.duplicatePassengerKeys = duplicatePassengerKeys;
        this.processingTimeMs = processingTimeMs;
        this.config = config;
    }
    
    // Getters
    public PnrData getInputData() { return inputData; }
    public PnrData getOutputData() { return outputData; }
    public FlightComparison getFlightComparison() { return flightComparison; }
    
    public java.util.Set<String> getProcessedPassengerKeys() { return processedPassengerKeys; }
    public java.util.Set<String> getDroppedPassengerKeys() { return droppedPassengerKeys; }
    public java.util.Set<String> getAddedPassengerKeys() { return addedPassengerKeys; }
    
    public java.util.Set<String> getProcessedPnrKeys() { return processedPnrKeys; }
    public java.util.Set<String> getDroppedPnrKeys() { return droppedPnrKeys; }
    public java.util.Set<String> getAddedPnrKeys() { return addedPnrKeys; }
    public java.util.Set<String> getDuplicatePassengerKeys() { return duplicatePassengerKeys; }
    
    public long getProcessingTimeMs() { return processingTimeMs; }
    public PnrgovConfig getConfig() { return config; }
    
    // Convenience methods for statistics
    public int getTotalInputPassengers() { return inputData.getPassengers().size(); }
    public int getTotalOutputPassengers() { return outputData.getPassengers().size(); }
    public int getTotalInputPnrs() { return inputData.getPnrCount(); }
    public int getTotalOutputPnrs() { return outputData.getPnrCount(); }
    
    public int getProcessedPassengerCount() { return processedPassengerKeys.size(); }
    public int getDroppedPassengerCount() { return droppedPassengerKeys.size(); }
    public int getAddedPassengerCount() { return addedPassengerKeys.size(); }
    
    public int getProcessedPnrCount() { return processedPnrKeys.size(); }
    public int getDroppedPnrCount() { return droppedPnrKeys.size(); }
    public int getAddedPnrCount() { return addedPnrKeys.size(); }
    
    public double getSuccessRate() {
        if (getTotalInputPassengers() == 0) return 0.0;
        return (double) getProcessedPassengerCount() / getTotalInputPassengers() * 100.0;
    }
    
    public double getDropRate() {
        if (getTotalInputPassengers() == 0) return 0.0;
        return (double) getDroppedPassengerCount() / getTotalInputPassengers() * 100.0;
    }
    
    public String getStatusSummary() {
        double successRate = getSuccessRate();
        if (successRate > 75) return "✅ EXCELLENT";
        else if (successRate > 50) return "🔄 GOOD";
        else return "⚠️ NEEDS REVIEW";
    }
    
    // Get passengers by status
    public List<PassengerRecord> getProcessedPassengers() {
        return inputData.getPassengers().stream()
            .filter(p -> {
                String key = generateKey(p);
                return processedPassengerKeys.contains(key);
            })
            .collect(java.util.stream.Collectors.toList());
    }
    
    public List<PassengerRecord> getDroppedPassengers() {
        return inputData.getPassengers().stream()
            .filter(p -> {
                String key = generateKey(p);
                return droppedPassengerKeys.contains(key);
            })
            .collect(java.util.stream.Collectors.toList());
    }
    
    public List<PassengerRecord> getAddedPassengers() {
        return outputData.getPassengers().stream()
            .filter(p -> {
                String key = generateKey(p);
                return addedPassengerKeys.contains(key);
            })
            .collect(java.util.stream.Collectors.toList());
    }
    
    public List<PassengerRecord> getDuplicatePassengers() {
        return inputData.getPassengers().stream()
            .filter(p -> {
                String key = generateKey(p);
                return duplicatePassengerKeys.contains(key);
            })
            .collect(java.util.stream.Collectors.toList());
    }
    
    // Helper method to generate key (must match PnrgovComparator.generatePassengerKey logic)
    private String generateKey(PassengerRecord passenger) {
        // Use the same key generation logic as PnrgovComparator for PNR_NAME strategy
        switch (config.getMatchingStrategy()) {
            case PNR_NAME:
                return cleanString(passenger.getPnrRloc()) + "|" + cleanString(passenger.getName());
                
            case NAME_DOC_DOB:
                String cleanName = passenger.getName() != null ? 
                    java.util.Arrays.stream(passenger.getName().replace("[^\\w\\s]", "").split("\\s+"))
                          .filter(s -> !s.isEmpty())
                          .sorted()
                          .collect(java.util.stream.Collectors.joining(" "))
                          .toUpperCase() : "";
                String docValue = "NODOC"; // PNR data doesn't typically have documents
                String dobValue = "NODOB"; // PNR data doesn't typically have DOB
                return cleanName + "|" + docValue + "|" + dobValue;
                
            case CUSTOM:
                return cleanString(passenger.getPnrRloc()) + "|" + cleanString(passenger.getName()) + "|";
                
            default:
                return cleanString(passenger.getPnrRloc()) + "|" + cleanString(passenger.getName());
        }
    }
    
    // Clean string method to match PnrgovComparator
    private String cleanString(String input) {
        if (input == null) return "";
        return input.replaceAll("\\s", "").replaceAll("\\W", "").toUpperCase();
    }
}