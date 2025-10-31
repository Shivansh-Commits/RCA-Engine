package com.l3.rcaengine.pnr;

import com.l3.rcaengine.pnr.model.*;
import com.l3.rcaengine.pnr.utils.PnrgovLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * PNRGOV processor that integrates with the L3-Engine UI
 * Provides result formatting compatible with the existing table structure
 */
public class PnrgovProcessor {
    
    private final PnrgovLogger logger;
    
    public PnrgovProcessor() {
        this.logger = new PnrgovLogger(false); // Disable file logging for UI integration
    }
    
    /**
     * Process PNRGOV comparison and return UI-compatible result
     */
    public PnrgovResult processFolder(File folder) throws Exception {
        logger.info("Starting PNRGOV processing for folder: " + folder.getAbsolutePath());
        
        // Create default configuration for PNRGOV processing
        PnrgovConfig config = new PnrgovConfig();
        config.setMode(PnrgovConfig.Mode.EDIFACT);
        config.setMatchingStrategy(PnrgovConfig.MatchingStrategy.PNR_NAME);
        config.setEnableLogging(false); // Enable logging for debugging
        
        // Create comparator and perform comparison
        PnrgovComparator comparator = new PnrgovComparator(config);
        ComparisonResult result = comparator.compare(folder);
        
        // Collect validation warnings from comparator
        List<String> validationWarnings = comparator.getSegmentValidationWarnings();
        
        // Debug logging
//        logger.info("=== COMPARISON RESULTS DEBUG ===");
//        logger.info("Total Input Passengers: " + result.getTotalInputPassengers());
//        logger.info("Total Output Passengers: " + result.getTotalOutputPassengers());
//        logger.info("Dropped Passenger Count: " + result.getDroppedPassengerCount());
//        logger.info("Processed Passenger Count: " + result.getProcessedPassengerCount());
//        logger.info("Added Passenger Count: " + result.getAddedPassengerCount());
//        logger.info("Duplicate Passenger Count: " + result.getDuplicatePassengers().size());
//        logger.info("Input Passengers List Size: " + result.getInputData().getPassengers().size());
//        logger.info("Output Passengers List Size: " + result.getOutputData().getPassengers().size());
//        logger.info("Dropped Passengers List Size: " + result.getDroppedPassengers().size());
//        logger.info("Processed Passengers List Size: " + result.getProcessedPassengers().size());
//        logger.info("Duplicate Passengers List Size: " + result.getDuplicatePassengers().size());
//        logger.info("===============================");
        
        // Convert to UI-compatible format
        return convertToUiResult(result, validationWarnings, comparator.getLastFileDiscoveryResult());
    }
    
