package com.l3.dqengine.pnr.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Flight comparison result between input and output files
 */
public class FlightComparison {
    
    private final FlightDetails inputFlight;
    private final FlightDetails outputFlight;
    private final boolean isMatch;
    private final List<String> differences;
    
    public FlightComparison(FlightDetails inputFlight, FlightDetails outputFlight) {
        this.inputFlight = inputFlight;
        this.outputFlight = outputFlight;
        this.differences = new ArrayList<>();
        this.isMatch = compareFlights();
    }
    
    private boolean compareFlights() {
        if (inputFlight == null || outputFlight == null) {
            return false;
        }
        
        boolean match = true;
        
        if (!safeEquals(inputFlight.getFullFlightNumber(), outputFlight.getFullFlightNumber())) {
            differences.add("Flight Number: Input(" + inputFlight.getFullFlightNumber() + 
                          ") vs Output(" + outputFlight.getFullFlightNumber() + ")");
            match = false;
        }
        
        if (!safeEquals(inputFlight.getRoute(), outputFlight.getRoute())) {
            differences.add("Route: Input(" + inputFlight.getRoute() + 
                          ") vs Output(" + outputFlight.getRoute() + ")");
            match = false;
        }
        
        if (!safeEquals(inputFlight.getDepartureDate(), outputFlight.getDepartureDate())) {
            differences.add("Departure Date: Input(" + inputFlight.getDepartureDate() + 
                          ") vs Output(" + outputFlight.getDepartureDate() + ")");
            match = false;
        }
        
        if (!safeEquals(inputFlight.getDepartureTime(), outputFlight.getDepartureTime())) {
            differences.add("Departure Time: Input(" + inputFlight.getDepartureTime() + 
                          ") vs Output(" + outputFlight.getDepartureTime() + ")");
            match = false;
        }
        
        return match;
    }
    
    private boolean safeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
    
    // Getters
    public FlightDetails getInputFlight() { return inputFlight; }
    public FlightDetails getOutputFlight() { return outputFlight; }
    public boolean isMatch() { return isMatch; }
    public List<String> getDifferences() { return differences; }
    
    public boolean hasInputFlight() { return inputFlight != null; }
    public boolean hasOutputFlight() { return outputFlight != null; }
    
    @Override
    public String toString() {
        return "FlightComparison{" +
                "inputFlight=" + inputFlight +
                ", outputFlight=" + outputFlight +
                ", isMatch=" + isMatch +
                ", differences=" + differences.size() +
                '}';
    }
}