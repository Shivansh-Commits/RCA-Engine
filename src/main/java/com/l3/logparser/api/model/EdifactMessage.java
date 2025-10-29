package com.l3.logparser.api.model;

import com.l3.logparser.enums.MessageType;

/**
 * Model representing an EDIFACT message part extracted from logs
 */
public class EdifactMessage {
    private String fullMessage;
    private String messageId;
    private String flightNumber;
    private int partNumber;
    private boolean isLastPart;
    private String partIndicator; // C, F, etc.
    private FlightDetails flightDetails;
    private String messageType; // PAXLST, etc.
    private String rawContent;
    private String dataType; // "PASSENGER" or "CREW" based on BGM segment
    private MessageType direction; // INPUT or OUTPUT

    public EdifactMessage() {}

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

    public FlightDetails getFlightDetails() { return flightDetails; }
    public void setFlightDetails(FlightDetails flightDetails) { this.flightDetails = flightDetails; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getRawContent() { return rawContent; }
    public void setRawContent(String rawContent) { this.rawContent = rawContent; }

    public MessageType getDirection() { return direction; }
    public void setDirection(MessageType direction) { this.direction = direction; }

    @Override
    public String toString() {
        return "EdifactMessage{" +
                "messageId='" + messageId + '\'' +
                ", flightNumber='" + flightNumber + '\'' +
                ", partNumber=" + partNumber +
                ", partIndicator='" + partIndicator + '\'' +
                ", dataType='" + dataType + '\'' +
                ", isLastPart=" + isLastPart +
                '}';
    }
}
