package com.l3.logparser.pnr.model;

import com.l3.logparser.pnr.model.PnrFlightDetails;

/**
 * Model representing a PNR EDIFACT message part extracted from MessageMHPNRGOV.log files
 */
public class PnrMessage {
    private String fullMessage;
    private String messageId;
    private String flightNumber;
    private int partNumber;
    private boolean isLastPart;
    private String partIndicator; // C, F, etc.
    private PnrFlightDetails flightDetails;
    private String messageType; // PNRGOV
    private String rawContent;
    private String timestamp;
    private String traceId;

    public PnrMessage() {}

    // Getters and Setters
    public String getFullMessage() { return fullMessage; }
    public void setFullMessage(String fullMessage) { this.fullMessage = fullMessage; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }

    public int getPartNumber() { return partNumber; }
    public void setPartNumber(int partNumber) { this.partNumber = partNumber; }

    public boolean isLastPart() { return isLastPart; }
    public void setLastPart(boolean lastPart) { isLastPart = lastPart; }

    public String getPartIndicator() { return partIndicator; }
    public void setPartIndicator(String partIndicator) { this.partIndicator = partIndicator; }

    public PnrFlightDetails getFlightDetails() { return flightDetails; }
    public void setFlightDetails(PnrFlightDetails flightDetails) { this.flightDetails = flightDetails; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getRawContent() { return rawContent; }
    public void setRawContent(String rawContent) { this.rawContent = rawContent; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    @Override
    public String toString() {
        return String.format("PnrMessage{messageId='%s', flight='%s', part=%d, isLast=%s, type='%s'}",
                messageId, flightNumber, partNumber, isLastPart, messageType);
    }

    /**
     * Generate unique key for deduplication
     */
    public String getUniqueKey() {
        return messageId + "_" + partNumber;
    }

    /**
     * Check if this is the first part (contains 'C' indicator)
     */
    public boolean isFirstPart() {
        return "C".equals(partIndicator);
    }

    /**
     * Get display name for UI
     */
    public String getDisplayName() {
        String partInfo = isFirstPart() ? " (First)" : isLastPart() ? " (Last)" : "";
        return String.format("Part %d%s - %s", partNumber, partInfo,
                flightDetails != null ? flightDetails.getDisplayName() : flightNumber);
    }
}
