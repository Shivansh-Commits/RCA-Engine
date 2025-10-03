package com.l3.engine.apiutils;

import com.l3.engine.model.Passenger;

import java.util.*;

public class ParseResult {
    private Map<String, Passenger> globalInputPassengers = new LinkedHashMap<>();
    private Map<String, Passenger> duplicatePassengers = new LinkedHashMap<>();
    private Map<String, Passenger> outputPassengers = new LinkedHashMap<>();
    private List<Passenger> dropped = new ArrayList<>();
    private List<String> allInvalidNads = new ArrayList<>();
    private List<String> allInvalidDocs = new ArrayList<>();
    private List<String> allMissingSegments = new ArrayList<>();
    private int totalInputAll;
    private int totalOutput;
    private String flightNumber;
    private String departureDate;
    private String departureTime;
    private String departureAirport;
    private String arrivalAirport;
    private List<String> processedFiles = new ArrayList<>();

    public List<String> getProcessedFiles() { return processedFiles; }
    public void setProcessedFiles(List<String> files) { this.processedFiles = files; }

    public String getArrivalAirport() {return arrivalAirport;}
    public void setArrivalAirport(String arrivalAirport) {this.arrivalAirport = arrivalAirport;}

    public String getFlightNumber() {return flightNumber;}
    public void setFlightNumber(String flightNumber) {this.flightNumber = flightNumber;}

    public String getDepartureDate() {return departureDate;}
    public void setDepartureDate(String departureDate) {this.departureDate = departureDate;}

    public String getDepartureTime() {return departureTime;}
    public void setDepartureTime(String departureTime) {this.departureTime = departureTime;}

    public String getDepartureAirport() {return departureAirport;}
    public void setDepartureAirport(String departureAirport) {this.departureAirport = departureAirport;}

    public Map<String, Passenger> getGlobalInputPassengers() { return globalInputPassengers; }
    public void setGlobalInputPassengers(Map<String, Passenger> m) { this.globalInputPassengers = m; }

    public Map<String, Passenger> getDuplicatePassengers() { return duplicatePassengers; }
    public void setDuplicatePassengers(Map<String, Passenger> m) { this.duplicatePassengers = m; }

    public List<Passenger> getDropped() { return dropped; }
    public void setDropped(List<Passenger> d) { this.dropped = d; }

    public List<String> getAllInvalidNads() { return allInvalidNads; }
    public void setAllInvalidNads(List<String> l) { this.allInvalidNads = l; }

    public List<String> getAllInvalidDocs() { return allInvalidDocs; }
    public void setAllInvalidDocs(List<String> l) { this.allInvalidDocs = l; }

    public List<String> getAllMissingSegments() { return allMissingSegments; }
    public void setAllMissingSegments(List<String> l) { this.allMissingSegments = l; }

    public Map<String, Passenger> getOutputPassengers() { return outputPassengers; }
    public void setOutputPassengers(Map<String, Passenger> m) { this.outputPassengers = m; }

    public int getTotalInputAll() { return totalInputAll; }
    public void setTotalInputAll(int n) { this.totalInputAll = n; }

    public int getTotalOutput() { return totalOutput; }
    public void setTotalOutput(int n) { this.totalOutput = n; }
}

