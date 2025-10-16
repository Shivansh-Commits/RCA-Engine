package com.l3.logparser.pnr.parser;

import com.l3.logparser.pnr.model.PnrMessage;
import com.l3.logparser.pnr.model.PnrFlightDetails;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for PNR EDIFACT messages found in MessageMHPNRGOV.log files
 * Handles UNA separators and extracts PNR-specific flight information from TVL segments
 */
public class PnrEdifactParser {

    // UNA separators - will be dynamically set for each message
    private char subElementSeparator = ':';
    private char elementSeparator = '+';
    private char decimalSeparator = '.';
    private char releaseIndicator = '?';
    private char reservedSeparator = ' ';
    private char terminatorSeparator = '\'';

    /**
     * Parse log content and extract PNR messages for a specific flight
     */
    public List<PnrMessage> parseLogContent(String content, String flightNumber) {
        List<PnrMessage> messages = new ArrayList<>();

        try {
            System.out.println("DEBUG: Starting PNR parsing for flight: " + flightNumber);
            System.out.println("DEBUG: Content length: " + content.length());

            // Split content by log entries (INFO markers)
            String[] logEntries = content.split("(?=INFO\\s+\\[)");
            System.out.println("DEBUG: Found " + logEntries.length + " log entries");

            int pnrgovCount = 0;
            for (String entry : logEntries) {
                if (entry.trim().isEmpty()) continue;

                if (entry.contains("PNRGOV")) {
                    pnrgovCount++;
                }

                // Extract timestamp and trace ID from log entry
                String timestamp = extractTimestamp(entry);
                String traceId = extractTraceId(entry);

                // Find EDIFACT message blocks (starting with UNA)
                List<String> edifactBlocks = extractEdifactBlocks(entry);

                if (!edifactBlocks.isEmpty()) {
                    System.out.println("DEBUG: Found " + edifactBlocks.size() + " EDIFACT blocks in entry");
                }

                for (String block : edifactBlocks) {
                    PnrMessage message = parseEdifactBlock(block, flightNumber, timestamp, traceId);
                    if (message != null) {
                        messages.add(message);
                    }
                }
            }

            System.out.println("DEBUG: Total entries with PNRGOV: " + pnrgovCount);
            System.out.println("DEBUG: Successfully parsed " + messages.size() + " PNR messages");

        } catch (Exception e) {
            System.err.println("Error parsing PNR log content: " + e.getMessage());
            e.printStackTrace();
        }

        return messages;
    }

    /**
     * Extract EDIFACT message blocks from log entry
     */
    private List<String> extractEdifactBlocks(String logEntry) {
        List<String> blocks = new ArrayList<>();

        // Look for UNA patterns that start EDIFACT messages
        Pattern unaPattern = Pattern.compile("UNA.{6}.*?(?=UNA.{6}|$)", Pattern.DOTALL);
        Matcher matcher = unaPattern.matcher(logEntry);

        while (matcher.find()) {
            String block = matcher.group().trim();
            if (!block.isEmpty()) {
                blocks.add(block);
            }
        }

        // If no UNA pattern found, try to extract the entire message body
        if (blocks.isEmpty()) {
            // Try different patterns for message body extraction
            Pattern[] messageBodyPatterns = {
                Pattern.compile("Message body \\[(.*?)\\]", Pattern.DOTALL),
                Pattern.compile("PNRGOV_MESSAGE_HANDLER.*?Message body \\[(.*?)\\]", Pattern.DOTALL),
                Pattern.compile("Request - MQ Message received.*?Message body \\[(.*?)\\]", Pattern.DOTALL)
            };

            for (Pattern pattern : messageBodyPatterns) {
                Matcher bodyMatcher = pattern.matcher(logEntry);
                if (bodyMatcher.find()) {
                    String messageBody = bodyMatcher.group(1).trim();
                    if (messageBody.contains("UNA") || messageBody.contains("PNRGOV")) {
                        blocks.add(messageBody);
                        break;
                    }
                }
            }
        }

        // If still no blocks found but entry contains PNRGOV, try to extract raw content
        if (blocks.isEmpty() && logEntry.contains("PNRGOV")) {
            // Look for any content that might be an EDIFACT message
            String[] lines = logEntry.split("\n");
            StringBuilder currentBlock = new StringBuilder();
            boolean inMessage = false;

            for (String line : lines) {
                String trimmedLine = line.trim();
                if (trimmedLine.startsWith("UNA") || trimmedLine.startsWith("UNB") ||
                    trimmedLine.startsWith("UNG") || trimmedLine.startsWith("UNH")) {
                    if (inMessage && currentBlock.length() > 0) {
                        blocks.add(currentBlock.toString().trim());
                        currentBlock = new StringBuilder();
                    }
                    inMessage = true;
                    currentBlock.append(trimmedLine).append("\n");
                } else if (inMessage && (trimmedLine.startsWith("TVL") ||
                           trimmedLine.startsWith("MSG") || trimmedLine.startsWith("ORG") ||
                           trimmedLine.startsWith("EQN") || trimmedLine.contains("+"))) {
                    currentBlock.append(trimmedLine).append("\n");
                } else if (inMessage && trimmedLine.isEmpty()) {
                    // Continue adding empty lines within message
                    continue;
                } else if (inMessage && !trimmedLine.isEmpty()) {
                    // End of message block
                    if (currentBlock.length() > 0) {
                        blocks.add(currentBlock.toString().trim());
                    }
                    currentBlock = new StringBuilder();
                    inMessage = false;
                }
            }

            // Add any remaining block
            if (inMessage && currentBlock.length() > 0) {
                blocks.add(currentBlock.toString().trim());
            }
        }

        // Debug output
        if (blocks.isEmpty() && logEntry.contains("PNRGOV")) {
            System.out.println("DEBUG: Found PNRGOV entry but no extractable blocks:");
            System.out.println("Entry preview: " + logEntry.substring(0, Math.min(200, logEntry.length())));
        }

        return blocks;
    }

