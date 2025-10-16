package com.l3.logparser.pnr.service;

import com.l3.logparser.pnr.model.PnrMessage;
import com.l3.logparser.pnr.parser.PnrEdifactParser;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service class for extracting PNR messages from MessageMHPNRGOV.log files
 * Handles file discovery, parsing, and extraction of all message parts
 */
public class PnrExtractionService {

    private final PnrEdifactParser pnrParser;
    private static final String PNR_LOG_PATTERN = "MessageMHPNRGOV.log*";

    public PnrExtractionService() {
        this.pnrParser = new PnrEdifactParser();
    }

    /**
     * Extract PNR messages from log directory for a specific flight
     */
    public PnrExtractionResult extractPnrMessages(String logDirectoryPath, String flightNumber) {
        PnrExtractionResult result = new PnrExtractionResult();
        result.setFlightNumber(flightNumber);
        result.setLogDirectoryPath(logDirectoryPath);

        try {
            System.out.println("DEBUG: Starting PNR extraction from: " + logDirectoryPath);
            System.out.println("DEBUG: Looking for flight: " + flightNumber);

            Path logDir = Paths.get(logDirectoryPath);
            if (!Files.exists(logDir) || !Files.isDirectory(logDir)) {
                result.addError("Log directory does not exist: " + logDirectoryPath);
                return result;
            }

            // Find all MessageMHPNRGOV.log files
            List<Path> pnrLogFiles = findPnrLogFiles(logDir);
            System.out.println("DEBUG: Found " + pnrLogFiles.size() + " PNR log files");

            if (pnrLogFiles.isEmpty()) {
                // Also try to find files with different patterns that might contain PNR data
                List<Path> allLogFiles = findAllLogFiles(logDir);
                System.out.println("DEBUG: Found " + allLogFiles.size() + " total log files in directory");

                // Check if any files contain PNRGOV content
                for (Path logFile : allLogFiles) {
                    if (containsPnrContent(logFile)) {
                        System.out.println("DEBUG: Found PNR content in file: " + logFile.getFileName());
                        pnrLogFiles.add(logFile);
                    }
                }

                if (pnrLogFiles.isEmpty()) {
                    result.addWarning("No MessageMHPNRGOV.log files found in directory: " + logDirectoryPath);
                    result.addInfo("Directory contains " + allLogFiles.size() + " log files but none contain PNR data");
                    return result;
                }
            }

            // Process all PNR log files
            List<PnrMessage> allMessages = new ArrayList<>();
            for (Path logFile : pnrLogFiles) {
                try {
                    System.out.println("DEBUG: Processing file: " + logFile.getFileName());
                    List<PnrMessage> fileMessages = processLogFile(logFile, flightNumber);
                    System.out.println("DEBUG: Extracted " + fileMessages.size() + " messages from " + logFile.getFileName());
                    allMessages.addAll(fileMessages);
                    result.addProcessedFile(logFile.toString());
                } catch (Exception e) {
                    System.err.println("ERROR: Failed to process file " + logFile + ": " + e.getMessage());
                    result.addError("Error processing file " + logFile + ": " + e.getMessage());
                }
            }

            System.out.println("DEBUG: Total messages before deduplication: " + allMessages.size());

            // Remove duplicates (same message ID and part number)
            List<PnrMessage> deduplicatedMessages = removeDuplicateMessages(allMessages);
            System.out.println("DEBUG: Messages after deduplication: " + deduplicatedMessages.size());

            // Group messages by message ID and analyze completeness
            Map<String, List<PnrMessage>> messageGroups = groupMessagesByMessageId(deduplicatedMessages);
            analyzeMessageCompleteness(messageGroups, result);

            result.setExtractedMessages(deduplicatedMessages);
            result.setMessageGroups(messageGroups);
            result.setSuccess(true);

            if (deduplicatedMessages.isEmpty()) {
                result.addWarning("No PNR messages found for flight: " + flightNumber);
                result.addInfo("Processed " + pnrLogFiles.size() + " files but found no matching messages");
            } else {
                result.addInfo("Successfully extracted " + deduplicatedMessages.size() + " message parts from " + messageGroups.size() + " messages");
            }

        } catch (Exception e) {
            result.addError("Error processing PNR log directory: " + e.getMessage());
            System.err.println("ERROR in extractPnrMessages: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Extract all PNR messages regardless of flight number (for complete extraction)
     */
    public PnrExtractionResult extractAllPnrMessages(String logDirectoryPath) {
        return extractPnrMessages(logDirectoryPath, null); // null means extract all flights
    }

    /**
     * Extract and save all message parts to input folder
     */
    public PnrExtractionResult extractAndSaveMessages(String logDirectoryPath, String outputDirectoryPath, String flightNumber) {
        PnrExtractionResult result = extractPnrMessages(logDirectoryPath, flightNumber);

        if (!result.isSuccess() || result.getExtractedMessages().isEmpty()) {
            return result;
        }

        try {
            // Create input directory
            Path inputDir = Paths.get(outputDirectoryPath, "input");
            Files.createDirectories(inputDir);

            // Save each message part as a separate file
            Map<String, List<PnrMessage>> messageGroups = result.getMessageGroups();
            int savedFiles = 0;

            for (Map.Entry<String, List<PnrMessage>> entry : messageGroups.entrySet()) {
                String messageId = entry.getKey();
                List<PnrMessage> parts = entry.getValue();

                // Sort parts by part number
                parts.sort(Comparator.comparingInt(PnrMessage::getPartNumber));

                for (PnrMessage part : parts) {
                    String fileName = generateFileName(part);
                    Path filePath = inputDir.resolve(fileName);

                    try {
                        Files.writeString(filePath, part.getRawContent());
                        savedFiles++;
                        result.addSavedFile(filePath.toString());
                    } catch (IOException e) {
                        result.addError("Failed to save file " + fileName + ": " + e.getMessage());
                    }
                }
            }

            result.addInfo("Successfully saved " + savedFiles + " message parts to " + inputDir);

        } catch (Exception e) {
            result.addError("Error creating output directory: " + e.getMessage());
        }

        return result;
    }

    /**
     * Find all MessageMHPNRGOV.log files in directory
     */
    private List<Path> findPnrLogFiles(Path directory) throws IOException {
        List<Path> files = new ArrayList<>();

        System.out.println("DEBUG: Searching for PNR log files with pattern: " + PNR_LOG_PATTERN);
        System.out.println("DEBUG: In directory: " + directory.toAbsolutePath());

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, PNR_LOG_PATTERN)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    long fileSize = Files.size(file);
                    System.out.println("DEBUG: Found PNR log file: " + file.getFileName() + " (size: " + fileSize + " bytes)");
                    files.add(file);
                }
            }
        }

