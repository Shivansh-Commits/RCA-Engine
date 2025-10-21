package com.l3.logparser.pnr.model;

/**
 * Model representing flight details extracted from PNR TVL segments
 * Format: TVL+290825:1435:290825:2325+OSL+DXB+EK+0160'
 */
public class PnrFlightDetails {
    private String flightNumber;
    private String airlineCode;
    private String departureAirport;
    private String arrivalAirport;
    private String departureDate; // ddmmyy format from TVL
    private String departureTime; // hhmm format from TVL
    private String arrivalDate;   // ddmmyy format from TVL
    private String arrivalTime;   // hhmm format from TVL
    private String rawTvlSegment; // Original TVL segment for reference

    public PnrFlightDetails() {}

    public PnrFlightDetails(String airlineCode, String flightNumber, String departureAirport, 
                           String arrivalAirport, String departureDate, String departureTime, 
                           String arrivalDate, String arrivalTime) {
        this.airlineCode = airlineCode;
        this.flightNumber = flightNumber;
        this.departureAirport = departureAirport;
        this.arrivalAirport = arrivalAirport;
        this.departureDate = departureDate;
        this.departureTime = departureTime;
        this.arrivalDate = arrivalDate;
        this.arrivalTime = arrivalTime;
    }

    // Getters and Setters
    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }

    public String getAirlineCode() { return airlineCode; }
    public void setAirlineCode(String airlineCode) { this.airlineCode = airlineCode; }

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

    public String getRawTvlSegment() { return rawTvlSegment; }
    public void setRawTvlSegment(String rawTvlSegment) { this.rawTvlSegment = rawTvlSegment; }

    /**
     * Get the full flight identifier (airline + flight number)
     */
    public String getFullFlightNumber() {
        return (airlineCode != null ? airlineCode : "") + (flightNumber != null ? flightNumber : "");
    }

    /**
     * Get departure date and time as a formatted string
     */
    public String getDepartureDateTime() {
        if (departureDate != null && departureTime != null) {
            return departureDate + " " + departureTime;
        }
        return departureDate != null ? departureDate : "";
    }

    /**
     * Get arrival date and time as a formatted string
     */
    public String getArrivalDateTime() {
        if (arrivalDate != null && arrivalTime != null) {
            return arrivalDate + " " + arrivalTime;
        }
        return arrivalDate != null ? arrivalDate : "";
    }

    @Override
    public String toString() {
        return "PnrFlightDetails{" +
                "airlineCode='" + airlineCode + '\'' +
                ", flightNumber='" + flightNumber + '\'' +
                ", departureAirport='" + departureAirport + '\'' +
                ", arrivalAirport='" + arrivalAirport + '\'' +
                ", departureDateTime='" + getDepartureDateTime() + '\'' +
                ", arrivalDateTime='" + getArrivalDateTime() + '\'' +
                '}';
    }
}