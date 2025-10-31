package com.l3.rcaengine.pnr.model;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a multipart EDIFACT file group
 */
public class MultipartGroup {
    
    private final String identifier;
    private final String messageRef;
    private final Map<Integer, File> parts;
    private boolean hasFirst = false;
    private boolean hasFinal = false;
    
    public MultipartGroup(String identifier, String messageRef) {
        this.identifier = identifier;
        this.messageRef = messageRef;
        this.parts = new HashMap<>();
    }
    
    public void addPart(int partNumber, File file, String partType) {
        parts.put(partNumber, file);
        
        if (partNumber == 1) {
            hasFirst = true;
        }
        
        if ("F".equals(partType)) {
            hasFinal = true;
        }
    }
    
    public boolean isComplete() {
        return hasFirst && hasFinal;
    }
    
    // Getters
    public String getIdentifier() { return identifier; }
    public String getMessageRef() { return messageRef; }
    public Map<Integer, File> getParts() { return parts; }
    public boolean hasFirst() { return hasFirst; }
    public boolean hasFinal() { return hasFinal; }
    
    @Override
    public String toString() {
        return "MultipartGroup{" +
                "identifier='" + identifier + '\'' +
                ", messageRef='" + messageRef + '\'' +
                ", parts=" + parts.size() +
                ", complete=" + isComplete() +
                '}';
    }
}