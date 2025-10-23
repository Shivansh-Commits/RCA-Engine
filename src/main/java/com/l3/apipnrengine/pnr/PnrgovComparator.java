package com.l3.apipnrengine.pnr;

import com.l3.apipnrengine.pnr.model.*;
import com.l3.apipnrengine.pnr.utils.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * Main PNRGOV Comparator - Java implementation of the PowerShell Compare-PNRGOV script
 * Provides comprehensive EDIFACT file comparison and analysis functionality
 * 
 * FOLDER STRUCTURE SUPPORT:
 * 
 * The comparator now supports two folder structures for PNR processing:
 * 
 * 1. NEW STRUCTURE (Recommended):
 *    - Main folder containing two subdirectories:
 *      ├── input/     (contains all input EDIFACT files)
 *      └── output/    (contains all output EDIFACT files)
 * 
 * 2. LEGACY STRUCTURE:
 *    - Main folder containing files with specific naming:
 *      ├── [filename]_input.[ext]   (files containing 'input' in name)
 *      └── [filename]_output.[ext]  (files containing 'output' in name)
 * 
 * The comparator automatically detects which structure is being used and processes accordingly.
 * New structure is preferred for better organization and clearer separation of input/output data.
 */
public class PnrgovComparator {
    
    private final PnrgovConfig config;
    private final PnrgovLogger logger;
    private final List<String> segmentValidationWarnings = new ArrayList<>();
    
    public PnrgovComparator(PnrgovConfig config) {
        this.config = config;
        this.logger = new PnrgovLogger(config.isEnableLogging());
    }
    
    /**
     * Main comparison method that compares input and output PNRGOV files
     */
    public ComparisonResult compare(File folder) throws Exception {
        logger.info("Starting PNRGOV comparison with strategy: " + config.getMatchingStrategy());
        
        // Find and validate input/output files
        FileDiscoveryResult discovery = findInputOutputFiles(folder);
        
        // Validate files
        validateFiles(discovery.getInputFile(), discovery.getOutputFile());
        
        // Extract PNR and passenger data
        PnrData inputData = extractPnrAndPassengers(discovery.getInputFile(), discovery.getInputSegmentSourceMap());
        PnrData outputData = extractPnrAndPassengers(discovery.getOutputFile(), null);
        
        // Perform comparison
        return performComparison(inputData, outputData, discovery);
    }
    
    /**
     * Find input and output files in the given folder
     * Enhanced to support both old format (files with 'input'/'output' in names) 
     * and new format (input/ and output/ subdirectories)
     */
    private FileDiscoveryResult findInputOutputFiles(File folder) throws Exception {
        logger.debug("Scanning folder for input and output files: " + folder.getAbsolutePath());
        
        if (!folder.exists() || !folder.isDirectory()) {
            throw new IllegalArgumentException("Folder not found: " + folder.getAbsolutePath());
        }
        
        // Check for new folder structure first (input/ and output/ subdirectories)
        File inputDir = new File(folder, "input");
        File outputDir = new File(folder, "output");
        
        List<File> inputFiles = new ArrayList<>();
        List<File> outputFiles = new ArrayList<>();
        
        if (inputDir.exists() && inputDir.isDirectory() && outputDir.exists() && outputDir.isDirectory()) {
            logger.info("Using new folder structure: input/ and output/ subdirectories");
            
            // Get all EDIFACT files from input directory
            File[] inputDirFiles = inputDir.listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".txt") || 
                name.toLowerCase().endsWith(".edifact") ||
                name.toLowerCase().endsWith(".edi"));
            
            if (inputDirFiles != null && inputDirFiles.length > 0) {
                inputFiles.addAll(Arrays.asList(inputDirFiles));
                logger.info("Found " + inputFiles.size() + " files in input directory");
            }
            
