package com.l3.apipnrengine.pnr.model;

import java.util.List;

/**
 * Represents a PNR block from EDIFACT file
 */
public class PnrBlock {
    
    private final List<String> segments;
    private final String source;
    private final int startIndex;
    
    public PnrBlock(List<String> segments, String source, int startIndex) {
        this.segments = segments;
        this.source = source;
        this.startIndex = startIndex;
    }
    
    // Getters
    public List<String> getSegments() { return segments; }
    public String getSource() { return source; }
    public int getStartIndex() { return startIndex; }
    
    @Override
    public String toString() {
        return "PnrBlock{" +
                "segments=" + segments.size() +
                ", source='" + source + '\'' +
                ", startIndex=" + startIndex +
                '}';
    }
}