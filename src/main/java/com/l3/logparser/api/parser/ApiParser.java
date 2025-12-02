package com.l3.logparser.api.parser;

import com.l3.logparser.api.model.EdifactMessage;
import com.l3.logparser.api.model.FlightDetails;
import com.l3.logparser.config.AdvancedParserConfig;
import com.l3.logparser.config.ApiPatternConfig;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Parser for EDIFACT messages found in log files
 * Handles UNA separators and extracts flight information
 */
public class ApiParser {

    private static final String UNA_PATTERN = "UNA:(.)(.)(.)(.)(.)(.)";
    private static final String UNH_PATTERN = "UNH(.)(\\d+)(.)(PAXLST:D:05B:UN:IATA(.)(\\w+)(.)(.+?)(.)";

    private char subElementSeparator;
    private char elementSeparator;
    private char decimalSeparator;
    private char releaseIndicator;
    private char reservedSeparator;
    private char terminatorSeparator;

    // Advanced configuration for customizable patterns and codes
    private AdvancedParserConfig advancedConfig;

    /**
     * Constructor - initialize with default EDIFACT separators and load configuration
     */
    public ApiParser() {
        setDefaultSeparators();
        this.advancedConfig = new AdvancedParserConfig();
    }

    /**
     * Constructor with custom configuration
     */
    public ApiParser(AdvancedParserConfig config) {
        setDefaultSeparators();
        this.advancedConfig = config != null ? config : new AdvancedParserConfig();
    }

    /**
     * Set default EDIFACT separators when UNA segment is not available (e.g., for UNB messages)
     */
    private void setDefaultSeparators() {
        subElementSeparator = ':';
        elementSeparator = '+';
        decimalSeparator = '.';
        releaseIndicator = '?';
        reservedSeparator = ' ';
        terminatorSeparator = '\'';
    }

    /**
     * Parse UNA segment to extract separators - enhanced with error recovery
     */
    public boolean parseUNA(String line) {

        // Handle both $UNA: and UNA: formats
        String unaLine = line;
        if (line.contains("$UNA")) {
            unaLine = line.substring(line.indexOf("$UNA") + 4); // Skip "$UNA"
        }

        // ENHANCED FIX: More aggressive whitespace and control character removal
        String originalUnaLine = unaLine;
        unaLine = unaLine.replaceAll("[\\r\\n\\t]", ""); // Remove carriage returns, newlines, tabs
        unaLine = unaLine.replaceAll("\\p{Cntrl}", ""); // Remove all control characters
        unaLine = unaLine.trim(); // Remove leading/trailing whitespace

        // Handle corrupted/incomplete UNA headers
        if (unaLine.length() < 9) {
            // Use common EDIFACT separators as fallback
            subElementSeparator = ':';
            elementSeparator = '+';
            decimalSeparator = '.';
            releaseIndicator = '?';
            reservedSeparator = ' ';
            terminatorSeparator = '\'';
            return true; // Return success with defaults
        }

        // Handle malformed UNA that doesn't start with "UNA"
        if (!unaLine.startsWith("UNA")) {
            // Try to extract separators from whatever we have
            if (unaLine.length() >= 6) {
                try {
                    subElementSeparator = unaLine.charAt(0);
                    elementSeparator = unaLine.charAt(1);
                    decimalSeparator = unaLine.charAt(2);
                    releaseIndicator = unaLine.charAt(3);
                    reservedSeparator = unaLine.charAt(4);
                    terminatorSeparator = unaLine.charAt(5);
                    return true;
                } catch (Exception e) {
                    // Silent fallback to defaults
                    setDefaultSeparators();
                }
            }

            // Use defaults if extraction fails
            setDefaultSeparators();
            return true;
        }

        // Standard UNA processing
        if (unaLine.startsWith("UNA") && unaLine.length() >= 9) {
            // Extract the 6 separator characters immediately after "UNA"
            subElementSeparator = unaLine.charAt(3);  // 1st separator after "UNA"
            elementSeparator = unaLine.charAt(4);     // 2nd separator after "UNA"
            decimalSeparator = unaLine.charAt(5);     // 3rd separator after "UNA"
            releaseIndicator = unaLine.charAt(6);     // 4th separator after "UNA"
            reservedSeparator = unaLine.charAt(7);    // 5th separator after "UNA"
            terminatorSeparator = unaLine.charAt(8);  // 6th separator after "UNA"
            return true;
        }

        // Final fallback to standard EDIFACT separators
        setDefaultSeparators();

        return true; // Always return true so processing can continue
    }


