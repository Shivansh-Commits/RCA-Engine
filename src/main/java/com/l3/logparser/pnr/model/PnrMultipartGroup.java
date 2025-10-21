package com.l3.logparser.pnr.model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Model representing a group of related multipart PNR messages
 * Groups messages by message reference number and flight details
 */
public class PnrMultipartGroup {
    private String groupId;
    private String messageReferenceNumber;
    private String flightNumber;
    private PnrFlightDetails flightDetails;
    private List<PnrMessage> parts;
    private boolean isComplete;
    private int expectedParts;
    private String direction; // INPUT/OUTPUT

    public PnrMultipartGroup() {
        this.parts = new ArrayList<>();
    }

    public PnrMultipartGroup(String groupId, String messageReferenceNumber, String flightNumber) {
        this();
        this.groupId = groupId;
        this.messageReferenceNumber = messageReferenceNumber;
        this.flightNumber = flightNumber;
    }

    /**
     * Add a part to this group
     */
    public void addPart(PnrMessage message) {
        if (message != null) {
            parts.add(message);
            
            // Update group details from the first part
            if (parts.size() == 1) {
                this.flightDetails = message.getFlightDetails();
                this.direction = message.getDirection() != null ? message.getDirection().toString() : null;
            }
            
            // Update completeness check
            updateCompleteness();
        }
    }

    /**
     * Check if this group is complete (has final part)
     */
    private void updateCompleteness() {
        isComplete = parts.stream().anyMatch(PnrMessage::isLastPart);
        
        // Find the highest part number to estimate expected parts
        expectedParts = parts.stream()
                .mapToInt(PnrMessage::getPartNumber)
                .max()
                .orElse(1);
    }

    /**
     * Get parts sorted by part number
     */
    public List<PnrMessage> getSortedParts() {
        return parts.stream()
                .sorted(Comparator.comparingInt(PnrMessage::getPartNumber))
                .collect(Collectors.toList());
    }

    /**
     * Get the complete message by concatenating all parts
     */
    public String getCompleteMessage() {
        return getSortedParts().stream()
                .map(PnrMessage::getRawContent)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Check if all parts from 1 to highest part number are present
     */
    public boolean hasAllParts() {
        if (parts.isEmpty()) return false;
        
        Set<Integer> partNumbers = parts.stream()
                .map(PnrMessage::getPartNumber)
                .collect(Collectors.toSet());
        
        int maxPart = Collections.max(partNumbers);
        
        for (int i = 1; i <= maxPart; i++) {
            if (!partNumbers.contains(i)) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Get missing part numbers
     */
    public List<Integer> getMissingParts() {
        List<Integer> missing = new ArrayList<>();
        
        if (!parts.isEmpty()) {
            Set<Integer> partNumbers = parts.stream()
                    .map(PnrMessage::getPartNumber)
                    .collect(Collectors.toSet());
            
            int maxPart = Collections.max(partNumbers);
            
            for (int i = 1; i <= maxPart; i++) {
                if (!partNumbers.contains(i)) {
                    missing.add(i);
                }
            }
        }
        
        return missing;
    }

    // Getters and Setters
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getMessageReferenceNumber() { return messageReferenceNumber; }
    public void setMessageReferenceNumber(String messageReferenceNumber) { this.messageReferenceNumber = messageReferenceNumber; }

    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }

    public PnrFlightDetails getFlightDetails() { return flightDetails; }
    public void setFlightDetails(PnrFlightDetails flightDetails) { this.flightDetails = flightDetails; }

    public List<PnrMessage> getParts() { return new ArrayList<>(parts); }
    public void setParts(List<PnrMessage> parts) { 
        this.parts = new ArrayList<>(parts != null ? parts : new ArrayList<>());
        updateCompleteness();
    }

    public boolean isComplete() { return isComplete; }

    public int getExpectedParts() { return expectedParts; }

    public int getActualPartCount() { return parts.size(); }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    @Override
    public String toString() {
        return "PnrMultipartGroup{" +
                "groupId='" + groupId + '\'' +
                ", flightNumber='" + flightNumber + '\'' +
                ", parts=" + parts.size() +
                ", complete=" + isComplete +
                ", expectedParts=" + expectedParts +
                '}';
    }
}