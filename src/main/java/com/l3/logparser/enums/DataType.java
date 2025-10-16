package com.l3.logparser.enums;

/**
 * Enum representing the type of data to extract from log files
 */
public enum DataType {
    /**
     * Extract only API (Advanced Passenger Information) data
     */
    API("API"),

    /**
     * Extract only PNR (Passenger Name Record) data
     */
    PNR("PNR");

    private final String displayName;

    DataType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
