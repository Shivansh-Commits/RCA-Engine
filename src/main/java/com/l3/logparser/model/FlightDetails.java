package com.l3.logparser.model;

/**
 * Model representing flight details extracted from log files
 */
public class FlightDetails {
    private String flightNumber;
    private String departureAirport;
    private String arrivalAirport;
    private String departureDate; // yymmdd format
    private String departureTime; // hhmm format
    private String arrivalDate;   // yymmdd format
    private String arrivalTime;   // hhmm format
    private boolean isPassengerData; // true for BGM 745, false for BGM 250 (crew)

    public FlightDetails() {}

    public FlightDetails(String flightNumber, String departureAirport, String arrivalAirport,
                        String departureDate, String departureTime, String arrivalDate, String arrivalTime,
                        boolean isPassengerData) {
        this.flightNumber = flightNumber;
        this.departureAirport = departureAirport;
        this.arrivalAirport = arrivalAirport;
        this.departureDate = departureDate;
        this.departureTime = departureTime;
        this.arrivalDate = arrivalDate;
        this.arrivalTime = arrivalTime;
        this.isPassengerData = isPassengerData;
    }

    // Getters and Setters
    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }

    public String getDepartureAirport() { return departureAirport; }
    public void setDepartureAirport(String departureAirport) { this.departureAirport = departureAirport; }

    public String getArrivalAirport() { return arrivalAirport; }
    public void setArrivalAirport(String arrivalAirport) { this.arrivalAirport = arrivalAirport; }

    public String getDepartureDate() { return departureDate; }
    public void setDepartureDate(String departureDate) { this.departureDate = departureDate; }

    public String getDepartureTime() { return departureTime; }
    public void setDepartureTime(String departureTime) { this.departureTime = departureTime; }

    public String getArrivalDate() { return arrivalDate; }
    public void setArrivalDate(String arrivalDate) { this.arrivalDate = arrivalDate; }

    public String getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(String arrivalTime) { this.arrivalTime = arrivalTime; }

    public boolean isPassengerData() { return isPassengerData; }
    public void setPassengerData(boolean passengerData) { isPassengerData = passengerData; }

    @Override
    public String toString() {
        return String.format("Flight: %s, %s -> %s, Date: %s %s - %s %s, Type: %s",
                flightNumber, departureAirport, arrivalAirport,
                departureDate, departureTime, arrivalDate, arrivalTime,
                isPassengerData ? "Passenger" : "Crew");
    }
}