    /**
     * Convert comparison result to UI-compatible format
     */
    private PnrgovResult convertToUiResult(ComparisonResult result, List<String> validationWarnings, FileDiscoveryResult discovery) {
        PnrgovResult uiResult = new PnrgovResult();
        
        // Set flight information
        if (result.getFlightComparison() != null && result.getFlightComparison().getInputFlight() != null) {
            FlightDetails flight = result.getFlightComparison().getInputFlight();
            uiResult.setFlightNumber(flight.getFullFlightNumber()); // Use full flight number (e.g., EK0160)
            uiResult.setDepartureDate(flight.getDepartureDate());
            uiResult.setDepartureAirport(flight.getOrigin());
            uiResult.setArrivalAirport(flight.getDestination());
        } else {
            uiResult.setFlightNumber("N/A");
            uiResult.setDepartureDate("N/A");
            uiResult.setDepartureAirport("N/A");
            uiResult.setArrivalAirport("N/A");
        }
        
        // DEBUG: Create a set to track seen passengers to avoid duplicates
        Set<String> seenInputPassengers = new HashSet<>();
        Set<String> seenOutputPassengers = new HashSet<>();
        Set<String> seenDroppedPassengers = new HashSet<>();
        Set<String> seenProcessedPassengers = new HashSet<>();
        
        // Convert input passengers to UI table rows - ALL INPUT PASSENGERS
        List<PnrgovTableRow> inputRows = new ArrayList<>();
        int index = 1;
        for (PassengerRecord passenger : result.getInputData().getPassengers()) {
            String key = passenger.getName() + "|" + passenger.getPnrRloc();
            if (!seenInputPassengers.contains(key)) {
                seenInputPassengers.add(key);
                inputRows.add(new PnrgovTableRow(
                    index++,
                    passenger.getName(),
                    passenger.getPnrRloc(),
                    passenger.getLegsAsString(),
                    passenger.getSource(),
                    "📥 INPUT",
                    1
                ));
            }
        }
        uiResult.setInputPassengers(inputRows);
        logger.info("Input passengers added to UI: " + inputRows.size());
        
        // Convert output passengers to UI table rows - ALL OUTPUT PASSENGERS  
        List<PnrgovTableRow> outputRows = new ArrayList<>();
        index = 1;
        for (PassengerRecord passenger : result.getOutputData().getPassengers()) {
            String key = passenger.getName() + "|" + passenger.getPnrRloc();
            if (!seenOutputPassengers.contains(key)) {
                seenOutputPassengers.add(key);
                outputRows.add(new PnrgovTableRow(
                    index++,
                    passenger.getName(),
                    passenger.getPnrRloc(),
                    passenger.getLegsAsString(),
                    passenger.getSource(),
                    "📤 OUTPUT",
                    1
                ));
            }
        }
        uiResult.setOutputPassengers(outputRows);
        logger.info("Output passengers added to UI: " + outputRows.size());
        
        // Convert dropped passengers (passengers in input but not in output)
        List<PnrgovTableRow> droppedRows = new ArrayList<>();
        index = 1;
        List<PassengerRecord> droppedPassengers = result.getDroppedPassengers();
        if (droppedPassengers != null) {
            for (PassengerRecord passenger : droppedPassengers) {
                String key = passenger.getName() + "|" + passenger.getPnrRloc();
                if (!seenDroppedPassengers.contains(key)) {
                    seenDroppedPassengers.add(key);
                    droppedRows.add(new PnrgovTableRow(
                        index++,
                        passenger.getName(),
                        passenger.getPnrRloc(),
                        passenger.getLegsAsString(),
                        passenger.getSource(),
                        "❌ DROPPED",
                        1
                    ));
                }
            }
        }
        uiResult.setDroppedPassengers(droppedRows);
        logger.info("Dropped passengers added to UI: " + droppedRows.size());
        
        // Debug: Count unique PNRs in dropped passengers
        Set<String> uniqueDroppedPnrs = droppedRows.stream()
            .map(row -> row.getPnrRloc())
            .collect(java.util.stream.Collectors.toSet());
        logger.info("Unique dropped PNRs: " + uniqueDroppedPnrs.size() + " - " + uniqueDroppedPnrs);
        
        // Calculate NEW PNRs: Use the already calculated added PNR keys from comparison result
        // This represents PNRs that were truly added during processing, not just RLOC differences
        Set<String> newPnrRlocs = result.getAddedPnrKeys();
        
        logger.info("NEW PNRs (actually added during processing): " + newPnrRlocs.size() + " - " + newPnrRlocs);
        
        // Convert duplicate passengers - passengers that appear multiple times in input data
        List<PnrgovTableRow> duplicateRows = new ArrayList<>();
        index = 1;
        List<PassengerRecord> duplicatePassengers = result.getDuplicatePassengers();
        if (duplicatePassengers != null && !duplicatePassengers.isEmpty()) {
            for (PassengerRecord passenger : duplicatePassengers) {
                String key = passenger.getName() + "|" + passenger.getPnrRloc();
                if (!seenProcessedPassengers.contains(key)) {
                    seenProcessedPassengers.add(key);
                    duplicateRows.add(new PnrgovTableRow(
                        index++,
                        passenger.getName(),
                        passenger.getPnrRloc(),
                        passenger.getLegsAsString(),
                        "Input File", // This passenger appears multiple times in input
                        "🔄 DUPLICATE",
                        1
                    ));
                }
            }
        }
        uiResult.setDuplicatePassengers(duplicateRows);
        logger.info("Duplicate passengers added to UI: " + duplicateRows.size());
        
        // Set statistics with correct counts
        uiResult.setTotalInputAll(result.getTotalInputPassengers());
        uiResult.setTotalOutput(result.getTotalOutputPassengers());
        uiResult.setTotalInputPnrs(result.getTotalInputPnrs());
        uiResult.setTotalOutputPnrs(result.getTotalOutputPnrs());
        
        // Use unique PNR count for dropped count instead of passenger count for better accuracy
        uiResult.setDroppedCount(uniqueDroppedPnrs.size()); // Use unique PNR count
        uiResult.setProcessedCount(result.getProcessedPassengerCount()); // Use actual processed count
        uiResult.setAddedCount(result.getAddedPassengerCount());
        uiResult.setDuplicateCount(duplicateRows.size()); // Use actual duplicate count from UI rows
        uiResult.setNewPnrCount(newPnrRlocs.size()); // NEW PNRs count



        // Set processed files with enhanced tracking
        List<String> processedFiles = new ArrayList<>();
        
        // Use original input files from discovery result first
        if (discovery != null && !discovery.getOriginalInputFiles().isEmpty()) {
            for (String originalFile : discovery.getOriginalInputFiles()) {
                processedFiles.add("Input: " + originalFile);
            }
        } else {
            // Fallback: Extract all unique source files from passenger records
            Set<String> uniqueInputFiles = new HashSet<>();
            for (PassengerRecord passenger : result.getInputData().getPassengers()) {
                if (passenger.getSource() != null && !passenger.getSource().isEmpty()) {
                    uniqueInputFiles.add(passenger.getSource());
                }
            }

            // Add all individual input files
            if (!uniqueInputFiles.isEmpty()) {
                for (String inputFile : uniqueInputFiles) {
                    processedFiles.add("Input: " + inputFile);
                }
            } else {
                // If no source tracking found, try to get info from the input file path
                String inputPath = result.getInputData().getFilePath();
                File inputFile = new File(inputPath);
                if (inputFile.getName().startsWith("merged_pnrgov_")) {
                    // This is a merged file - try to extract source info from the name or add a generic message
                    processedFiles.add("Input: Multiple files merged (" + inputFile.getName() + ")");
                } else {
                    processedFiles.add("Input: " + inputFile.getName());
                }
            }
        }
        
        processedFiles.add("Output: " + new File(result.getOutputData().getFilePath()).getName());
        uiResult.setProcessedFiles(processedFiles);
        
        // Set warnings/messages
        List<String> messages = new ArrayList<>();
         if (!result.getFlightComparison().getDifferences().isEmpty()) {
            messages.add("⚠️ Flight details mismatch:");
            messages.addAll(result.getFlightComparison().getDifferences());
        }
        
        // Add summary message

        
        // Add SRC/RCI segment validation warnings
        if (validationWarnings != null && !validationWarnings.isEmpty()) {
            messages.add("");
           // messages.add("🔍 EDIFACT Segment Validation:");
            messages.addAll(validationWarnings);
        }
        
        uiResult.setAllInvalidNads(new ArrayList<>());
        uiResult.setAllInvalidDocs(new ArrayList<>());
        uiResult.setAllMissingSegments(messages);
        
        return uiResult;
    }

