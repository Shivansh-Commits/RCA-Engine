package com.l3.logparser.config;

import java.util.*;
import java.util.function.Consumer;

/**
 * Configuration for PNR message patterns
 * Based on patterns from PnrEdifactParser
 */
public class PnrPatternConfig {

    // Message start patterns
    private List<MessagePattern> messageStartPatterns;
    
    // Progress callback for UI logging
    private Consumer<String> progressCallback;
    
    // Debug mode flag
    private boolean debugMode = false;

    public PnrPatternConfig() {
        this.messageStartPatterns = new ArrayList<>();
    }
    
    /**
     * Set progress callback for UI logging
     */
    public void setProgressCallback(Consumer<String> callback) {
        this.progressCallback = callback;
    }
    
    /**
     * Log a progress message to the UI
     */
    private void logProgress(String message) {
        if (progressCallback != null) {
            progressCallback.accept(message);
        }
    }
    
    /**
     * Enable or disable debug mode for detailed logging
     */
    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }
    
    /**
     * Get debug mode status
     */
    public boolean isDebugMode() {
        return debugMode;
    }

    /**
     * Load default PNR patterns and codes
     */
    public void loadDefaults() {
        loadDefaultMessagePatterns();
    }

    private void loadDefaultMessagePatterns() {
        messageStartPatterns.clear();

        // Add patterns based on containsPnrMessage() in PnrEdifactParser
        messageStartPatterns.add(new MessagePattern("UNA_PATTERN", "contains", "UNA", true));
        
        // UNB with PNRGOV (separator is dynamic, so don't hardcode +)
        MessagePattern unbPnrgov = new MessagePattern("UNB_PNRGOV", "multiple", "", true);
        unbPnrgov.addCondition("contains", "UNB");
        unbPnrgov.addCondition("contains", "PNRGOV");
        messageStartPatterns.add(unbPnrgov);
        
        messageStartPatterns.add(new MessagePattern("PNRGOV_PUSH", "contains", "PNRGOV_PNR_PUSH", true));
        
        // Message body with PNRGOV
        MessagePattern messageBodyPnrgov = new MessagePattern("MESSAGE_BODY_PNRGOV", "multiple", "", true);
        messageBodyPnrgov.addCondition("contains", "Message body");
        messageBodyPnrgov.addCondition("contains", "PNRGOV");
        messageStartPatterns.add(messageBodyPnrgov);
        
        // Output message patterns - INFO with Message body [UNA
        MessagePattern outputUna = new MessagePattern("MessageForwarder_UNA", "multiple", "", true);
        outputUna.addCondition("contains", "INFO");
        outputUna.addCondition("contains", "Message body [UNA");
        messageStartPatterns.add(outputUna);
        
        // Output message patterns - INFO with Message body [UNB
        MessagePattern outputUnb = new MessagePattern("MessageForwarder_UNB", "multiple", "", true);
        outputUnb.addCondition("contains", "INFO");
        outputUnb.addCondition("contains", "Message body [UNB");
        messageStartPatterns.add(outputUnb);
        
        // Forward.BUSINESS_RULES_PROCESSOR patterns
        MessagePattern forwarderPattern = new MessagePattern("FORWARDER_MESSAGE_BODY", "multiple", "", true);
        forwarderPattern.addCondition("contains", "Forward.BUSINESS_RULES_PROCESSOR");
        forwarderPattern.addCondition("contains", "Message body");
        messageStartPatterns.add(forwarderPattern);
    }

    /**
     * Load configuration from properties
     */
    public void loadFromProperties(Properties props) {
        loadMessagePatternsFromProperties(props);
    }

    private void loadMessagePatternsFromProperties(Properties props) {
        // Load custom message patterns if they exist
        int patternCount = Integer.parseInt(props.getProperty("pnr.patterns.count", "0"));
        
        if (debugMode) {
            logProgress("[PNR Config Debug] Loading PNR patterns from properties...");
            logProgress("[PNR Config Debug] Found " + patternCount + " patterns in configuration");
        }

        if (patternCount > 0) {
            messageStartPatterns.clear();
            
            if (debugMode) {
                logProgress("[PNR Config Debug] Clearing existing patterns and loading from properties");
            }

            for (int i = 0; i < patternCount; i++) {
                String prefix = "pnr.pattern." + i + ".";
                String name = props.getProperty(prefix + "name", "");
                String type = props.getProperty(prefix + "type", "contains");
                String value = props.getProperty(prefix + "value", "");
                boolean enabled = Boolean.parseBoolean(props.getProperty(prefix + "enabled", "true"));
                
                if (debugMode) {
                    logProgress("[PNR Config Debug] Pattern " + i + ":");
                    logProgress("    Name: " + name);
                    logProgress("    Type: " + type);
                    logProgress("    Value: " + value);
                    logProgress("    Enabled: " + enabled);
                }

                MessagePattern pattern = new MessagePattern(name, type, value, enabled);

                if ("multiple".equals(type)) {
                    int conditionCount = Integer.parseInt(props.getProperty(prefix + "conditions.count", "0"));
                    
                    if (debugMode) {
                        logProgress("    Conditions count: " + conditionCount);
                    }
                    
                    for (int j = 0; j < conditionCount; j++) {
                        String condPrefix = prefix + "condition." + j + ".";
                        String condType = props.getProperty(condPrefix + "type", "contains");
                        String condValue = props.getProperty(condPrefix + "value", "");
                        
                        if (debugMode) {
                            logProgress("      Condition " + j + ": [" + condType + "] = '" + condValue + "'");
                        }
                        
                        pattern.addCondition(condType, condValue);
                    }
                }

                messageStartPatterns.add(pattern);
            }
            
            if (debugMode) {
                logProgress("[PNR Config Debug] Successfully loaded " + messageStartPatterns.size() + " PNR patterns");
            }
        } else {
            if (debugMode) {
                logProgress("[PNR Config Debug] No patterns found in properties, using defaults");
            }
        }
    }

    /**
     * Save configuration to properties
     */
    public void saveToProperties(Properties props) {
        saveMessagePatternsToProperties(props);
    }

    private void saveMessagePatternsToProperties(Properties props) {
        props.setProperty("pnr.patterns.count", String.valueOf(messageStartPatterns.size()));

        for (int i = 0; i < messageStartPatterns.size(); i++) {
            MessagePattern pattern = messageStartPatterns.get(i);
            String prefix = "pnr.pattern." + i + ".";

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

    // Getters and Setters
    public List<MessagePattern> getMessageStartPatterns() {
        return messageStartPatterns;
    }

    public void setMessageStartPatterns(List<MessagePattern> messageStartPatterns) {
        this.messageStartPatterns = messageStartPatterns;
    }

    /**
     * Inner class for message pattern configuration
     * (Reuses the same structure as API patterns)
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
}
