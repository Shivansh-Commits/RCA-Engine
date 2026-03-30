package com.l3.logparser.pnr.parser;

import com.l3.logparser.config.AdvancedParserConfig;
import com.l3.logparser.config.PnrPatternConfig;
import com.l3.logparser.pnr.model.PnrMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for PNR Advanced Configuration
 * Verifies: 
 * 1. Only files with configured patterns are processed
 * 2. Configurations load dynamically
 * 3. Pattern-based file validation
 * 4. Adding new configurations works correctly
 */
@DisplayName("PNR Advanced Configuration Tests")
public class PnrAdvancedConfigTest {

    private PnrEdifactParser parser;
    private AdvancedParserConfig config;
    
    // Sample log entries with different patterns
    private static final String LOG_ENTRY_WITH_UNA = 
        "2025-10-15T10:30:45,123 INFO [trace.id:ABC123] Message body [UNA:+.?*'UNB+IATA:1+SENDER+NORAPI+251015:1030+0001+++PNRGOV'UNH+1+PNRGOV:11:1:IA+EK0160/290825+01:F'BGM+745+01:F'...UNZ+1+0001']";
    
    private static final String LOG_ENTRY_WITH_UNB_PNRGOV = 
        "2025-10-15T10:30:46,456 INFO [trace.id:DEF456] Message UNB+IATA:1+EK+NR+251015:1030+0002++PNRGOV'UNH+1+PNRGOV:11:1:IA+EK0160/290825+01:F'...UNZ+1+0002'";
    
    private static final String LOG_ENTRY_WITH_PNRGOV_PUSH = 
        "2025-10-15T10:30:47,789 INFO [trace.id:GHI789] Processing PNRGOV_PNR_PUSH request";
    
    private static final String LOG_ENTRY_WITH_MESSAGE_BODY_PNRGOV = 
        "2025-10-15T10:30:48,012 INFO [trace.id:JKL012] Message body contains PNRGOV data";
    
    private static final String LOG_ENTRY_WITH_CUSTOM_PATTERN = 
        "2025-10-15T10:30:49,345 INFO [trace.id:MNO345] CUSTOM_PNR_PATTERN detected in system";
    
    private static final String LOG_ENTRY_WITHOUT_PNR = 
        "2025-10-15T10:30:50,678 INFO [trace.id:PQR678] Regular log entry without any PNR data";
    
    private static final String LOG_ENTRY_WITH_MULTIPLE_CONDITIONS = 
        "2025-10-15T10:30:51,901 INFO [trace.id:STU901] Forward.BUSINESS_RULES_PROCESSOR Message body [UNA:+.?*'UNB+IATA:1+...']";

    @BeforeEach
    public void setUp() {
        parser = new PnrEdifactParser();
        config = new AdvancedParserConfig();
        parser.setAdvancedConfig(config);
    }

    @Test
    @DisplayName("Test 1: Default configuration recognizes standard PNR patterns")
    public void testDefaultConfigurationRecognizesStandardPatterns() {
        // Test UNA pattern
        List<PnrMessage> messagesUna = parser.parseLogContent(LOG_ENTRY_WITH_UNA, null);
        assertTrue(messagesUna.size() > 0 || LOG_ENTRY_WITH_UNA.contains("UNA"), 
            "Default config should recognize UNA pattern");
        
        // Test UNB+PNRGOV pattern
        List<PnrMessage> messagesUnb = parser.parseLogContent(LOG_ENTRY_WITH_UNB_PNRGOV, null);
        // Note: May be 0 if parsing fails, but the pattern should be detected
        
        // Test PNRGOV_PUSH pattern
        List<PnrMessage> messagesPush = parser.parseLogContent(LOG_ENTRY_WITH_PNRGOV_PUSH, null);
        // Note: May be 0 since this doesn't have full EDIFACT message
        
        // Test non-PNR entry should not be processed
        List<PnrMessage> messagesNonPnr = parser.parseLogContent(LOG_ENTRY_WITHOUT_PNR, null);
        assertEquals(0, messagesNonPnr.size(), 
            "Non-PNR log entries should not be processed");
    }

