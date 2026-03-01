package com.l3.logparser.pnr.parser;

import com.l3.logparser.pnr.model.*;
import com.l3.logparser.enums.MessageType;
import com.l3.logparser.config.AdvancedParserConfig;
import com.l3.logparser.config.PnrPatternConfig;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Parser for PNR EDIFACT messages
 * Handles UNA separator detection, UNB validation, UNH part parsing, and TVL flight details extraction
 */
public class PnrEdifactParser {
    
    private static final Pattern LOG_TIMESTAMP_PATTERN = Pattern.compile(
        "\\[(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2},\\d{3})\\]"
    );
    
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile(
        "\\[trace\\.id:([^\\]]+)\\]"
    );

    // Progress callback for real-time logging
    private Consumer<String> progressCallback;

    // Debug mode flag for detailed logging
    private boolean debugMode = false;

    // Track if we've logged separator details for current batch (to avoid spam)
    private boolean separatorDetailsLogged = false;

    // Advanced parser configuration
    private AdvancedParserConfig advancedConfig;

    public PnrEdifactParser() {
        // Initialize with default configuration
        this.advancedConfig = new AdvancedParserConfig();
    }

    /**
     * Set advanced parser configuration
     * @param config The advanced parser configuration
     */
    public void setAdvancedConfig(AdvancedParserConfig config) {
        this.advancedConfig = config;
        if (config != null) {
            // Set progress callback and debug mode on config for logging
            config.setProgressCallback(this.progressCallback);
            config.setDebugMode(this.debugMode);
            
            // Reload config to show debug logs if debug mode is enabled
            if (this.debugMode) {
                config.reload();
            }
        }
    }

    /**
     * Set progress callback for real-time logging updates
     */
    public void setProgressCallback(Consumer<String> callback) {
        this.progressCallback = callback;
        // Also set the callback for PnrSeparators detailed logging
        PnrSeparators.setLogCallback(callback);
        // Update config callback if config is already set
        if (advancedConfig != null) {
            advancedConfig.setProgressCallback(callback);
        }
    }