    /**
     * UI-compatible result class
     */
    public static class PnrgovResult {
        private String flightNumber = "N/A";
        private String departureDate = "N/A";
        private String departureAirport = "N/A";
        private String arrivalAirport = "N/A";
        
        private List<PnrgovTableRow> inputPassengers = new ArrayList<>();
        private List<PnrgovTableRow> outputPassengers = new ArrayList<>();
        private List<PnrgovTableRow> droppedPassengers = new ArrayList<>();
        private List<PnrgovTableRow> duplicatePassengers = new ArrayList<>();
        
        private int totalInputAll = 0;
        private int totalOutput = 0;
        private int totalInputPnrs = 0;
        private int totalOutputPnrs = 0;
        private int droppedCount = 0;
        private int processedCount = 0;
        private int addedCount = 0;
        private int duplicateCount = 0;
        private int newPnrCount = 0;
        
        private List<String> processedFiles = new ArrayList<>();
        private List<String> allInvalidNads = new ArrayList<>();
        private List<String> allInvalidDocs = new ArrayList<>();
        private List<String> allMissingSegments = new ArrayList<>();
        
        // Getters and setters
        public String getFlightNumber() { return flightNumber; }
        public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }
        
        public String getDepartureDate() { return departureDate; }
        public void setDepartureDate(String departureDate) { this.departureDate = departureDate; }
        
        public String getDepartureAirport() { return departureAirport; }
        public void setDepartureAirport(String departureAirport) { this.departureAirport = departureAirport; }
        
        public String getArrivalAirport() { return arrivalAirport; }
        public void setArrivalAirport(String arrivalAirport) { this.arrivalAirport = arrivalAirport; }
        
        public List<PnrgovTableRow> getInputPassengers() { return inputPassengers; }
        public void setInputPassengers(List<PnrgovTableRow> inputPassengers) { this.inputPassengers = inputPassengers; }
        
