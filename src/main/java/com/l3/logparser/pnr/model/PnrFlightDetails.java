package com.l3.logparser.pnr.model;

/**
 * Model representing PNR flight details extracted from TVL segment in MessageMHPNRGOV.log files
 */
public class PnrFlightDetails {
    private String flightNumber;
    private String airlineCode;
    private String departureAirport;
    private String arrivalAirport;
    private String departureDate; // ddmmyy format
    private String departureTime; // hhmm format
    private String arrivalDate;   // ddmmyy format
    private String arrivalTime;   // hhmm format

    public PnrFlightDetails() {}

    public PnrFlightDetails(String departureDate, String departureTime, String arrivalDate, String arrivalTime,
                           String departureAirport, String arrivalAirport, String airlineCode, String flightNumber) {
        this.departureDate = departureDate;
        this.departureTime = departureTime;
        this.arrivalDate = arrivalDate;
        this.arrivalTime = arrivalTime;
        this.departureAirport = departureAirport;
        this.arrivalAirport = arrivalAirport;
        this.airlineCode = airlineCode;
        this.flightNumber = flightNumber;
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

    /**
     * Get full flight identifier (airline + flight number)
     */
    public String getFullFlightNumber() {
        if (airlineCode != null && flightNumber != null) {
            return airlineCode + flightNumber;
        }
        return flightNumber != null ? flightNumber : "";
    }

    /**
     * Get route string for display
     */
    public String getRoute() {
        return String.format("%s-%s",
                departureAirport != null ? departureAirport : "???",
                arrivalAirport != null ? arrivalAirport : "???");
    }

    /**
     * Get departure datetime string for display
     */
    public String getDepartureDateTime() {
        if (departureDate != null && departureTime != null) {
            return String.format("%s %s", formatDate(departureDate), formatTime(departureTime));
        }
        return departureDate != null ? formatDate(departureDate) : "???";
    }

    /**
     * Get arrival datetime string for display
     */
    public String getArrivalDateTime() {
        if (arrivalDate != null && arrivalTime != null) {
            return String.format("%s %s", formatDate(arrivalDate), formatTime(arrivalTime));
        }
        return arrivalDate != null ? formatDate(arrivalDate) : "???";
    }

    /**
     * Get display name for UI
     */
    public String getDisplayName() {
        return String.format("%s (%s) %s", getFullFlightNumber(), getRoute(), getDepartureDateTime());
    }

    /**
     * Format date from ddmmyy to readable format
     */
    private String formatDate(String ddmmyy) {
        if (ddmmyy != null && ddmmyy.length() == 6) {
            try {
                String dd = ddmmyy.substring(0, 2);
                String mm = ddmmyy.substring(2, 4);
                String yy = ddmmyy.substring(4, 6);
                return String.format("20%s-%s-%s", yy, mm, dd);
            } catch (Exception e) {
                return ddmmyy;
            }
        }
        return ddmmyy != null ? ddmmyy : "";
    }

    /**
     * Format time from hhmm to readable format
     */
    private String formatTime(String hhmm) {
        if (hhmm != null && hhmm.length() == 4) {
            try {
                String hh = hhmm.substring(0, 2);
                String mm = hhmm.substring(2, 4);
                return String.format("%s:%s", hh, mm);
            } catch (Exception e) {
                return hhmm;
            }
        }
        return hhmm != null ? hhmm : "";
    }

    @Override
    public String toString() {
        return String.format("PnrFlightDetails{flight='%s', route='%s', departure='%s', arrival='%s'}",
                getFullFlightNumber(), getRoute(), getDepartureDateTime(), getArrivalDateTime());
    }
}
