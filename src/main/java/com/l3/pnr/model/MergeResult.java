package com.l3.pnr.model;

import java.io.File;
import java.util.Map;

/**
 * Result of merging multipart files
 */
public class MergeResult {
    
    private final File file;
    private final Map<Integer, String> segmentSourceMap;
    
    public MergeResult(File file, Map<Integer, String> segmentSourceMap) {
        this.file = file;
        this.segmentSourceMap = segmentSourceMap;
    }
    
    // Getters
    public File getFile() { return file; }
    public Map<Integer, String> getSegmentSourceMap() { return segmentSourceMap; }
    
    @Override
    public String toString() {
        return "MergeResult{" +
                "file=" + (file != null ? file.getName() : "null") +
                ", segmentSourceMap=" + (segmentSourceMap != null ? segmentSourceMap.size() + " entries" : "null") +
                '}';
    }
}