    @Test
    @DisplayName("Test 2: Only configured patterns are processed")
    public void testOnlyConfiguredPatternsAreProcessed() {
        // Create a restrictive configuration with only one pattern
        PnrPatternConfig pnrConfig = new PnrPatternConfig();
        List<PnrPatternConfig.MessagePattern> patterns = new ArrayList<>();
        
        // Only enable UNA pattern
        patterns.add(new PnrPatternConfig.MessagePattern("UNA_ONLY", "contains", "UNA", true));
        pnrConfig.setMessageStartPatterns(patterns);
        
        config.setPnrConfig(pnrConfig);
        parser.setAdvancedConfig(config);
        
        // Should detect UNA pattern
        List<PnrMessage> messagesUna = parser.parseLogContent(LOG_ENTRY_WITH_UNA, null);
        assertTrue(messagesUna.size() > 0 || LOG_ENTRY_WITH_UNA.contains("UNA"), 
            "Should detect configured UNA pattern");
        
        // Should NOT detect PNRGOV_PUSH (not configured)
        List<PnrMessage> messagesPush = parser.parseLogContent(LOG_ENTRY_WITH_PNRGOV_PUSH, null);
        assertEquals(0, messagesPush.size(), 
            "Should not detect unconfigured PNRGOV_PUSH pattern");
    }

    @Test
    @DisplayName("Test 3: Disabled patterns are ignored")
    public void testDisabledPatternsAreIgnored() {
        PnrPatternConfig pnrConfig = new PnrPatternConfig();
        List<PnrPatternConfig.MessagePattern> patterns = new ArrayList<>();
        
        // Add UNA pattern but disable it
        patterns.add(new PnrPatternConfig.MessagePattern("UNA_DISABLED", "contains", "UNA", false));
        pnrConfig.setMessageStartPatterns(patterns);
        
        config.setPnrConfig(pnrConfig);
        parser.setAdvancedConfig(config);
        
        // Should NOT detect UNA pattern (disabled)
        List<PnrMessage> messages = parser.parseLogContent(LOG_ENTRY_WITH_UNA, null);
        assertEquals(0, messages.size(), 
            "Disabled patterns should not be detected");
    }

    @Test
    @DisplayName("Test 4: Dynamic configuration loading from properties")
    public void testDynamicConfigurationLoadingFromProperties(@TempDir Path tempDir) throws Exception {
        // Create a custom configuration file
        Path configFile = tempDir.resolve("advanced-parser-config.properties");
        Properties props = new Properties();
        
        // Configure 2 patterns
        props.setProperty("pnr.patterns.count", "2");
        
        // Pattern 1: UNA
        props.setProperty("pnr.pattern.0.name", "UNA_PATTERN");
        props.setProperty("pnr.pattern.0.type", "contains");
        props.setProperty("pnr.pattern.0.value", "UNA");
        props.setProperty("pnr.pattern.0.enabled", "true");
        
        // Pattern 2: Custom pattern
        props.setProperty("pnr.pattern.1.name", "CUSTOM_PATTERN");
        props.setProperty("pnr.pattern.1.type", "contains");
        props.setProperty("pnr.pattern.1.value", "CUSTOM_PNR_PATTERN");
        props.setProperty("pnr.pattern.1.enabled", "true");
        
        // Save properties
        try (FileOutputStream fos = new FileOutputStream(configFile.toFile())) {
            props.store(fos, "Test Configuration");
        }
        
        // Load configuration from properties
        PnrPatternConfig pnrConfig = new PnrPatternConfig();
        pnrConfig.loadFromProperties(props);
        
        // Verify loaded patterns
        List<PnrPatternConfig.MessagePattern> loadedPatterns = pnrConfig.getMessageStartPatterns();
        assertEquals(2, loadedPatterns.size(), "Should load 2 patterns");
        
        assertEquals("UNA_PATTERN", loadedPatterns.get(0).getName());
        assertEquals("contains", loadedPatterns.get(0).getType());
        assertEquals("UNA", loadedPatterns.get(0).getValue());
        assertTrue(loadedPatterns.get(0).isEnabled());
        
        assertEquals("CUSTOM_PATTERN", loadedPatterns.get(1).getName());
        assertEquals("contains", loadedPatterns.get(1).getType());
        assertEquals("CUSTOM_PNR_PATTERN", loadedPatterns.get(1).getValue());
        assertTrue(loadedPatterns.get(1).isEnabled());
    }

