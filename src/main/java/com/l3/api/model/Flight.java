package com.l3.api.model;

import java.util.Objects;

public class Flight {
    public String flightNo;

    public String getArrPort() {
        return arrPort;
    }

    public void setArrPort(String arrPort) {
        this.arrPort = arrPort;
    }

    public String getDepPort() {
        return depPort;
    }

    public void setDepPort(String depPort) {
        this.depPort = depPort;
    }

    public String getDepDate() {
        return depDate;
    }

    public void setDepDate(String depDate) {
        this.depDate = depDate;
    }

    public String getDepTime() {
        return depTime;
    }

    public void setDepTime(String depTime) {
        this.depTime = depTime;
    }

    public String getFlightNo() {
        return flightNo;
    }

    public void setFlightNo(String flightNo) {
        this.flightNo = flightNo;
    }

    public String depTime;
    public String depDate;
    public String depPort;
    public String arrPort;

    public Flight(String flightNo, String depTime, String depDate, String depPort, String arrPort) {
        this.flightNo = flightNo;
        this.depTime = depTime;
        this.depDate = depDate;
        this.depPort = depPort;
        this.arrPort = arrPort;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Flight)) return false;
        Flight f = (Flight) o;
        return Objects.equals(flightNo, f.flightNo) &&
                Objects.equals(depTime, f.depTime) &&
                Objects.equals(depDate, f.depDate) &&
                Objects.equals(depPort, f.depPort) &&
                Objects.equals(arrPort, f.arrPort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flightNo, depTime, depDate, depPort, arrPort);
    }
}