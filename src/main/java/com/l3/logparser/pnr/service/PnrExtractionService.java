package com.l3.logparser.pnr.service;

import com.l3.logparser.pnr.model.*;
import com.l3.logparser.pnr.parser.PnrEdifactParser;
import com.l3.logparser.enums.MessageType;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Collectors;

/**
 * Service for extracting PNR messages from log files
 * Handles MessageMHPNRGOV.log* files (input) and MessageForwarder.log* files (output)
 * Coordinates multipart message assembly for input messages
 */
public class PnrExtractionService {

    private final PnrEdifactParser parser;
    private static final List<String> PNR_INPUT_LOG_PATTERNS = Arrays.asList(
            "MessageMHPNRGOV.log*", "MessagePNRGOV.log*"
    );
    private static final List<String> PNR_OUTPUT_LOG_PATTERNS = Arrays.asList(
            "MessageForwarder.log*"
    );

    public PnrExtractionService() {
        this.parser = new PnrEdifactParser();
    }

    /**
     * Extract PNR messages from log directory for a specific flight
     * @param logDirectoryPath Path to the log directory
     * @param flightNumber Flight number to search for
     * @param departureDate Departure date (optional filter)
     * @param departureAirport Departure airport (optional filter)
     * @param arrivalAirport Arrival airport (optional filter)
     * @return PnrExtractionResult containing found messages and processing info
     */
    public PnrExtractionResult extractPnrMessages(String logDirectoryPath,
                                                 String flightNumber,
                                                 String departureDate,
                                                 String departureAirport,
                                                 String arrivalAirport) {

        PnrExtractionResult result = new PnrExtractionResult();
        result.setFlightNumber(flightNumber);
        result.setLogDirectoryPath(logDirectoryPath);

        try {
            Path logDir = Paths.get(logDirectoryPath);
            if (!Files.exists(logDir) || !Files.isDirectory(logDir)) {
                result.addError("Log directory does not exist: " + logDirectoryPath);
                return result;
            }

            // Find and process PNR log files (both input and output)
            List<PnrMessage> allMessages = new ArrayList<>();

            // Process input log files (MessageMHPNRGOV.log*)
            for (String pattern : PNR_INPUT_LOG_PATTERNS) {
                List<Path> logFiles = findLogFiles(logDir, pattern);
                for (Path logFile : logFiles) {
                    List<PnrMessage> fileMessages = processLogFile(logFile, flightNumber, MessageType.INPUT);
                    allMessages.addAll(fileMessages);
                    result.addProcessedFile(logFile.toString() + " (INPUT)");
                }
            }

            // Process output log files (MessageForwarder.log*)
            for (String pattern : PNR_OUTPUT_LOG_PATTERNS) {
                List<Path> logFiles = findLogFiles(logDir, pattern);
                for (Path logFile : logFiles) {
                    List<PnrMessage> fileMessages = processLogFile(logFile, flightNumber, MessageType.OUTPUT);
                    allMessages.addAll(fileMessages);
                    result.addProcessedFile(logFile.toString() + " (OUTPUT)");
                }
            }

            // Remove duplicate messages (same message ID from multiple files)
            List<PnrMessage> deduplicatedMessages = removeDuplicateMessages(allMessages);

            // Group multipart messages
            List<PnrMultipartGroup> groups = groupMultipartMessages(deduplicatedMessages);

            // Analyze completeness (only for groups matching target flight criteria)
            analyzeCompleteness(groups, result, flightNumber, departureDate, departureAirport, arrivalAirport);

            // Filter messages based on additional criteria
            List<PnrMessage> filteredMessages = filterMessages(deduplicatedMessages,
                    flightNumber, departureDate, departureAirport, arrivalAirport);

            result.setExtractedMessages(filteredMessages);
            result.setMultipartGroups(groups);
            result.setSuccess(true);

            if (filteredMessages.isEmpty()) {
                result.addWarning("No PNR messages found matching the specified criteria");
            }

        } catch (Exception e) {
            result.addError("Error processing PNR log directory: " + e.getMessage());
            System.err.println("ERROR in extractPnrMessages: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Find log files matching a pattern in the directory
     */
    private List<Path> findLogFiles(Path directory, String pattern) throws IOException {
        List<Path> files = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, pattern)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    files.add(file);
                }
            }
        }

