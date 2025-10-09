package com.l3.pnr.model;

/**
 * Flight details extracted from EDIFACT files
 */
public class FlightDetails {
    
    private final String airline;
    private final String flightNumber;
    private final String fullFlightNumber;
    private final String origin;
    private final String destination;
    private final String departureDate;
    private final String departureTime;
    private final String arrivalDate;
    private final String arrivalTime;
    private final String route;
    private final String formattedDeparture;
    private final String formattedArrival;
    
    public FlightDetails(String airline, String flightNumber, String origin, String destination,
                        String departureDate, String departureTime, String arrivalDate, String arrivalTime) {
        this.airline = airline != null ? airline : "";
        this.flightNumber = flightNumber != null ? flightNumber : "";
        this.fullFlightNumber = this.airline + this.flightNumber;
        this.origin = origin != null ? origin : "";
        this.destination = destination != null ? destination : "";
        this.departureDate = departureDate != null ? departureDate : "";
        this.departureTime = departureTime != null ? departureTime : "";
        this.arrivalDate = arrivalDate != null ? arrivalDate : "";
        this.arrivalTime = arrivalTime != null ? arrivalTime : "";
        this.route = this.origin + " → " + this.destination;
        this.formattedDeparture = this.departureDate + " " + this.departureTime;
        this.formattedArrival = this.arrivalDate + " " + this.arrivalTime;
    }
    
    // Getters
    public String getAirline() { return airline; }
    public String getFlightNumber() { return flightNumber; }
    public String getFullFlightNumber() { return fullFlightNumber; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public String getDepartureDate() { return departureDate; }
    public String getDepartureTime() { return departureTime; }
    public String getArrivalDate() { return arrivalDate; }
    public String getArrivalTime() { return arrivalTime; }
    public String getRoute() { return route; }
    public String getFormattedDeparture() { return formattedDeparture; }
    public String getFormattedArrival() { return formattedArrival; }
    
    @Override
    public String toString() {
        return "FlightDetails{" +
                "fullFlightNumber='" + fullFlightNumber + '\'' +
                ", route='" + route + '\'' +
                ", formattedDeparture='" + formattedDeparture + '\'' +
                ", formattedArrival='" + formattedArrival + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        FlightDetails that = (FlightDetails) o;
        
        if (!fullFlightNumber.equals(that.fullFlightNumber)) return false;
        if (!route.equals(that.route)) return false;
        if (!departureDate.equals(that.departureDate)) return false;
        return departureTime.equals(that.departureTime);
    }
    
    @Override
    public int hashCode() {
        int result = fullFlightNumber.hashCode();
        result = 31 * result + route.hashCode();
        result = 31 * result + departureDate.hashCode();
        result = 31 * result + departureTime.hashCode();
        return result;
    }
}