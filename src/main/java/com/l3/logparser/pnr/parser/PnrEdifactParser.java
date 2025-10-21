package com.l3.logparser.pnr.parser;

import com.l3.logparser.pnr.model.*;
import com.l3.logparser.enums.MessageType;

import java.util.*;
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
        
        // More flexible log entry splitting to handle various log formats
        String[] logEntries = logContent.split("(?=\\d{4}-\\d{2}-\\d{2}|INFO\\s|DEBUG\\s|WARN\\s|ERROR\\s)");
        
        for (String logEntry : logEntries) {
            if (containsPnrMessage(logEntry)) {
                PnrMessage message = parseLogEntry(logEntry, targetFlightNumber, messageType);
                if (message != null) {
                    messages.add(message);
                }
            }
        }
        
        return messages;
    }

    /**
     * Check if log entry contains a PNR message (input or output)
     */
    private boolean containsPnrMessage(String logEntry) {
        return logEntry.contains("UNA:") || 
               (logEntry.contains("UNB+") && logEntry.contains("PNRGOV")) ||
               logEntry.contains("PNRGOV_PNR_PUSH") ||
               (logEntry.contains("Message body") && logEntry.contains("PNRGOV")) ||
               // Output messages from MessageForwarder.log
               (logEntry.contains("Forward.BUSINESS_RULES_PROCESSOR") && logEntry.contains("Message body")) ||
               (logEntry.contains("TO.NO.PNR.OUT") && logEntry.contains("UNA")) ||
               (logEntry.contains("TO.NO.PNR.OUT") && logEntry.contains("UNB+"));
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
                return null;
            }
            
            // Parse the EDIFACT message
            PnrMessage message = parseEdifactMessage(edifactContent);
            if (message == null) {
                return null;
            }
            
            // Set additional properties
            message.setLogTimestamp(timestamp);
            message.setLogTraceId(traceId);
            message.setDirection(direction);
            message.setRawContent(edifactContent);
            
            // Filter by flight number if specified
            if (targetFlightNumber != null && !targetFlightNumber.trim().isEmpty()) {
                if (!isFlightMatch(message, targetFlightNumber)) {
                    return null;
                }
            }
            
            return message;
            
        } catch (Exception e) {
            System.err.println("Error parsing log entry: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse EDIFACT message content
     */
    private PnrMessage parseEdifactMessage(String edifactContent) {
        PnrMessage message = new PnrMessage();
        
        // Detect separators from UNA or UNB segment
        PnrSeparators separators = detectSeparators(edifactContent);
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
     */
    private PnrSeparators detectSeparators(String edifactContent) {
        // Try to find UNA segment first
        int unaIndex = edifactContent.indexOf("UNA");
        if (unaIndex >= 0) {
            int unaEnd = edifactContent.indexOf('\n', unaIndex);
            if (unaEnd == -1) unaEnd = edifactContent.indexOf(' ', unaIndex + 9);
            if (unaEnd == -1) unaEnd = Math.min(unaIndex + 15, edifactContent.length());
            
            String unaSegment = edifactContent.substring(unaIndex, unaEnd).trim();
            return PnrSeparators.fromUnaSegment(unaSegment);
        }
        
        // Fallback to UNB segment
        int unbIndex = edifactContent.indexOf("UNB");
        if (unbIndex >= 0) {
            int unbEnd = edifactContent.indexOf('\n', unbIndex);
            if (unbEnd == -1) unbEnd = edifactContent.indexOf('\'', unbIndex + 3);
            if (unbEnd == -1) unbEnd = Math.min(unbIndex + 100, edifactContent.length());
            
            String unbSegment = edifactContent.substring(unbIndex, unbEnd + 1).trim();
            return PnrSeparators.fromUnbSegment(unbSegment);
        }
        
        // Return default separators if neither found
        return PnrSeparators.DEFAULT;
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
                
                // Look for UNA or UNB after the Message body marker
                int unaIndex = afterMessageBody.indexOf("UNA");
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
            // Try UNA first
            startIndex = logEntry.indexOf("UNA");
            if (startIndex < 0) {
                // Try UNB
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