        return files.stream().sorted().collect(Collectors.toList());
    }

    /**
     * Process a single PNR log file
     */
    private List<PnrMessage> processLogFile(Path logFile, String flightNumber, MessageType messageType) {
        List<PnrMessage> messages = new ArrayList<>();

        try {
            long fileSize = Files.size(logFile);

            if (fileSize > 50 * 1024 * 1024) { // If file is larger than 50MB
                messages = processLargeLogFile(logFile, flightNumber, messageType);
            } else {
                String content = Files.readString(logFile);
                messages = parser.parseLogContent(content, flightNumber, messageType);
            }

            System.out.println("Processed " + logFile.getFileName() + 
                             " - Found " + messages.size() + " PNR messages");

        } catch (IOException e) {
            System.err.println("Error reading PNR log file " + logFile + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error processing PNR file " + logFile + ": " + e.getMessage());
            e.printStackTrace();
        }

        return messages;
    }

    /**
     * Process large log files in chunks to avoid memory issues
     */
    private List<PnrMessage> processLargeLogFile(Path logFile, String flightNumber, MessageType messageType) {
        List<PnrMessage> messages = new ArrayList<>();
        StringBuilder currentEntry = new StringBuilder();
        boolean inPnrMessage = false;

        try (BufferedReader reader = Files.newBufferedReader(logFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Check if this line starts a new log entry
                if (isNewLogEntry(line)) {
                    // Process the previous entry if it was a PNR message
                    if (inPnrMessage && currentEntry.length() > 0) {
                        List<PnrMessage> entryMessages = parser.parseLogContent(
                            currentEntry.toString(), flightNumber, messageType);
                        messages.addAll(entryMessages);
                    }

                    // Start new entry
                    currentEntry.setLength(0);
                    currentEntry.append(line).append("\n");
                    inPnrMessage = line.contains("PNRGOV") || line.contains("UNA:");
                } else {
                    currentEntry.append(line).append("\n");
                    if (!inPnrMessage && (line.contains("PNRGOV") || line.contains("UNA:"))) {
                        inPnrMessage = true;
                    }
                }
            }

            // Process the last entry
            if (inPnrMessage && currentEntry.length() > 0) {
                List<PnrMessage> entryMessages = parser.parseLogContent(
                    currentEntry.toString(), flightNumber, messageType);
                messages.addAll(entryMessages);
            }

        } catch (IOException e) {
            System.err.println("Error reading large PNR log file " + logFile + ": " + e.getMessage());
        }

        return messages;
    }

    /**
     * Check if a line starts a new log entry
     */
    private boolean isNewLogEntry(String line) {
        return line.matches("^\\d{4}-\\d{2}-\\d{2}.*") || 
               line.startsWith("INFO ") || 
               line.startsWith("DEBUG ") || 
               line.startsWith("WARN ") || 
               line.startsWith("ERROR ");
    }

    /**
     * Remove duplicate messages based on message ID and content
     */
    private List<PnrMessage> removeDuplicateMessages(List<PnrMessage> messages) {
        Map<String, PnrMessage> uniqueMessages = new LinkedHashMap<>();

        for (PnrMessage message : messages) {
            String key = createMessageKey(message);
            if (!uniqueMessages.containsKey(key)) {
                uniqueMessages.put(key, message);
            }
        }

        return new ArrayList<>(uniqueMessages.values());
    }

    /**
     * Create a unique key for message deduplication
     */
    private String createMessageKey(PnrMessage message) {
        return message.getMessageReferenceNumber() + "_" + 
               message.getPartNumber() + "_" + 
               message.getFlightNumber() + "_" +
               (message.getDirection() != null ? message.getDirection().toString() : "UNKNOWN");
    }

    /**
     * Group multipart messages by message reference number and flight
     */
    private List<PnrMultipartGroup> groupMultipartMessages(List<PnrMessage> messages) {
        Map<String, PnrMultipartGroup> groups = new HashMap<>();

        for (PnrMessage message : messages) {
            String groupKey = message.getGroupId();
            
            PnrMultipartGroup group = groups.get(groupKey);
            if (group == null) {
                group = new PnrMultipartGroup(groupKey, 
                    message.getMessageReferenceNumber(), 
                    message.getFlightNumber());
                groups.put(groupKey, group);
            }
            
            group.addPart(message);
        }

        return new ArrayList<>(groups.values());
    }

    /**
     * Analyze multipart message completeness
     * Only analyzes groups that match the specified flight criteria
     * Note: Output messages are always single-part and don't need completeness analysis
     */
    private void analyzeCompleteness(List<PnrMultipartGroup> groups, PnrExtractionResult result,
                                   String flightNumber, String departureDate, 
                                   String departureAirport, String arrivalAirport) {
        int completeGroups = 0;
        int incompleteGroups = 0;

        for (PnrMultipartGroup group : groups) {
            // Only analyze groups that match the target flight criteria
            if (!groupMatchesFlightCriteria(group, flightNumber, departureDate, departureAirport, arrivalAirport)) {
                continue; // Skip groups that don't match the target flight
            }
            
            // Skip completeness analysis for output messages (they are always single-part)
            boolean hasOutputMessages = group.getParts().stream()
                .anyMatch(msg -> msg.getDirection() == com.l3.logparser.enums.MessageType.OUTPUT);
            
            if (hasOutputMessages) {
                completeGroups++; // Output messages are always complete
                continue;
            }
            
            if (group.isComplete() && group.hasAllParts()) {
                completeGroups++;
            } else {
                incompleteGroups++;
                List<Integer> missingParts = group.getMissingParts();
                if (!missingParts.isEmpty()) {
                    // Include departure date in warning for better identification
                    String flightInfo = group.getFlightNumber();
                    if (group.getParts() != null && !group.getParts().isEmpty()) {
                        PnrMessage firstPart = group.getParts().iterator().next();
                        if (firstPart.getFlightDetails() != null && firstPart.getFlightDetails().getDepartureDate() != null) {
                            flightInfo += " (" + firstPart.getFlightDetails().getDepartureDate() + ")";
                        }
                    }
                    result.addWarning("Incomplete multipart message for flight " + 
                        flightInfo + ". Missing parts: " + missingParts);
                }
            }
        }

        result.setCompleteGroups(completeGroups);
        result.setIncompleteGroups(incompleteGroups);
    }

    /**
     * Check if a multipart group matches the specified flight criteria
     */
    private boolean groupMatchesFlightCriteria(PnrMultipartGroup group,
                                             String flightNumber,
                                             String departureDate,
                                             String departureAirport,
                                             String arrivalAirport) {
        if (group.getParts() == null || group.getParts().isEmpty()) {
            return false;
        }
        
        // Check any part of the group - if any part matches, the group matches
        for (PnrMessage message : group.getParts()) {
            if (matchesFlightCriteria(message, flightNumber, departureDate, departureAirport, arrivalAirport)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Filter messages based on additional criteria
     */
    private List<PnrMessage> filterMessages(List<PnrMessage> messages,
                                           String flightNumber,
                                           String departureDate,
                                           String departureAirport,
                                           String arrivalAirport) {

        return messages.stream()
                .filter(message -> matchesFlightCriteria(message, flightNumber, 
                    departureDate, departureAirport, arrivalAirport))
                .collect(Collectors.toList());
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

    /**
     * Check if message matches the specified flight criteria
     * ALL specified criteria must match for the message to be considered a match
     */
    private boolean matchesFlightCriteria(PnrMessage message,
                                        String flightNumber,
                                        String departureDate,
                                        String departureAirport,
                                        String arrivalAirport) {

        // Check flight number if specified
        if (flightNumber != null && !flightNumber.trim().isEmpty()) {
            String targetFlight = flightNumber.trim().toUpperCase();
            String messageFlight = message.getFlightNumber();
            
            if (messageFlight == null) {
                return false;
            }
            
            messageFlight = messageFlight.toUpperCase();
            
            // Check if flight number matches (exact, normalized, or contains)
            boolean flightMatches = false;
            if (messageFlight.equals(targetFlight)) {
                flightMatches = true;
            } else {
                // Check normalized match (handle leading zeros)
                String normalizedMessage = normalizeFlightNumber(messageFlight);
                String normalizedTarget = normalizeFlightNumber(targetFlight);
                if (normalizedMessage.equals(normalizedTarget)) {
                    flightMatches = true;
                } else if (messageFlight.contains(targetFlight) || targetFlight.contains(messageFlight)) {
                    flightMatches = true;
                }
            }
            
            if (!flightMatches) {
                return false; // Flight number doesn't match, so this message doesn't match
            }
        }

        // Get flight details for additional criteria checking
        PnrFlightDetails details = message.getFlightDetails();
        
        // Check departure date if specified
        if (departureDate != null && !departureDate.trim().isEmpty()) {
            if (details == null) {
                return false; // No flight details available, can't match departure date
            }
            String targetDate = departureDate.trim();
            String messageDate = details.getDepartureDate();
            if (messageDate == null || !messageDate.equals(targetDate)) {
                return false; // Departure date doesn't match
            }
        }

        // Check departure airport if specified
        if (departureAirport != null && !departureAirport.trim().isEmpty()) {
            if (details == null) {
                return false; // No flight details available, can't match departure airport
            }
            String targetAirport = departureAirport.trim().toUpperCase();
            String messageAirport = details.getDepartureAirport();
            if (messageAirport == null || !messageAirport.toUpperCase().equals(targetAirport)) {
                return false; // Departure airport doesn't match
            }
        }

        // Check arrival airport if specified
        if (arrivalAirport != null && !arrivalAirport.trim().isEmpty()) {
            if (details == null) {
                return false; // No flight details available, can't match arrival airport
            }
            String targetAirport = arrivalAirport.trim().toUpperCase();
            String messageAirport = details.getArrivalAirport();
            if (messageAirport == null || !messageAirport.toUpperCase().equals(targetAirport)) {
                return false; // Arrival airport doesn't match
            }
        }

        // If we reach here, all specified criteria match
        return true;
    }

    /**
     * Save extracted PNR messages to files
     * Input messages go to "input" subdirectory, output messages go to "output" subdirectory
     */
    public boolean saveExtractedMessages(List<PnrMessage> messages, String outputDirectory) {
        try {
            Path outputDir = Paths.get(outputDirectory);
            Files.createDirectories(outputDir);

            // Create input and output subdirectories
            Path inputDir = outputDir.resolve("input");
            Path outputOutputDir = outputDir.resolve("output");
            Files.createDirectories(inputDir);
            Files.createDirectories(outputOutputDir);

            // Separate messages by direction
            List<PnrMessage> inputMessages = messages.stream()
                .filter(m -> m.getDirection() == null || m.getDirection() == com.l3.logparser.enums.MessageType.INPUT)
                .collect(Collectors.toList());
            
            List<PnrMessage> outputMessages = messages.stream()
                .filter(m -> m.getDirection() == com.l3.logparser.enums.MessageType.OUTPUT)
                .collect(Collectors.toList());

            // Save input messages
            if (!inputMessages.isEmpty()) {
                saveMessagesByDirection(inputMessages, inputDir, "INPUT");
            }
            
            // Save output messages
            if (!outputMessages.isEmpty()) {
                saveMessagesByDirection(outputMessages, outputOutputDir, "OUTPUT");
            }

            return true;

        } catch (IOException e) {
            System.err.println("Error saving PNR messages: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Save messages by direction to the specified directory
     */
    private void saveMessagesByDirection(List<PnrMessage> messages, Path directory, String directionLabel) throws IOException {
        // Save each message part as a separate file
        for (PnrMessage message : messages) {
            String flightNumber = message.getFlightNumber() != null ? message.getFlightNumber() : "UNKNOWN";
            
            // Create filename with part number for individual files
            String filename = "PNR_" + flightNumber + "_Part" + message.getPartNumber() + 
                            "_" + message.getPartIndicator() + "_" + directionLabel + ".txt";
            Path outputFile = directory.resolve(filename);

            // Write individual message to file
            try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
                writer.write(message.getRawContent());
                writer.write("\n");
            }

            System.out.println("Saved " + directionLabel.toLowerCase() + " message part " + 
                             message.getPartNumber() + " to: " + outputFile);
        }
    }

    /**
     * Group messages by flight number for file organization
     */
    private Map<String, List<PnrMessage>> groupMessagesByFlight(List<PnrMessage> messages) {
        Map<String, List<PnrMessage>> groups = new HashMap<>();

        for (PnrMessage message : messages) {
            String flightNumber = message.getFlightNumber() != null ? message.getFlightNumber() : "UNKNOWN";
            groups.computeIfAbsent(flightNumber, k -> new ArrayList<>()).add(message);
        }

        return groups;
    }
    
    /**
     * Group messages by flight number and direction for backward compatibility
     * @deprecated Use groupMessagesByFlight instead
     */
    @Deprecated
    private Map<String, List<PnrMessage>> groupMessagesByFlightAndDirection(List<PnrMessage> messages) {
        Map<String, List<PnrMessage>> groups = new HashMap<>();

        for (PnrMessage message : messages) {
            String flightNumber = message.getFlightNumber() != null ? message.getFlightNumber() : "UNKNOWN";
            String direction = message.getDirection() != null ? message.getDirection().toString() : "UNKNOWN";
            String key = flightNumber + "_" + direction;

            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(message);
        }

        return groups;
    }

    /**
     * Result class for PNR extraction operations
     */
    public static class PnrExtractionResult {
        private String flightNumber;
        private String logDirectoryPath;
        private List<PnrMessage> extractedMessages = new ArrayList<>();
        private List<PnrMultipartGroup> multipartGroups = new ArrayList<>();
        private List<String> processedFiles = new ArrayList<>();
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private boolean success = false;
        private int completeGroups = 0;
        private int incompleteGroups = 0;

        // Getters and Setters
        public String getFlightNumber() { return flightNumber; }
        public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }

        public String getLogDirectoryPath() { return logDirectoryPath; }
        public void setLogDirectoryPath(String logDirectoryPath) { this.logDirectoryPath = logDirectoryPath; }

        public List<PnrMessage> getExtractedMessages() { return extractedMessages; }
        public void setExtractedMessages(List<PnrMessage> extractedMessages) { this.extractedMessages = extractedMessages; }

        public List<PnrMultipartGroup> getMultipartGroups() { return multipartGroups; }
        public void setMultipartGroups(List<PnrMultipartGroup> multipartGroups) { this.multipartGroups = multipartGroups; }

        public List<String> getProcessedFiles() { return processedFiles; }
        public void addProcessedFile(String file) { this.processedFiles.add(file); }

        public List<String> getErrors() { return errors; }
        public void addError(String error) { this.errors.add(error); }

        public List<String> getWarnings() { return warnings; }
        public void addWarning(String warning) { this.warnings.add(warning); }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public int getCompleteGroups() { return completeGroups; }
        public void setCompleteGroups(int completeGroups) { this.completeGroups = completeGroups; }

        public int getIncompleteGroups() { return incompleteGroups; }
        public void setIncompleteGroups(int incompleteGroups) { this.incompleteGroups = incompleteGroups; }
    }
}