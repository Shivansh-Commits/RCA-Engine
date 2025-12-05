package com.l3.logparser.config;

import java.util.*;

/**
 * Configuration for API message patterns and segment codes
 */
public class ApiPatternConfig {

    // Message start patterns
    private List<MessagePattern> messageStartPatterns;

    // Segment codes
    private SegmentCodes segmentCodes;

    public ApiPatternConfig() {
        this.messageStartPatterns = new ArrayList<>();
        this.segmentCodes = new SegmentCodes();
    }

    /**
     * Load default patterns and codes
     */
    public void loadDefaults() {
        loadDefaultMessagePatterns();
        loadDefaultSegmentCodes();
    }

    private void loadDefaultMessagePatterns() {
        messageStartPatterns.clear();

        // Add all existing hardcoded patterns
        messageStartPatterns.add(new MessagePattern("$STX$UNA", "contains", "$STX$UNA", true));
        messageStartPatterns.add(new MessagePattern("$STX$UNB", "contains", "$STX$UNB", true));

        // Complex pattern: MessageForwarder UNA
        MessagePattern forwarderUNA = new MessagePattern("MessageForwarder_UNA", "multiple", "", true);
        forwarderUNA.addCondition("contains", "INFO ");
        forwarderUNA.addCondition("contains", "Forward.BUSINESS_RULES_PROCESSOR");
        forwarderUNA.addCondition("contains", "Message body [UNA");
        messageStartPatterns.add(forwarderUNA);

        // Complex pattern: MessageForwarder UNB
        MessagePattern forwarderUNB = new MessagePattern("MessageForwarder_UNB", "multiple", "", true);
        forwarderUNB.addCondition("contains", "INFO ");
        forwarderUNB.addCondition("contains", "Forward.BUSINESS_RULES_PROCESSOR");
        forwarderUNB.addCondition("contains", "Message body [UNB");
        messageStartPatterns.add(forwarderUNB);

        // Complex pattern: Failed to parse API UNA
        MessagePattern warnUNA = new MessagePattern("WARN_UNA", "multiple", "", true);
        warnUNA.addCondition("contains", "Failed to parse API message");
        warnUNA.addCondition("contains", "[UNA");
        messageStartPatterns.add(warnUNA);

        // Complex pattern: Failed to parse API UNB
        MessagePattern warnUNB = new MessagePattern("WARN_UNB", "multiple", "", true);
        warnUNB.addCondition("contains", "Failed to parse API message");
        warnUNB.addCondition("contains", "[UNB");
        messageStartPatterns.add(warnUNB);

        // Complex pattern: Failed to parse API (multiline)
        MessagePattern warnMultiline = new MessagePattern("WARN_MULTILINE", "multiple", "", true);
        warnMultiline.addCondition("contains", "Failed to parse API message");
        warnMultiline.addCondition("contains", "[");
        messageStartPatterns.add(warnMultiline);

        // Simple pattern: Standalone UNA
        messageStartPatterns.add(new MessagePattern("STANDALONE_UNA", "startsWith", "UNA", true));
    }

    private void loadDefaultSegmentCodes() {
        segmentCodes = new SegmentCodes();

        // BGM codes
        segmentCodes.setBgmPassengerCode("745");
        segmentCodes.setBgmCrewCode("250");

        // LOC codes
        segmentCodes.setLocDepartureCode("125");
        segmentCodes.setLocArrivalCode("87");

        // DTM codes
        segmentCodes.setDtmDepartureCode("189");
        segmentCodes.setDtmArrivalCode("232");

        // TDT codes (flight number is typically in position 2)
        segmentCodes.setTdtFlightPosition(2);
    }

    /**
     * Load configuration from properties
     */
    public void loadFromProperties(Properties props) {
        loadMessagePatternsFromProperties(props);
        loadSegmentCodesFromProperties(props);
    }

    private void loadMessagePatternsFromProperties(Properties props) {
        // Load custom message patterns if they exist
        int patternCount = Integer.parseInt(props.getProperty("api.patterns.count", "0"));

        if (patternCount > 0) {
            messageStartPatterns.clear();

            for (int i = 0; i < patternCount; i++) {
                String prefix = "api.pattern." + i + ".";
                String name = props.getProperty(prefix + "name", "");
                String type = props.getProperty(prefix + "type", "contains");
                String value = props.getProperty(prefix + "value", "");
                boolean enabled = Boolean.parseBoolean(props.getProperty(prefix + "enabled", "true"));

                MessagePattern pattern = new MessagePattern(name, type, value, enabled);

                if ("multiple".equals(type)) {
                    int conditionCount = Integer.parseInt(props.getProperty(prefix + "conditions.count", "0"));
                    for (int j = 0; j < conditionCount; j++) {
                        String condPrefix = prefix + "condition." + j + ".";
                        String condType = props.getProperty(condPrefix + "type", "contains");
                        String condValue = props.getProperty(condPrefix + "value", "");
                        pattern.addCondition(condType, condValue);
                    }
                }

                messageStartPatterns.add(pattern);
            }
        }
    }

    private void loadSegmentCodesFromProperties(Properties props) {
        segmentCodes.setBgmPassengerCode(props.getProperty("api.bgm.passenger", "745"));
        segmentCodes.setBgmCrewCode(props.getProperty("api.bgm.crew", "250"));
        segmentCodes.setLocDepartureCode(props.getProperty("api.loc.departure", "125"));
        segmentCodes.setLocArrivalCode(props.getProperty("api.loc.arrival", "87"));
        segmentCodes.setDtmDepartureCode(props.getProperty("api.dtm.departure", "189"));
        segmentCodes.setDtmArrivalCode(props.getProperty("api.dtm.arrival", "232"));
        segmentCodes.setTdtFlightPosition(Integer.parseInt(props.getProperty("api.tdt.flight.position", "2")));
    }

