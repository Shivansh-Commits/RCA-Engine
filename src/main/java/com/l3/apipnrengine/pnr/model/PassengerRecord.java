package com.l3.apipnrengine.pnr.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a passenger record from PNRGOV data
 */
public class PassengerRecord {
    
    private final int blockId;
    private final String pnrRloc;
    private final String name;
    private final String source;
    private final List<String> legs;
    
    public PassengerRecord(int blockId, String pnrRloc, String name, String source) {
        this.blockId = blockId;
        this.pnrRloc = pnrRloc;
        this.name = name;
        this.source = source;
        this.legs = new ArrayList<>();
    }
    
    // Getters
    public int getBlockId() { return blockId; }
    public String getPnrRloc() { return pnrRloc; }
    public String getName() { return name; }
    public String getSource() { return source; }
    public List<String> getLegs() { return legs; }
    
    // Helper methods
    public String getLegsAsString() {
        return String.join(" → ", legs);
    }
    
    @Override
    public String toString() {
        return "PassengerRecord{" +
                "blockId=" + blockId +
                ", pnrRloc='" + pnrRloc + '\'' +
                ", name='" + name + '\'' +
                ", source='" + source + '\'' +
                ", legs=" + legs.size() +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        PassengerRecord that = (PassengerRecord) o;
        
        if (!pnrRloc.equals(that.pnrRloc)) return false;
        return name.equals(that.name);
    }
    
    @Override
    public int hashCode() {
        int result = pnrRloc.hashCode();
        result = 31 * result + name.hashCode();
        return result;
    }
}