        public List<PnrgovTableRow> getOutputPassengers() { return outputPassengers; }
        public void setOutputPassengers(List<PnrgovTableRow> outputPassengers) { this.outputPassengers = outputPassengers; }
        
        public List<PnrgovTableRow> getDroppedPassengers() { return droppedPassengers; }
        public void setDroppedPassengers(List<PnrgovTableRow> droppedPassengers) { this.droppedPassengers = droppedPassengers; }
        
        public List<PnrgovTableRow> getDuplicatePassengers() { return duplicatePassengers; }
        public void setDuplicatePassengers(List<PnrgovTableRow> duplicatePassengers) { this.duplicatePassengers = duplicatePassengers; }
        
        public int getTotalInputAll() { return totalInputAll; }
        public void setTotalInputAll(int totalInputAll) { this.totalInputAll = totalInputAll; }
        
        public int getTotalOutput() { return totalOutput; }
        public void setTotalOutput(int totalOutput) { this.totalOutput = totalOutput; }
        
        public int getTotalInputPnrs() { return totalInputPnrs; }
        public void setTotalInputPnrs(int totalInputPnrs) { this.totalInputPnrs = totalInputPnrs; }
        
        public int getTotalOutputPnrs() { return totalOutputPnrs; }
        public void setTotalOutputPnrs(int totalOutputPnrs) { this.totalOutputPnrs = totalOutputPnrs; }
        
        public int getDroppedCount() { return droppedCount; }
        public void setDroppedCount(int droppedCount) { this.droppedCount = droppedCount; }
        
        public int getProcessedCount() { return processedCount; }
        public void setProcessedCount(int processedCount) { this.processedCount = processedCount; }
        
        public int getAddedCount() { return addedCount; }
        public void setAddedCount(int addedCount) { this.addedCount = addedCount; }
        
        public int getDuplicateCount() { return duplicateCount; }
        public void setDuplicateCount(int duplicateCount) { this.duplicateCount = duplicateCount; }
        
        public int getNewPnrCount() { return newPnrCount; }
        public void setNewPnrCount(int newPnrCount) { this.newPnrCount = newPnrCount; }
        
        public List<String> getProcessedFiles() { return processedFiles; }
        public void setProcessedFiles(List<String> processedFiles) { this.processedFiles = processedFiles; }
        
        public List<String> getAllInvalidNads() { return allInvalidNads; }
        public void setAllInvalidNads(List<String> allInvalidNads) { this.allInvalidNads = allInvalidNads; }
        
        public List<String> getAllInvalidDocs() { return allInvalidDocs; }
        public void setAllInvalidDocs(List<String> allInvalidDocs) { this.allInvalidDocs = allInvalidDocs; }
        
        public List<String> getAllMissingSegments() { return allMissingSegments; }
        public void setAllMissingSegments(List<String> allMissingSegments) { this.allMissingSegments = allMissingSegments; }
    }
    
    /**
     * Table row class compatible with existing UI
     */
    public static class PnrgovTableRow {
        private final int no;
        private final String name;
        private final String pnrRloc;
        private final String legs;
        private final String source;
        private final String status;
        private final int count;
        
        public PnrgovTableRow(int no, String name, String pnrRloc, String legs, String source, String status, int count) {
            this.no = no;
            this.name = name != null ? name : "";
            this.pnrRloc = pnrRloc != null ? pnrRloc : "";
            this.legs = legs != null ? legs : "";
            this.source = source != null ? source : "";
            this.status = status != null ? status : "";
            this.count = count;
        }
        
        // Getters
        public int getNo() { return no; }
        public String getName() { return name; }
        public String getPnrRloc() { return pnrRloc; }
        public String getLegs() { return legs; }
        public String getSource() { return source; }
        public String getStatus() { return status; }
        public int getCount() { return count; }
        
        // For compatibility with existing UI
        public String getDtm() { return ""; } // PNR doesn't have DTM
        public String getDoc() { return ""; } // PNR doesn't have DOC
        public String getRecordedKey() { return pnrRloc + "|" + name; }
        
        @Override
        public String toString() {
            return "PnrgovTableRow{" +
                    "no=" + no +
                    ", name='" + name + '\'' +
                    ", pnrRloc='" + pnrRloc + '\'' +
                    ", status='" + status + '\'' +
                    '}';
        }
    }
}