            // Get all EDIFACT files from output directory
            File[] outputDirFiles = outputDir.listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".txt") || 
                name.toLowerCase().endsWith(".edifact") ||
                name.toLowerCase().endsWith(".edi"));
            
            if (outputDirFiles != null && outputDirFiles.length > 0) {
                outputFiles.addAll(Arrays.asList(outputDirFiles));
                logger.info("Found " + outputFiles.size() + " files in output directory");
            }
        } else {
            logger.info("Using legacy folder structure: files with 'input'/'output' in names");
            
            // Fall back to old method: look for files with 'input'/'output' in their names
            File[] allFiles = folder.listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".txt") || 
                name.toLowerCase().endsWith(".edifact") ||
                name.toLowerCase().endsWith(".edi"));
            
            if (allFiles == null || allFiles.length == 0) {
                throw new Exception("No files found in folder: " + folder.getAbsolutePath());
            }
            
            // Find input files (contains 'input' anywhere in filename, case-insensitive)
            inputFiles = Arrays.stream(allFiles)
                .filter(file -> file.getName().toLowerCase().contains("input"))
                .collect(Collectors.toList());
            
            // Find output files (contains 'output' anywhere in filename, case-insensitive)
            outputFiles = Arrays.stream(allFiles)
                .filter(file -> file.getName().toLowerCase().contains("output"))
                .collect(Collectors.toList());
        }
        
        if (inputFiles.isEmpty()) {
            if (inputDir.exists() && inputDir.isDirectory()) {
                throw new Exception("No EDIFACT files found in input directory: " + inputDir.getAbsolutePath());
            } else {
                throw new Exception("No input files found! Please ensure files contain 'input' in their name or place files in 'input/' subdirectory");
            }
        }
        
        if (outputFiles.isEmpty()) {
            if (outputDir.exists() && outputDir.isDirectory()) {
                throw new Exception("No EDIFACT files found in output directory: " + outputDir.getAbsolutePath());
            } else {
                throw new Exception("No output files found! Please ensure files contain 'output' in their name or place files in 'output/' subdirectory");
            }
        }
        
        // Analyze multipart structure
        MultipartAnalysis inputAnalysis = analyzeMultipartFiles(inputFiles);
        analyzeMultipartFiles(outputFiles); // Analysis for logging purposes
        
        // Handle input selection (for now, take the first available option)
        File selectedInput;
        Map<Integer, String> inputSegmentSourceMap = null;
        
        if (!inputAnalysis.getCompleteMultipartGroups().isEmpty()) {
            // Use first complete multipart group
            MultipartGroup group = inputAnalysis.getCompleteMultipartGroups().values().iterator().next();
            MergeResult mergeResult = mergeMultipartFiles(group);
            selectedInput = mergeResult.getFile();
            inputSegmentSourceMap = mergeResult.getSegmentSourceMap();
        } else if (!inputAnalysis.getSingleFiles().isEmpty()) {
            // Use first single file
            selectedInput = inputAnalysis.getSingleFiles().get(0);
        } else {
            throw new Exception("No valid input options available!");
        }
        
        // Handle output selection (take first output file)
        File selectedOutput = outputFiles.get(0);
        
        return new FileDiscoveryResult(selectedInput, selectedOutput, inputSegmentSourceMap);
    }
    
    /**
     * Analyze files for multipart structure
     */
    private MultipartAnalysis analyzeMultipartFiles(List<File> files) {
        Map<String, MultipartGroup> completeGroups = new HashMap<>();
        Map<String, MultipartGroup> incompleteGroups = new HashMap<>();
        List<File> singleFiles = new ArrayList<>();
        List<File> invalidFiles = new ArrayList<>();
        
        Map<String, String> messageRefValidation = new HashMap<>();
        
        for (File file : files) {
            try {
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                // Parse separators for potential validation (not currently used)
                EdifactSeparators.parse(content);
                
                // Look for UNH segment pattern
                Pattern unhPattern = Pattern.compile("UNH\\+([^\\+]+)\\+PNRGOV:[^\\+]+\\+([^\\+]+)\\+(\\d+)(?::(C|F))?");
                Matcher matcher = unhPattern.matcher(content);
                
                if (matcher.find()) {
                    String messageRef = matcher.group(1);
                    String identifier = matcher.group(2);
                    int partNumber = Integer.parseInt(matcher.group(3));
                    String partType = matcher.group(4); // C for continuation, F for final
                    
                    // Validate message reference consistency
                    if (messageRefValidation.containsKey(identifier)) {
                        String expectedRef = messageRefValidation.get(identifier);
                        if (!messageRef.equals(expectedRef)) {
                            invalidFiles.add(file);
                            continue;
                        }
                    } else {
                        messageRefValidation.put(identifier, messageRef);
                    }
                    
                    String groupKey = messageRef + "|" + identifier;
                    
                    MultipartGroup group = completeGroups.computeIfAbsent(groupKey, 
                        k -> new MultipartGroup(identifier, messageRef));
                    
                    group.addPart(partNumber, file, partType);
                } else {
                    // Not a multipart file
                    singleFiles.add(file);
                }
            } catch (Exception e) {
                invalidFiles.add(file);
            }
        }
        
        // Validate completeness of multipart groups
        for (MultipartGroup group : completeGroups.values()) {
            if (!group.isComplete()) {
                incompleteGroups.put(group.getIdentifier(), group);
            }
        }
        
        // Remove incomplete groups from complete list
        incompleteGroups.keySet().forEach(key -> 
            completeGroups.entrySet().removeIf(entry -> entry.getValue().getIdentifier().equals(key)));
        
        return new MultipartAnalysis(completeGroups, incompleteGroups, singleFiles, invalidFiles);
    }
    
    /**
     * Merge multipart files into a single file
     */
    private MergeResult mergeMultipartFiles(MultipartGroup group) throws Exception {
        logger.info("Merging multipart files for group: " + group.getIdentifier());
        
        List<String> mergedContent = new ArrayList<>();
        Map<Integer, String> segmentSourceMap = new HashMap<>();
        String messageRef = null;
        String interchangeControlRef = null;
        boolean isFirstFile = true;
        int segmentIndex = 0;
        
        // Sort parts by part number
        List<Integer> partNumbers = new ArrayList<>(group.getParts().keySet());
        Collections.sort(partNumbers);
        
        for (Integer partNumber : partNumbers) {
            File file = group.getParts().get(partNumber);
            String fileName = file.getName();
            //logger.debug("Processing file: " + fileName);
            
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            if (content.trim().isEmpty()) {
                logger.warn("File is empty: " + fileName);
                continue;
            }
            
            // Split into segments
            String[] segments = content.split("'");
            
            for (String segment : segments) {
                String cleanSegment = segment.trim();
                if (cleanSegment.isEmpty()) continue;
                
                if (isFirstFile) {
                    // Include everything from first file
                    mergedContent.add(cleanSegment);
                    segmentSourceMap.put(segmentIndex, fileName);
                    segmentIndex++;
                    
                    // Extract control references
                    if (cleanSegment.startsWith("UNH+")) {
                        Pattern pattern = Pattern.compile("UNH\\+([^\\+]+)\\+");
                        Matcher matcher = pattern.matcher(cleanSegment);
                        if (matcher.find()) {
                            messageRef = matcher.group(1);
                        }
                    }
                    
                    if (cleanSegment.startsWith("UNB+")) {
                        Pattern pattern = Pattern.compile("UNB\\+[^\\+]+\\+[^\\+]+\\+[^\\+]+\\+[^\\+]+\\+([^\\+]+)");
                        Matcher matcher = pattern.matcher(cleanSegment);
                        if (matcher.find()) {
                            interchangeControlRef = matcher.group(1);
                        }
                    }
                } else {
                    // Skip duplicate headers/footers for subsequent files
                    if (!cleanSegment.startsWith("UNA") &&
                        !cleanSegment.startsWith("UNB+") &&
                        !cleanSegment.startsWith("UNH+") &&
                        !cleanSegment.startsWith("UNT+") &&
                        !cleanSegment.startsWith("UNZ+")) {
                        
                        mergedContent.add(cleanSegment);
                        segmentSourceMap.put(segmentIndex, fileName);
                        segmentIndex++;
                    }
                }
            }
            
            isFirstFile = false;
        }
        
        // Add closing segments
        if (messageRef != null) {
            int segmentCount = mergedContent.size() - 
                (int) mergedContent.stream().filter(s -> s.startsWith("UNA") || s.startsWith("UNB+") || 
                                                        s.startsWith("UNH+") || s.startsWith("UNT+") || 
                                                        s.startsWith("UNZ+")).count() + 2;
            mergedContent.add("UNT+" + segmentCount + "+" + messageRef);
            segmentSourceMap.put(segmentIndex++, "SYSTEM");
        }
        
        if (interchangeControlRef != null) {
            mergedContent.add("UNZ+1+" + interchangeControlRef);
        } else {
            mergedContent.add("UNZ+1+1");
        }
        segmentSourceMap.put(segmentIndex, "SYSTEM");
        
        // Create merged file
        String mergedText = String.join("'", mergedContent) + "'";
        File tempFile = File.createTempFile("merged_pnrgov_", ".edi");
        Files.write(tempFile.toPath(), mergedText.getBytes(StandardCharsets.UTF_8));
        
        logger.info("Merged file created successfully with " + mergedContent.size() + " segments");
        
        return new MergeResult(tempFile, segmentSourceMap);
    }
    
    /**
     * Validate EDIFACT files
     */
    private void validateFiles(File inputFile, File outputFile) throws Exception {
        logger.info("Validating input and output files");
        
        // Validate input file
        validateEdifactFile(inputFile, "input");
        
        // Validate output file
        validateEdifactFile(outputFile, "output");
        
        logger.info("File validation completed successfully");
    }
    
    /**
     * Validate a single EDIFACT file
     */
    private void validateEdifactFile(File file, String fileType) throws Exception {
        //logger.debug("Validating " + fileType + " file: " + file.getName());
        
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        String[] segments = content.split("'");
        
        // Find UNB and UNZ segments
        String unb = null;
        String unz = null;
        
        for (String segment : segments) {
            String trimmed = segment.trim();
            if (trimmed.startsWith("UNB+") && unb == null) {
                unb = trimmed;
            }
            if (trimmed.startsWith("UNZ+")) {
                unz = trimmed;
            }
        }
        
        if (unb == null) {
            throw new Exception("UNB segment missing in " + fileType + " file: " + file.getName());
        }
        
        if (unz == null) {
            throw new Exception("UNZ segment missing in " + fileType + " file: " + file.getName());
        }
        
        // Validate UNB/UNZ ICR match
        Pattern unbPattern = Pattern.compile("UNB\\+[^\\+]*\\+[^\\+]*\\+[^\\+]*\\+[^\\+]*\\+([^\\+]+)");
        Pattern unzPattern = Pattern.compile("UNZ\\+\\d+\\+(\\d+)");
        
        Matcher unbMatcher = unbPattern.matcher(unb);
        Matcher unzMatcher = unzPattern.matcher(unz);
        
        if (unbMatcher.find() && unzMatcher.find()) {
            String icrStart = unbMatcher.group(1);
            String icrEnd = unzMatcher.group(1);
            
            if (!icrStart.equals(icrEnd)) {
                if (config.isStrictValidation()) {
                    throw new Exception("ICR mismatch in " + fileType + " file (UNB=" + icrStart + ", UNZ=" + icrEnd + ")");
                } else {
                    logger.warn("ICR mismatch in " + fileType + " file (continuing in non-strict mode)");
                }
            }
        }
        
        //logger.debug(fileType + " file validation passed");
    }
    
    /**
     * Extract PNR and passenger data from EDIFACT file
     */
    private PnrData extractPnrAndPassengers(File file, Map<Integer, String> segmentSourceMap) throws Exception {
        logger.info("Extracting passenger data from: " + file.getName());
        
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        EdifactSeparators separators = EdifactSeparators.parse(content);
        
        logger.debug("Using separators - Element: '" + separators.getElement() +"', SubElement: '" + separators.getSubElement() +"', Segment: '" + separators.getSegment() + "'");
        
        String[] segments = content.split(Pattern.quote(String.valueOf(separators.getSegment())));
        
        // Validate SRC/RCI segments and collect warnings
        List<String> srcRciWarnings = validateSrcRciSegments(segments, separators, file.getName(), segmentSourceMap);
        this.segmentValidationWarnings.addAll(srcRciWarnings);
        
        // Count TRI+ segments for DCS count
        int triCount = (int) Arrays.stream(segments)
            .filter(seg -> seg.trim().startsWith("TRI" + separators.getElement()))
            .count();
        
        // Split into PNR blocks by SRC
        List<PnrBlock> blocks = splitIntoPnrBlocks(segments, segmentSourceMap, file.getName());
        
        List<PnrRecord> pnrRecords = new ArrayList<>();
        List<PassengerRecord> allPassengers = new ArrayList<>();
        int pnrIndex = 0;
        
        for (PnrBlock block : blocks) {
            pnrIndex++;
            
            // Validate mandatory segments
            boolean hasSrc = block.getSegments().stream().anyMatch(s -> s.equals("SRC"));
            boolean hasRci = block.getSegments().stream()
                .anyMatch(s -> s.startsWith("RCI" + separators.getElement()));
            boolean hasTif = block.getSegments().stream()
                .anyMatch(s -> s.startsWith("TIF" + separators.getElement()));
            
            // Skip blocks without basic PNR structure
            if (!hasSrc && !hasRci && !hasTif) {
                logger.warn("Skipping block " + pnrIndex + " - appears to be header/footer data");
                continue;
            }
            
            // Extract primary RLOC from first RCI segment
            String primaryRloc = extractPrimaryRloc(block.getSegments(), separators, pnrIndex);
            
            // Extract passengers from block
            List<PassengerRecord> blockPassengers = extractPassengersFromBlock(
                block, separators, primaryRloc, pnrIndex);
            
            // Create PNR record
            PnrRecord pnrRecord = new PnrRecord(pnrIndex, primaryRloc, block.getSource(), blockPassengers);
            pnrRecords.add(pnrRecord);
            allPassengers.addAll(blockPassengers);
        }
        
        PnrData result = new PnrData(file.getAbsolutePath(), pnrRecords.size(), triCount, 
                                   pnrRecords, allPassengers);
        
        logger.info("Extraction completed - PNRs: " + result.getPnrCount() + 
                   ", Passengers: " + result.getPassengers().size());
        
        return result;
    }
    
    /**
     * Split segments into PNR blocks
     */
    private List<PnrBlock> splitIntoPnrBlocks(String[] segments, Map<Integer, String> segmentSourceMap, String fileName) {
        List<PnrBlock> blocks = new ArrayList<>();
        List<String> currentBlock = new ArrayList<>();
        int currentStartIndex = -1;
        int segmentIndex = 0;
        
        for (String segment : segments) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                segmentIndex++;
                continue;
            }
            
            if (trimmed.equals("SRC")) {
                if (!currentBlock.isEmpty()) {
                    String source = getBlockSource(currentStartIndex, segmentSourceMap, fileName);
                    blocks.add(new PnrBlock(new ArrayList<>(currentBlock), source, currentStartIndex));
                    currentBlock.clear();
                }
                currentBlock.add(trimmed);
                currentStartIndex = segmentIndex;
            } else {
                currentBlock.add(trimmed);
            }
            segmentIndex++;
        }
        
        if (!currentBlock.isEmpty()) {
            String source = getBlockSource(currentStartIndex, segmentSourceMap, fileName);
            blocks.add(new PnrBlock(new ArrayList<>(currentBlock), source, currentStartIndex));
        }
        
        return blocks;
    }
    
    /**
     * Get source for a block from segment map
     */
    private String getBlockSource(int blockStartIndex, Map<Integer, String> segmentSourceMap, String fallbackFileName) {
        if (segmentSourceMap == null) {
            return fallbackFileName.replace(".edi", "").replace(".txt", "");
        }
        
        if (segmentSourceMap.containsKey(blockStartIndex)) {
            return segmentSourceMap.get(blockStartIndex);
        }
        
        return fallbackFileName.replace(".edi", "").replace(".txt", "");
    }
    
    /**
     * Get the source file name for a specific segment index
     */
    private String getSegmentSourceFileName(int segmentIndex, Map<Integer, String> segmentSourceMap, String fallbackFileName) {
        if (segmentSourceMap == null) {
            return fallbackFileName;
        }
        
        if (segmentSourceMap.containsKey(segmentIndex)) {
            return segmentSourceMap.get(segmentIndex);
        }
        
        return fallbackFileName;
    }
    
    /**
     * Extract primary RLOC from RCI segments
     */
    private String extractPrimaryRloc(List<String> segments, EdifactSeparators separators, int blockIndex) {
        for (String segment : segments) {
            if (segment.startsWith("RCI" + separators.getElement())) {
                String locator = EdifactParser.parseRci(segment, separators);
                if (locator != null && !locator.trim().isEmpty()) {
                    return locator;
                }
            }
        }
        
        logger.warn("No valid RCI locator in block " + blockIndex);
        return "NO-RLOC-" + blockIndex;
    }
    
    /**
     * Extract passengers from a PNR block
     */
    private List<PassengerRecord> extractPassengersFromBlock(PnrBlock block, EdifactSeparators separators, 
                                                           String primaryRloc, int blockIndex) {
        List<PassengerRecord> passengers = new ArrayList<>();
        List<String> blockLegs = new ArrayList<>();
        PassengerRecord currentPassenger = null;
        boolean seenAnyTif = false;
        
        for (String segment : block.getSegments()) {
            if (segment.startsWith("TIF" + separators.getElement())) {
                seenAnyTif = true;
                
                EdifactParser.TifData tifData = EdifactParser.parseTif(segment, separators);
                if (tifData.getSurname().isEmpty()) {
                    logger.warn("TIF surname missing in block " + blockIndex + " (TIF=" + segment + ")");
                }
                
                currentPassenger = new PassengerRecord(blockIndex, primaryRloc, 
                                                     tifData.getDisplay(), block.getSource());
                passengers.add(currentPassenger);
                
            } else if (segment.startsWith("TVL" + separators.getElement())) {
                EdifactParser.TvlData tvlData = EdifactParser.parseTvl(segment, separators);
                String leg = tvlData != null ? tvlData.getRaw().substring(3) : segment.substring(3);
                
                if (seenAnyTif && currentPassenger != null) {
                    currentPassenger.getLegs().add(leg);
                } else {
                    blockLegs.add(leg);
                }
            }
        }
        
        // Add block-level legs to passengers who don't have their own legs
        for (PassengerRecord passenger : passengers) {
            if (passenger.getLegs().isEmpty() && !blockLegs.isEmpty()) {
                passenger.getLegs().addAll(blockLegs);
            }
        }
        
        return passengers;
    }
    
    /**
     * Validate SRC and RCI segments according to EDIFACT PNRGOV standards
     * 
     * SRC Segment Rules:
     * - Mandatory segment indicating the start of a PNR record
     * - Can appear multiple times (each new SRC starts a new PNR)
     * - Must be present for valid PNR structure
     * 
     * RCI Segment Rules:
     * - Mandatory segment that must appear after each SRC
     * - Contains reservation control information
     * - Company identification code: up to 3 characters alphanumeric
     * - Reservation control number: up to 20 characters alphanumeric
     */
    private List<String> validateSrcRciSegments(String[] segments, EdifactSeparators separators, String fileName, Map<Integer, String> segmentSourceMap) {
        List<String> warnings = new ArrayList<>();
        
        logger.debug("Starting SRC/RCI segment validation for file: " + fileName);
        
        List<String> srcPositions = new ArrayList<>();
        List<String> rciPositions = new ArrayList<>();
        
        // First pass: collect all SRC and RCI segment positions
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i].trim();
            
            if (segment.equals("SRC")) {
                srcPositions.add("Position " + (i + 1));
                logger.debug("Found SRC segment at position " + (i + 1));
            } else if (segment.startsWith("RCI" + separators.getElement())) {
                rciPositions.add("Position " + (i + 1));
                logger.debug("Found RCI segment at position " + (i + 1));
                
                // Determine actual source file for this segment
                String actualFileName = getSegmentSourceFileName(i, segmentSourceMap, fileName);
                
                // Validate RCI segment structure
                validateRciSegmentStructure(segment, separators, i + 1, warnings, actualFileName);
            }
        }
        
        // Validate SRC segment requirements
        if (srcPositions.isEmpty()) {
            warnings.add("⚠️ CRITICAL: No SRC segments found in " + fileName + " - SRC is mandatory to indicate PNR record start");
        } else {
            logger.info("Found " + srcPositions.size() + " SRC segment(s) in " + fileName + ": " + String.join(", ", srcPositions));
        }
        
        // Validate RCI segment requirements
        if (rciPositions.isEmpty()) {
            warnings.add("⚠️ CRITICAL: No RCI segments found in " + fileName + " - RCI is mandatory after each SRC");
        } else {
            logger.info("Found " + rciPositions.size() + " RCI segment(s) in " + fileName + ": " + String.join(", ", rciPositions));
        }
        
        // Validate SRC/RCI pairing - each SRC should be followed by an RCI
        if (!srcPositions.isEmpty() && !rciPositions.isEmpty()) {
            validateSrcRciPairing(segments, separators, warnings, fileName, segmentSourceMap);
        }
        
        if (warnings.isEmpty()) {
            logger.info("✅ SRC/RCI segment validation passed for " + fileName);
        } else {
            logger.warn("⚠️ SRC/RCI segment validation found " + warnings.size() + " issue(s) in " + fileName);
            for (String warning : warnings) {
                logger.warn("  - " + warning);
            }
        }
        
        return warnings;
    }
    
    /**
     * Validate individual RCI segment structure and content
     */
    private void validateRciSegmentStructure(String rciSegment, EdifactSeparators separators, 
                                           int position, List<String> warnings, String fileName) {
        try {
            // Parse RCI segment: RCI+element1:subelement1:subelement2+element2...
            String payload = rciSegment.substring(3); // Remove "RCI" prefix
            String[] elements = payload.split(Pattern.quote(String.valueOf(separators.getElement())));
            
            if (elements.length < 2) {
                warnings.add("⚠️ RCI segment at position " + position + " in " + fileName + 
                           " has insufficient elements (expected at least 2)");
                return;
            }
            
            // Find the first non-empty element that contains company identification
            String firstElement = null;
            for (String element : elements) {
                if (element != null && !element.trim().isEmpty()) {
                    firstElement = element;
                    break;
                }
            }
            
            if (firstElement != null) {
                // Company identification should be 3 characters alphanumeric
                String[] subElements = firstElement.split(Pattern.quote(String.valueOf(separators.getSubElement())));
                
                if (subElements.length > 0) {
                    String companyId = subElements[0];
                    
                    if (companyId.length() == 0 || companyId.length() > 3) {
                        warnings.add("⚠️ RCI segment at position " + position + " in " + fileName + 
                                   " has invalid company ID length (" + companyId.length() + 
                                   " chars, expected 1-3): '" + companyId + "'");
                    } else if (!companyId.matches("[A-Za-z0-9]+")) {
                        warnings.add("⚠️ RCI segment at position " + position + " in " + fileName + 
                                   " has invalid company ID format (expected alphanumeric): '" + companyId + "'");
                    }
                }
            }
            
            // Second element contains reservation control number
            String secondElement = elements[1];
            String[] subElements = secondElement.split(Pattern.quote(String.valueOf(separators.getSubElement())));
            if (subElements.length > 1) {
                String reservationNumber = subElements[1];
                if (reservationNumber.length() > 20) {
                    warnings.add("⚠️ RCI segment at position " + position + " in " + fileName + 
                               " has reservation control number too long (" + reservationNumber.length() + 
                               " chars, max 20): '" + reservationNumber + "'");
                } else if (!reservationNumber.matches("[A-Za-z0-9]*")) {
                    warnings.add("⚠️ RCI segment at position " + position + " in " + fileName + 
                               " has invalid reservation control number format (expected alphanumeric): '" + 
                               reservationNumber + "'");
                }
            } else {
                warnings.add("⚠️ RCI segment at position " + position + " in " + fileName + 
                           " is missing reservation control number");
            }
            
        } catch (Exception e) {
            warnings.add("⚠️ Error parsing RCI segment at position " + position + " in " + fileName + 
                       ": " + e.getMessage());
        }
    }
    
    /**
     * Validate that each SRC segment is properly followed by an RCI segment
     */
    private void validateSrcRciPairing(String[] segments, EdifactSeparators separators, 
                                     List<String> warnings, String fileName, Map<Integer, String> segmentSourceMap) {
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i].trim();
            
            if (segment.equals("SRC")) {
                boolean foundRci = false;
                
                // Look for RCI in subsequent segments (within reasonable distance)
                for (int j = i + 1; j < Math.min(segments.length, i + 10); j++) {
                    String nextSegment = segments[j].trim();
                    
                    if (nextSegment.startsWith("RCI" + separators.getElement())) {
                        foundRci = true;
                        break;
                    }
                    
                    // If we hit another SRC, stop looking
                    if (nextSegment.equals("SRC")) {
                        break;
                    }
                }
                
                if (!foundRci) {
                    String actualFileName = getSegmentSourceFileName(i, segmentSourceMap, fileName);
//                    warnings.add("⚠️ SRC segment at position " + (i + 1) + " in " + actualFileName +
//                               " is not followed by a corresponding RCI segment");
                    warnings.add("⚠\uFE0F Missing Mandatory RCI segment after SRC at position " + (i + 1));
                }
            }
        }
    }
    
    /**
     * Finds duplicate passengers within the input data based on PNR and passenger name
     */
    private Set<String> findDuplicatePassengers(PnrData inputData) {
        // Map: passengerKey -> Set of source files
        Map<String, Set<String>> passengerSourceMap = new HashMap<>();
        Set<String> duplicateKeys = new HashSet<>();
        
        //logger.info("=== DUPLICATE DETECTION DEBUG ===");
        //logger.info("Total input passengers to analyze: " + inputData.getPassengers().size());
        
        // Group passengers by key and track which source files they come from
        for (PassengerRecord passenger : inputData.getPassengers()) {
            String key = generatePassengerKey(passenger);
            String sourceFile = passenger.getSource();
            
            passengerSourceMap.computeIfAbsent(key, k -> new HashSet<>()).add(sourceFile);
            //logger.debug("Passenger: '" + passenger.getName() + "', PNR: '" + passenger.getPnrRloc() + "', Source: '" + sourceFile + "', Key: '" + key + "'");
        }
        
        //logger.info("Unique passenger keys found: " + passengerSourceMap.size());
        
        // Find keys that appear in multiple source files (cross-file duplicates)
        for (Map.Entry<String, Set<String>> entry : passengerSourceMap.entrySet()) {
            String key = entry.getKey();
            Set<String> sources = entry.getValue();
            
            //logger.info("Key: '" + key + "' appears in " + sources.size() + " source file(s): " + sources);
            
            if (sources.size() > 1) {
                duplicateKeys.add(key);
                logger.info("  -> MARKED AS DUPLICATE (appears across " + sources.size() + " different input files)");
            }
        }
        
        //logger.info("Total cross-file duplicate keys identified: " + duplicateKeys.size());
        //logger.info("=================================");
        return duplicateKeys;
    }
    
    /**
     * Perform the actual comparison between input and output data
     */
    private ComparisonResult performComparison(PnrData inputData, PnrData outputData, FileDiscoveryResult discovery) {
        logger.info("Performing comparison with strategy: " + config.getMatchingStrategy());
        
        long startTime = System.currentTimeMillis();
        
        // Extract flight details
        FlightComparison flightComparison = compareFlightDetails(inputData, outputData);
        
        // Generate passenger keys for comparison
        List<String> inputKeys = inputData.getPassengers().stream()
            .map(this::generatePassengerKey)
            .collect(Collectors.toList());
        
        List<String> outputKeys = outputData.getPassengers().stream()
            .map(this::generatePassengerKey)
            .collect(Collectors.toList());
        
        // Generate PNR keys for comparison
        List<String> inputPnrKeys = inputData.getPnrRecords().stream()
            .map(pnr -> generatePnrKey(pnr.getRloc()))
            .collect(Collectors.toList());
        
        List<String> outputPnrKeys = outputData.getPnrRecords().stream()
            .map(pnr -> generatePnrKey(pnr.getRloc()))
            .collect(Collectors.toList());
        
        // Find differences
        Set<String> inputKeySet = new HashSet<>(inputKeys);
        Set<String> outputKeySet = new HashSet<>(outputKeys);
        
        Set<String> processedKeys = new HashSet<>(inputKeySet);
        processedKeys.retainAll(outputKeySet);
        
        Set<String> droppedKeys = new HashSet<>(inputKeySet);
        droppedKeys.removeAll(outputKeySet);
        
        Set<String> addedKeys = new HashSet<>(outputKeySet);
        addedKeys.removeAll(inputKeySet);
        
        // Same for PNRs
        Set<String> inputPnrKeySet = new HashSet<>(inputPnrKeys);
        Set<String> outputPnrKeySet = new HashSet<>(outputPnrKeys);
        
        Set<String> processedPnrKeys = new HashSet<>(inputPnrKeySet);
        processedPnrKeys.retainAll(outputPnrKeySet);
        
        Set<String> droppedPnrKeys = new HashSet<>(inputPnrKeySet);
        droppedPnrKeys.removeAll(outputPnrKeySet);
        
        Set<String> addedPnrKeys = new HashSet<>(outputPnrKeySet);
        addedPnrKeys.removeAll(inputPnrKeySet);
        
        // Find duplicate passengers within input data
        Set<String> duplicateKeys = findDuplicatePassengers(inputData);
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        return new ComparisonResult(
            inputData, outputData, flightComparison,
            processedKeys, droppedKeys, addedKeys,
            processedPnrKeys, droppedPnrKeys, addedPnrKeys,
            duplicateKeys,
            processingTime, config
        );
    }
    
    /**
     * Compare flight details between input and output
     */
    private FlightComparison compareFlightDetails(PnrData inputData, PnrData outputData) {
        try {
            FlightDetails inputFlight = FlightExtractor.extractFlightDetails(inputData.getFilePath());
            FlightDetails outputFlight = FlightExtractor.extractFlightDetails(outputData.getFilePath());
            
            return new FlightComparison(inputFlight, outputFlight);
        } catch (Exception e) {
            logger.error("Error comparing flight details: " + e.getMessage());
            return new FlightComparison(null, null);
        }
    }
    
    /**
     * Generate passenger key based on matching strategy
     */
    private String generatePassengerKey(PassengerRecord passenger) {
        switch (config.getMatchingStrategy()) {
            case PNR_NAME:
                return cleanString(passenger.getPnrRloc()) + "|" + cleanString(passenger.getName());
                
            case NAME_DOC_DOB:
                String cleanName = passenger.getName() != null ? 
                    Arrays.stream(passenger.getName().replace("[^\\w\\s]", "").split("\\s+"))
                          .filter(s -> !s.isEmpty())
                          .sorted()
                          .collect(Collectors.joining(" "))
                          .toUpperCase() : "";
                String docValue = "NODOC"; // PNR data doesn't typically have documents
                String dobValue = "NODOB"; // PNR data doesn't typically have DOB
                return cleanName + "|" + docValue + "|" + dobValue;
                
            case CUSTOM:
                return cleanString(passenger.getPnrRloc()) + "|" + cleanString(passenger.getName()) + "|";
                
            default:
                return cleanString(passenger.getPnrRloc()) + "|" + cleanString(passenger.getName());
        }
    }
    
    /**
     * Generate PNR key
     */
    private String generatePnrKey(String rloc) {
        return cleanString(rloc);
    }
    
    /**
     * Clean string for key generation
     */
    private String cleanString(String input) {
        if (input == null) return "";
        return input.replaceAll("\\s", "").replaceAll("\\W", "").toUpperCase();
    }
    
    /**
     * Get validation warnings collected during processing
     */
    public List<String> getSegmentValidationWarnings() {
        return new ArrayList<>(segmentValidationWarnings);
    }
}