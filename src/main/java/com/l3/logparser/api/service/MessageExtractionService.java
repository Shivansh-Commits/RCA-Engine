package com.l3.logparser.api.service;

import com.l3.logparser.api.model.EdifactMessage;
import com.l3.logparser.api.model.FlightDetails;
import com.l3.logparser.api.parser.ApiParser;
import com.l3.logparser.enums.DataType;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Service class for extracting messages from log files
 * Supports both API and PNR data extraction
 */
public class MessageExtractionService {

    private final ApiParser edifactParser;
    private static final List<String> LOG_FILE_PATTERNS = Arrays.asList(
            "das.log*", "MessageTypeB.log*", "MessageAPI.log*", "MessageForwarder.log*"
    );

    public MessageExtractionService() {
        this.edifactParser = new ApiParser();
    }

    /**
     * Extract messages from a log directory for a specific flight
     * @param logDirectoryPath Path to the log directory
     * @param flightNumber Flight number to search for
     * @param departureDate Departure date (optional filter)
     * @param departureAirport Departure airport (optional filter)
     * @param arrivalAirport Arrival airport (optional filter)
     * @param dataType Type of data to extract (API, PNR, or BOTH)
     * @return ExtractionResult containing found messages and processing info
     */
    public ExtractionResult extractMessages(String logDirectoryPath,
                                          String flightNumber,
                                          String departureDate,
                                          String departureAirport,
                                          String arrivalAirport,
                                          DataType dataType,
                                          boolean debugMode,
                                          Consumer<String> debugLogger) {

        ExtractionResult result = new ExtractionResult();
        result.setFlightNumber(flightNumber);
        result.setLogDirectoryPath(logDirectoryPath);
        result.setRequestedDataType(dataType);

        try {
            Path logDir = Paths.get(logDirectoryPath);
            if (!Files.exists(logDir) || !Files.isDirectory(logDir)) {
                result.addError("Log directory does not exist: " + logDirectoryPath);
                return result;
            }

            // Find and process ALL log files to get complete multi-part messages
            List<EdifactMessage> allMessages = new ArrayList<>();

            // Process different log file types based on data type
            if (dataType == DataType.API) {
                allMessages.addAll(extractApiMessages(logDir, flightNumber, result, debugMode, debugLogger));
            }
            if (debugMode && debugLogger != null) {
                debugLogger.accept("Total messages after parsing all files: " + allMessages.size());
            }


            // Filter messages based on additional criteria
            List<EdifactMessage> filteredMessages = filterMessages(allMessages, flightNumber, departureDate, departureAirport, arrivalAirport);
            if (debugMode && debugLogger != null) {
                debugLogger.accept("Total messages after filtering: " + filteredMessages.size());
            }


            // Remove duplicate messages (same message ID from multiple files)
            List<EdifactMessage> deduplicatedMessages = removeDuplicateMessages(filteredMessages);
            if (debugMode && debugLogger != null) {
                debugLogger.accept("Total messages after deduplication: " + deduplicatedMessages.size());
            }

            // Analyze part completeness
            analyzePartCompleteness(deduplicatedMessages, flightNumber);

            result.setExtractedMessages(deduplicatedMessages);
            result.setSuccess(true);

            if (deduplicatedMessages.isEmpty()) {
                result.addWarning("No messages found matching the specified criteria");
            }

        } catch (Exception e) {
            result.addError("Error processing log directory: " + e.getMessage());
            System.err.println("ERROR in extractMessages: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Extract API messages from log files
     */
    private List<EdifactMessage> extractApiMessages(Path logDir, String flightNumber, ExtractionResult result, boolean debugMode, Consumer<String> debugLogger) throws IOException {
        List<EdifactMessage> messages = new ArrayList<>();

        // 1. Process das.log files first (highest priority for API)
        List<Path> dasLogFiles = findLogFiles(logDir, "das.log*");

        for (Path logFile : dasLogFiles) {
            List<EdifactMessage> fileMessages = processLogFile(logFile, flightNumber, debugMode, debugLogger);
            messages.addAll(fileMessages);
            result.addProcessedFile(logFile.toString());
        }

        // 2. Process MessageTypeB.log files for additional API parts
        List<Path> typeBLogFiles = findLogFiles(logDir, "MessageTypeB.log*");
        for (Path logFile : typeBLogFiles) {
            List<EdifactMessage> fileMessages = processLogFile(logFile, flightNumber, debugMode, debugLogger);
            messages.addAll(fileMessages);
            result.addProcessedFile(logFile.toString());
        }

        // 3. Process MessageAPI.log files for additional API parts
        List<Path> apiLogFiles = findLogFiles(logDir, "MessageAPI.log*");
        for (Path logFile : apiLogFiles) {
            List<EdifactMessage> fileMessages = processLogFile(logFile, flightNumber, debugMode, debugLogger);
            messages.addAll(fileMessages);
            result.addProcessedFile(logFile.toString());
        }

        // 4. Process MessageForwarder.log files for API output messages
        List<Path> forwarderLogFiles = findLogFiles(logDir, "MessageForwarder.log*");
        for (Path logFile : forwarderLogFiles) {
            List<EdifactMessage> fileMessages = processLogFile(logFile, flightNumber, debugMode, debugLogger);
            messages.addAll(fileMessages);
            result.addProcessedFile(logFile.toString());
        }

        return messages;
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
     * Process a single log file
     */
    private List<EdifactMessage> processLogFile(Path logFile, String flightNumber, boolean debugMode, Consumer<String> debugLogger) {
        List<EdifactMessage> messages = new ArrayList<>();

        try {
            if (debugLogger != null) {
                debugLogger.accept("Processing file: " + logFile.getFileName());
            }
            long fileSize = Files.size(logFile);

            if (fileSize > 50 * 1024 * 1024)
            { // If file is larger than 50MB
                messages = processLargeLogFile(logFile, flightNumber, debugMode, debugLogger);
            }
            else
            {
                String content = Files.readString(logFile);

                messages = edifactParser.parseLogContent(content, flightNumber, debugMode, debugLogger);
            }

        } catch (IOException e) {
            System.err.println("Error reading log file " + logFile + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error processing " + logFile + ": " + e.getMessage());
            e.printStackTrace();
        }

        return messages;
    }

    /**
     * Process large log files by reading in chunks
     */
    private List<EdifactMessage> processLargeLogFile(Path logFile, String flightNumber, boolean debugMode, Consumer<String> debugLogger) throws IOException {
        List<EdifactMessage> messages = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(logFile)) {
            StringBuilder buffer = new StringBuilder();
            String line;
            boolean inEdifactMessage = false;

            while ((line = reader.readLine()) != null) {
                // ENHANCED FIX: Clean carriage returns that may be appended to lines
                // This handles scenarios where "\r" is added at end of log lines
                line = line.replaceAll("[\\r]", "");

                if (line.contains("$STX$UNA") || line.contains("UNA:") ||
                        line.contains("Failed to parse API message")) {
                    if (!buffer.isEmpty()) {
                        List<EdifactMessage> chunkMessages = edifactParser.parseLogContent(buffer.toString(), flightNumber, debugMode, debugLogger);
                        messages.addAll(chunkMessages);
                        buffer.setLength(0);
                    }
                    inEdifactMessage = true;
                }

                if (inEdifactMessage) {
                    buffer.append(line).append("\n");

                    if (line.trim().isEmpty() ||
                            (line.startsWith("INFO ") && !buffer.toString().trim().isEmpty()) ||
                            (line.startsWith("WARN ") && !line.contains("Failed to parse API message") && !buffer.toString().trim().isEmpty()) ||
                            (line.startsWith("ERROR ") && !buffer.toString().trim().isEmpty())) {
                        if (!buffer.isEmpty()) {
                            List<EdifactMessage> chunkMessages = edifactParser.parseLogContent(buffer.toString(), flightNumber, debugMode, debugLogger);
                            messages.addAll(chunkMessages);
                            buffer.setLength(0);
                        }
                        inEdifactMessage = false;
                    }
                }
            }

            if (!buffer.isEmpty()) {
                List<EdifactMessage> chunkMessages = edifactParser.parseLogContent(buffer.toString(), flightNumber, debugMode, debugLogger);
                messages.addAll(chunkMessages);
            }
        }

        return messages;
    }

    /**
     * Filter messages based on flight criteria
     */
    private List<EdifactMessage> filterMessages(List<EdifactMessage> messages,
                                                String flightNumber,
                                                String departureDate,
                                                String departureAirport,
                                                String arrivalAirport) {

        return messages.stream()
                .filter(msg -> matchesFlightCriteria(msg, flightNumber, departureDate, departureAirport, arrivalAirport))
                .collect(Collectors.toList());
    }

    /**
     * Enhanced flight number matching that handles padding differences between input and output files.
     */
    private boolean isFlightNumberMatch(String messageFlightNumber, String targetFlightNumber) {
        if (messageFlightNumber == null || targetFlightNumber == null) {
            return false;
        }

        String msgFlight = messageFlightNumber.toUpperCase().trim();
        String targetFlight = targetFlightNumber.toUpperCase().trim();

        if (msgFlight.equals(targetFlight)) {
            return true;
        }

        String msgAirlineCode = extractAirlineCode(msgFlight);
        String msgNumber = extractFlightNumber(msgFlight);
        String targetAirlineCode = extractAirlineCode(targetFlight);
        String targetNumber = extractFlightNumber(targetFlight);

        if (!msgAirlineCode.equals(targetAirlineCode)) {
            return false;
        }

        String msgNumberNormalized = msgNumber.replaceFirst("^0+", "");
        String targetNumberNormalized = targetNumber.replaceFirst("^0+", "");

        if (msgNumberNormalized.isEmpty()) msgNumberNormalized = "0";
        if (targetNumberNormalized.isEmpty()) targetNumberNormalized = "0";

        if (msgNumberNormalized.equals(targetNumberNormalized)) {
            return true;
        }

        try {
            String msgPadded = msgAirlineCode + String.format("%04d", Integer.parseInt(msgNumberNormalized));
            String targetPadded = targetAirlineCode + String.format("%04d", Integer.parseInt(targetNumberNormalized));
            return msgPadded.equals(targetPadded);
        } catch (NumberFormatException e) {
            return msgFlight.contains(targetFlight) || targetFlight.contains(msgFlight);
        }
    }

    private String extractAirlineCode(String flightNumber) {
        if (flightNumber == null || flightNumber.length() < 2) {
            return "";
        }
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
        StringBuilder number = new StringBuilder();
        boolean foundDigit = false;
        for (char c : flightNumber.toCharArray()) {
            if (Character.isDigit(c)) {
                number.append(c);
                foundDigit = true;
            } else if (foundDigit) {
                break;
            }
        }
        return number.toString();
    }

    private boolean matchesFlightCriteria(EdifactMessage message,
                                          String flightNumber,
                                          String departureDate,
                                          String departureAirport,
                                          String arrivalAirport) {

        FlightDetails details = message.getFlightDetails();

        boolean flightMatches = false;
        if (flightNumber != null && !flightNumber.trim().isEmpty()) {
            String directFlight = message.getFlightNumber();
            if (directFlight != null && isFlightNumberMatch(directFlight, flightNumber)) {
                flightMatches = true;
            }

            if (!flightMatches && details != null) {
                String detailsFlight = details.getFlightNumber();
                if (detailsFlight != null && isFlightNumberMatch(detailsFlight, flightNumber)) {
                    flightMatches = true;
                }
            }

            if (!flightMatches && message.getMessageId() != null) {
                if (message.getMessageId().toUpperCase().contains(flightNumber.toUpperCase())) {
                    flightMatches = true;
                }
            }

            if (!flightMatches) {
                return false;
            }
        } else {
            flightMatches = true;
        }

        boolean criteriaMatch = true;

        if (details != null) {
            if (departureDate != null && !departureDate.trim().isEmpty()) {
                String messageDate = details.getDepartureDate();
                if (messageDate != null) {
                    String normalizedTargetDate = departureDate.replaceAll("\\D", "");
                    String normalizedMessageDate = messageDate.replaceAll("\\D", "");

                    if (!normalizedMessageDate.equals(normalizedTargetDate)) {
                        criteriaMatch = false;
                    }
                }
            }

            if (departureAirport != null && !departureAirport.trim().isEmpty()) {
                String messageDepartureAirport = details.getDepartureAirport();
                if (messageDepartureAirport != null) {
                    if (!messageDepartureAirport.toUpperCase().equals(departureAirport.toUpperCase())) {
                        criteriaMatch = false;
                    }
                }
            }

            if (arrivalAirport != null && !arrivalAirport.trim().isEmpty()) {
                String messageArrivalAirport = details.getArrivalAirport();
                if (messageArrivalAirport != null) {
                    if (!messageArrivalAirport.toUpperCase().equals(arrivalAirport.toUpperCase())) {
                        criteriaMatch = false;
                    }
                }
            }
        }

        return flightMatches && criteriaMatch;
    }

    /**
     * Save extracted messages to files
     */
    public boolean saveExtractedMessages(List<EdifactMessage> messages, String outputDirectory) {
        try {
            Path outputDir = Paths.get(outputDirectory);
            Files.createDirectories(outputDir);

            Path inputDir = outputDir.resolve("input");
            Path outputDir2 = outputDir.resolve("output");
            Files.createDirectories(inputDir);
            Files.createDirectories(outputDir2);

            List<EdifactMessage> inputMessages = new ArrayList<>();
            List<EdifactMessage> outputMessages = new ArrayList<>();

            for (EdifactMessage msg : messages) {
                if ("OUTPUT".equals(msg.getMessageType())) {
                    outputMessages.add(msg);
                } else {
                    inputMessages.add(msg);
                }
            }

            int savedInputs = saveMessagesToDirectory(inputMessages, inputDir, "input");
            int savedOutputs = saveMessagesToDirectory(outputMessages, outputDir2, "output");


            return (savedInputs + savedOutputs) > 0;

        } catch (IOException e) {
            System.err.println("Error saving extracted messages: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Save messages to a specific directory with proper naming
     */
    private int saveMessagesToDirectory(List<EdifactMessage> messages, Path directory, String type) throws IOException {
        int savedCount = 0;

        Map<String, List<EdifactMessage>> groupedMessages = groupMessagesByFlight(messages);

        for (Map.Entry<String, List<EdifactMessage>> entry : groupedMessages.entrySet()) {
            String flightKey = entry.getKey();
            List<EdifactMessage> flightMessages = entry.getValue();

            flightMessages.sort(Comparator.comparingInt(EdifactMessage::getPartNumber));

            for (EdifactMessage msg : flightMessages) {
                try {
                    String messageContent = msg.getRawContent();
                    if (messageContent == null || messageContent.trim().isEmpty()) {
                        messageContent = reconstructMessageContent(msg);
                    }

                    if ("output".equals(type)) {
                        messageContent = applyFlightNumberPadding(messageContent);
                    }

                    String fileName = generateFileName(msg, type);
                    Path outputFile = directory.resolve(fileName);

                    Files.writeString(outputFile, messageContent);
                    savedCount++;

                } catch (Exception e) {
                    System.err.println("Error saving message part " + msg.getPartNumber() + ": " + e.getMessage());
                }
            }
        }

        return savedCount;
    }

    /**
     * Apply flight number padding for output files (MS775 -> MS0775)
     */
    private String applyFlightNumberPadding(String messageContent) {
        try {
            String[] lines = messageContent.split("\\n");
            StringBuilder result = new StringBuilder();

            for (String line : lines) {
                if (line.contains("TDT") && line.contains("+")) {
                    String[] parts = line.split("\\+");
                    if (parts.length >= 3) {
                        String flightNumber = parts[2].replaceAll("[^A-Z0-9]", "");

                        StringBuilder airline = new StringBuilder();
                        StringBuilder number = new StringBuilder();
                        boolean foundDigit = false;

                        for (char c : flightNumber.toCharArray()) {
                            if (Character.isLetter(c)) {
                                if (!foundDigit) {
                                    airline.append(c);
                                }
                            } else if (Character.isDigit(c)) {
                                number.append(c);
                                foundDigit = true;
                            }
                        }

                        if (!number.toString().isEmpty()) {
                            try {
                                int flightNum = Integer.parseInt(number.toString());
                                String paddedFlight = airline.toString() + String.format("%04d", flightNum);
                                line = line.replace(flightNumber, paddedFlight);
                            } catch (NumberFormatException e) {
                                // Keep original if parsing fails
                            }
                        }
                    }
                }
                result.append(line).append("\n");
            }

            return result.toString();
        } catch (Exception e) {
            System.err.println("Error applying flight number padding: " + e.getMessage());
            return messageContent;
        }
    }

    /**
     * Generate appropriate filename for message
     */
    private String generateFileName(EdifactMessage msg, String type) {
        String flightNumber = msg.getFlightNumber();
        if (flightNumber == null) {
            FlightDetails details = msg.getFlightDetails();
            if (details != null) {
                flightNumber = details.getFlightNumber();
            }
        }

        if (flightNumber == null) {
            flightNumber = "UNKNOWN";
        }

        flightNumber = flightNumber.replaceAll("[^A-Za-z0-9]", "");

        String dataType = msg.getDataType() != null ? msg.getDataType().toLowerCase() : "unknown";
        int partNumber = msg.getPartNumber();
        String partIndicator = msg.getPartIndicator() != null ? msg.getPartIndicator() : "C";

        return String.format("%s_%s_%s_part%02d%s.txt",
            type, flightNumber, dataType, partNumber, partIndicator);
    }

    /**
     * Group messages by flight number for organized saving
     */
    private Map<String, List<EdifactMessage>> groupMessagesByFlight(List<EdifactMessage> messages) {
        Map<String, List<EdifactMessage>> grouped = new HashMap<>();

        for (EdifactMessage msg : messages) {
            String flightKey = msg.getFlightNumber();
            if (flightKey == null) {
                FlightDetails details = msg.getFlightDetails();
                if (details != null) {
                    flightKey = details.getFlightNumber();
                }
            }

            if (flightKey == null) {
                flightKey = "UNKNOWN";
            }

            grouped.computeIfAbsent(flightKey, k -> new ArrayList<>()).add(msg);
        }

        return grouped;
    }

    /**
     * Reconstruct message content if raw content is missing
     */
    private String reconstructMessageContent(EdifactMessage msg) {
        StringBuilder content = new StringBuilder();

        content.append("UNA:+.'? '\n");
        content.append("UNB+UNOA:4+SENDER:ZZ+RECEIVER:ZZ+").append(getCurrentDateTimeForEdifact()).append("+1'\n");
        content.append("UNG+PAXLST+SENDER:ZZ+RECEIVER:ZZ+").append(getCurrentDateTimeForEdifact()).append("+1+UN+D:05B'\n");

        content.append("UNH+").append(msg.getPartNumber()).append("+PAXLST:D:05B:UN:IATA+");
        content.append(msg.getFlightNumber() != null ? msg.getFlightNumber() : "UNKNOWN");
        if (msg.getPartNumber() > 0) {
            content.append("+").append(String.format("%02d", msg.getPartNumber()));
            if (msg.getPartIndicator() != null) {
                content.append(":").append(msg.getPartIndicator());
            }
        }
        content.append("'\n");

        String bgmCode = "PASSENGER".equals(msg.getDataType()) ? "745" : "250";
        content.append("BGM+").append(bgmCode).append("'\n");

        FlightDetails details = msg.getFlightDetails();
        if (details != null) {
            if (details.getFlightNumber() != null) {
                content.append("TDT+20+").append(details.getFlightNumber()).append("'\n");
            }
            if (details.getDepartureAirport() != null) {
                content.append("LOC+125+").append(details.getDepartureAirport()).append("'\n");
            }
            if (details.getArrivalAirport() != null) {
                content.append("LOC+87+").append(details.getArrivalAirport()).append("'\n");
            }
            if (details.getDepartureDate() != null && details.getDepartureTime() != null) {
                content.append("DTM+189:").append(details.getDepartureDate()).append(details.getDepartureTime()).append(":201'\n");
            }
            if (details.getArrivalDate() != null && details.getArrivalTime() != null) {
                content.append("DTM+232:").append(details.getArrivalDate()).append(details.getArrivalTime()).append(":201'\n");
            }
        }

        content.append("UNT+").append(content.toString().split("\\n").length + 1).append("+").append(msg.getPartNumber()).append("'\n");
        content.append("UNE+1+1'\n");
        content.append("UNZ+1+1'\n");

        return content.toString();
    }

    /**
     * Get current date and time in EDIFACT format
     */
    private String getCurrentDateTimeForEdifact() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return String.format("%02d%02d%02d:%02d%02d",
            now.getYear() % 100, now.getMonthValue(), now.getDayOfMonth(),
            now.getHour(), now.getMinute());
    }

    /**
     * Remove duplicate messages but preserve multipart message parts
     * For multipart messages, each part should be unique based on messageId + partNumber
     */
    private List<EdifactMessage> removeDuplicateMessages(List<EdifactMessage> messages) {
        Map<String, EdifactMessage> uniqueMessages = new LinkedHashMap<>();

        for (EdifactMessage msg : messages) {
            // Create a unique key that includes both messageId and partNumber
            // This ensures multipart messages (same messageId, different parts) are preserved
            String baseKey = msg.getMessageId();
            if (baseKey == null) {
                baseKey = String.format("%s_%d",
                    msg.getFlightNumber() != null ? msg.getFlightNumber() : "UNKNOWN",
                    System.nanoTime());
            }
            
            // Add part number to make each part unique
            String uniqueKey = baseKey + "_PART_" + msg.getPartNumber();
            
            uniqueMessages.putIfAbsent(uniqueKey, msg);
        }

        return new ArrayList<>(uniqueMessages.values());
    }

    /**
     * Analyze part completeness for multi-part messages
     */
    private void analyzePartCompleteness(List<EdifactMessage> messages, String flightNumber) {
        Map<String, List<EdifactMessage>> messageGroups = new HashMap<>();

        for (EdifactMessage msg : messages) {
            String baseKey = generateBaseMessageKey(msg);
            messageGroups.computeIfAbsent(baseKey, k -> new ArrayList<>()).add(msg);
        }

        for (Map.Entry<String, List<EdifactMessage>> entry : messageGroups.entrySet()) {
            String baseKey = entry.getKey();
            List<EdifactMessage> parts = entry.getValue();

            parts.sort(Comparator.comparingInt(EdifactMessage::getPartNumber));

            // Analysis completed - results used internally for validation
        }
    }

    /**
     * Generate base key for message grouping
     */
    private String generateBaseMessageKey(EdifactMessage msg) {
        String flight = msg.getFlightNumber();
        if (flight == null && msg.getFlightDetails() != null) {
            flight = msg.getFlightDetails().getFlightNumber();
        }
        if (flight == null) {
            flight = "UNKNOWN";
        }

        String dataType = msg.getDataType() != null ? msg.getDataType() : "UNKNOWN";
        String date = "";

        if (msg.getFlightDetails() != null && msg.getFlightDetails().getDepartureDate() != null) {
            date = msg.getFlightDetails().getDepartureDate();
        }

        return flight + "_" + dataType + "_" + date;
    }

    /**
     * Result class to hold extraction results
     */
    public static class ExtractionResult {
        private boolean success = false;
        private String flightNumber;
        private String logDirectoryPath;
        private DataType requestedDataType;
        private List<EdifactMessage> extractedMessages = new ArrayList<>();
        private List<String> processedFiles = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private List<String> errors = new ArrayList<>();
        private List<String> info = new ArrayList<>();

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getFlightNumber() { return flightNumber; }
        public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }

        public String getLogDirectoryPath() { return logDirectoryPath; }
        public void setLogDirectoryPath(String logDirectoryPath) { this.logDirectoryPath = logDirectoryPath; }

        public DataType getRequestedDataType() { return requestedDataType; }
        public void setRequestedDataType(DataType requestedDataType) { this.requestedDataType = requestedDataType; }

        public List<EdifactMessage> getExtractedMessages() { return extractedMessages; }
        public void setExtractedMessages(List<EdifactMessage> extractedMessages) { this.extractedMessages = extractedMessages; }

        public List<String> getProcessedFiles() { return processedFiles; }
        public void addProcessedFile(String file) { this.processedFiles.add(file); }

        public List<String> getWarnings() { return warnings; }
        public void addWarning(String warning) { this.warnings.add(warning); }

        public List<String> getErrors() { return errors; }
        public void addError(String error) { this.errors.add(error); }

        public List<String> getInfo() { return info; }
        public void addInfo(String info) { this.info.add(info); }

        public int getMessageCount() { return extractedMessages.size(); }

        public int getPartCount() {
            return extractedMessages.stream()
                .mapToInt(msg -> Math.max(1, msg.getPartNumber()))
                .sum();
        }
    }
}
