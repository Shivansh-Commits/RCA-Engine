package com.l3.rcaengine.pnr.model;

import java.util.List;

/**
 * Represents PNR data extracted from EDIFACT files
 */
public class PnrData {
    
    private final String filePath;
    private final int pnrCount;
    private final int dcsCount;
    private final List<PnrRecord> pnrRecords;
    private final List<PassengerRecord> passengers;
    
    public PnrData(String filePath, int pnrCount, int dcsCount, 
                   List<PnrRecord> pnrRecords, List<PassengerRecord> passengers) {
        this.filePath = filePath;
        this.pnrCount = pnrCount;
        this.dcsCount = dcsCount;
        this.pnrRecords = pnrRecords;
        this.passengers = passengers;
    }
    
    // Getters
    public String getFilePath() { return filePath; }
    public int getPnrCount() { return pnrCount; }
    public int getDcsCount() { return dcsCount; }
    public List<PnrRecord> getPnrRecords() { return pnrRecords; }
    public List<PassengerRecord> getPassengers() { return passengers; }
    
    @Override
    public String toString() {
        return "PnrData{" +
                "filePath='" + filePath + '\'' +
                ", pnrCount=" + pnrCount +
                ", dcsCount=" + dcsCount +
                ", pnrRecords=" + pnrRecords.size() +
                ", passengers=" + passengers.size() +
                '}';
    }
}