    /**
     * Save configuration to properties
     */
    public void saveToProperties(Properties props) {
        saveMessagePatternsToProperties(props);
        saveSegmentCodesToProperties(props);
    }

    private void saveMessagePatternsToProperties(Properties props) {
        props.setProperty("api.patterns.count", String.valueOf(messageStartPatterns.size()));

        for (int i = 0; i < messageStartPatterns.size(); i++) {
            MessagePattern pattern = messageStartPatterns.get(i);
            String prefix = "api.pattern." + i + ".";

            props.setProperty(prefix + "name", pattern.getName());
            props.setProperty(prefix + "type", pattern.getType());
            props.setProperty(prefix + "value", pattern.getValue());
            props.setProperty(prefix + "enabled", String.valueOf(pattern.isEnabled()));

            if ("multiple".equals(pattern.getType()) && pattern.getConditions() != null) {
                List<MessagePattern.Condition> conditions = pattern.getConditions();
                props.setProperty(prefix + "conditions.count", String.valueOf(conditions.size()));

                for (int j = 0; j < conditions.size(); j++) {
                    MessagePattern.Condition condition = conditions.get(j);
                    String condPrefix = prefix + "condition." + j + ".";
                    props.setProperty(condPrefix + "type", condition.getType());
                    props.setProperty(condPrefix + "value", condition.getValue());
                }
            }
        }
    }

    private void saveSegmentCodesToProperties(Properties props) {
        props.setProperty("api.bgm.passenger", segmentCodes.getBgmPassengerCode());
        props.setProperty("api.bgm.crew", segmentCodes.getBgmCrewCode());
        props.setProperty("api.loc.departure", segmentCodes.getLocDepartureCode());
        props.setProperty("api.loc.arrival", segmentCodes.getLocArrivalCode());
        props.setProperty("api.dtm.departure", segmentCodes.getDtmDepartureCode());
        props.setProperty("api.dtm.arrival", segmentCodes.getDtmArrivalCode());
        props.setProperty("api.tdt.flight.position", String.valueOf(segmentCodes.getTdtFlightPosition()));
    }

    // Getters and Setters
    public List<MessagePattern> getMessageStartPatterns() {
        return messageStartPatterns;
    }

    public void setMessageStartPatterns(List<MessagePattern> messageStartPatterns) {
        this.messageStartPatterns = messageStartPatterns;
    }

    public SegmentCodes getSegmentCodes() {
        return segmentCodes;
    }

    public void setSegmentCodes(SegmentCodes segmentCodes) {
        this.segmentCodes = segmentCodes;
    }

    /**
     * Inner class for message pattern configuration
     */
    public static class MessagePattern {
        private String name;
        private String type; // "contains", "startsWith", "multiple"
        private String value;
        private boolean enabled;
        private List<Condition> conditions; // For multiple conditions

        public MessagePattern(String name, String type, String value, boolean enabled) {
            this.name = name;
            this.type = type;
            this.value = value;
            this.enabled = enabled;
            this.conditions = new ArrayList<>();
        }

        public void addCondition(String type, String value) {
            conditions.add(new Condition(type, value));
        }

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public List<Condition> getConditions() { return conditions; }
        public void setConditions(List<Condition> conditions) { this.conditions = conditions; }

        /**
         * Inner class for individual conditions in multiple pattern types
         */
        public static class Condition {
            private String type; // "contains", "startsWith"
            private String value;

            public Condition(String type, String value) {
                this.type = type;
                this.value = value;
            }

            public String getType() { return type; }
            public void setType(String type) { this.type = type; }

            public String getValue() { return value; }
            public void setValue(String value) { this.value = value; }
        }
    }

    /**
     * Inner class for segment codes configuration
     */
    public static class SegmentCodes {
        private String bgmPassengerCode = "745";
        private String bgmCrewCode = "250";
        private String locDepartureCode = "125";
        private String locArrivalCode = "87";
        private String dtmDepartureCode = "189";
        private String dtmArrivalCode = "232";
        private int tdtFlightPosition = 2;

        // Getters and Setters
        public String getBgmPassengerCode() { return bgmPassengerCode; }
        public void setBgmPassengerCode(String bgmPassengerCode) { this.bgmPassengerCode = bgmPassengerCode; }

        public String getBgmCrewCode() { return bgmCrewCode; }
        public void setBgmCrewCode(String bgmCrewCode) { this.bgmCrewCode = bgmCrewCode; }

        public String getLocDepartureCode() { return locDepartureCode; }
        public void setLocDepartureCode(String locDepartureCode) { this.locDepartureCode = locDepartureCode; }

        public String getLocArrivalCode() { return locArrivalCode; }
        public void setLocArrivalCode(String locArrivalCode) { this.locArrivalCode = locArrivalCode; }

        public String getDtmDepartureCode() { return dtmDepartureCode; }
        public void setDtmDepartureCode(String dtmDepartureCode) { this.dtmDepartureCode = dtmDepartureCode; }

        public String getDtmArrivalCode() { return dtmArrivalCode; }
        public void setDtmArrivalCode(String dtmArrivalCode) { this.dtmArrivalCode = dtmArrivalCode; }

        public int getTdtFlightPosition() { return tdtFlightPosition; }
        public void setTdtFlightPosition(int tdtFlightPosition) { this.tdtFlightPosition = tdtFlightPosition; }
    }
}
