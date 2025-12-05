package com.l3.rcaengine.pnr.utils;

/**
 * TvlData class to support FlightExtractor
 */
public class TvlData {
    private final String dateTime;
    private final String origin;
    private final String destination;
    private final String airline;
    private final String flightNumber;
    private final String raw;
    
    public TvlData(String dateTime, String origin, String destination, 
                  String airline, String flightNumber, String raw) {
        this.dateTime = dateTime != null ? dateTime : "";
        this.origin = origin != null ? origin : "";
        this.destination = destination != null ? destination : "";
        this.airline = airline != null ? airline : "";
        this.flightNumber = flightNumber != null ? flightNumber : "";
        this.raw = raw != null ? raw : "";
    }
    
    public String getDateTime() { return dateTime; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public String getAirline() { return airline; }
    public String getFlightNumber() { return flightNumber; }
    public String getRaw() { return raw; }
    
    @Override
    public String toString() {
        return "TvlData{" +
                "dateTime='" + dateTime + '\'' +
                ", origin='" + origin + '\'' +
                ", destination='" + destination + '\'' +
                ", airline='" + airline + '\'' +
                ", flightNumber='" + flightNumber + '\'' +
                '}';
    }
}