    /**
     * Enable or disable debug mode for detailed logging
     */
    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
        // Update config debug mode and reload if debug is enabled and config is set
        if (advancedConfig != null) {
            advancedConfig.setDebugMode(debugMode);
            if (debugMode && progressCallback != null) {
                // Reload config to show debug logs
                advancedConfig.reload();
            }
        }
    }

    /**
     * Reset separator logging flag (call this when starting to process a new file)
     */
    public void resetSeparatorLogging() {
        this.separatorDetailsLogged = false;
    }

    /**
     * Log progress message
     */
    private void logProgress(String message) {
        if (progressCallback != null) {
            progressCallback.accept(message);
        }
        // Removed console logging - logs only go to UI via callback
    }

    /**
     * Parse PNR messages from log content
     * @param logContent Raw log file content
     * @param targetFlightNumber Flight number to filter for (optional)
     * @return List of parsed PNR messages
     */
    public List<PnrMessage> parseLogContent(String logContent, String targetFlightNumber) {
        return parseLogContent(logContent, targetFlightNumber, null);
    }

    /**
     * Parse PNR messages from log content with explicit message type
     * @param logContent Raw log file content
     * @param targetFlightNumber Flight number to filter for (optional)
     * @param messageType Explicit message type (INPUT/OUTPUT) based on file type
     * @return List of parsed PNR messages
     */
    public List<PnrMessage> parseLogContent(String logContent, String targetFlightNumber, MessageType messageType) {
        List<PnrMessage> messages = new ArrayList<>();
        
        if (logContent == null || logContent.trim().isEmpty()) {
            return messages;
        }
        
        // Split on actual log entry boundaries, not on dates within EDIFACT content
        // Look for patterns that indicate the start of a new log entry:
        // - Log level followed by timestamp: INFO [2025-10-15T...
        // - Standalone timestamp at start: 2025-10-15T06:50:51,113
        // - Log levels at start: INFO, DEBUG, WARN, ERROR (but not embedded in content)
        String[] logEntries = logContent.split("(?m)(?=^(?:INFO|DEBUG|WARN|ERROR)\\s+\\[|^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})");
        
        if (debugMode) {
            logProgress("  Split log content into " + logEntries.length + " log entries");
        }
        
        int entryNum = 0;
        for (String logEntry : logEntries) {
            entryNum++;
            boolean containsPnr = containsPnrMessage(logEntry);
            
            if (debugMode) {
                logProgress("    Entry #" + entryNum + ": Length=" + logEntry.length() + 
                           " chars, Contains PNR=" + containsPnr);
            }
            
            if (containsPnr) {
                PnrMessage message = parseLogEntry(logEntry, targetFlightNumber, messageType);
                if (message != null) {
                    messages.add(message);
                    if (debugMode) {
                        logProgress("      → Message extracted successfully");
                    }
                } else {
                    if (debugMode) {
                        logProgress("      → Message parsing returned null (filtered out or parsing failed)");
                    }
                }
            }
        }
        
        return messages;
    }

    /**
     * Check if log entry contains a PNR message (input or output)
     * Uses configurable patterns from AdvancedParserConfig
     */
    private boolean containsPnrMessage(String logEntry) {
        if (advancedConfig == null || advancedConfig.getPnrConfig() == null) {
            // Fallback to hardcoded patterns if config is not available
            return logEntry.contains("UNA:") || 
                   (logEntry.contains("UNB+") && logEntry.contains("PNRGOV")) ||
                   logEntry.contains("PNRGOV_PNR_PUSH") ||
                   (logEntry.contains("Message body") && logEntry.contains("PNRGOV")) ||
                   (logEntry.contains("Forward.BUSINESS_RULES_PROCESSOR") && logEntry.contains("Message body")) ||
                   (logEntry.contains("TO.NO.PNR.OUT") && logEntry.contains("UNA")) ||
                   (logEntry.contains("TO.NO.PNR.OUT") && logEntry.contains("UNB+"));
        }

        // Use patterns from advanced configuration
        List<PnrPatternConfig.MessagePattern> patterns = advancedConfig.getPnrConfig().getMessageStartPatterns();
        
        for (PnrPatternConfig.MessagePattern pattern : patterns) {
            if (!pattern.isEnabled()) {
                continue; // Skip disabled patterns
            }

            if (matchesPattern(logEntry, pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if log entry matches a specific pattern
     */
    private boolean matchesPattern(String logEntry, PnrPatternConfig.MessagePattern pattern) {
        String type = pattern.getType();

        if ("contains".equals(type)) {
            return logEntry.contains(pattern.getValue());
        } else if ("startsWith".equals(type)) {
            return logEntry.trim().startsWith(pattern.getValue());
        } else if ("multiple".equals(type)) {
            // For multiple conditions, all conditions must match
            List<PnrPatternConfig.MessagePattern.Condition> conditions = pattern.getConditions();
            if (conditions == null || conditions.isEmpty()) {
                return false;
            }

            for (PnrPatternConfig.MessagePattern.Condition condition : conditions) {
                if ("contains".equals(condition.getType())) {
                    if (!logEntry.contains(condition.getValue())) {
                        return false;
                    }
                } else if ("startsWith".equals(condition.getType())) {
                    if (!logEntry.trim().startsWith(condition.getValue())) {
                        return false;
                    }
                }
            }

            return true; // All conditions matched
        }

        return false;
    }

    /**
     * Parse a single log entry containing a PNR message
     */
    private PnrMessage parseLogEntry(String logEntry, String targetFlightNumber, MessageType explicitMessageType) {
        try {
            // Extract timestamp
            String timestamp = extractTimestamp(logEntry);
            
            // Extract trace ID
            String traceId = extractTraceId(logEntry);
            
            // Determine message direction - use explicit type if provided, otherwise auto-detect
            MessageType direction = explicitMessageType != null ? explicitMessageType : determineMessageDirection(logEntry);
            
            // Extract the EDIFACT message content
            String edifactContent = extractEdifactContent(logEntry);
            if (edifactContent == null || edifactContent.trim().isEmpty()) {
                if (debugMode) {
                    logProgress("        → extractEdifactContent returned null/empty");
                }
                return null;
            }
            
            if (debugMode) {
                logProgress("        → Extracted EDIFACT content: " + edifactContent.substring(0, Math.min(100, edifactContent.length())) + "...");
            }
            
            // Parse the EDIFACT message
            PnrMessage message = parseEdifactMessage(edifactContent);
            if (message == null) {
                if (debugMode) {
                    logProgress("        → parseEdifactMessage returned null");
                }
                return null;
            }
            
            // Set additional properties
            message.setLogTimestamp(timestamp);
            message.setLogTraceId(traceId);
            message.setDirection(direction);
            message.setRawContent(edifactContent);
            
            // Filter by flight number if specified
            if (targetFlightNumber != null && !targetFlightNumber.trim().isEmpty()) {
                boolean flightMatches = isFlightMatch(message, targetFlightNumber);
                if (debugMode) {
                    logProgress("        → Flight filter: target=" + targetFlightNumber + 
                               ", message=" + message.getFlightNumber() + 
                               ", matches=" + flightMatches);
                }
                if (!flightMatches) {
                    return null;
                }
            }
            
            return message;
            
        } catch (Exception e) {
            if (debugMode) {
                logProgress("        → Exception during parsing: " + e.getMessage());
            }
            System.err.println("Error parsing log entry: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse EDIFACT message content
     */
    private PnrMessage parseEdifactMessage(String edifactContent) {
        PnrMessage message = new PnrMessage();
        
        // Detect separators from UNA segment for this specific message
        // Each message is processed independently - if it has UNA, use those separators
        // If it doesn't have UNA, use default separators
        boolean shouldLogSeparators = debugMode && !separatorDetailsLogged;
        PnrSeparators separators = detectSeparators(edifactContent, shouldLogSeparators);

        // Mark that we've logged separators for this file (to avoid spam)
        if (shouldLogSeparators && separators != null) {
            separatorDetailsLogged = true;
            // Log the detected separators
            logProgress("    Separators detected: " + separators.toString());
        }

        message.setSeparators(separators);
        
        // Parse UNH segment for message details
        parseUnhSegment(message, edifactContent, separators);
        
        // Parse TVL segment for flight details
        parseTvlSegment(message, edifactContent, separators);
        
        // Set message type
        message.setMessageType("PNRGOV");
        
        return message;
    }

    /**
     * Detect EDIFACT separators from UNA or UNB segment
     * @param enableLogging If true, log the separator detection details (should only be enabled once per file)
     */
    private PnrSeparators detectSeparators(String edifactContent, boolean enableLogging) {
        // Try to find UNA segment first
        // UNA must be at the start of content or preceded only by whitespace/newlines
        // This prevents false matches with words like "UNABLE", "TUNA", etc.
        int unaIndex = findUnaSegmentStart(edifactContent);
        if (unaIndex >= 0) {
            int unaEnd = edifactContent.indexOf('\n', unaIndex);
            if (unaEnd == -1) unaEnd = edifactContent.indexOf(' ', unaIndex + 9);
            if (unaEnd == -1) unaEnd = Math.min(unaIndex + 15, edifactContent.length());
            
            String unaSegment = edifactContent.substring(unaIndex, unaEnd).trim();
            if (enableLogging) {
                logProgress("  Found UNA segment, extracting separators");
            }
            PnrSeparators separators = PnrSeparators.fromUnaSegment(unaSegment);
            return separators;
        }
        
       /* // UNA not found - fallback to UNB segment
        if (enableLogging) {
            logProgress("  UNA segment not found, extracting separators from UNB segment");
        }
        int unbIndex = edifactContent.indexOf("UNB");
        if (unbIndex >= 0) {
            // Find the end of UNB segment - look for segment terminator
            int unbEnd = -1;

            // Try to find the terminator character (typically ')
            // First, detect what the element separator is (character right after UNB)
            char elementSep = edifactContent.charAt(unbIndex + 3);

            // Now look for common terminators after UNB
            for (int i = unbIndex + 4; i < Math.min(unbIndex + 200, edifactContent.length()); i++) {
                char c = edifactContent.charAt(i);
                if (c == '\'' || c == '~' || c == '!') {
                    unbEnd = i + 1;
                    break;
                }
                // Also check for newline as potential segment boundary
                if (c == '\n' || c == '\r') {
                    String potentialSegment = edifactContent.substring(unbIndex, i).trim();
                    if (potentialSegment.length() > 20) { // UNB should be reasonably long
                        unbEnd = i;
                        break;
                    }
                }
            }

            if (unbEnd == -1) {
                unbEnd = Math.min(unbIndex + 150, edifactContent.length());
            }

            String unbSegment = edifactContent.substring(unbIndex, unbEnd).trim();
            if (enableLogging) {
                logProgress("  Successfully extracted separators from UNB segment");
            }
            PnrSeparators separators = PnrSeparators.fromUnbSegment(unbSegment);
            return separators;
        }*/

        
        // Neither UNA nor UNB found - return default separators
        if (enableLogging) {
            logProgress("  WARN: No UNA segment found, using default separators");
        }
        return PnrSeparators.DEFAULT;
    }

    /**
     * Detect separators with logging enabled (for backward compatibility)
     */
    private PnrSeparators detectSeparators(String edifactContent) {
        return detectSeparators(edifactContent, false); // Default to no logging per message
    }

    /**
     * Find the start position of a valid UNA segment in the content.
     * UNA must:
     * 1. Be at the start of content or immediately after whitespace/newlines
     * 2. Be followed by exactly 6 separator characters (not letters)
     * 3. The 6 characters should form valid EDIFACT separators
     * 
     * This prevents false matches with words like "UNABLE", "TUNA", etc.
     * 
     * @param content The EDIFACT content to search
     * @return The index of "UNA" if found at a valid position, -1 otherwise
     */
    private int findUnaSegmentStart(String content) {
        if (content == null || content.isEmpty() || content.length() < 9) {
            return -1; // UNA segment needs at least 9 characters (UNA + 6 separators)
        }
        
        int searchIndex = 0;
        while (searchIndex < content.length()) {
            int unaIndex = content.indexOf("UNA", searchIndex);
            
            if (unaIndex == -1) {
                return -1; // No more "UNA" found
            }
            
            // Check if there's enough space for UNA + 6 separator characters
            if (unaIndex + 9 > content.length()) {
                return -1; // Not enough characters left
            }
            
            // Check position validity (at start or after whitespace)
            boolean validPosition = false;
            if (unaIndex == 0) {
                validPosition = true;
            } else {
                char charBefore = content.charAt(unaIndex - 1);
                if (charBefore == '\n' || charBefore == '\r' || 
                    charBefore == ' ' || charBefore == '\t') {
                    validPosition = true;
                }
            }
            
            if (!validPosition) {
                // This "UNA" is part of another word (like "UNABLE")
                searchIndex = unaIndex + 3;
                continue;
            }
            
            // Validate the UNA segment structure
            // UNA should be followed by 6 separator characters, then typically UNB
            if (isValidUnaStructure(content, unaIndex)) {
                return unaIndex;
            }
            
            // This looked like UNA but isn't valid, continue searching
            searchIndex = unaIndex + 3;
        }
        
        return -1; // No valid UNA segment found
    }
    
    /**
     * Validate that the UNA segment has proper structure.
     * After "UNA" there should be 6 separator characters, followed by "UNB" or other segment.
     * The 6 characters should not all be letters (like in "UNABLE TO")
     */
    private boolean isValidUnaStructure(String content, int unaIndex) {
        if (unaIndex + 9 > content.length()) {
            return false;
        }
        
        // Extract the 6 potential separator characters after "UNA"
        String potentialSeparators = content.substring(unaIndex + 3, unaIndex + 9);
        
        // Check 1: Not all characters should be letters ("UNABLE" would fail this)
        int letterCount = 0;
        for (char c : potentialSeparators.toCharArray()) {
            if (Character.isLetter(c)) {
                letterCount++;
            }
        }
        // If more than 3 characters are letters, it's likely a word, not separators
        if (letterCount > 3) {
            return false;
        }
        
        // Check 2: After UNA + 6 separators, we should typically see "UNB"
        // Look ahead to see if there's a valid EDIFACT segment tag
        if (unaIndex + 12 <= content.length()) {
            String nextThreeChars = content.substring(unaIndex + 9, unaIndex + 12);
            // Common EDIFACT segment tags after UNA
            if (nextThreeChars.equals("UNB") || nextThreeChars.equals("UNH") || 
                nextThreeChars.equals("UNG") || nextThreeChars.equals("UNZ")) {
                return true;
            }
        }
        
        // Check 3: The 6 characters should include typical separator characters
        // Typical separators include: : + . ? * ' ~ ! | -
        boolean hasTypicalSeparators = potentialSeparators.matches(".*[+:'.*?|~!-].*");
        
        return hasTypicalSeparators;
    }

    /**
     * Parse UNH segment for message ID, reference number, and part information
     * Format: UNH+00000000000154+PNRGOV:11:1:IA+EK0160/290825/1435+01:F'
     */
    private void parseUnhSegment(PnrMessage message, String edifactContent, PnrSeparators separators) {
        int unhIndex = edifactContent.indexOf("UNH");
        if (unhIndex < 0) return;
        
        int unhEnd = edifactContent.indexOf(separators.getTerminatorSeparator(), unhIndex);
        if (unhEnd < 0) return;
        
        String unhSegment = edifactContent.substring(unhIndex, unhEnd + 1);
        String[] elements = separators.splitElements(unhSegment);
        
        if (elements.length >= 5) {
            // Element 1: Message reference number
            message.setMessageReferenceNumber(elements[1]);
            message.setMessageId(elements[1]);
            
            // Element 3: Flight details (EK0160/290825/1435)
            String flightInfo = elements[3];
            parseFlightFromUnhSegment(message, flightInfo);
            
            // Element 4: Part information (01:F or 01:C or just 06)
            parsePartInformation(message, elements[4], separators);
        }
    }

    /**
     * Parse part information from UNH segment
     * Examples: "01:F", "01:C", "06", "11:F"
     * Note: Output messages are always single-part with F indicator
     */
    private void parsePartInformation(PnrMessage message, String partInfo, PnrSeparators separators) {
        if (partInfo == null || partInfo.trim().isEmpty()) {
            message.setPartNumber(1);
            message.setPartIndicator("F");
            message.setLastPart(true);
            message.setMultipart(false);
            return;
        }
        
        // Clean any terminator characters from the part info
        String cleanPartInfo = partInfo.replace(String.valueOf(separators.getTerminatorSeparator()), "").trim();
        
        String[] partElements = separators.splitSubElements(cleanPartInfo);
        
        if (partElements.length >= 2) {
            // Format: "01:F" or "11:F"
            try {
                message.setPartNumber(Integer.parseInt(partElements[0]));
                String indicator = partElements[1].trim();
                message.setPartIndicator(indicator);
                message.setLastPart("F".equals(indicator));
                
                // For output messages, they are always single-part regardless of part number
                if (message.getDirection() == MessageType.OUTPUT) {
                    message.setMultipart(false);
                } else {
                    message.setMultipart(!"F".equals(indicator) || message.getPartNumber() > 1);
                }
            } catch (NumberFormatException e) {
                message.setPartNumber(1);
                message.setPartIndicator("F");
                message.setLastPart(true);
                message.setMultipart(false);
            }
        } else {
            // Format: "06" (just part number)
            try {
                int partNum = Integer.parseInt(partElements[0].trim());
                message.setPartNumber(partNum);
                message.setPartIndicator(partNum == 1 ? "F" : "C");
                message.setLastPart(partNum == 1);
                
                // For output messages, they are always single-part
                if (message.getDirection() == MessageType.OUTPUT) {
                    message.setMultipart(false);
                    message.setPartIndicator("F");
                    message.setLastPart(true);
                } else {
                    message.setMultipart(partNum > 1);
                }
            } catch (NumberFormatException e) {
                message.setPartNumber(1);
                message.setPartIndicator("F");
                message.setLastPart(true);
                message.setMultipart(false);
            }
        }
    }

    /**
     * Parse flight information from UNH segment
     * Format: EK0160/290825/1435 or QR512/290825/2045
     */
    private void parseFlightFromUnhSegment(PnrMessage message, String flightInfo) {
        if (flightInfo == null || !flightInfo.contains("/")) {
            return;
        }
        
        String[] parts = flightInfo.split("/");
        if (parts.length > 0) {
            String flightCode = parts[0].trim();
            // Store the original flight code format (preserve leading zeros)
            message.setFlightNumber(flightCode);
        }
    }

    /**
     * Parse TVL segment for detailed flight information
     * Format: TVL+290825:1435:290825:2325+OSL+DXB+EK+0160'
     */
    private void parseTvlSegment(PnrMessage message, String edifactContent, PnrSeparators separators) {
        int tvlIndex = edifactContent.indexOf("TVL");
        if (tvlIndex < 0) return;
        
        int tvlEnd = edifactContent.indexOf(separators.getTerminatorSeparator(), tvlIndex);
        if (tvlEnd < 0) return;
        
        String tvlSegment = edifactContent.substring(tvlIndex, tvlEnd + 1);
        String[] elements = separators.splitElements(tvlSegment);
        
        if (elements.length >= 6) {
            PnrFlightDetails flightDetails = new PnrFlightDetails();
            flightDetails.setRawTvlSegment(tvlSegment);
            
            // Element 1: Date and time information (290825:1435:290825:2325)
            String[] dateTimeInfo = separators.splitSubElements(elements[1]);
            if (dateTimeInfo.length >= 4) {
                flightDetails.setDepartureDate(dateTimeInfo[0]); // ddmmyy
                flightDetails.setDepartureTime(dateTimeInfo[1]); // hhmm
                flightDetails.setArrivalDate(dateTimeInfo[2]);   // ddmmyy
                flightDetails.setArrivalTime(dateTimeInfo[3]);   // hhmm
            }
            
            // Element 2: Departure airport
            if (elements.length > 2) {
                flightDetails.setDepartureAirport(elements[2]);
            }
            
            // Element 3: Arrival airport
            if (elements.length > 3) {
                flightDetails.setArrivalAirport(elements[3]);
            }
            
            // Element 4: Airline code
            if (elements.length > 4) {
                flightDetails.setAirlineCode(elements[4]);
            }
            
            // Element 5: Flight number
            if (elements.length > 5) {
                String cleanFlightNumber = elements[5].replace(String.valueOf(separators.getTerminatorSeparator()), "").trim();
                
                // Store the flight number as-is in the flight details
                flightDetails.setFlightNumber(cleanFlightNumber);
                
                // Update message flight number with more complete info if available
                if (flightDetails.getAirlineCode() != null) {
                    String fullFlightNumber = flightDetails.getAirlineCode() + cleanFlightNumber;
                    // Only update if the TVL provides more complete info or if message flight is null
                    if (message.getFlightNumber() == null || message.getFlightNumber().trim().isEmpty()) {
                        message.setFlightNumber(fullFlightNumber);
                    }
                }
            }
            
            message.setFlightDetails(flightDetails);
        }
    }

    /**
     * Extract timestamp from log entry
     */
    private String extractTimestamp(String logEntry) {
        Matcher matcher = LOG_TIMESTAMP_PATTERN.matcher(logEntry);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extract trace ID from log entry
     */
    private String extractTraceId(String logEntry) {
        Matcher matcher = TRACE_ID_PATTERN.matcher(logEntry);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Determine message direction from log entry content
     */
    private MessageType determineMessageDirection(String logEntry) {
        // Check for output messages first - be more specific to avoid false positives
        if (logEntry.contains("Forward.BUSINESS_RULES_PROCESSOR") || 
            logEntry.contains("TO.NO.PNR.OUT") ||
            logEntry.contains("MQ Message sent") ||
            (logEntry.contains("Response") && !logEntry.contains("ResponseListenerContainer")) || // Exclude ResponseListenerContainer 
            logEntry.contains("OUTPUT") || 
            logEntry.contains("sent")) {
            return MessageType.OUTPUT;
        } else if (logEntry.contains("Request") || logEntry.contains("INPUT") || logEntry.contains("received")) {
            return MessageType.INPUT;
        }
        return MessageType.INPUT; // Default to INPUT
    }

    /**
     * Extract EDIFACT message content from log entry
     * Handles both input messages (MessageMHPNRGOV.log) and output messages (MessageForwarder.log)
     */
    private String extractEdifactContent(String logEntry) {
        // Look for the start of EDIFACT content (UNA or UNB)
        int startIndex = -1;
        
        // For output messages, look for content after "Message body ["
        if (logEntry.contains("Message body [")) {
            int messageBodyIndex = logEntry.indexOf("Message body [");
            if (messageBodyIndex >= 0) {
                String afterMessageBody = logEntry.substring(messageBodyIndex + "Message body [".length());
                
                // Use smart UNA detection to avoid matching "UNABLE"
                int unaIndex = findUnaSegmentStart(afterMessageBody);
                int unbIndex = afterMessageBody.indexOf("UNB+");
                
                if (unaIndex >= 0) {
                    startIndex = messageBodyIndex + "Message body [".length() + unaIndex;
                } else if (unbIndex >= 0) {
                    startIndex = messageBodyIndex + "Message body [".length() + unbIndex;
                }
            }
        }
        
        // Fallback to original logic if not found in Message body
        if (startIndex < 0) {
            // Use smart UNA detection instead of simple indexOf
            int unaIndex = findUnaSegmentStart(logEntry);
            if (unaIndex >= 0) {
                startIndex = unaIndex;
            } else {
                // Try UNB if no valid UNA found
                startIndex = logEntry.indexOf("UNB+");
            }
        }
        
        if (startIndex < 0) {
            return null;
        }
        
        // Find the end of the EDIFACT message
        int endIndex = logEntry.length();
        
        // Look for UNZ segment which typically ends PNR messages
        int unzIndex = logEntry.indexOf("UNZ+", startIndex);
        if (unzIndex > 0) {
            // Find the terminator after UNZ
            int terminatorIndex = logEntry.indexOf('\'', unzIndex);
            if (terminatorIndex > 0) {
                endIndex = terminatorIndex + 1;
            }
        } else {
            // For output messages, look for closing bracket
            if (logEntry.contains("Message body [") && startIndex > logEntry.indexOf("Message body [")) {
                int closingBracket = logEntry.indexOf("]", startIndex + 50);
                if (closingBracket > 0) {
                    endIndex = closingBracket;
                }
            } else {
                // If no UNZ found, look for other end markers
                int[] endMarkers = {
                    logEntry.indexOf("\n", startIndex + 100),  // Next line break after substantial content
                    logEntry.indexOf(" ] ", startIndex + 50),   // End of log entry marker
                    logEntry.indexOf(" - ", startIndex + 100),  // Log separator
                    logEntry.indexOf("INFO ", startIndex + 50), // Next log entry
                    logEntry.indexOf("DEBUG ", startIndex + 50),
                    logEntry.indexOf("WARN ", startIndex + 50),
                    logEntry.indexOf("ERROR ", startIndex + 50)
                };
                
                for (int marker : endMarkers) {
                    if (marker > startIndex + 50 && marker < endIndex) { // Ensure minimum content length
                        endIndex = marker;
                    }
                }
            }
        }
        
        String content = logEntry.substring(startIndex, endIndex).trim();
        
        // Clean up the content - remove log prefixes that might be mixed in
        content = content.replaceAll("\\s*\\]\\s*$", ""); // Remove trailing ]
        content = content.replaceAll("\\s*-\\s*$", "");   // Remove trailing -
        
        return content;
    }

    /**
     * Check if the parsed message matches the target flight number
     */
    private boolean isFlightMatch(PnrMessage message, String targetFlightNumber) {
        if (targetFlightNumber == null || targetFlightNumber.trim().isEmpty()) {
            return true;
        }
        
        String target = targetFlightNumber.trim().toUpperCase();
        String messageFlight = message.getFlightNumber();
        
        if (messageFlight != null) {
            messageFlight = messageFlight.toUpperCase();
            
            // Check exact match first
            if (messageFlight.equals(target)) {
                return true;
            }
            
            // Check if they match after normalizing flight numbers (removing leading zeros)
            String normalizedMessage = normalizeFlightNumber(messageFlight);
            String normalizedTarget = normalizeFlightNumber(target);
            if (normalizedMessage.equals(normalizedTarget)) {
                return true;
            }
            
            // Check contains
            if (messageFlight.contains(target) || target.contains(messageFlight)) {
                return true;
            }
        }
        
        // Also check flight details
        PnrFlightDetails details = message.getFlightDetails();
        if (details != null) {
            String fullFlight = details.getFullFlightNumber();
            if (fullFlight != null) {
                fullFlight = fullFlight.toUpperCase();
                
                // Same logic for flight details
                if (fullFlight.equals(target)) {
                    return true;
                }
                
                String normalizedFlight = normalizeFlightNumber(fullFlight);
                String normalizedTarget = normalizeFlightNumber(target);
                if (normalizedFlight.equals(normalizedTarget)) {
                    return true;
                }
                
                if (fullFlight.contains(target) || target.contains(fullFlight)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Normalize flight number by removing leading zeros from the numeric part
     * EK0160 -> EK160, QR512 -> QR512
     */
    private String normalizeFlightNumber(String flightNumber) {
        if (flightNumber == null || flightNumber.trim().isEmpty()) {
            return flightNumber;
        }
        
        // Find where the numeric part starts
        int numericStart = -1;
        for (int i = 0; i < flightNumber.length(); i++) {
            if (Character.isDigit(flightNumber.charAt(i))) {
                numericStart = i;
                break;
            }
        }
        
        if (numericStart > 0 && numericStart < flightNumber.length()) {
            String airlineCode = flightNumber.substring(0, numericStart);
            String flightNum = flightNumber.substring(numericStart);
            
            // Remove leading zeros but keep at least one digit
            flightNum = flightNum.replaceFirst("^0+(?!$)", "");
            
            return airlineCode + flightNum;
        }
        
        return flightNumber;
    }
}
