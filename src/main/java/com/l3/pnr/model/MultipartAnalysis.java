package com.l3.pnr.model;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Result of multipart file analysis
 */
public class MultipartAnalysis {
    
    private final Map<String, MultipartGroup> completeMultipartGroups;
    private final Map<String, MultipartGroup> incompleteMultipartGroups;
    private final List<File> singleFiles;
    private final List<File> invalidFiles;
    
    public MultipartAnalysis(Map<String, MultipartGroup> completeMultipartGroups,
                           Map<String, MultipartGroup> incompleteMultipartGroups,
                           List<File> singleFiles,
                           List<File> invalidFiles) {
        this.completeMultipartGroups = completeMultipartGroups;
        this.incompleteMultipartGroups = incompleteMultipartGroups;
        this.singleFiles = singleFiles;
        this.invalidFiles = invalidFiles;
    }
    
    // Getters
    public Map<String, MultipartGroup> getCompleteMultipartGroups() { return completeMultipartGroups; }
    public Map<String, MultipartGroup> getIncompleteMultipartGroups() { return incompleteMultipartGroups; }
    public List<File> getSingleFiles() { return singleFiles; }
    public List<File> getInvalidFiles() { return invalidFiles; }
    
    @Override
    public String toString() {
        return "MultipartAnalysis{" +
                "completeGroups=" + completeMultipartGroups.size() +
                ", incompleteGroups=" + incompleteMultipartGroups.size() +
                ", singleFiles=" + singleFiles.size() +
                ", invalidFiles=" + invalidFiles.size() +
                '}';
    }
}