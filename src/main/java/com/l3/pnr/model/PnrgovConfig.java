package com.l3.pnr.model;

/**
 * Configuration class for PNRGOV comparison operations
 */
public class PnrgovConfig {
    
    public enum Mode {
        EDIFACT, API, HYBRID
    }
    
    public enum MatchingStrategy {
        PNR_NAME, NAME_DOC_DOB, CUSTOM
    }
    
    public enum ValidationLevel {
        STANDARD, STRICT
    }
    
    public enum OutputFormat {
        STANDARD, DETAILED, SUMMARY
    }
    
    private Mode mode = Mode.EDIFACT;
    private MatchingStrategy matchingStrategy = MatchingStrategy.PNR_NAME;
    private boolean strictValidation = false;
    private boolean performanceMode = false;
    private long maxFileSize = 100 * 1024 * 1024; // 100MB
    private OutputFormat outputFormat = OutputFormat.STANDARD;
    private boolean enableLogging = false;
    private ValidationLevel validationLevel = ValidationLevel.STANDARD;
    
    public PnrgovConfig() {
        // Default constructor with standard settings
    }
    
    public PnrgovConfig(Mode mode, MatchingStrategy matchingStrategy, boolean enableLogging) {
        this.mode = mode;
        this.matchingStrategy = matchingStrategy;
        this.enableLogging = enableLogging;
        this.validationLevel = strictValidation ? ValidationLevel.STRICT : ValidationLevel.STANDARD;
    }
    
    // Getters and setters
    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    
    public MatchingStrategy getMatchingStrategy() { return matchingStrategy; }
    public void setMatchingStrategy(MatchingStrategy matchingStrategy) { this.matchingStrategy = matchingStrategy; }
    
    public boolean isStrictValidation() { return strictValidation; }
    public void setStrictValidation(boolean strictValidation) { 
        this.strictValidation = strictValidation;
        this.validationLevel = strictValidation ? ValidationLevel.STRICT : ValidationLevel.STANDARD;
    }
    
    public boolean isPerformanceMode() { return performanceMode; }
    public void setPerformanceMode(boolean performanceMode) { this.performanceMode = performanceMode; }
    
    public long getMaxFileSize() { return maxFileSize; }
    public void setMaxFileSize(long maxFileSize) { this.maxFileSize = maxFileSize; }
    
    public OutputFormat getOutputFormat() { return outputFormat; }
    public void setOutputFormat(OutputFormat outputFormat) { this.outputFormat = outputFormat; }
    
    public boolean isEnableLogging() { return enableLogging; }
    public void setEnableLogging(boolean enableLogging) { this.enableLogging = enableLogging; }
    
    public ValidationLevel getValidationLevel() { return validationLevel; }
    public void setValidationLevel(ValidationLevel validationLevel) { this.validationLevel = validationLevel; }
    
    @Override
    public String toString() {
        return "PnrgovConfig{" +
                "mode=" + mode +
                ", matchingStrategy=" + matchingStrategy +
                ", strictValidation=" + strictValidation +
                ", performanceMode=" + performanceMode +
                ", maxFileSize=" + maxFileSize +
                ", outputFormat=" + outputFormat +
                ", enableLogging=" + enableLogging +
                ", validationLevel=" + validationLevel +
                '}';
    }
}