package com.l3.logparser.parser;

import com.l3.logparser.model.EdifactMessage;
import com.l3.logparser.model.FlightDetails;

import java.util.*;

/**
 * Parser for EDIFACT messages found in log files
 * Handles UNA separators and extracts flight information
 */
public class EdifactParser {

    private static final String UNA_PATTERN = "UNA:(.)(.)(.)(.)(.)(.)";
    private static final String UNH_PATTERN = "UNH(.)(\\d+)(.)(PAXLST:D:05B:UN:IATA(.)(\\w+)(.)(.+?)(.)";

    private char subElementSeparator = ':';
    private char elementSeparator = '(';
    private char decimalSeparator = '.';
    private char releaseIndicator = ')';
    private char reservedSeparator = ' ';
    private char terminatorSeparator = '-';

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
                }
            }

            // Use defaults if extraction fails
            subElementSeparator = ':';
            elementSeparator = '+';
            decimalSeparator = '.';
            releaseIndicator = '?';
            reservedSeparator = ' ';
            terminatorSeparator = '\'';
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
        subElementSeparator = ':';
        elementSeparator = '+';
        decimalSeparator = '.';
        releaseIndicator = '?';
        reservedSeparator = ' ';
        terminatorSeparator = '\'';

        return true; // Always return true so processing can continue
    }

    /**
     * Extract EDIFACT message from log content
     */
    public List<EdifactMessage> parseLogContent(String logContent, String targetFlightNumber) {
        List<EdifactMessage> messages = new ArrayList<>();
        String[] lines = logContent.split("\\r?\\n");

        System.out.println("=== EDIFACT PARSER ===");
        System.out.println("Processing " + lines.length + " lines for flight: " + targetFlightNumber);

        boolean inMessage = false;
        StringBuilder currentMessage = new StringBuilder();
        EdifactMessage currentEdifactMessage = null;
        int lineNumber = 0;
        int foundMessages = 0;

        // Store UNA segment for current message being processed
        String currentUnaSegment = null;

        for (String line : lines) {
            lineNumber++;
            line = line.trim();

            // Check for start of EDIFACT message with $STX$UNA
            if (line.contains("$STX$UNA")) {
                System.out.println("Found $STX$UNA at line " + lineNumber);

                // Save previous message if exists
                if (currentEdifactMessage != null && currentMessage.length() > 0) {
                    finalizeMessageWithUNA(currentMessage, currentEdifactMessage, currentUnaSegment, messages, targetFlightNumber);
                    foundMessages++;
                }

                // Start new message
                String unaLine = line.substring(line.indexOf("$STX") + 4);
                boolean unaParseSuccess = parseUNA(unaLine);

                // Store UNA segment for this message
                if (unaLine.startsWith("UNA") && unaLine.length() >= 9) {
                    currentUnaSegment = unaLine.substring(0, 9);
                }

                currentMessage = new StringBuilder();
                currentEdifactMessage = new EdifactMessage();
                inMessage = true;

                // Process the embedded EDIFACT message within the UNA line
                if (unaParseSuccess && unaLine.length() > 9) {
                    processEmbeddedEdifactMessage(unaLine, currentMessage, currentEdifactMessage, messages, targetFlightNumber);
                    // Reset after processing embedded message
                    currentMessage = new StringBuilder();
                    currentEdifactMessage = null;
                    currentUnaSegment = null;
                    inMessage = false;
                }
            }
            // Check for MessageForwarder INFO logs containing EDIFACT messages
            else if (line.contains("INFO ") && line.contains("Forward.BUSINESS_RULES_PROCESSOR") &&
                    line.contains("Message body [UNA:")) {
                System.out.println("Found MessageForwarder log at line " + lineNumber);

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

                    // Extract UNA segment from MessageForwarder content
                    if (edifactContent.startsWith("UNA") && edifactContent.length() >= 9) {
                        currentUnaSegment = edifactContent.substring(0, 9);
                    }

                    currentMessage = new StringBuilder();
                    currentEdifactMessage = new EdifactMessage();
                    currentEdifactMessage.setMessageType("OUTPUT");
                    inMessage = true;

                    // Process the MessageForwarder EDIFACT content
                    processMessageForwarderEdifact(edifactContent, currentMessage, currentEdifactMessage, messages, targetFlightNumber);
                    // Reset after processing MessageForwarder message
                    currentMessage = new StringBuilder();
                    currentEdifactMessage = null;
                    currentUnaSegment = null;
                    inMessage = false;
                }
            }
            // Check for WARN logs with "Failed to parse API message" containing EDIFACT
            else if (line.contains("Failed to parse API message") && line.contains("[UNA:")) {
                System.out.println("Found Failed API message at line " + lineNumber);

                // Save previous message if exists
                if (currentEdifactMessage != null && currentMessage.length() > 0) {
                    finalizeMessageWithUNA(currentMessage, currentEdifactMessage, currentUnaSegment, messages, targetFlightNumber);
                    foundMessages++;
                }

                // Extract UNA part from the log message
                int unaStartIndex = line.indexOf("[UNA:");
                if (unaStartIndex != -1) {
                    String unaLine = line.substring(unaStartIndex + 1);
                    // Remove closing bracket if present
                    if (unaLine.endsWith("]")) {
                        unaLine = unaLine.substring(0, unaLine.length() - 1);
                    }

                    boolean unaParseSuccess = parseUNA(unaLine);

                    // Store UNA segment for this message
                    if (unaLine.startsWith("UNA") && unaLine.length() >= 9) {
                        currentUnaSegment = unaLine.substring(0, 9);
                    }

                    currentMessage = new StringBuilder();
                    currentEdifactMessage = new EdifactMessage();
                    inMessage = true;

                    if (unaParseSuccess && unaLine.length() > 9) {
                        processEmbeddedEdifactMessage(unaLine, currentMessage, currentEdifactMessage, messages, targetFlightNumber);
                        // Reset after processing embedded message
                        currentMessage = new StringBuilder();
                        currentEdifactMessage = null;
                        currentUnaSegment = null;
                        inMessage = false;
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
            else if (line.startsWith("UNA:") && !inMessage) {
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
                // This looks like an EDIFACT segment
                currentMessage.append(line).append("\n");

                // Parse UNH segment for message details
                if (line.startsWith("UNH" + elementSeparator)) {
                    parseUNH(line, currentEdifactMessage);
                }

                // Parse flight details from various segments
                if (currentEdifactMessage != null) {
                    parseFlightDetails(line, currentEdifactMessage);
                }

                // Check for end of message (UNZ segment typically ends EDIFACT)
                if (line.startsWith("UNZ" + elementSeparator)) {
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

            // Progress indicator for large files
            if (lineNumber % 10000 == 0) {
                System.out.println("Processed " + lineNumber + " lines, found " + foundMessages + " messages");
            }
        }

        // Handle last message if file doesn't end properly
        if (currentEdifactMessage != null && currentMessage.length() > 0) {
            finalizeMessageWithUNA(currentMessage, currentEdifactMessage, currentUnaSegment, messages, targetFlightNumber);
            foundMessages++;
        }

        System.out.println("=== PARSING COMPLETE ===");
        System.out.println("Total messages extracted: " + messages.size());
        return messages;
    }

    /**
     * Process single-line EDIFACT messages (from $STX$UNA or WARN logs)
     */
    private void processSingleLineEdifact(String line, List<EdifactMessage> messages, String targetFlightNumber,
                                          int lineNumber, EdifactMessage currentEdifactMessage, StringBuilder currentMessage) {
        // Save previous message if exists
        if (currentEdifactMessage != null && currentMessage.length() > 0) {
            currentEdifactMessage.setRawContent(currentMessage.toString());
            if (matchesFlightCriteria(currentEdifactMessage, targetFlightNumber)) {
                messages.add(currentEdifactMessage);
                System.out.println("Added previous message to results");
            } else {
                System.out.println("Previous message didn't match flight criteria");
            }
        }

        // Extract UNA part
        String unaLine = null;
        if (line.contains("$STX$UNA")) {
            unaLine = line.substring(line.indexOf("$STX") + 4);
        } else if (line.contains("[UNA:")) {
            int unaStartIndex = line.indexOf("[UNA:");
            unaLine = line.substring(unaStartIndex + 1); // Skip the opening '['
        }

        if (unaLine != null) {
            System.out.println("Extracted UNA line: " + unaLine);
            boolean unaParseSuccess = parseUNA(unaLine);
            System.out.println("UNA parse success: " + unaParseSuccess);
            System.out.println("Separators - Element: '" + elementSeparator + "', Sub-element: '" + subElementSeparator + "', Terminator: '" + terminatorSeparator + "'");

            EdifactMessage newMessage = new EdifactMessage();
            StringBuilder newMessageContent = new StringBuilder();

            // Process the embedded EDIFACT message within the UNA line
            if (unaParseSuccess && unaLine.length() > 9) {
                processEmbeddedEdifactMessage(unaLine, newMessageContent, newMessage, messages, targetFlightNumber);
            }
        }
    }

    /**
     * Detect element separator from UNH line by analyzing the pattern
     */
    private char detectElementSeparatorFromUNH(String line) {
        // Look for UNH followed by a character, then digits, then the same character
        // Pattern: UNH<sep>1<sep>PAXLST...
        int unhIndex = line.indexOf("UNH");
        if (unhIndex >= 0 && unhIndex + 4 < line.length()) {
            char possibleSeparator = line.charAt(unhIndex + 3);
            // Verify this is likely a separator by checking if it appears multiple times in the expected pattern
            String afterUnh = line.substring(unhIndex + 3);
            if (afterUnh.length() > 3 && afterUnh.charAt(2) == possibleSeparator) {
                System.out.println("Detected element separator: '" + possibleSeparator + "'");
                return possibleSeparator;
            }
        }
        return '\0'; // Not found
    }

    /**
     * Detect terminator separator from line (usually the last character or second to last)
     */
    private char detectTerminatorSeparatorFromLine(String line) {
        // The terminator is usually at the end of the line, possibly followed by \r
        if (line.length() > 0) {
            char lastChar = line.charAt(line.length() - 1);
            if (lastChar == '\r' && line.length() > 1) {
                lastChar = line.charAt(line.length() - 2);
            }

            // Common EDIFACT terminators
            if (lastChar == '-' || lastChar == '\'' || lastChar == '~') {
                System.out.println("Detected terminator separator: '" + lastChar + "'");
                return lastChar;
            }
        }
        return '\0'; // Not found
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
        String edifactContent = unaLine.length() > 9 ? unaLine.substring(9) : "";

        // Split the EDIFACT content by the terminator separator to get individual segments
        String[] segments = edifactContent.split("\\" + terminatorSeparator);

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i].trim();
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
        if (currentEdifactMessage != null && currentMessage.length() > 0) {
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
                System.out.println("Prepended UNA segment to message: " + unaSegment);
            } else {
                // Fallback: construct UNA segment from current separators
                String constructedUNA = "UNA" + subElementSeparator + elementSeparator + decimalSeparator +
                                       releaseIndicator + reservedSeparator + terminatorSeparator;
                messageContent = constructedUNA + "\n" + messageContent;
                System.out.println("Constructed and prepended UNA segment: " + constructedUNA);
            }
        }

        currentEdifactMessage.setRawContent(messageContent);
        if (matchesFlightCriteria(currentEdifactMessage, targetFlightNumber)) {
            messages.add(currentEdifactMessage);
            System.out.println("Successfully added message with UNA segment preserved");
        }
    }

    /**
     * Parse UNH segment to extract message ID, flight number, and part information
     */
    private void parseUNH(String line, EdifactMessage message) {
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
                    } else if ("250".equals(bgmCode)) {
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

                    if ("125".equals(locType)) {
                        // Departure airport
                        details.setDepartureAirport(airport);
                    } else if ("87".equals(locType)) {
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

                        if ("189".equals(dtmType)) {
                            // Departure date/time: format YYMMDDHHMM
                            if (dateTime.length() >= 8) {
                                String date = dateTime.substring(0, 6); // YYMMDD
                                String time = dateTime.length() >= 10 ? dateTime.substring(6, 10) : ""; // HHMM
                                details.setDepartureDate(date);
                                details.setDepartureTime(time);
                            }
                        } else if ("232".equals(dtmType)) {
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
            } else {
                return false;
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
                                                String targetFlightNumber) {
        System.out.println("=== PROCESSING MESSAGEFORWARDER EDIFACT ===");
        System.out.println("Raw content length: " + edifactContent.length());
        System.out.println("Raw content preview: " + edifactContent.substring(0, Math.min(200, edifactContent.length())));

        // Extract UNA segment from MessageForwarder content for preservation
        String unaSegment = null;
        if (edifactContent.startsWith("UNA") && edifactContent.length() >= 9) {
            unaSegment = edifactContent.substring(0, 9);
        }

        // MessageForwarder logs often have literal \n characters instead of actual newlines
        // Replace them with actual newlines for proper parsing
        String normalizedContent = edifactContent.replace("\\n", "\n");

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
            System.out.println("Converted single-line to multi-line format");
        }

        // Split into lines/segments for processing
        String[] segments = normalizedContent.split("\\r?\\n");
        System.out.println("Processing " + segments.length + " segments");

        boolean foundUNA = false;
        boolean foundUNH = false;
        boolean foundTDT = false;

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i].trim();
            if (!segment.isEmpty()) {
                currentMessage.append(segment);
                if (!segment.endsWith(String.valueOf(terminatorSeparator))) {
                    currentMessage.append(terminatorSeparator);
                }
                currentMessage.append("\n");

                System.out.println("Processing segment " + (i+1) + ": " + segment.substring(0, Math.min(50, segment.length())));

                // Parse UNA header to get separators
                if (segment.startsWith("UNA")) {
                    parseUNA(segment);
                    foundUNA = true;
                    System.out.println("Found UNA in MessageForwarder: " + segment);
                }

                // Parse UNH segment for message details
                else if (segment.startsWith("UNH" + elementSeparator)) {
                    parseUNH(segment + terminatorSeparator, currentEdifactMessage);
                    foundUNH = true;
                    System.out.println("Found UNH in MessageForwarder: " + segment);
                }

                // Parse flight details from various segments
                if (currentEdifactMessage != null) {
                    parseFlightDetails(segment + terminatorSeparator, currentEdifactMessage);

                    // Track important segments
                    if (segment.startsWith("TDT" + elementSeparator)) {
                        foundTDT = true;
                        System.out.println("Found TDT (flight) in MessageForwarder: " + segment);
                    }
                }

                // Check for end of message (UNZ segment typically ends EDIFACT)
                if (segment.startsWith("UNZ" + elementSeparator)) {
                    System.out.println("Found UNZ in MessageForwarder: " + segment);
                    break; // Stop processing segments after UNZ
                }
            }
        }

        // CRITICAL: Set message type as OUTPUT for MessageForwarder messages
        if (currentEdifactMessage != null) {
            currentEdifactMessage.setMessageType("OUTPUT");
            System.out.println("Set message type to OUTPUT for MessageForwarder message: " + currentEdifactMessage.getMessageId());
            System.out.println("MessageForwarder parsing summary - UNA: " + foundUNA + ", UNH: " + foundUNH + ", TDT: " + foundTDT);
        }

        // Complete the MessageForwarder message processing using finalizeMessageWithUNA
        if (currentEdifactMessage != null && currentMessage.length() > 0) {
            // Use the same UNA preservation logic as other methods
            finalizeMessageWithUNA(currentMessage, currentEdifactMessage, unaSegment, messages, targetFlightNumber);
        } else {
            System.out.println("MessageForwarder message processing failed - no message or content");
        }
    }
}
