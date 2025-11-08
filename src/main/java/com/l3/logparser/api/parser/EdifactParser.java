package com.l3.logparser.api.parser;

import com.l3.logparser.api.model.EdifactMessage;
import com.l3.logparser.api.model.FlightDetails;

import java.util.*;
import java.util.function.Consumer;

/**
 * Parser for EDIFACT messages found in log files
 * Handles UNA separators and extracts flight information
 */
public class EdifactParser {

    private static final String UNA_PATTERN = "UNA:(.)(.)(.)(.)(.)(.)";
    private static final String UNH_PATTERN = "UNH(.)(\\d+)(.)(PAXLST:D:05B:UN:IATA(.)(\\w+)(.)(.+?)(.)";

    private char subElementSeparator;
    private char elementSeparator;
    private char decimalSeparator;
    private char releaseIndicator;
    private char reservedSeparator;
    private char terminatorSeparator;

    /**
     * Constructor - initialize with default EDIFACT separators
     */
    public EdifactParser() {
        setDefaultSeparators();
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

    /**
     * Extract EDIFACT message from log content
     */
    public List<EdifactMessage> parseLogContent(String logContent, String targetFlightNumber) {
        return parseLogContent(logContent, targetFlightNumber, false, null);
    }

    public List<EdifactMessage> parseLogContent(String logContent, String targetFlightNumber, boolean debugMode, Consumer<String> debugLogger) {
        List<EdifactMessage> messages = new ArrayList<>();
        String[] lines = logContent.split("\\r?\\n");

        boolean inMessage = false;
        StringBuilder currentMessage = new StringBuilder();
        EdifactMessage currentEdifactMessage = null;
        int lineNumber = 0;
        int foundMessages = 0;

        // Store UNA segment for current message being processed
        String currentUnaSegment = null;

        for (String line : lines) {
            lineNumber++;
            // ENHANCED FIX: Use robust carriage return cleaning method
            line = cleanCarriageReturns(line);

            // Check for start of EDIFACT message with $STX$UNA
            if (line.contains("$STX$UNA")) {
                if (debugMode && debugLogger != null) {
                    int unaIndex = line.indexOf("$STX$UNA");
                    String debugLine = line.substring(unaIndex);
                    debugLogger.accept("[Line " + lineNumber + "] Found potential message start with $STX$UNA: " + debugLine.substring(0, Math.min(debugLine.length(), 200)));
                }

                // Save previous message if exists
                if (currentEdifactMessage != null && currentMessage.length() > 0) {
                    finalizeMessageWithUNA(currentMessage, currentEdifactMessage, currentUnaSegment, messages, targetFlightNumber);
                    foundMessages++;
                }

                // Start new message
                String unaLine = line.substring(line.indexOf("$STX$") + 5);

                // ENHANCED FIX: Use robust carriage return cleaning method
                unaLine = cleanCarriageReturns(unaLine);

                boolean unaParseSuccess = parseUNA(unaLine);

                // Store UNA segment for this message
                if (unaLine.startsWith("UNA") && unaLine.length() >= 9) {
                    currentUnaSegment = unaLine.substring(0, 9);
                }

                currentMessage = new StringBuilder();
                currentEdifactMessage = new EdifactMessage();
                inMessage = true;

                // Process the embedded EDIFACT message within the UNA line ONLY if there's substantial content
                // Check for substantial EDIFACT content (more than just terminators and whitespace)
                String edifactContent = unaLine.length() >= 9 ? unaLine.substring(9).trim() : "";
                if (unaParseSuccess && edifactContent.length() > 10 &&
                    (edifactContent.contains("UNB") || edifactContent.contains("UNH") || edifactContent.contains("TDT"))) {
                    processEmbeddedEdifactMessage(unaLine, currentMessage, currentEdifactMessage, messages, targetFlightNumber);
                    // Reset after processing embedded message
                    currentMessage = new StringBuilder();
                    currentEdifactMessage = null;
                    currentUnaSegment = null;
                    inMessage = false;
                } else {
                    // If not processing as embedded, add the UNA line to buffer for multi-line processing
                    currentMessage.append(unaLine).append("\n");
                }
            }
            // Check for start of EDIFACT message with $STX$UNB (no UNA header, use default separators)
            else if (line.contains("$STX$UNB")) {
                if (debugMode && debugLogger != null) {
                    int unbIndex = line.indexOf("$STX$UNB");
                    String debugLine = line.substring(unbIndex);
                    debugLogger.accept("[Line " + lineNumber + "] Found potential message start with $STX$UNB: " + debugLine.substring(0, Math.min(debugLine.length(), 200)));
                }

                // Save previous message if exists
                if (currentEdifactMessage != null && currentMessage.length() > 0) {
                    finalizeMessageWithUNA(currentMessage, currentEdifactMessage, currentUnaSegment, messages, targetFlightNumber);
                    foundMessages++;
                }

                // Start new message with default separators (no UNA segment)
                String unbLine = line.substring(line.indexOf("$STX$") + 5);

                // ENHANCED FIX: Use robust carriage return cleaning method
                unbLine = cleanCarriageReturns(unbLine);

                setDefaultSeparators(); // Use default EDIFACT separators for UNB messages

                currentMessage = new StringBuilder();
                currentEdifactMessage = new EdifactMessage();
                currentUnaSegment = null; // No UNA segment for UNB messages
                inMessage = true;

                // Process the embedded EDIFACT message within the UNB line ONLY if there's substantial content
                // Check for substantial EDIFACT content (more than just terminators and whitespace)
                String edifactContent = unbLine.trim();
                if (edifactContent.length() > 10 &&
                    (edifactContent.contains("UNB") || edifactContent.contains("UNH") || edifactContent.contains("TDT"))) {
                    processEmbeddedEdifactMessage(unbLine, currentMessage, currentEdifactMessage, messages, targetFlightNumber);
                    // Reset after processing embedded message
                    currentMessage = new StringBuilder();
                    currentEdifactMessage = null;
                    currentUnaSegment = null;
                    inMessage = false;
                } else {
                    // If not processing as embedded, add the UNB line to buffer for multi-line processing
                    currentMessage.append(unbLine).append("\n");
                }
            }
            // Check for MessageForwarder INFO logs containing EDIFACT messages (with UNA headers)
            else if (line.contains("INFO ") && line.contains("Forward.BUSINESS_RULES_PROCESSOR") &&
                    line.contains("Message body [UNA")) {

                // Save previous message if exists
                if (currentEdifactMessage != null && currentMessage.length() > 0) {
                    finalizeMessageWithUNA(currentMessage, currentEdifactMessage, currentUnaSegment, messages, targetFlightNumber);
                    foundMessages++;
                }

                // Extract EDIFACT content from MessageForwarder log
                int messageBodyStart = line.indexOf("Message body [");
                if (messageBodyStart != -1) {
                    String edifactContent = line.substring(messageBodyStart + "Message body [".length());
                    if (edifactContent.endsWith("]")) {
                        edifactContent = edifactContent.substring(0, edifactContent.length() - 1);
                    }

                    // ENHANCED FIX: Clean carriage returns from MessageForwarder content
                    edifactContent = cleanCarriageReturns(edifactContent);

                    // Extract UNA segment from MessageForwarder content
                    if (edifactContent.startsWith("UNA") && edifactContent.length() >= 9) {
                        currentUnaSegment = edifactContent.substring(0, 9);
                    }

                    currentMessage = new StringBuilder();
                    currentEdifactMessage = new EdifactMessage();
                    currentEdifactMessage.setMessageType("OUTPUT");
                    inMessage = true;

                    // Process the MessageForwarder EDIFACT content
                    processMessageForwarderEdifact(edifactContent, currentMessage, currentEdifactMessage, messages, targetFlightNumber,false,null);
                    // Reset after processing MessageForwarder message
                    currentMessage = new StringBuilder();
                    currentEdifactMessage = null;
                    currentUnaSegment = null;
                    inMessage = false;
                }
            }
            // Check for MessageForwarder INFO logs containing EDIFACT messages (with UNB headers - no UNA)
            else if (line.contains("INFO ") && line.contains("Forward.BUSINESS_RULES_PROCESSOR") &&
                    line.contains("Message body [UNB")) {

                // Save previous message if exists
                if (currentEdifactMessage != null && currentMessage.length() > 0) {
                    finalizeMessageWithUNA(currentMessage, currentEdifactMessage, currentUnaSegment, messages, targetFlightNumber);
                    foundMessages++;
                }

                // Extract EDIFACT content from MessageForwarder log
                int messageBodyStart = line.indexOf("Message body [");
                if (messageBodyStart != -1) {
                    String edifactContent = line.substring(messageBodyStart + "Message body [".length());
                    if (edifactContent.endsWith("]")) {
                        edifactContent = edifactContent.substring(0, edifactContent.length() - 1);
                    }

                    // ENHANCED FIX: Clean carriage returns from MessageForwarder content
                    edifactContent = cleanCarriageReturns(edifactContent);

                    // Use default separators for UNB messages (no UNA header)
                    setDefaultSeparators();

                    currentMessage = new StringBuilder();
                    currentEdifactMessage = new EdifactMessage();
                    currentEdifactMessage.setMessageType("OUTPUT");
                    currentUnaSegment = null; // No UNA segment for UNB messages
                    inMessage = true;

                    // Process the MessageForwarder EDIFACT content
                    processMessageForwarderEdifact(edifactContent, currentMessage, currentEdifactMessage, messages, targetFlightNumber,false,null);
                    // Reset after processing MessageForwarder message
                    currentMessage = new StringBuilder();
                    currentEdifactMessage = null;
                    currentUnaSegment = null;
                    inMessage = false;
                }
            }
            // Check for WARN logs with "Failed to parse API message" containing EDIFACT with UNA headers
            else if (line.contains("Failed to parse API message") && line.contains("[UNA")) {

                // Save previous message if exists
                if (currentEdifactMessage != null && currentMessage.length() > 0) {
                    finalizeMessageWithUNA(currentMessage, currentEdifactMessage, currentUnaSegment, messages, targetFlightNumber);
                    foundMessages++;
                }

                // Extract UNA part from the log message
                int unaStartIndex = line.indexOf("[UNA");
                if (unaStartIndex != -1) {
                    String unaLine = line.substring(unaStartIndex + 1);
                    // Remove closing bracket if present
                    if (unaLine.endsWith("]")) {
                        unaLine = unaLine.substring(0, unaLine.length() - 1);
                    }

                    // ENHANCED FIX: Use robust carriage return cleaning method
                    unaLine = cleanCarriageReturns(unaLine);

                    boolean unaParseSuccess = parseUNA(unaLine);

                    // Store UNA segment for this message
                    if (unaLine.startsWith("UNA") && unaLine.length() >= 9) {
                        currentUnaSegment = unaLine.substring(0, 9);
                    }

                    currentMessage = new StringBuilder();
                    currentEdifactMessage = new EdifactMessage();
                    inMessage = true;

                    // Process embedded message only if there's substantial EDIFACT content
                    String edifactContent = unaLine.length() >= 9 ? unaLine.substring(9).trim() : "";
                    if (unaParseSuccess && edifactContent.length() > 10 &&
                        (edifactContent.contains("UNB") || edifactContent.contains("UNH") || edifactContent.contains("TDT"))) {
                        processEmbeddedEdifactMessage(unaLine, currentMessage, currentEdifactMessage, messages, targetFlightNumber);
                        // Reset after processing embedded message
                        currentMessage = new StringBuilder();
                        currentEdifactMessage = null;
                        currentUnaSegment = null;
                        inMessage = false;
                    } else {
                        // If not processing as embedded, add the UNA line to buffer for multi-line processing
                        currentMessage.append(unaLine).append("\n");
                    }
                }
            }
            // Check for WARN logs with "Failed to parse API message" containing EDIFACT with UNB headers (no UNA)
            else if (line.contains("Failed to parse API message") && line.contains("[UNB")) {

                // Save previous message if exists
                if (currentEdifactMessage != null && currentMessage.length() > 0) {
                    finalizeMessageWithUNA(currentMessage, currentEdifactMessage, currentUnaSegment, messages, targetFlightNumber);
                    foundMessages++;
                }

                // Extract UNB part from the log message
                int unbStartIndex = line.indexOf("[UNB");
                if (unbStartIndex != -1) {
                    String unbLine = line.substring(unbStartIndex + 1);
                    // Remove closing bracket if present
                    if (unbLine.endsWith("]")) {
                        unbLine = unbLine.substring(0, unbLine.length() - 1);
                    }

                    // ENHANCED FIX: Use robust carriage return cleaning method
                    unbLine = cleanCarriageReturns(unbLine);

                    // Use default separators for UNB messages
                    setDefaultSeparators();

                    currentMessage = new StringBuilder();
                    currentEdifactMessage = new EdifactMessage();
                    currentUnaSegment = null; // No UNA segment for UNB messages
                    inMessage = true;

                    // Process embedded message only if there's substantial EDIFACT content
                    String edifactContent = unbLine.trim();
                    if (edifactContent.length() > 10 &&
                        (edifactContent.contains("UNB") || edifactContent.contains("UNH") || edifactContent.contains("TDT"))) {
                        processEmbeddedEdifactMessage(unbLine, currentMessage, currentEdifactMessage, messages, targetFlightNumber);
                        // Reset after processing embedded message
                        currentMessage = new StringBuilder();
                        currentEdifactMessage = null;
                        currentUnaSegment = null;
                        inMessage = false;
                    } else {
                        // If not processing as embedded, add the UNB line to buffer for multi-line processing
                        currentMessage.append(unbLine).append("\n");
                    }
                }
            }
            // Check for WARN logs with "Failed to parse API message" starting a multi-line block
            else if (line.contains("Failed to parse API message") && line.contains("[")) {
                // Save previous message if exists
                if (currentEdifactMessage != null && currentMessage.length() > 0) {
                    finalizeMessageWithUNA(currentMessage, currentEdifactMessage, currentUnaSegment, messages, targetFlightNumber);
                    foundMessages++;
                }

                currentMessage = new StringBuilder();
                currentEdifactMessage = new EdifactMessage();
                currentUnaSegment = null; // Reset UNA segment
                inMessage = true;

                // Extract and include UNA segment from the warning line
                String contentAfterBracket = "";
                int bracketIndex = line.indexOf("[");
                if (bracketIndex != -1 && bracketIndex < line.length() - 1) {
                    contentAfterBracket = line.substring(bracketIndex + 1).trim();
                    if (!contentAfterBracket.isEmpty()) {
                        // Check if this line contains UNA segment
                        if (contentAfterBracket.startsWith("UNA")) {
                            if (debugMode && debugLogger != null) {
                                debugLogger.accept("Found UNA segment: " + contentAfterBracket.substring(0, Math.min(contentAfterBracket.length(), 200)));
                            }
                            // Parse UNA to get separators and store the segment
                            parseUNA(contentAfterBracket);
                            if (contentAfterBracket.length() >= 9) {
                                currentUnaSegment = contentAfterBracket.substring(0, 9);
                            }
                            // Store the UNA segment at the beginning
                            currentMessage.append(contentAfterBracket).append("\n");
                        } else {
                            // Non-UNA content, add it normally
                            currentMessage.append(contentAfterBracket).append("\n");
                        }
                    }
                }
            }
            // Check for standalone UNA line
            else if (line.startsWith("UNA") && !inMessage) {
                if (debugMode && debugLogger != null) {
                    debugLogger.accept("[Line " + lineNumber + "] Found standalone UNA segment: " + line.substring(0, Math.min(line.length(), 200)));
                }
                boolean unaParseSuccess = parseUNA(line);

                if (unaParseSuccess) {
                    currentMessage = new StringBuilder();
                    currentEdifactMessage = new EdifactMessage();
                    // Store UNA segment
                    if (line.length() >= 9) {
                        currentUnaSegment = line.substring(0, 9);
                    }
                    inMessage = true;
                    currentMessage.append(line).append("\n");
                }
            } else if (inMessage && (line.startsWith("UN") || line.contains(String.valueOf(elementSeparator)))) {
                // ENHANCED FIX: Use robust carriage return cleaning method
                String cleanedLine = cleanCarriageReturns(line);

                // This looks like an EDIFACT segment
                currentMessage.append(cleanedLine).append("\n");

                // Parse UNH segment for message details
                if (cleanedLine.startsWith("UNH" + elementSeparator)) {
                    parseUNH(cleanedLine, currentEdifactMessage);
                }

                // Parse flight details from various segments
                if (currentEdifactMessage != null) {
                    parseFlightDetails(cleanedLine, currentEdifactMessage);
                }

                // Check for end of message (UNZ segment typically ends EDIFACT)
                if (cleanedLine.startsWith("UNZ" + elementSeparator)) {
                    inMessage = false;
                    // Use finalizeMessageWithUNA instead of direct processing
                    finalizeMessageWithUNA(currentMessage, currentEdifactMessage, currentUnaSegment, messages, targetFlightNumber);
                    foundMessages++;
                    currentEdifactMessage = null;
                    currentMessage = new StringBuilder();
                    currentUnaSegment = null;
                }
            } else if (inMessage && line.isEmpty()) {
                // Empty line might indicate end of message
                inMessage = false;
                if (currentEdifactMessage != null && currentMessage.length() > 0) {
                    finalizeMessageWithUNA(currentMessage, currentEdifactMessage, currentUnaSegment, messages, targetFlightNumber);
                    foundMessages++;
                    currentEdifactMessage = null;
                    currentMessage = new StringBuilder();
                    currentUnaSegment = null;
                }
            }
        }

        // Handle last message if file doesn't end properly
        if (currentEdifactMessage != null && currentMessage.length() > 0) {
            finalizeMessageWithUNA(currentMessage, currentEdifactMessage, currentUnaSegment, messages, targetFlightNumber);
            foundMessages++;
        }

        if (debugMode && debugLogger != null) {
            debugLogger.accept("Finished parsing log content. Total messages found: " + messages.size());
        }
        return messages;
    }

    /**
     * Process embedded EDIFACT message from UNA line
     */
    private void processEmbeddedEdifactMessage(String unaLine, StringBuilder currentMessage,
                                               EdifactMessage currentEdifactMessage,
                                               List<EdifactMessage> messages,
                                               String targetFlightNumber) {

        // STORE THE COMPLETE UNA SEGMENT FOR LATER USE
        String unaSegment = null;
        if (unaLine.startsWith("UNA") && unaLine.length() >= 9) {
            // Extract the complete UNA segment (UNA + 6 separators)
            unaSegment = unaLine.substring(0, 9);
        }

        // Extract the EDIFACT content after the UNA header (UNA + 6 separators = 9 chars)
        String edifactContent = unaLine.length() >= 9 ? unaLine.substring(9) : "";

        // ENHANCED FIX: Use robust carriage return cleaning method
        edifactContent = cleanCarriageReturns(edifactContent);

        // Split the EDIFACT content by the terminator separator to get individual segments
        String[] segments = edifactContent.split("\\" + terminatorSeparator);

        for (String segment : segments) {
            // ENHANCED FIX: Use robust carriage return cleaning method
            segment = cleanCarriageReturns(segment);
            if (!segment.isEmpty()) {
                currentMessage.append(segment).append(terminatorSeparator).append("\n");

                // Parse different segment types
                if (segment.startsWith("UNH" + elementSeparator)) {
                    parseUNH(segment + terminatorSeparator, currentEdifactMessage);
                }

                if (currentEdifactMessage != null) {
                    parseFlightDetails(segment + terminatorSeparator, currentEdifactMessage);
                }

                // Check for end of message
                if (segment.startsWith("UNZ" + elementSeparator)) {
                    // FINALIZE MESSAGE WITH UNA SEGMENT CHECK
                    finalizeMessageWithUNA(currentMessage, currentEdifactMessage, unaSegment, messages, targetFlightNumber);
                    return; // Exit after finalizing
                }
            }
        }

        // If we didn't find UNZ, end the message anyway for single-line embedded messages
        if (currentEdifactMessage != null && !currentMessage.toString().isEmpty()) {
            boolean messageAlreadyAdded = false;
            for (EdifactMessage msg : messages) {
                if (msg.getMessageId() != null && msg.getMessageId().equals(currentEdifactMessage.getMessageId())) {
                    messageAlreadyAdded = true;
                    break;
                }
            }

            if (!messageAlreadyAdded) {
                // FINALIZE MESSAGE WITH UNA SEGMENT CHECK
                finalizeMessageWithUNA(currentMessage, currentEdifactMessage, unaSegment, messages, targetFlightNumber);
            }
        }
    }

    /**
     * Finalize message by ensuring UNA segment is at the beginning
     */
    private void finalizeMessageWithUNA(StringBuilder currentMessage, EdifactMessage currentEdifactMessage,
                                       String unaSegment, List<EdifactMessage> messages, String targetFlightNumber) {
        if (currentEdifactMessage == null || currentMessage.length() == 0) {
            return;
        }

        String messageContent = currentMessage.toString();

        // Check if message starts with UNA
        if (!messageContent.trim().startsWith("UNA")) {
            // If we have a stored UNA segment, prepend it
            if (unaSegment != null && !unaSegment.isEmpty()) {
                messageContent = unaSegment + "\n" + messageContent;
            } else {
                // Fallback: construct UNA segment from current separators
                String constructedUNA = "UNA" + subElementSeparator + elementSeparator + decimalSeparator +
                                       releaseIndicator + reservedSeparator + terminatorSeparator;
                messageContent = constructedUNA + "\n" + messageContent;
            }
        }

        currentEdifactMessage.setRawContent(messageContent);
        if (matchesFlightCriteria(currentEdifactMessage, targetFlightNumber)) {
            messages.add(currentEdifactMessage);
        }
    }

    /**
     * Parse UNH segment to extract message ID, flight number, and part information
     */
    private void parseUNH(String line, EdifactMessage message) {
        // ENHANCED FIX: Use robust carriage return cleaning method
        line = cleanCarriageReturns(line);

        try {
            // Split by element separator
            String[] segments = line.split("\\" + elementSeparator);

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
     * Parse flight details from EDIFACT segments (TDT, LOC, DTM, BGM)
     */
    private void parseFlightDetails(String segment, EdifactMessage message) {
        // ENHANCED FIX: Use robust carriage return cleaning method
        segment = cleanCarriageReturns(segment);

        if (message.getFlightDetails() == null) {
            message.setFlightDetails(new FlightDetails());
        }

        FlightDetails details = message.getFlightDetails();

        try {
            // Parse BGM segment for data type (745 = passenger, 250 = crew)
            if (segment.startsWith("BGM" + elementSeparator)) {
                String[] parts = segment.split("\\" + elementSeparator);
                if (parts.length >= 2) {
                    String bgmCode = parts[1].replace(String.valueOf(terminatorSeparator), "").trim();
                    if ("745".equals(bgmCode)) {
                        message.setDataType("PASSENGER");
                        details.setPassengerData(true);
                    }
                    else if ("250".equals(bgmCode)) {
                        message.setDataType("CREW");
                        details.setPassengerData(false);
                    }
                }
            }

            // Parse TDT segment for flight number
            else if (segment.startsWith("TDT" + elementSeparator)) {
                String[] parts = segment.split("\\" + elementSeparator);
                if (parts.length >= 3) {
                    String flightNumber = parts[2].replace(String.valueOf(terminatorSeparator), "").trim();
                    details.setFlightNumber(flightNumber);
                    message.setFlightNumber(flightNumber); // Also set on message directly
                }
            }

            // Parse LOC segments for airports
            else if (segment.startsWith("LOC" + elementSeparator)) {
                String[] parts = segment.split("\\" + elementSeparator);
                if (parts.length >= 3) {
                    String locType = parts[1];
                    String airport = parts[2].replace(String.valueOf(terminatorSeparator), "").trim();

                    if ("125".equals(locType) && details.getDepartureAirport()==null) {
                        // Departure airport
                        details.setDepartureAirport(airport);
                    }
                    else if ("87".equals(locType) && details.getArrivalAirport()==null) {
                        // Arrival airport
                        details.setArrivalAirport(airport);
                    }
                }
            }

            // Parse DTM segments for dates and times
            else if (segment.startsWith("DTM" + elementSeparator)) {
                String[] parts = segment.split("\\" + elementSeparator);
                if (parts.length >= 2) {
                    String dtmInfo = parts[1];
                    String[] dtmParts = dtmInfo.split(String.valueOf(subElementSeparator));

                    if (dtmParts.length >= 2) {
                        String dtmType = dtmParts[0];
                        String dateTime = dtmParts[1].replace(String.valueOf(terminatorSeparator), "").trim();

                        if ("189".equals(dtmType) && details.getDepartureDate()==null && details.getDepartureTime()==null) {
                            // Departure date/time: format YYMMDDHHMM
                            if (dateTime.length() >= 8) {
                                String date = dateTime.substring(0, 6); // YYMMDD
                                String time = dateTime.length() >= 10 ? dateTime.substring(6, 10) : ""; // HHMM
                                details.setDepartureDate(date);
                                details.setDepartureTime(time);
                            }
                        }
                        else if ("232".equals(dtmType) && details.getArrivalDate()==null && details.getArrivalTime()==null) {
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
     * Process EDIFACT content from MessageForwarder logs
     * MessageForwarder logs contain EDIFACT in a different format with newlines as literal "\n"
     */
    private void processMessageForwarderEdifact(String edifactContent, StringBuilder currentMessage,
                                                EdifactMessage currentEdifactMessage,
                                                List<EdifactMessage> messages,
                                                String targetFlightNumber,
                                                boolean debugMode, Consumer<String> debugLogger) {
        // Extract UNA segment from MessageForwarder content for preservation
        String unaSegment = null;
        if (edifactContent.startsWith("UNA") && edifactContent.length() >= 9) {
            unaSegment = edifactContent.substring(0, 9);
        }

        // MessageForwarder logs often have literal \n characters instead of actual newlines
        // Replace them with actual newlines for proper parsing
        String normalizedContent = edifactContent.replace("\\n", "\n");

        // ENHANCED FIX: Use robust carriage return cleaning method
        normalizedContent = cleanCarriageReturns(normalizedContent);

        // ENHANCED: Also handle cases where the entire message is on a single line
        // Split by common EDIFACT segment terminators first
        if (!normalizedContent.contains("\n")) {
            // Single line - split by terminator separator to create segments
            String[] potentialSegments = normalizedContent.split("\\" + terminatorSeparator);
            StringBuilder rebuiltContent = new StringBuilder();
            for (String segment : potentialSegments) {
                if (!segment.trim().isEmpty()) {
                    rebuiltContent.append(segment.trim()).append(terminatorSeparator).append("\n");
                }
            }
            normalizedContent = rebuiltContent.toString();
        }

        // Split into lines/segments for processing
        String[] segments = normalizedContent.split("\\r?\\n");

        boolean foundUNA = false;
        boolean foundUNH = false;
        boolean foundTDT = false;

        for (String segment : segments) {
            // ENHANCED FIX: Use robust carriage return cleaning method
            segment = cleanCarriageReturns(segment);
            if (!segment.isEmpty()) {
                currentMessage.append(segment);
                if (!segment.endsWith(String.valueOf(terminatorSeparator))) {
                    currentMessage.append(terminatorSeparator);
                }
                currentMessage.append("\n");


                // Parse UNA header to get separators
                if (segment.startsWith("UNA")) {
                    parseUNA(segment);
                    foundUNA = true;
                }

                // Parse UNH segment for message details
                else if (segment.startsWith("UNH" + elementSeparator)) {
                    parseUNH(segment + terminatorSeparator, currentEdifactMessage);
                    foundUNH = true;
                }

                // Parse flight details from various segments
                if (currentEdifactMessage != null) {
                    parseFlightDetails(segment + terminatorSeparator, currentEdifactMessage);

                    // Track important segments
                    if (segment.startsWith("TDT" + elementSeparator)) {
                        foundTDT = true;
                    }
                }

                // Check for end of message (UNZ segment typically ends EDIFACT)
                if (segment.startsWith("UNZ" + elementSeparator)) {
                    break; // Stop processing segments after UNZ
                }
            }
        }

        // CRITICAL: Set message type as OUTPUT for MessageForwarder messages
        if (currentEdifactMessage != null) {
            currentEdifactMessage.setMessageType("OUTPUT");
        }

        // Complete the MessageForwarder message processing using finalizeMessageWithUNA
        if (currentEdifactMessage != null && currentMessage.length() > 0) {
            // Use the same UNA preservation logic as other methods
            finalizeMessageWithUNA(currentMessage, currentEdifactMessage, unaSegment, messages, targetFlightNumber);
        } else {
            System.out.println("MessageForwarder message processing failed - no message or content");
        }
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
}