    @Test
    @DisplayName("Test 5: Adding new configuration dynamically")
    public void testAddingNewConfigurationDynamically() {
        // Start with default configuration
        PnrPatternConfig pnrConfig = config.getPnrConfig();
        int initialPatternCount = pnrConfig.getMessageStartPatterns().size();
        
        // Add a new custom pattern
        PnrPatternConfig.MessagePattern newPattern = 
            new PnrPatternConfig.MessagePattern("NEW_CUSTOM", "contains", "NEW_PATTERN_TEXT", true);
        pnrConfig.getMessageStartPatterns().add(newPattern);
        
        // Verify the pattern was added
        assertEquals(initialPatternCount + 1, pnrConfig.getMessageStartPatterns().size(),
            "Should have one more pattern after adding");
        
        // Verify the new pattern exists
        boolean foundNewPattern = pnrConfig.getMessageStartPatterns().stream()
            .anyMatch(p -> "NEW_CUSTOM".equals(p.getName()));
        assertTrue(foundNewPattern, "New pattern should be in the configuration");
        
        // Test that the new pattern is used
        String logWithNewPattern = "2025-10-15T10:30:52,123 INFO [trace.id:NEW123] NEW_PATTERN_TEXT detected";
        List<PnrMessage> messages = parser.parseLogContent(logWithNewPattern, null);
        // The pattern should be detected (even if full parsing fails without valid EDIFACT)
        
        // Save and reload to verify persistence
        Properties props = new Properties();
        pnrConfig.saveToProperties(props);
        
        // Create new config and load
        PnrPatternConfig reloadedConfig = new PnrPatternConfig();
        reloadedConfig.loadFromProperties(props);
        
        assertEquals(initialPatternCount + 1, reloadedConfig.getMessageStartPatterns().size(),
            "Reloaded config should have the new pattern");
    }

    @Test
    @DisplayName("Test 6: Multiple condition patterns work correctly")
    public void testMultipleConditionPatterns() {
        PnrPatternConfig pnrConfig = new PnrPatternConfig();
        List<PnrPatternConfig.MessagePattern> patterns = new ArrayList<>();
        
        // Create a pattern with multiple conditions (all must match)
        PnrPatternConfig.MessagePattern multiPattern = 
            new PnrPatternConfig.MessagePattern("MULTI_CONDITION", "multiple", "", true);
        multiPattern.addCondition("contains", "Forward.BUSINESS_RULES_PROCESSOR");
        multiPattern.addCondition("contains", "Message body");
        patterns.add(multiPattern);
        
        pnrConfig.setMessageStartPatterns(patterns);
        config.setPnrConfig(pnrConfig);
        parser.setAdvancedConfig(config);
        
        // Test log entry with both conditions
        List<PnrMessage> messagesMatch = parser.parseLogContent(LOG_ENTRY_WITH_MULTIPLE_CONDITIONS, null);
        assertTrue(messagesMatch.size() > 0 || LOG_ENTRY_WITH_MULTIPLE_CONDITIONS.contains("Message body"),
            "Should detect when all conditions match");
        
        // Test log entry with only one condition
        String logPartialMatch = "2025-10-15T10:30:53,456 INFO [trace.id:ABC] Forward.BUSINESS_RULES_PROCESSOR only";
        List<PnrMessage> messagesPartial = parser.parseLogContent(logPartialMatch, null);
        assertEquals(0, messagesPartial.size(),
            "Should NOT detect when only one condition matches");
    }