    public List<EdifactMessage> parseLogContent(String logContent, String targetFlightNumber, boolean debugMode, Consumer<String> debugLogger) {
        List<EdifactMessage> messages = new ArrayList<>();
        String[] lines = logContent.split("\\r?\\n");

        // First stage: Extract complete message boundaries
        List<String> rawMessages = extractRawMessages(lines, debugMode, debugLogger);

        if (debugMode && debugLogger != null) {
            debugLogger.accept("Stage 1 complete: Found " + rawMessages.size() + " raw message boundaries");
        }

        // Second stage: Parse each extracted message
        for (int i = 0; i < rawMessages.size(); i++) {
            String rawMessage = rawMessages.get(i);
            if (debugMode && debugLogger != null) {
                debugLogger.accept("Stage 2: Processing message " + (i + 1) + " of " + rawMessages.size());
            }

            EdifactMessage parsedMessage = parseRawMessage(rawMessage, debugMode, debugLogger);
            if (parsedMessage != null && matchesFlightCriteria(parsedMessage, targetFlightNumber)) {
                messages.add(parsedMessage);
            }
        }

        if (debugMode && debugLogger != null) {
            debugLogger.accept("Parsing complete. Total valid messages: " + messages.size());
        }
        return messages;
    }

    /**
     * Stage 1: Extract raw EDIFACT messages from log content
     * Finds message boundaries from start patterns to UNZ segments
     */
    private List<String> extractRawMessages(String[] lines, boolean debugMode, Consumer<String> debugLogger) {
        List<String> rawMessages = new ArrayList<>();
        StringBuilder currentRawMessage = new StringBuilder();
        boolean inMessage = false;
        String messageType = null;
        int lineNumber = 0;

        for (String line : lines) {
            lineNumber++;
            line = cleanCarriageReturns(line);

            // Check for various start patterns
            String startPattern = detectMessageStart(line);

            if (startPattern != null) {
                // Save previous message if exists
                if (inMessage && currentRawMessage.length() > 0) {
                    String rawMsg = currentRawMessage.toString().trim();
                    if (!rawMsg.isEmpty()) {
                        rawMessages.add(rawMsg);
                        if (debugMode && debugLogger != null) {
                            debugLogger.accept("Extracted raw message " + rawMessages.size() + " (ended by new start pattern)");
                        }
                    }
                }

                // Start new message
                String extractedContent = extractEdifactContent(line, startPattern);
                if (!extractedContent.isEmpty()) {
                    currentRawMessage = new StringBuilder();
                    currentRawMessage.append(extractedContent);
                    inMessage = true;
                    messageType = determineMessageType(startPattern, line);

                    if (debugMode && debugLogger != null) {
                        debugLogger.accept("[Line " + lineNumber + "] Started new message with pattern: " + startPattern);
                    }
                }
            }
            // Continue building message if we're inside one
            else if (inMessage) {
                // Add line to current message if it looks like EDIFACT content
                if (isEdifactContent(line)) {
                    currentRawMessage.append("\n").append(line);
                }

                // Check for message end (UNZ segment)
                if (line.contains("UNZ")) {
                    inMessage = false;
                    String rawMsg = currentRawMessage.toString().trim();
                    if (!rawMsg.isEmpty()) {
                        rawMessages.add(rawMsg);
                        if (debugMode && debugLogger != null) {
                            debugLogger.accept("Extracted raw message " + rawMessages.size() + " (ended by UNZ)");
                        }
                    }
                    currentRawMessage = new StringBuilder();
                    messageType = null;
                }
            }
        }

        // Handle last message if file doesn't end with UNZ
        if (inMessage && currentRawMessage.length() > 0) {
            String rawMsg = currentRawMessage.toString().trim();
            if (!rawMsg.isEmpty()) {
                rawMessages.add(rawMsg);
                if (debugMode && debugLogger != null) {
                    debugLogger.accept("Extracted final raw message " + rawMessages.size() + " (EOF)");
                }
            }
        }

        return rawMessages;
    }

