package com.l3.apipnrengine.pnr.model;

import java.util.List;

/**
 * Represents a PNR record containing passenger information
 */
public class PnrRecord {
    
    private final int blockId;
    private final String rloc;
    private final String source;
    private final List<PassengerRecord> passengers;
    
    public PnrRecord(int blockId, String rloc, String source, List<PassengerRecord> passengers) {
        this.blockId = blockId;
        this.rloc = rloc;
        this.source = source;
        this.passengers = passengers;
    }
    
    // Getters
    public int getBlockId() { return blockId; }
    public String getRloc() { return rloc; }
    public String getSource() { return source; }
    public List<PassengerRecord> getPassengers() { return passengers; }
    
    // Helper methods
    public int getPassengerCount() { return passengers.size(); }
    
    @Override
    public String toString() {
        return "PnrRecord{" +
                "blockId=" + blockId +
                ", rloc='" + rloc + '\'' +
                ", source='" + source + '\'' +
                ", passengers=" + passengers.size() +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        PnrRecord pnrRecord = (PnrRecord) o;
        
        return rloc.equals(pnrRecord.rloc);
    }
    
    @Override
    public int hashCode() {
        return rloc.hashCode();
    }
}