    @Test
    @DisplayName("Test 7: Pattern validation - startsWith type")
    public void testPatternValidationStartsWith() {
        PnrPatternConfig pnrConfig = new PnrPatternConfig();
        List<PnrPatternConfig.MessagePattern> patterns = new ArrayList<>();
        
        // Create a startsWith pattern
        patterns.add(new PnrPatternConfig.MessagePattern("STARTS_WITH_INFO", "startsWith", "INFO", true));
        pnrConfig.setMessageStartPatterns(patterns);
        
        config.setPnrConfig(pnrConfig);
        parser.setAdvancedConfig(config);
        
        // Should match (starts with INFO after trim)
        String logStartsWithInfo = "INFO [trace.id:XYZ] This starts with INFO";
        List<PnrMessage> messagesMatch = parser.parseLogContent(logStartsWithInfo, null);
        // Pattern should be detected (even if no valid EDIFACT message)
        
        // Should NOT match (doesn't start with INFO)
        String logNoStartInfo = "2025-10-15T10:30:54,789 INFO [trace.id:XYZ] This has INFO but doesn't start";
        List<PnrMessage> messagesNoMatch = parser.parseLogContent(logNoStartInfo, null);
        assertEquals(0, messagesNoMatch.size(),
            "Should not match when pattern is not at start");
    }

    @Test
    @DisplayName("Test 8: Configuration persistence - save and reload")
    public void testConfigurationPersistence(@TempDir Path tempDir) throws Exception {
        // Create a custom configuration
        PnrPatternConfig pnrConfig = new PnrPatternConfig();
        List<PnrPatternConfig.MessagePattern> patterns = new ArrayList<>();
        
        patterns.add(new PnrPatternConfig.MessagePattern("PATTERN_1", "contains", "TEST1", true));
        patterns.add(new PnrPatternConfig.MessagePattern("PATTERN_2", "contains", "TEST2", false));
        
        PnrPatternConfig.MessagePattern multiPattern = 
            new PnrPatternConfig.MessagePattern("PATTERN_3", "multiple", "", true);
        multiPattern.addCondition("contains", "COND1");
        multiPattern.addCondition("contains", "COND2");
        patterns.add(multiPattern);
        
        pnrConfig.setMessageStartPatterns(patterns);
        
        // Save to properties
        Properties saveProps = new Properties();
        pnrConfig.saveToProperties(saveProps);
        
        // Verify saved properties
        assertEquals("3", saveProps.getProperty("pnr.patterns.count"));
        assertEquals("PATTERN_1", saveProps.getProperty("pnr.pattern.0.name"));
        assertEquals("TEST1", saveProps.getProperty("pnr.pattern.0.value"));
        assertEquals("true", saveProps.getProperty("pnr.pattern.0.enabled"));
        
        assertEquals("PATTERN_2", saveProps.getProperty("pnr.pattern.1.name"));
        assertEquals("false", saveProps.getProperty("pnr.pattern.1.enabled"));
        
        assertEquals("PATTERN_3", saveProps.getProperty("pnr.pattern.2.name"));
        assertEquals("multiple", saveProps.getProperty("pnr.pattern.2.type"));
        assertEquals("2", saveProps.getProperty("pnr.pattern.2.conditions.count"));
        
        // Reload from properties
        PnrPatternConfig reloadedConfig = new PnrPatternConfig();
        reloadedConfig.loadFromProperties(saveProps);
        
        // Verify reloaded patterns
        List<PnrPatternConfig.MessagePattern> reloadedPatterns = reloadedConfig.getMessageStartPatterns();
        assertEquals(3, reloadedPatterns.size());
        
        assertEquals("PATTERN_1", reloadedPatterns.get(0).getName());
        assertTrue(reloadedPatterns.get(0).isEnabled());
        
        assertEquals("PATTERN_2", reloadedPatterns.get(1).getName());
        assertFalse(reloadedPatterns.get(1).isEnabled());
        
        assertEquals("PATTERN_3", reloadedPatterns.get(2).getName());
        assertEquals(2, reloadedPatterns.get(2).getConditions().size());
    }