    /**
     * Detect different types of message start patterns using configuration
     */
    private String detectMessageStart(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        List<ApiPatternConfig.MessagePattern> patterns = advancedConfig.getApiConfig().getMessageStartPatterns();

        for (ApiPatternConfig.MessagePattern pattern : patterns) {
            if (!pattern.isEnabled()) {
                continue; // Skip disabled patterns
            }

            boolean matches = false;

            switch (pattern.getType()) {
                case "contains":
                    matches = line.contains(pattern.getValue());
                    break;

                case "startsWith":
                    matches = line.startsWith(pattern.getValue());
                    break;

                case "multiple":
                    matches = true; // Start with true, all conditions must match
                    for (ApiPatternConfig.MessagePattern.Condition condition : pattern.getConditions()) {
                        boolean conditionMatches = false;
                        switch (condition.getType()) {
                            case "contains":
                                conditionMatches = line.contains(condition.getValue());
                                break;
                            case "startsWith":
                                conditionMatches = line.startsWith(condition.getValue());
                                break;
                        }
                        if (!conditionMatches) {
                            matches = false;
                            break;
                        }
                    }
                    break;
            }

            if (matches) {
                return pattern.getName();
            }
        }

        return null;
    }

    /**
     * Extract EDIFACT content based on the start pattern
     */
    private String extractEdifactContent(String line, String startPattern) {
        switch (startPattern) {
            case "$STX$UNA":
                int stxUnaIndex = line.indexOf("$STX$UNA");
                return line.substring(stxUnaIndex + 5);

            case "$STX$UNB":
                int stxUnbIndex = line.indexOf("$STX$UNB");
                return line.substring(stxUnbIndex + 5);

            case "MessageForwarder_UNA":
            case "MessageForwarder_UNB":
                int messageBodyStart = line.indexOf("Message body [");
                if (messageBodyStart != -1) {
                    String content = line.substring(messageBodyStart + "Message body [".length());
                    if (content.endsWith("]")) {
                        content = content.substring(0, content.length() - 1);
                    }
                    return content;
                }
                return "";

            case "WARN_UNA":
                int unaStartIndex = line.indexOf("[UNA");
                if (unaStartIndex != -1) {
                    String content = line.substring(unaStartIndex + 1);
                    if (content.endsWith("]")) {
                        content = content.substring(0, content.length() - 1);
                    }
                    return content;
                }
                return "";

            case "WARN_UNB":
                int unbStartIndex = line.indexOf("[UNB");
                if (unbStartIndex != -1) {
                    String content = line.substring(unbStartIndex + 1);
                    if (content.endsWith("]")) {
                        content = content.substring(0, content.length() - 1);
                    }
                    return content;
                }
                return "";

            case "WARN_MULTILINE":
                int bracketIndex = line.indexOf("[");
                if (bracketIndex != -1 && bracketIndex < line.length() - 1) {
                    return line.substring(bracketIndex + 1).trim();
                }
                return "";

            case "STANDALONE_UNA":
                return line;

            default:
                return "";
        }
    }

    /**
     * Determine message type based on pattern and line content
     */
    private String determineMessageType(String startPattern, String line) {
        if (startPattern.startsWith("MessageForwarder")) {
            return "OUTPUT";
        }
        return "INPUT"; // Default for other patterns
    }

    /**
     * Check if a line contains EDIFACT content
     */
    private boolean isEdifactContent(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }

        // Look for EDIFACT segment patterns
        return line.startsWith("UN") ||
               line.startsWith("BGM") ||
               line.startsWith("TDT") ||
               line.startsWith("LOC") ||
               line.startsWith("DTM") ||
               line.startsWith("NAD") ||
               line.contains("+") || // Element separator
               line.contains("'"); // Terminator separator
    }

    /**
     * Stage 2: Parse raw EDIFACT message character by character
     * Split into segments using separators and extract data
     */
    private EdifactMessage parseRawMessage(String rawMessage, boolean debugMode, Consumer<String> debugLogger) {
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            return null;
        }

        EdifactMessage message = new EdifactMessage();

        try {
            // Step 1: Parse UNA header to get separators (if present)
            String workingMessage = rawMessage;
            if (rawMessage.startsWith("UNA")) {
                parseUNA(rawMessage);
                if (debugMode && debugLogger != null) {
                    debugLogger.accept("Parsed UNA header - separators extracted");
                }
            } else {
                // No UNA header, use defaults
                setDefaultSeparators();
                if (debugMode && debugLogger != null) {
                    debugLogger.accept("No UNA header found - using default separators");
                }
            }

            // Step 2: Split message into segments character by character
            List<String> segments = splitIntoSegments(workingMessage);

            if (debugMode && debugLogger != null) {
                debugLogger.accept("Split into " + segments.size() + " segments");
            }

            // Step 3: Process each segment to extract data
            for (String segment : segments) {
                processSegment(segment, message, debugMode, debugLogger);
            }

            // Step 4: Set the complete raw content
            message.setRawContent(rawMessage);

            return message;

        } catch (Exception e) {
            if (debugMode && debugLogger != null) {
                debugLogger.accept("Error parsing raw message: " + e.getMessage());
            }
            return null;
        }
    }

    /**
     * Split raw message into segments using the terminator separator
     * Reads character by character to handle escape sequences properly
     */
    private List<String> splitIntoSegments(String rawMessage) {
        List<String> segments = new ArrayList<>();
        StringBuilder currentSegment = new StringBuilder();

        char[] chars = rawMessage.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];

            // Check for release/escape character
            if (c == releaseIndicator && i + 1 < chars.length) {
                // Next character is escaped, add both characters
                currentSegment.append(c);
                currentSegment.append(chars[i + 1]);
                i++; // Skip next character
            }
            // Check for segment terminator
            else if (c == terminatorSeparator) {
                // End of segment
                String segment = currentSegment.toString().trim();
                if (!segment.isEmpty()) {
                    segments.add(segment);
                }
                currentSegment = new StringBuilder();
            }
            // Regular character
            else {
                currentSegment.append(c);
            }
        }

        // Add final segment if exists
        String finalSegment = currentSegment.toString().trim();
        if (!finalSegment.isEmpty()) {
            segments.add(finalSegment);
        }

        return segments;
    }

    /**
     * Process individual segment to extract data
     */
    private void processSegment(String segment, EdifactMessage message, boolean debugMode, Consumer<String> debugLogger) {
        if (segment == null || segment.trim().isEmpty()) {
            return;
        }

        segment = segment.trim();

        try {
            // UNH segment - message header
            if (segment.startsWith("UNH" + elementSeparator)) {
                parseUNH(segment, message);
                if (debugMode && debugLogger != null) {
                    debugLogger.accept("Processed UNH segment");
                }
            }
            // BGM segment - beginning of message
            else if (segment.startsWith("BGM" + elementSeparator)) {
                parseFlightDetails(segment, message);
                if (debugMode && debugLogger != null) {
                    debugLogger.accept("Processed BGM segment");
                }
            }
            // TDT segment - transport details
            else if (segment.startsWith("TDT" + elementSeparator)) {
                parseFlightDetails(segment, message);
                if (debugMode && debugLogger != null) {
                    debugLogger.accept("Processed TDT segment");
                }
            }
            // LOC segment - location
            else if (segment.startsWith("LOC" + elementSeparator)) {
                parseFlightDetails(segment, message);
                if (debugMode && debugLogger != null) {
                    debugLogger.accept("Processed LOC segment");
                }
            }
            // DTM segment - date/time
            else if (segment.startsWith("DTM" + elementSeparator)) {
                parseFlightDetails(segment, message);
                if (debugMode && debugLogger != null) {
                    debugLogger.accept("Processed DTM segment");
                }
            }
            // Other segments can be added as needed
            else {
                if (debugMode && debugLogger != null) {
                    String segmentType = segment.length() >= 3 ? segment.substring(0, 3) : segment;
                    debugLogger.accept("Skipped segment type: " + segmentType);
                }
            }
        } catch (Exception e) {
            if (debugMode && debugLogger != null) {
                debugLogger.accept("Error processing segment: " + segment.substring(0, Math.min(segment.length(), 50)) + " - " + e.getMessage());
            }
        }
    }

    /**
     * Parse UNH segment to extract message ID, flight number, and part information
     */
    private void parseUNH(String line, EdifactMessage message) {
        // ENHANCED FIX: Use robust carriage return cleaning method
        line = cleanCarriageReturns(line);

        // Ensure the segment ends with terminator for consistent parsing
        if (!line.endsWith(String.valueOf(terminatorSeparator))) {
            line = line + terminatorSeparator;
        }

        try {
            // Split by element separator
            String[] segments = line.split(Pattern.quote(String.valueOf(elementSeparator)));

            if (segments.length >= 4) {
                // Extract message reference (segment 1)
                String messageRef = segments[1];

                // Extract message type info (segment 2) - PAXLST:D:05B:UN:IATA
                String messageType = segments[2];

                // Extract flight info and message ID (segment 3) - e.g., TS2302507251130
                String flightInfo = segments[3];

                // Check if there's a part number in segment 4
                String partInfo = "";
                if (segments.length >= 5) {
                    partInfo = segments[4].replace(String.valueOf(terminatorSeparator), "").trim();
                }

                // Parse part number and indicator
                if (!partInfo.isEmpty()) {
                    // Check if part info contains sub-element separator (e.g., "13:F")
                    if (partInfo.contains(String.valueOf(subElementSeparator))) {
                        String[] partParts = partInfo.split(String.valueOf(subElementSeparator));
                        if (partParts.length >= 2) {
                            try {
                                int partNum = Integer.parseInt(partParts[0]);
                                String partInd = partParts[1];
                                message.setPartNumber(partNum);
                                message.setPartIndicator(partInd);
                                message.setLastPart("F".equals(partInd));

                                // Create unique message ID including part info
                                String uniqueMessageId = messageRef + "_" + flightInfo + "_P" + partNum + partInd;
                                message.setMessageId(uniqueMessageId);
                            } catch (NumberFormatException e) {
                                setDefaultPartInfo(message, messageRef, flightInfo);
                            }
                        } else {
                            setDefaultPartInfo(message, messageRef, flightInfo);
                        }
                    } else {
                        // Try parsing as just a number (part number without indicator)
                        try {
                            int partNum = Integer.parseInt(partInfo);
                            message.setPartNumber(partNum);
                            message.setPartIndicator("C"); // Default to continuation
                            message.setLastPart(false);

                            // Create unique message ID
                            String uniqueMessageId = messageRef + "_" + flightInfo + "_P" + String.format("%02d", partNum) + "C";
                            message.setMessageId(uniqueMessageId);
                        } catch (NumberFormatException e) {
                            // Maybe it's a letter/string indicator, treat as part 1 with this indicator
                            message.setPartNumber(1);
                            message.setPartIndicator(partInfo);
                            message.setLastPart("F".equalsIgnoreCase(partInfo));

                            String uniqueMessageId = messageRef + "_" + flightInfo + "_P1" + partInfo;
                            message.setMessageId(uniqueMessageId);
                        }
                    }
                } else {
                    // No part info found, set defaults
                    setDefaultPartInfo(message, messageRef, flightInfo);
                }

                // Try to extract flight number from flight info
                String extractedFlightNumber = extractFlightNumber(flightInfo);
                if (extractedFlightNumber != null && !extractedFlightNumber.isEmpty()) {
                    message.setFlightNumber(extractedFlightNumber);
                }

            } else {
                setDefaultPartInfo(message, "UNKNOWN", "UNKNOWN");
            }
        } catch (Exception e) {
            System.err.println("Error parsing UNH: " + e.getMessage());
            setDefaultPartInfo(message, "ERROR", "ERROR");
        }
    }

    /**
     * Set default part information when parsing fails
     */
    private void setDefaultPartInfo(EdifactMessage message, String messageRef, String flightInfo) {
        message.setPartNumber(1);
        message.setPartIndicator("C");
        String uniqueMessageId = messageRef + "_" + flightInfo + "_P1C_" + System.nanoTime();
        message.setMessageId(uniqueMessageId);
    }

    /**
     * Normalize flight numbers to handle padding differences between input and output files.
     * Input files may have MS775 while output files have MS0775.
     * This method creates both variants for proper matching.
     */
    private boolean isFlightNumberMatch(String messageFlightNumber, String targetFlightNumber) {
        if (messageFlightNumber == null || targetFlightNumber == null) {
            return false;
        }

        String msgFlight = messageFlightNumber.toUpperCase().trim();
        String targetFlight = targetFlightNumber.toUpperCase().trim();

        // Direct match
        if (msgFlight.equals(targetFlight)) {
            return true;
        }

        // Extract airline code and number parts
        String msgAirlineCode = extractAirlineCode(msgFlight);
        String msgNumber = extractFlightNumber(msgFlight);
        String targetAirlineCode = extractAirlineCode(targetFlight);
        String targetNumber = extractFlightNumber(targetFlight);

        // Must have same airline code
        if (!msgAirlineCode.equals(targetAirlineCode)) {
            return false;
        }

        // Handle flight number padding:
        // - Remove leading zeros from both for comparison
        // - Also try adding leading zero to shorter number
        String msgNumberNormalized = msgNumber.replaceFirst("^0+", "");
        String targetNumberNormalized = targetNumber.replaceFirst("^0+", "");

        if (msgNumberNormalized.equals(targetNumberNormalized)) {
            return true;
        }

        // Try padding variations (ensure 4-digit format for output files)
        String msgPadded = msgAirlineCode + String.format("%04d", Integer.parseInt(msgNumberNormalized.isEmpty() ? "0" : msgNumberNormalized));
        String targetPadded = targetAirlineCode + String.format("%04d", Integer.parseInt(targetNumberNormalized.isEmpty() ? "0" : targetNumberNormalized));

        boolean paddedMatch = msgPadded.equals(targetPadded);

        return paddedMatch;
    }

    private String extractAirlineCode(String flightNumber) {
        if (flightNumber == null || flightNumber.length() < 2) {
            return "";
        }
        // Extract leading alphabetic characters
        StringBuilder airline = new StringBuilder();
        for (char c : flightNumber.toCharArray()) {
            if (Character.isLetter(c)) {
                airline.append(c);
            } else {
                break;
            }
        }
        return airline.toString();
    }

    private String extractFlightNumber(String flightNumber) {
        if (flightNumber == null) {
            return "";
        }
        // Extract trailing numeric characters
        StringBuilder number = new StringBuilder();
        boolean foundDigit = false;
        for (char c : flightNumber.toCharArray()) {
            if (Character.isDigit(c)) {
                number.append(c);
                foundDigit = true;
            } else if (foundDigit) {
                break; // Stop at first non-digit after finding digits
            }
        }
        return number.toString();
    }

    /**
     * Parse flight details from EDIFACT segments (TDT, LOC, DTM, BGM) using configuration
     */
    private void parseFlightDetails(String segment, EdifactMessage message) {
        // ENHANCED FIX: Use robust carriage return cleaning method
        segment = cleanCarriageReturns(segment);

        // Ensure the segment ends with terminator for consistent parsing
        if (!segment.endsWith(String.valueOf(terminatorSeparator))) {
            segment = segment + terminatorSeparator;
        }

        if (message.getFlightDetails() == null) {
            message.setFlightDetails(new FlightDetails());
        }

        FlightDetails details = message.getFlightDetails();
        ApiPatternConfig.SegmentCodes codes = advancedConfig.getApiConfig().getSegmentCodes();

        try {
            // Parse BGM segment for data type using configurable codes
            if (segment.startsWith("BGM" + elementSeparator)) {
                String[] parts = segment.split(Pattern.quote(String.valueOf(elementSeparator)));
                if (parts.length >= 2) {
                    String bgmCode = parts[1].replace(String.valueOf(terminatorSeparator), "").trim();
                    if (codes.getBgmPassengerCode().equals(bgmCode)) {
                        message.setDataType("PASSENGER");
                        details.setPassengerData(true);
                    }
                    else if (codes.getBgmCrewCode().equals(bgmCode)) {
                        message.setDataType("CREW");
                        details.setPassengerData(false);
                    }
                }
            }

            // Parse TDT segment for flight number using configurable position
            else if (segment.startsWith("TDT" + elementSeparator)) {
                String[] parts = segment.split(Pattern.quote(String.valueOf(elementSeparator)));
                int flightPos = codes.getTdtFlightPosition();
                if (parts.length > flightPos) {
                    String flightNumber = parts[flightPos].replace(String.valueOf(terminatorSeparator), "").trim();
                    details.setFlightNumber(flightNumber);
                    message.setFlightNumber(flightNumber); // Also set on message directly
                }
            }

            // Parse LOC segments for airports using configurable codes
            else if (segment.startsWith("LOC" + elementSeparator)) {
                String[] parts = segment.split(Pattern.quote(String.valueOf(elementSeparator)));
                if (parts.length >= 3) {
                    String locType = parts[1];
                    String airport = parts[2].replace(String.valueOf(terminatorSeparator), "").trim();

                    if (codes.getLocDepartureCode().equals(locType) && details.getDepartureAirport()==null) {
                        // Departure airport
                        details.setDepartureAirport(airport);
                    }
                    else if (codes.getLocArrivalCode().equals(locType) && details.getArrivalAirport()==null) {
                        // Arrival airport
                        details.setArrivalAirport(airport);
                    }
                }
            }

            // Parse DTM segments for dates and times using configurable codes
            else if (segment.startsWith("DTM" + elementSeparator)) {
                String[] parts = segment.split(Pattern.quote(String.valueOf(elementSeparator)));
                if (parts.length >= 2) {
                    String dtmInfo = parts[1];
                    String[] dtmParts = dtmInfo.split(Pattern.quote(String.valueOf(subElementSeparator)));

                    if (dtmParts.length >= 2) {
                        String dtmType = dtmParts[0];
                        String dateTime = dtmParts[1].replace(String.valueOf(terminatorSeparator), "").trim();

                        if (codes.getDtmDepartureCode().equals(dtmType) && details.getDepartureDate()==null && details.getDepartureTime()==null) {
                            // Departure date/time: format YYMMDDHHMM
                            if (dateTime.length() >= 8) {
                                String date = dateTime.substring(0, 6); // YYMMDD
                                String time = dateTime.length() >= 10 ? dateTime.substring(6, 10) : ""; // HHMM
                                details.setDepartureDate(date);
                                details.setDepartureTime(time);
                            }
                        }
                        else if (codes.getDtmArrivalCode().equals(dtmType) && details.getArrivalDate()==null && details.getArrivalTime()==null) {
                            // Arrival date/time: format YYMMDDHHMM
                            if (dateTime.length() >= 8) {
                                String date = dateTime.substring(0, 6); // YYMMDD
                                String time = dateTime.length() >= 10 ? dateTime.substring(6, 10) : ""; // HHMM
                                details.setArrivalDate(date);
                                details.setArrivalTime(time);
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error parsing flight details from segment '" + segment + "': " + e.getMessage());
        }
    }

    private boolean matchesFlightCriteria(EdifactMessage message, String targetFlightNumber) {
        if (targetFlightNumber == null || targetFlightNumber.trim().isEmpty()) {
            return true; // No filter specified
        }

        String messageFlightNumber = message.getFlightNumber();
        if (messageFlightNumber == null) {
            FlightDetails details = message.getFlightDetails();
            if (details != null) {
                messageFlightNumber = details.getFlightNumber();
            }
        }

        // Use enhanced flight number matching that handles padding
        boolean matches = isFlightNumberMatch(messageFlightNumber, targetFlightNumber);

        return matches;
    }

    /**
     * Robust cleaning method to remove carriage returns and control characters
     * Handles different encodings and representations of \r characters
     */
    private String cleanCarriageReturns(String input) {
        if (input == null) {
            return null;
        }

        // Multiple approaches to ensure complete \r removal
        String cleaned = input;
        cleaned = cleaned.replace("\\r", ""); // Direct replacement first
        cleaned = cleaned.replace("\n", ""); // Direct newline replacement
        cleaned = cleaned.replace("\t", ""); // Direct tab replacement
        cleaned = cleaned.trim(); // Final trim

        return cleaned;
    }

    /**
     * Get the current advanced parser configuration
     */
    public AdvancedParserConfig getAdvancedConfig() {
        return advancedConfig;
    }

    /**
     * Update the advanced parser configuration
     */
    public void setAdvancedConfig(AdvancedParserConfig config) {
        this.advancedConfig = config != null ? config : new AdvancedParserConfig();
    }
}

