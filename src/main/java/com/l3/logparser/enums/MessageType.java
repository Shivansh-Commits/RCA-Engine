package com.l3.logparser.enums;

/**
 * Enum representing the message type (input/output direction)
 */
public enum MessageType {
    /**
     * Input message (received/incoming)
     */
    INPUT("INPUT"),

    /**
     * Output message (sent/outgoing)
     */
    OUTPUT("OUTPUT");

    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