    /**
     * Parse a single EDIFACT block
     */
    private PnrMessage parseEdifactBlock(String block, String flightNumber, String timestamp, String traceId) {
        try {
            // Parse UNA to get separators
            if (!parseUNA(block)) {
                return null;
            }

            // Extract message components
            String messageId = extractMessageId(block);
            if (messageId == null) {
                System.out.println("DEBUG: No message ID found in block");
                return null;
            }

            // Extract part information from UNH segment
            int partNumber = extractPartNumber(block);
            String partIndicator = extractPartIndicator(block);
            boolean isLastPart = "F".equals(partIndicator);

            // Extract flight details from TVL segment
            PnrFlightDetails flightDetails = extractFlightDetailsFromTVL(block);

            // Enhanced flight number filtering with more flexible matching
            if (flightNumber != null && !flightNumber.trim().isEmpty()) {
                boolean flightMatches = false;
                String searchFlight = flightNumber.trim().toUpperCase();

                if (flightDetails != null) {
                    String fullFlightNumber = flightDetails.getFullFlightNumber().toUpperCase();
                    String justFlightNumber = flightDetails.getFlightNumber();
                    String airlineCode = flightDetails.getAirlineCode();

                    // Try multiple matching strategies
                    flightMatches = fullFlightNumber.contains(searchFlight) ||
                                  (justFlightNumber != null && justFlightNumber.contains(searchFlight)) ||
                                  (airlineCode != null && justFlightNumber != null &&
                                   (airlineCode + justFlightNumber).toUpperCase().contains(searchFlight)) ||
                                  searchFlight.contains(justFlightNumber != null ? justFlightNumber : "");
                }

                // Also check if the flight appears anywhere in the block content
                if (!flightMatches) {
                    flightMatches = block.toUpperCase().contains(searchFlight);
                }

                if (!flightMatches) {
                    System.out.println("DEBUG: Flight " + searchFlight + " doesn't match. Found flight details: " +
                                     (flightDetails != null ? flightDetails.getFullFlightNumber() : "none"));
                    return null; // Skip if flight doesn't match
                }
            }

            // Create PNR message
            PnrMessage message = new PnrMessage();
            message.setMessageId(messageId);
            message.setPartNumber(partNumber);
            message.setPartIndicator(partIndicator);
            message.setLastPart(isLastPart);
            message.setFlightDetails(flightDetails);
            message.setMessageType("PNRGOV");
            message.setRawContent(block);
            message.setTimestamp(timestamp);
            message.setTraceId(traceId);

            if (flightDetails != null) {
                message.setFlightNumber(flightDetails.getFullFlightNumber());
            }

            System.out.println("DEBUG: Successfully parsed PNR message: " + messageId +
                             ", Part: " + partNumber +
                             ", Flight: " + (flightDetails != null ? flightDetails.getFullFlightNumber() : "none"));

            return message;

        } catch (Exception e) {
            System.err.println("Error parsing EDIFACT block: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Parse UNA segment to extract separators for PNR messages
     */
    private boolean parseUNA(String block) {
        try {
            // Find UNA in the block
            int unaIndex = block.indexOf("UNA");
            if (unaIndex == -1) {
                // Use default separators if UNA not found
                return true;
            }

            String unaSegment = block.substring(unaIndex);
            if (unaSegment.length() < 9) {
                return true; // Use defaults
            }

            // Extract separators: UNA:+.? '
            subElementSeparator = unaSegment.charAt(3);  // :
            elementSeparator = unaSegment.charAt(4);     // +
            decimalSeparator = unaSegment.charAt(5);     // .
            releaseIndicator = unaSegment.charAt(6);     // ?
            reservedSeparator = unaSegment.charAt(7);    // (space)
            terminatorSeparator = unaSegment.charAt(8);  // '

            return true;

        } catch (Exception e) {
            System.err.println("Error parsing UNA: " + e.getMessage());
            return true; // Continue with defaults
        }
    }

    /**
     * Extract message ID from UNH segment
     * Example: UNH+020210082521+PNRGOV:13:1:IA+EI0202/100825/0630/01+01:C'
     */
    private String extractMessageId(String block) {
        try {
            String escapedSeparator = Pattern.quote(String.valueOf(elementSeparator));
            Pattern unhPattern = Pattern.compile("UNH" + escapedSeparator + "([^" + Pattern.quote(String.valueOf(elementSeparator)) + "]*)" + escapedSeparator);
            Matcher matcher = unhPattern.matcher(block);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            System.err.println("Error extracting message ID: " + e.getMessage());
        }
        return null;
    }

    /**
     * Extract part number from UNH segment
     * Example: UNH+068010082521+PNRGOV:13:1:IA+EI0680/100825/0615/01+01:C
     * The part number is before the part indicator (01 in this case)
     */
    private int extractPartNumber(String block) {
        try {
            // Look for the last element in UNH which contains part info
            String escapedElementSep = Pattern.quote(String.valueOf(elementSeparator));
            String escapedSubElementSep = Pattern.quote(String.valueOf(subElementSeparator));
            String escapedTerminator = Pattern.quote(String.valueOf(terminatorSeparator));

            Pattern partPattern = Pattern.compile("UNH[^" + escapedTerminator + "]*" + escapedElementSep + "([0-9]+)(?:" + escapedSubElementSep + "[CF])?" + escapedTerminator);
            Matcher matcher = partPattern.matcher(block);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {
            System.err.println("Error extracting part number: " + e.getMessage());
        }
        return 1; // Default to part 1
    }

    /**
     * Extract part indicator (C for first part, F for last part)
     * Example: UNH+068010082521+PNRGOV:13:1:IA+EI0680/100825/0615/01+01:C
     */
    private String extractPartIndicator(String block) {
        try {
            String escapedElementSep = Pattern.quote(String.valueOf(elementSeparator));
            String escapedSubElementSep = Pattern.quote(String.valueOf(subElementSeparator));
            String escapedTerminator = Pattern.quote(String.valueOf(terminatorSeparator));

            Pattern indicatorPattern = Pattern.compile("UNH[^" + escapedTerminator + "]*" + escapedElementSep + "[0-9]+" + escapedSubElementSep + "([CF])" + escapedTerminator);
            Matcher matcher = indicatorPattern.matcher(block);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            System.err.println("Error extracting part indicator: " + e.getMessage());
        }
        return null;
    }

    /**
     * Extract flight details from TVL segment
     * Example: TVL+100825:0630:100825:0735+DUB+MAN+EI+0202'
     * Format: TVL+depDate:depTime:arrDate:arrTime+depPort+arrPort+airline+flightNo'
     * Note: Dates are in ddmmyy format (10=day, 08=month, 25=year 2025)
     */
    private PnrFlightDetails extractFlightDetailsFromTVL(String block) {
        try {
            // Find first TVL segment after UNH
            String escapedElementSep = Pattern.quote(String.valueOf(elementSeparator));
            String escapedTerminator = Pattern.quote(String.valueOf(terminatorSeparator));

            Pattern tvlPattern = Pattern.compile("TVL" + escapedElementSep + "([^" + escapedTerminator + "]*)" + escapedTerminator);
            Matcher matcher = tvlPattern.matcher(block);

            if (matcher.find()) {
                String tvlContent = matcher.group(1);
                String[] parts = tvlContent.split(Pattern.quote(String.valueOf(elementSeparator)));

                if (parts.length >= 4) {
                    // Parse datetime part: 100825:0630:100825:0735
                    String[] dateTimes = parts[0].split(Pattern.quote(String.valueOf(subElementSeparator)));
                    String departureDate = dateTimes.length > 0 ? dateTimes[0] : null;
                    String departureTime = dateTimes.length > 1 ? dateTimes[1] : null;
                    String arrivalDate = dateTimes.length > 2 ? dateTimes[2] : null;
                    String arrivalTime = dateTimes.length > 3 ? dateTimes[3] : null;

                    // Parse airports and flight info
                    String departureAirport = parts.length > 1 ? parts[1] : null;
                    String arrivalAirport = parts.length > 2 ? parts[2] : null;
                    String airlineCode = parts.length > 3 ? parts[3] : null;
                    String flightNumber = parts.length > 4 ? parts[4] : null;

                    return new PnrFlightDetails(departureDate, departureTime, arrivalDate, arrivalTime,
                                              departureAirport, arrivalAirport, airlineCode, flightNumber);
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting flight details from TVL: " + e.getMessage());
        }
        return null;
    }

    /**
     * Extract timestamp from log entry
     */
    private String extractTimestamp(String logEntry) {
        try {
            Pattern timestampPattern = Pattern.compile("INFO\\s+\\[([^\\]]+)\\]");
            Matcher matcher = timestampPattern.matcher(logEntry);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            System.err.println("Error extracting timestamp: " + e.getMessage());
        }
        return null;
    }

    /**
     * Extract trace ID from log entry
     */
    private String extractTraceId(String logEntry) {
        try {
            Pattern tracePattern = Pattern.compile("\\[trace\\.id:([^\\]]+)\\]");
            Matcher matcher = tracePattern.matcher(logEntry);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            System.err.println("Error extracting trace ID: " + e.getMessage());
        }
        return null;
    }
}