        if (files.isEmpty()) {
            System.out.println("DEBUG: No files found matching pattern '" + PNR_LOG_PATTERN + "'");
        }

        return files.stream().sorted().collect(Collectors.toList());
    }

    /**
     * Find all log files in directory (not just MessageMHPNRGOV pattern)
     */
    private List<Path> findAllLogFiles(Path directory) throws IOException {
        List<Path> files = new ArrayList<>();

        System.out.println("DEBUG: Searching for all log files with pattern: *.log*");

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.log*")) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    long fileSize = Files.size(file);
                    System.out.println("DEBUG: Found log file: " + file.getFileName() + " (size: " + fileSize + " bytes)");
                    files.add(file);
                }
            }
        }

        // Also list all files in directory to see what's actually there
        System.out.println("DEBUG: All files in directory:");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    long fileSize = Files.size(file);
                    System.out.println("DEBUG:   " + file.getFileName() + " (size: " + fileSize + " bytes)");
                }
            }
        }

        return files.stream().sorted().collect(Collectors.toList());
    }

    /**
     * Check if a log file contains PNR content
     */
    private boolean containsPnrContent(Path logFile) {
        try {
            System.out.println("DEBUG: Checking file for PNR content: " + logFile.getFileName());

            // Read first few KB to check for PNRGOV content
            byte[] buffer = new byte[8192]; // 8KB should be enough to detect PNR content
            try (InputStream is = Files.newInputStream(logFile)) {
                int bytesRead = is.read(buffer);
                if (bytesRead > 0) {
                    String preview = new String(buffer, 0, bytesRead);

                    boolean hasPnrGov = preview.contains("PNRGOV");
                    boolean hasMessageMH = preview.contains("MessageMHPNRGOV");
                    boolean hasUNA = preview.contains("UNA");
                    boolean hasTVL = preview.contains("TVL");

                    System.out.println("DEBUG:   File content analysis for " + logFile.getFileName() + ":");
                    System.out.println("DEBUG:     Contains 'PNRGOV': " + hasPnrGov);
                    System.out.println("DEBUG:     Contains 'MessageMHPNRGOV': " + hasMessageMH);
                    System.out.println("DEBUG:     Contains 'UNA': " + hasUNA);
                    System.out.println("DEBUG:     Contains 'TVL': " + hasTVL);
                    System.out.println("DEBUG:     First 200 chars: " + preview.substring(0, Math.min(200, preview.length())).replaceAll("\\r?\\n", "\\n"));

                    return hasPnrGov || hasMessageMH;
                } else {
                    System.out.println("DEBUG:   File is empty: " + logFile.getFileName());
                }
            }
        } catch (IOException e) {
            System.err.println("ERROR: Error checking file " + logFile + " for PNR content: " + e.getMessage());
        }
        return false;
    }

    /**
     * Process a single PNR log file
     */
    private List<PnrMessage> processLogFile(Path logFile, String flightNumber) throws IOException {
        System.out.println("Processing PNR log file: " + logFile);

        long fileSize = Files.size(logFile);
        if (fileSize == 0) {
            System.out.println("Skipping empty file: " + logFile);
            return new ArrayList<>();
        }

        if (fileSize > 100 * 1024 * 1024) { // 100MB threshold
            return processLargeLogFile(logFile, flightNumber);
        } else {
            String content = Files.readString(logFile);
            return pnrParser.parseLogContent(content, flightNumber);
        }
    }

    /**
     * Process large log files by reading in chunks
     */
    private List<PnrMessage> processLargeLogFile(Path logFile, String flightNumber) throws IOException {
        List<PnrMessage> messages = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(logFile)) {
            StringBuilder currentEntry = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("INFO") && currentEntry.length() > 0) {
                    // Process the accumulated entry
                    String entry = currentEntry.toString();
                    if (entry.contains("PNRGOV_MESSAGE_HANDLER") || entry.contains("MessageMHPNRGOV")) {
                        List<PnrMessage> entryMessages = pnrParser.parseLogContent(entry, flightNumber);
                        messages.addAll(entryMessages);
                    }
                    currentEntry = new StringBuilder();
                }
                currentEntry.append(line).append("\n");
            }

            // Process the last entry
            if (currentEntry.length() > 0) {
                String entry = currentEntry.toString();
                if (entry.contains("PNRGOV_MESSAGE_HANDLER") || entry.contains("MessageMHPNRGOV")) {
                    List<PnrMessage> entryMessages = pnrParser.parseLogContent(entry, flightNumber);
                    messages.addAll(entryMessages);
                }
            }
        }

        return messages;
    }

    /**
     * Remove duplicate messages based on unique key (messageId + partNumber)
     */
    private List<PnrMessage> removeDuplicateMessages(List<PnrMessage> messages) {
        Map<String, PnrMessage> uniqueMessages = new LinkedHashMap<>();

        for (PnrMessage message : messages) {
            String key = message.getUniqueKey();
            if (!uniqueMessages.containsKey(key)) {
                uniqueMessages.put(key, message);
            }
        }

        return new ArrayList<>(uniqueMessages.values());
    }

    /**
     * Group messages by message ID
     */
    private Map<String, List<PnrMessage>> groupMessagesByMessageId(List<PnrMessage> messages) {
        return messages.stream()
                .collect(Collectors.groupingBy(
                    PnrMessage::getMessageId,
                    LinkedHashMap::new,
                    Collectors.toList()
                ));
    }

    /**
     * Analyze message completeness and add warnings/info
     */
    private void analyzeMessageCompleteness(Map<String, List<PnrMessage>> messageGroups, PnrExtractionResult result) {
        for (Map.Entry<String, List<PnrMessage>> entry : messageGroups.entrySet()) {
            String messageId = entry.getKey();
            List<PnrMessage> parts = entry.getValue();

            // Sort parts by part number
            parts.sort(Comparator.comparingInt(PnrMessage::getPartNumber));

            // Check for first and last parts
            boolean hasFirstPart = parts.stream().anyMatch(PnrMessage::isFirstPart);
            boolean hasLastPart = parts.stream().anyMatch(PnrMessage::isLastPart);

            // Check for gaps in part numbers
            Set<Integer> partNumbers = parts.stream()
                    .map(PnrMessage::getPartNumber)
                    .collect(Collectors.toSet());

            int minPart = Collections.min(partNumbers);
            int maxPart = Collections.max(partNumbers);
            List<Integer> missingParts = new ArrayList<>();

            for (int i = minPart; i <= maxPart; i++) {
                if (!partNumbers.contains(i)) {
                    missingParts.add(i);
                }
            }

            // Generate completion report
            if (!hasFirstPart) {
                result.addWarning("Message " + messageId + " is missing first part (C indicator)");
            }
            if (!hasLastPart) {
                result.addWarning("Message " + messageId + " is missing last part (F indicator)");
            }
            if (!missingParts.isEmpty()) {
                result.addWarning("Message " + messageId + " is missing parts: " + missingParts);
            }
            if (hasFirstPart && hasLastPart && missingParts.isEmpty()) {
                result.addInfo("Message " + messageId + " is complete (" + parts.size() + " parts)");
            }
        }
    }

    /**
     * Generate filename for a message part
     */
    private String generateFileName(PnrMessage message) {
        String flightInfo = "";
        if (message.getFlightDetails() != null) {
            flightInfo = "_" + message.getFlightDetails().getFullFlightNumber() +
                        "_" + message.getFlightDetails().getDepartureDate();
        }

        String partInfo = String.format("_part%02d", message.getPartNumber());
        if (message.isFirstPart()) {
            partInfo += "_first";
        } else if (message.isLastPart()) {
            partInfo += "_last";
        }

        return String.format("PNR_%s%s%s.edifact",
                message.getMessageId(), flightInfo, partInfo);
    }

    /**
     * Result class for PNR extraction operations
     */
    public static class PnrExtractionResult {
        private boolean success = false;
        private String flightNumber;
        private String logDirectoryPath;
        private List<PnrMessage> extractedMessages = new ArrayList<>();
        private Map<String, List<PnrMessage>> messageGroups = new HashMap<>();
        private List<String> processedFiles = new ArrayList<>();
        private List<String> savedFiles = new ArrayList<>();
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private List<String> info = new ArrayList<>();

        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getFlightNumber() { return flightNumber; }
        public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }

        public String getLogDirectoryPath() { return logDirectoryPath; }
        public void setLogDirectoryPath(String logDirectoryPath) { this.logDirectoryPath = logDirectoryPath; }

        public List<PnrMessage> getExtractedMessages() { return extractedMessages; }
        public void setExtractedMessages(List<PnrMessage> extractedMessages) { this.extractedMessages = extractedMessages; }

        public Map<String, List<PnrMessage>> getMessageGroups() { return messageGroups; }
        public void setMessageGroups(Map<String, List<PnrMessage>> messageGroups) { this.messageGroups = messageGroups; }

        public List<String> getProcessedFiles() { return processedFiles; }
        public void addProcessedFile(String file) { this.processedFiles.add(file); }

        public List<String> getSavedFiles() { return savedFiles; }
        public void addSavedFile(String file) { this.savedFiles.add(file); }

        public List<String> getErrors() { return errors; }
        public void addError(String error) { this.errors.add(error); }

        public List<String> getWarnings() { return warnings; }
        public void addWarning(String warning) { this.warnings.add(warning); }

        public List<String> getInfo() { return info; }
        public void addInfo(String info) { this.info.add(info); }

        public boolean hasErrors() { return !errors.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }

        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("PNR Extraction Summary:\n");
            sb.append("- Success: ").append(success).append("\n");
            sb.append("- Flight: ").append(flightNumber != null ? flightNumber : "ALL").append("\n");
            sb.append("- Messages found: ").append(extractedMessages.size()).append("\n");
            sb.append("- Message groups: ").append(messageGroups.size()).append("\n");
            sb.append("- Files processed: ").append(processedFiles.size()).append("\n");
            sb.append("- Files saved: ").append(savedFiles.size()).append("\n");
            sb.append("- Errors: ").append(errors.size()).append("\n");
            sb.append("- Warnings: ").append(warnings.size()).append("\n");
            return sb.toString();
        }
    }
}