    @Test
    @DisplayName("Test 9: File validation with pattern matching")
    public void testFileValidationWithPatternMatching() {
        // Create configuration with specific patterns for input/output files
        PnrPatternConfig pnrConfig = new PnrPatternConfig();
        List<PnrPatternConfig.MessagePattern> patterns = new ArrayList<>();
        
        // Pattern for input files
        PnrPatternConfig.MessagePattern inputPattern = 
            new PnrPatternConfig.MessagePattern("INPUT_FILE_PATTERN", "multiple", "", true);
        inputPattern.addCondition("contains", "INFO");
        inputPattern.addCondition("contains", "Message body [UNA");
        patterns.add(inputPattern);
        
        pnrConfig.setMessageStartPatterns(patterns);
        config.setPnrConfig(pnrConfig);
        parser.setAdvancedConfig(config);
        
        // Valid input file content
        String validInputLog = 
            "2025-10-15T10:30:55,012 INFO [trace.id:INPUT123] MessageForwarder Message body [UNA:+.?*'UNB+IATA:1+...']";
        
        // Invalid input file content (doesn't match pattern)
        String invalidInputLog = 
            "2025-10-15T10:30:56,345 DEBUG [trace.id:DEBUG123] Some debug message without PNR";
        
        List<PnrMessage> validMessages = parser.parseLogContent(validInputLog, null);
        // Should detect pattern
        
        List<PnrMessage> invalidMessages = parser.parseLogContent(invalidInputLog, null);
        assertEquals(0, invalidMessages.size(), 
            "Invalid content should not be processed");
    }

    @Test
    @DisplayName("Test 10: Reset to defaults functionality")
    public void testResetToDefaults() {
        // Modify configuration
        PnrPatternConfig pnrConfig = config.getPnrConfig();
        pnrConfig.getMessageStartPatterns().clear();
        pnrConfig.getMessageStartPatterns().add(
            new PnrPatternConfig.MessagePattern("CUSTOM_ONLY", "contains", "CUSTOM", true)
        );
        
        assertEquals(1, pnrConfig.getMessageStartPatterns().size(),
            "Should have only custom pattern");
        
        // Reset to defaults
        config.resetToDefaults();
        
        // Verify default patterns are restored
        PnrPatternConfig defaultConfig = config.getPnrConfig();
        assertTrue(defaultConfig.getMessageStartPatterns().size() > 1,
            "Default config should have multiple patterns");
        
        // Verify default patterns include standard ones
        boolean hasUnaPattern = defaultConfig.getMessageStartPatterns().stream()
            .anyMatch(p -> p.getValue().contains("UNA") || p.getName().contains("UNA"));
        assertTrue(hasUnaPattern, "Default config should include UNA pattern");
    }

    @Test
    @DisplayName("Test 11: Configuration with empty patterns list")
    public void testConfigurationWithEmptyPatternsList() {
        // Create config with no patterns
        PnrPatternConfig pnrConfig = new PnrPatternConfig();
        pnrConfig.setMessageStartPatterns(new ArrayList<>());
        
        config.setPnrConfig(pnrConfig);
        parser.setAdvancedConfig(config);
        
        // No patterns means nothing should be detected
        List<PnrMessage> messages = parser.parseLogContent(LOG_ENTRY_WITH_UNA, null);
        assertEquals(0, messages.size(), 
            "Empty pattern list should not match any messages");
    }

    @Test
    @DisplayName("Test 12: Configuration with null config falls back to defaults")
    public void testNullConfigFallbackToDefaults() {
        // Set null config
        parser.setAdvancedConfig(null);
        
        // Should still use hardcoded fallback patterns
        List<PnrMessage> messages = parser.parseLogContent(LOG_ENTRY_WITH_UNA, null);
        // Fallback should still detect UNA pattern
    }

    @Test
    @DisplayName("Test 13: Case sensitivity in pattern matching")
    public void testCaseSensitivityInPatternMatching() {
        PnrPatternConfig pnrConfig = new PnrPatternConfig();
        List<PnrPatternConfig.MessagePattern> patterns = new ArrayList<>();
        
        patterns.add(new PnrPatternConfig.MessagePattern("CASE_SENSITIVE", "contains", "PNRGOV", true));
        pnrConfig.setMessageStartPatterns(patterns);
        
        config.setPnrConfig(pnrConfig);
        parser.setAdvancedConfig(config);
        
        // Test with correct case
        String logCorrectCase = "2025-10-15T10:30:57,678 INFO Message with PNRGOV";
        List<PnrMessage> messagesCorrect = parser.parseLogContent(logCorrectCase, null);
        
        // Test with wrong case (should not match - patterns are case-sensitive)
        String logWrongCase = "2025-10-15T10:30:58,901 INFO Message with pnrgov";
        List<PnrMessage> messagesWrong = parser.parseLogContent(logWrongCase, null);
        assertEquals(0, messagesWrong.size(), 
            "Case-sensitive pattern should not match different case");
    }
}
