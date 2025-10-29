package com.l3.apipnrengine.pnr.model;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import com.l3.apipnrengine.pnr.utils.EdifactSeparators;

/**
 * Result of file discovery process
 */
public class FileDiscoveryResult {
    
    private final File inputFile;
    private final File outputFile;
    private final Map<Integer, String> inputSegmentSourceMap;
    private final List<String> originalInputFiles;
    private final Map<String, EdifactSeparators> fileSeparatorsMap;
    
    public FileDiscoveryResult(File inputFile, File outputFile, Map<Integer, String> inputSegmentSourceMap) {
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        this.inputSegmentSourceMap = inputSegmentSourceMap;
        this.originalInputFiles = new ArrayList<>();
        this.fileSeparatorsMap = new HashMap<>();
    }
    
    public FileDiscoveryResult(File inputFile, File outputFile, Map<Integer, String> inputSegmentSourceMap, List<String> originalInputFiles) {
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        this.inputSegmentSourceMap = inputSegmentSourceMap;
        this.originalInputFiles = originalInputFiles != null ? new ArrayList<>(originalInputFiles) : new ArrayList<>();
        this.fileSeparatorsMap = new HashMap<>();
    }
    
    public FileDiscoveryResult(File inputFile, File outputFile, Map<Integer, String> inputSegmentSourceMap, 
                             List<String> originalInputFiles, Map<String, EdifactSeparators> fileSeparatorsMap) {
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        this.inputSegmentSourceMap = inputSegmentSourceMap;
        this.originalInputFiles = originalInputFiles != null ? new ArrayList<>(originalInputFiles) : new ArrayList<>();
        this.fileSeparatorsMap = fileSeparatorsMap != null ? new HashMap<>(fileSeparatorsMap) : new HashMap<>();
    }
    
    // Getters
    public File getInputFile() { return inputFile; }
    public File getOutputFile() { return outputFile; }
    public Map<Integer, String> getInputSegmentSourceMap() { return inputSegmentSourceMap; }
    public List<String> getOriginalInputFiles() { return originalInputFiles; }
    public Map<String, EdifactSeparators> getFileSeparatorsMap() { return fileSeparatorsMap; }
    
    @Override
    public String toString() {
        return "FileDiscoveryResult{" +
                "inputFile=" + (inputFile != null ? inputFile.getName() : "null") +
                ", outputFile=" + (outputFile != null ? outputFile.getName() : "null") +
                ", hasSegmentMap=" + (inputSegmentSourceMap != null) +
                ", originalInputFiles=" + originalInputFiles +
                ", fileSeparatorsCount=" + (fileSeparatorsMap != null ? fileSeparatorsMap.size() : 0) +
                '}';
    }
}