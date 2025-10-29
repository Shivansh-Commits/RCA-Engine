package com.l3.logparser.pnr.model;

import com.l3.logparser.enums.MessageType;

/**
 * Model representing a PNR EDIFACT message part extracted from logs
 * Extends the base EdifactMessage functionality with PNR-specific features
 */
public class PnrMessage {
    private String fullMessage;
    private String messageId; // From UNH segment
    private String messageReferenceNumber; // From UNH segment for multipart grouping
    private String flightNumber;
    private int partNumber;
    private boolean isLastPart;
    private String partIndicator; // C for continuation, F for final
    private boolean isMultipart; // true if part indicator is C or contains multiple parts
    private PnrFlightDetails flightDetails;
    private String messageType; // PNRGOV
    private String rawContent;
    private PnrSeparators separators; // UNA separators
    private MessageType direction; // INPUT/OUTPUT
    private String logTimestamp;
    private String logTraceId;

    public PnrMessage() {}

    // Getters and Setters
    public String getFullMessage() { return fullMessage; }
    public void setFullMessage(String fullMessage) { this.fullMessage = fullMessage; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getMessageReferenceNumber() { return messageReferenceNumber; }
    public void setMessageReferenceNumber(String messageReferenceNumber) { this.messageReferenceNumber = messageReferenceNumber; }

    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }

    public int getPartNumber() { return partNumber; }
    public void setPartNumber(int partNumber) { this.partNumber = partNumber; }

    public boolean isLastPart() { return isLastPart; }
    public void setLastPart(boolean lastPart) { isLastPart = lastPart; }

    public String getPartIndicator() { return partIndicator; }
    public void setPartIndicator(String partIndicator) { this.partIndicator = partIndicator; }

    public boolean isMultipart() { return isMultipart; }
    public void setMultipart(boolean multipart) { isMultipart = multipart; }

    public PnrFlightDetails getFlightDetails() { return flightDetails; }
    public void setFlightDetails(PnrFlightDetails flightDetails) { this.flightDetails = flightDetails; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getRawContent() { return rawContent; }
    public void setRawContent(String rawContent) { this.rawContent = rawContent; }

    public PnrSeparators getSeparators() { return separators; }
    public void setSeparators(PnrSeparators separators) { this.separators = separators; }

    public MessageType getDirection() { return direction; }
    public void setDirection(MessageType direction) { this.direction = direction; }

    public String getLogTimestamp() { return logTimestamp; }
    public void setLogTimestamp(String logTimestamp) { this.logTimestamp = logTimestamp; }

    public String getLogTraceId() { return logTraceId; }
    public void setLogTraceId(String logTraceId) { this.logTraceId = logTraceId; }

    /**
     * Check if this message belongs to the same multipart group as another message
     */
    public boolean belongsToSameGroup(PnrMessage other) {
        return this.messageReferenceNumber != null && 
               this.messageReferenceNumber.equals(other.getMessageReferenceNumber());
    }

    /**
     * Get a unique group identifier for multipart message grouping
     */
    public String getGroupId() {
        return messageReferenceNumber + "_" + flightNumber;
    }

    @Override
    public String toString() {
        return "PnrMessage{" +
                "messageId='" + messageId + '\'' +
                ", messageReferenceNumber='" + messageReferenceNumber + '\'' +
                ", flightNumber='" + flightNumber + '\'' +
                ", partNumber=" + partNumber +
                ", partIndicator='" + partIndicator + '\'' +
                ", isMultipart=" + isMultipart +
                ", isLastPart=" + isLastPart +
                ", direction=" + direction +
                '}';
    }
}
