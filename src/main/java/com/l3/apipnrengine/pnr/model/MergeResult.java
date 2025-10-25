package com.l3.apipnrengine.pnr.model;

import com.l3.apipnrengine.pnr.utils.EdifactSeparators;
import java.io.File;
import java.util.Map;
import java.util.HashMap;

/**
 * Result of merging multipart files with UNA separator isolation
 */
public class MergeResult {
    
    private final File file;
    private final Map<Integer, String> segmentSourceMap;
    private final Map<String, EdifactSeparators> fileSeparatorsMap;
    
    public MergeResult(File file, Map<Integer, String> segmentSourceMap) {
        this.file = file;
        this.segmentSourceMap = segmentSourceMap;
        this.fileSeparatorsMap = new HashMap<>();
    }
    
    public MergeResult(File file, Map<Integer, String> segmentSourceMap, Map<String, EdifactSeparators> fileSeparatorsMap) {
        this.file = file;
        this.segmentSourceMap = segmentSourceMap;
        this.fileSeparatorsMap = fileSeparatorsMap != null ? new HashMap<>(fileSeparatorsMap) : new HashMap<>();
    }
    
    // Getters
    public File getFile() { return file; }
    public Map<Integer, String> getSegmentSourceMap() { return segmentSourceMap; }
    public Map<String, EdifactSeparators> getFileSeparatorsMap() { return fileSeparatorsMap; }
    
    @Override
    public String toString() {
        return "MergeResult{" +
                "file=" + (file != null ? file.getName() : "null") +
                ", segmentSourceMap=" + (segmentSourceMap != null ? segmentSourceMap.size() + " entries" : "null") +
                ", fileSeparatorsTracked=" + fileSeparatorsMap.size() +
                '}';
    }
}