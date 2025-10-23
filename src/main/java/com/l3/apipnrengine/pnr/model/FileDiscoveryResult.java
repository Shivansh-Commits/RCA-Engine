package com.l3.apipnrengine.pnr.model;

import java.io.File;
import java.util.Map;

/**
 * Result of file discovery process
 */
public class FileDiscoveryResult {
    
    private final File inputFile;
    private final File outputFile;
    private final Map<Integer, String> inputSegmentSourceMap;
    
    public FileDiscoveryResult(File inputFile, File outputFile, Map<Integer, String> inputSegmentSourceMap) {
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        this.inputSegmentSourceMap = inputSegmentSourceMap;
    }
    
    // Getters
    public File getInputFile() { return inputFile; }
    public File getOutputFile() { return outputFile; }
    public Map<Integer, String> getInputSegmentSourceMap() { return inputSegmentSourceMap; }
    
    @Override
    public String toString() {
        return "FileDiscoveryResult{" +
                "inputFile=" + (inputFile != null ? inputFile.getName() : "null") +
                ", outputFile=" + (outputFile != null ? outputFile.getName() : "null") +
                ", hasSegmentMap=" + (inputSegmentSourceMap != null) +
                '}';
    }
}