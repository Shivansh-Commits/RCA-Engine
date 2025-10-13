package com.l3.dqengine.api.utils;

import com.l3.dqengine.api.model.Flight;
import com.l3.dqengine.api.model.Passenger;
import com.l3.dqengine.api.model.Separators;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * Implements the parsing logic from your PowerShell script.
 * Returns ParseResult containing maps and lists for UI.
 */
public class FileParser {

    private final String recordType;
    private final String dataType;

    public FileParser(String recordType,String dataType) {
        this.recordType = recordType == null ? "pax" : recordType.toLowerCase();
        this.dataType = dataType == null ? "api" : dataType.toLowerCase();
    }
    // Patterns will be constructed per-file using separators
    private static final int MAX_UNA_LINES = 8;

    public ParseResult parseFolder(File folder) throws Exception {

        // gather files
        List<Path> txtFiles = Files.list(folder.toPath())
                .filter(p -> p.toString().toLowerCase().endsWith(".txt"))
                .collect(Collectors.toList());

        List<Path> inputFiles = txtFiles.stream()
                .filter(p -> p.getFileName().toString().toLowerCase().matches("^input[_0-9]*\\.txt$") ||
                        p.getFileName().toString().toLowerCase().startsWith("input"))
                .collect(Collectors.toList());

        List<Path> outputFiles = txtFiles.stream()
                .filter(p -> p.getFileName().toString().toLowerCase().matches("^output[_0-9]*\\.txt$") ||
                        p.getFileName().toString().toLowerCase().startsWith("output"))
                .collect(Collectors.toList());

        if (inputFiles.isEmpty()) {
            throw new IOException("No input files found (expected at least 1 matching 'input*.txt').");
        }
        if (outputFiles.size() == 0) {
            throw new IOException("No output file found (expected exactly 1).");
        }
        if (outputFiles.size() > 1) {
            throw new IOException("Multiple output files found (expected exactly 1).");
        }

        List<String> processedFiles = new ArrayList<>();
        inputFiles.forEach(p -> processedFiles.add(p.getFileName().toString()));
        outputFiles.forEach(p -> processedFiles.add(p.getFileName().toString()));

        Path outputFile = outputFiles.get(0);

        if(dataType.equals("api"))
        {
            Map<String, Passenger> outputPassengers = parseAPIFile(outputFile, "(Output)");
            // parse each input
            Map<String, Passenger> globalInputPassengers = new LinkedHashMap<>();
            Map<String, Passenger> duplicatePassengers = new LinkedHashMap<>();

            List<String> allInvalidNads = new ArrayList<>();
            List<String> allInvalidDocs = new ArrayList<>();
            List<String> allMissingSegments = new ArrayList<>();

            int totalInputAll = 0;

            for (Path inputPath : inputFiles) {
                String fileName = inputPath.getFileName().toString();
                Map<String, Passenger> inputMap = parseAPIFile(inputPath, fileName);

                // merge into global map
                for (Map.Entry<String, Passenger> e : inputMap.entrySet()) {
                    final String key = e.getKey();
                    final Passenger p = e.getValue();
                    totalInputAll += p.getCount();

                    if (globalInputPassengers.containsKey(key)) {
                        Passenger existing = globalInputPassengers.get(key);
                        existing.incrementCountBy(p.getCount());
                        // add source if new
                        existing.addSource(p.getSources());
                        // record duplicate across files
                        duplicatePassengers.put(key, existing);
                    } else {
                        globalInputPassengers.put(key, new Passenger(p)); // copy
                    }
                }

                // NEW: capture duplicates that occurred within the same file (count > 1) that would otherwise be missed
                for (Map.Entry<String, Passenger> e : inputMap.entrySet()) {
                    Passenger p = e.getValue();
                    if (p.getCount() > 1) {
                        // Use the merged instance from globalInputPassengers so count reflects any cross-file additions too
                        Passenger merged = globalInputPassengers.get(e.getKey());
                        if (merged != null) {
                            duplicatePassengers.put(e.getKey(), merged);
                        }
                    }
                }

                // Also collect warnings (stored in map values as lists)
                for (Passenger p : inputMap.values()) {
                    allInvalidNads.addAll(p.getInvalidNads());
                    allInvalidDocs.addAll(p.getInvalidDocs());
                    allMissingSegments.addAll(p.getMissingSegments());
                }
            }

            // dropped = those in inputs not in outputs
            List<Passenger> dropped = globalInputPassengers.values().stream()
                    .filter(p -> {
                        String key = GetTokenised(p.getName()) + "|" +
                                (p.getDocNum() == null ? "" : p.getDocNum()) + "|" +
                                (p.getDtm() == null ? "" : p.getDtm());
                        return !outputPassengers.containsKey(key);
                    })
                    .collect(Collectors.toList());

            int totalOutput = outputPassengers.values().stream().mapToInt(Passenger::getCount).sum();

            Flight flight = extractFlight(outputFile);

            ParseResult result = new ParseResult();
            result.setGlobalInputPassengers(globalInputPassengers);
            result.setDuplicatePassengers(duplicatePassengers);
            result.setOutputPassengers(outputPassengers);
            result.setDropped(dropped);
            result.setAllInvalidNads(unique(allInvalidNads));
            result.setAllInvalidDocs(unique(allInvalidDocs));
            result.setAllMissingSegments(unique(allMissingSegments));
            result.setTotalInputAll(totalInputAll);
            result.setTotalOutput(totalOutput);
            result.setFlightNumber(flight.getFlightNo());
            result.setArrivalAirport(flight.getArrPort());
            result.setDepartureAirport(flight.getDepPort());
            result.setDepartureDate(flight.getDepDate());
            result.setDepartureTime(flight.getDepTime());
            result.setDepartureAirport(flight.getDepPort());
            result.setArrivalAirport(flight.getArrPort());
            result.setProcessedFiles(processedFiles);


            return result;
        }
        else
        {
            throw new IOException("Unsupported data type: " + dataType);
        }


    }

    private List<String> unique(List<String> list) {
        return list.stream().distinct().collect(Collectors.toList());
    }
    /**
     * Parse a single file and returns a map keyed by recordedKey token | doc | dtm.
     */
    private Map<String, Passenger> parseAPIFile(Path filePath, String sourceLabel) throws IOException {
        String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
        Separators separators = parseSeparators(content);

        String E = Pattern.quote(String.valueOf(separators.element));
        String S = Pattern.quote(String.valueOf(separators.subElement));
        String SEG = String.valueOf(separators.terminator);

        // split by newline or segment char
        String splitRegex = "(?:\\r?\\n)|" + Pattern.quote(SEG);
        String[] rawPieces = content.replace("\r", "\n").split(splitRegex);

        Map<String, Passenger> passengerList = new LinkedHashMap<>();

        String currentName = null;
        String docValue = null;
        String docType = null;
        String dtmValue = null;
        boolean docCaptured = false;
        boolean pendingPassenger = false;

        // compile patterns replicating the PowerShell regex using E and S
        Pattern nadPattern = Pattern.compile("^NAD" + E + "([A-Z]{2,3})" + E + E + E + "(.+?" + S + ".+?)$");
        Pattern dtmPattern = Pattern.compile("^DTM" + E + "329" + S + "([0-9]{6})");
        Pattern docPattern = Pattern.compile("^DOC" + E + "(\\w{1,2})(?:" + S + "[^" + E + "]*)*" + E + "([0-9A-Z]+)");

        // storage for warnings associated with this parse
        List<String> invalidNads = new ArrayList<>();
        List<String> invalidDocs = new ArrayList<>();
        List<String> missingSegments = new ArrayList<>();

        for (String piece : rawPieces) {
            String line = piece.trim();
            if (line.isEmpty()) continue;

            Matcher mNad = nadPattern.matcher(line);
            Matcher mDtm = dtmPattern.matcher(line);
            Matcher mDoc = docPattern.matcher(line);

            if (mNad.matches()) {
                String nadType = mNad.group(1);
                String fullName = mNad.group(2).trim();

                List<String> validNads;
                if ("crew".equalsIgnoreCase(recordType)) {
                    validNads = Arrays.asList("FM", "DDT");
                } else {
                    validNads = Arrays.asList("FL", "DDU");
                }


                if (!validNads.contains(nadType)) {
                    invalidNads.add("Invalid NAD Segment Found - "+nadType + ":" + fullName);
                    currentName = null;
                    pendingPassenger = false;
                } else {
                    // flush pending passenger if any
                    if (pendingPassenger && currentName != null) {
                        if (docValue != null || dtmValue != null) {
                            String tokenisedName = GetTokenised(currentName);
                            String key = tokenisedName + "|" + (docValue == null ? "" : docValue) + "|" + (dtmValue == null ? "" : dtmValue);

                            if (passengerList.containsKey(key)) {
                                passengerList.get(key).incrementCount();
                                // mark duplicate within same file
                                passengerList.get(key).addInvalidMarker("duplicate-within-file");
                            } else {
                                Passenger p = new Passenger(currentName, docValue, dtmValue, sourceLabel,docType);
                                p.setRecordedKey(buildRecordedKey(currentName, docValue, dtmValue));
                                p.getInvalidNads().addAll(invalidNads);
                                p.getInvalidDocs().addAll(invalidDocs);
                                p.getMissingSegments().addAll(missingSegments);
                                passengerList.put(key, p);
                            }
                        } else {
                            missingSegments.add(currentName + ": Missing DOC and/or DTM");
                        }
                    }

                    // NEW LOGIC: limit name to first 3 subelement parts
                    String[] rawParts = fullName.split(Pattern.quote(String.valueOf(separators.subElement)));
                    if (rawParts.length > 3) {
                        fullName = String.join(String.valueOf(separators.subElement), Arrays.copyOf(rawParts, 3));
                    } else {
                        fullName = String.join(String.valueOf(separators.subElement), rawParts);
                    }

                    currentName = NormalizeName.normalize(fullName, separators);
                    docValue = null;
                    dtmValue = null;
                    docCaptured = false;
                    pendingPassenger = true;
                }
            } else if (mDtm.matches()) {
                if (currentName != null) {
                    dtmValue = mDtm.group(1).trim();
                }
            } else if (mDoc.matches()) {
                if (currentName != null && !docCaptured) {
                    docType = mDoc.group(1);
                    String docNum = mDoc.group(2).trim();
                    docValue = docNum;
                    if (!docType.equals("P") && !docType.equals("V") && !docType.equals("IP")) {
                        invalidDocs.add("Invalid DOC type found - " + docType + ":" + docNum + " for " + currentName);
                    }
                    docCaptured = true;
                }
            }
        }

        // Final pending passenger
        if (pendingPassenger && currentName != null) {
            if (docValue != null || dtmValue != null) {
                String tokenisedName = GetTokenised(currentName);
                String key = tokenisedName + "|" + (docValue == null ? "" : docValue) + "|" + (dtmValue == null ? "" : dtmValue);
                if (passengerList.containsKey(key)) {
                    passengerList.get(key).incrementCount();
                } else {
                    Passenger p = new Passenger(currentName, docValue, dtmValue, sourceLabel,docType);
                    p.setRecordedKey(buildRecordedKey(currentName, docValue, dtmValue));
                    p.getInvalidNads().addAll(invalidNads);
                    p.getInvalidDocs().addAll(invalidDocs);
                    p.getMissingSegments().addAll(missingSegments);
                    passengerList.put(key, p);
                }
            } else {
                missingSegments.add(currentName + ": Missing DOC and/or DTM");
            }
        }

        // Assign stored warnings back into passengers for extraction by caller
        for (Passenger p : passengerList.values()) {
            p.getInvalidNads().addAll(invalidNads);
            p.getInvalidDocs().addAll(invalidDocs);
            p.getMissingSegments().addAll(missingSegments);
        }

        return passengerList;
    }

    private Separators parseSeparators(String content) {
        // replicate PS Parse-Separators logic: look at first up-to-8 lines for UNA... else default
        String[] lines = content.replace("\r", "\n").split("\n");
        String firstLines = Arrays.stream(lines)
                .limit(MAX_UNA_LINES)
                .collect(Collectors.joining("\n"));

        // find the UNA line if any (allow 1–6 chars after UNA)
        Pattern unaPat = Pattern.compile("UNA(.{1,6})");
        Matcher m = unaPat.matcher(firstLines);
        if (m.find()) {
            String chars = m.group(1);
            // pad to length 6 if fewer than 6 chars present
            while (chars.length() < 6) {
                chars += "'"; // or your preferred default char
            }

            char subElement = chars.charAt(0);
            char element    = chars.charAt(1);
            char decimal    = chars.charAt(2);
            char release    = chars.charAt(3);
            char segment    = chars.charAt(4);
            char terminator = chars.charAt(5);
            return new Separators(subElement, element, decimal, release, segment,terminator);
        }

        // defaults
        return new Separators(':', '+', '.', '?',' ','\'');
    }

    private static String GetTokenised(String normalizedName) {
        if (normalizedName == null) return "";
        String[] parts = normalizedName.trim().split("\\s+");
        Arrays.sort(parts, String.CASE_INSENSITIVE_ORDER);
        return String.join(" ", parts).trim();
    }

    private static String buildRecordedKey(String name, String doc, String dtm) {
        String docPart = (doc == null) ? "" : doc;
        String dtmPart = (dtm == null) ? "" : dtm;
        // recordedKey in PS is constructed by joining normalized name (not tokenised) then '|' doc '|' dtm.
        // In many places you used tokenisedName as lookup key. We will set recordedKey = normalizedName|doc|dtm
        return name + "|" + docPart + "|" + dtmPart;
    }

    public Flight extractFlight(Path filePath) throws Exception {
        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        // Remove empty lines
        List<String> nonEmptyLines = lines.stream().map(String::trim).filter(l -> !l.isEmpty()).collect(Collectors.toList());

        String unhLine;
        Separators separators;

        if (nonEmptyLines.size() == 1) {
            // Single-line format
            String singleLine = nonEmptyLines.get(0);
            int unhIndex = singleLine.indexOf("UNH");
            if (unhIndex == -1) {
                throw new IOException("UNH segment not found in single-line file.");
            }
            unhLine = singleLine.substring(unhIndex, Math.min(unhIndex + 100, singleLine.length())); // limit length
            separators = parseSeparators(unhLine);
        } else {
            // Multi-line format (existing logic)
            int maxLines = Math.min(20, lines.size());
            unhLine = null;
            for (int i = 0; i < Math.min(15, maxLines); i++) {
                String line = lines.get(i).trim();
                if (line.startsWith("UNH")) {
                    unhLine = line;
                    break;
                }
            }
            if (unhLine == null) {
                throw new IOException("UNH segment not found in first 15 lines.");
            }
            separators = parseSeparators(String.join("\n", lines.subList(0, maxLines)));
        }

        List<String> segmentLines;
        if (nonEmptyLines.size() == 1) {
            segmentLines = Arrays.asList(nonEmptyLines.get(0).split(Pattern.quote(String.valueOf(separators.terminator))));
        } else {
            segmentLines = nonEmptyLines;
        }

        char elementSep = separators.element;
        char subElementSep = separators.subElement;

        // Check if this is API mode to use new parsing logic
        if ("api".equalsIgnoreCase(this.dataType)) {
            return extractFlightFromApiSegments(segmentLines, elementSep, subElementSep);
        } else {
            // Use original UNH-based logic for non-API modes
            return extractFlightFromUnh(unhLine, segmentLines, elementSep, subElementSep);
        }
    }
    
    /**
     * New API-specific flight extraction logic using TDT, LOC, and DTM segments
     */
    private Flight extractFlightFromApiSegments(List<String> segmentLines, char elementSep, char subElementSep) throws Exception {
        String flightNo = null, depDate = null, depTime = null, depPort = "", arrPort = "";
        
        // Extract flight number from TDT segment
        // Pattern: TDT<Element separator>20<Element separator><Flight number in 6 characters>
        // Ex: TDT+20+MS0775
        Pattern tdtPattern = Pattern.compile("^TDT" + Pattern.quote(String.valueOf(elementSep)) + "20" + Pattern.quote(String.valueOf(elementSep)) + "([A-Za-z0-9]{6})");
        
        // Extract departure airport from LOC segment
        // Pattern: LOC<Element separator>125<Element separator><departure airport in 3 character>
        // Ex: LOC+125+CAI
        Pattern depPattern = Pattern.compile("^LOC" + Pattern.quote(String.valueOf(elementSep)) + "125" + Pattern.quote(String.valueOf(elementSep)) + "([A-Z]{3})");
        
        // Extract arrival airport from LOC segment
        // Pattern: LOC<Element separator>87<Element separator><arrival airport in 3 character>
        // Ex: LOC+87+DUB
        Pattern arrPattern = Pattern.compile("^LOC" + Pattern.quote(String.valueOf(elementSep)) + "87" + Pattern.quote(String.valueOf(elementSep)) + "([A-Z]{3})");
        
        // Extract departure date from DTM segment
        // Pattern: DTM<Element separator>189<Sub-Element separator><departure date and time><Sub-element separator>201
        // Ex: DTM+189:2508140935:201
        Pattern dtmPattern = Pattern.compile("^DTM" + Pattern.quote(String.valueOf(elementSep)) + "189" + Pattern.quote(String.valueOf(subElementSep)) + "(\\d{10})" + Pattern.quote(String.valueOf(subElementSep)) + "201");
        
        for (String line : segmentLines) {
            line = line.trim();
            
            // Extract flight number from TDT segment
            if (flightNo == null) {
                Matcher tdtMatcher = tdtPattern.matcher(line);
                if (tdtMatcher.find()) {
                    flightNo = tdtMatcher.group(1);
                }
            }
            
            // Extract departure airport
            if (depPort.isEmpty()) {
                Matcher depMatcher = depPattern.matcher(line);
                if (depMatcher.find()) {
                    depPort = depMatcher.group(1);
                }
            }
            
            // Extract arrival airport
            if (arrPort.isEmpty()) {
                Matcher arrMatcher = arrPattern.matcher(line);
                if (arrMatcher.find()) {
                    arrPort = arrMatcher.group(1);
                }
            }
            
            // Extract departure date and time
            if (depDate == null || depTime == null) {
                Matcher dtmMatcher = dtmPattern.matcher(line);
                if (dtmMatcher.find()) {
                    String dateTimeStr = dtmMatcher.group(1); // e.g., "2508140935"
                    // Format: YYMMDDHHMM (250814 = 14/08/2025, 0935 = 09:35)
                    if (dateTimeStr.length() == 10) {
                        String yearStr = dateTimeStr.substring(0, 2);
                        String monthStr = dateTimeStr.substring(2, 4);
                        String dayStr = dateTimeStr.substring(4, 6);
                        String hourStr = dateTimeStr.substring(6, 8);
                        String minuteStr = dateTimeStr.substring(8, 10);

                        // Format date as DD/MM/YYYY
                        depDate = dayStr + "/" + monthStr + "/20" + yearStr;
                        depTime = hourStr + minuteStr;
                    }
                }
            }

            // Break early if all required data is found
            if (flightNo != null && depDate != null && depTime != null && !depPort.isEmpty() && !arrPort.isEmpty()) {
                break;
            }
        }

        if (flightNo == null) {
            throw new IOException("Flight number not found in TDT segment for API mode.");
        }
        if (depDate == null || depTime == null) {
            throw new IOException("Departure date/time not found in DTM segment for API mode.");
        }

        return new Flight(flightNo, depTime, depDate, depPort, arrPort);
    }

    /**
     * Original UNH-based flight extraction logic for non-API modes
     */
    private Flight extractFlightFromUnh(String unhLine, List<String> segmentLines, char elementSep, char subElementSep) throws Exception {
        // Extract flight details (original logic)
        String[] elements = unhLine.substring(3).split(Pattern.quote(String.valueOf(elementSep)));
        Pattern flightPattern = Pattern.compile("([A-Za-z0-9]{6})/(\\d{6})/(\\d{4})");

        String flightNo = null, depDate = null, depTime = null;
        for (String el : elements) {
            String[] subElements = el.split(Pattern.quote(String.valueOf(subElementSep)));
            for (String part : subElements) {
                Matcher m = flightPattern.matcher(part.trim());
                if (m.matches()) {
                    flightNo = m.group(1);
                    String dateRaw = m.group(2);
                    depDate = dateRaw.substring(0, 2) + "/" + dateRaw.substring(2, 4) + "/" + dateRaw.substring(4, 6);
                    depTime = m.group(3);
                    break;
                }
            }
            if (flightNo != null) break;
        }

        // Extract ports from LOC segments (original logic)
        String depPort = "", arrPort = "";
        Pattern depPattern = Pattern.compile("^LOC" + Pattern.quote(String.valueOf(elementSep)) + "125" + Pattern.quote(String.valueOf(elementSep)) + "([A-Z]{3})");
        Pattern arrPattern = Pattern.compile("^LOC" + Pattern.quote(String.valueOf(elementSep)) + "87" + Pattern.quote(String.valueOf(elementSep)) + "([A-Z]{3})");
        for (String line : segmentLines) {
            Matcher mDep = depPattern.matcher(line);
            Matcher mArr = arrPattern.matcher(line);
            if (depPort.isEmpty() && mDep.find()) {
                depPort = mDep.group(1);
            }
            if (arrPort.isEmpty() && mArr.find()) {
                arrPort = mArr.group(1);
            }
            if (!depPort.isEmpty() && !arrPort.isEmpty()) break;
        }

        if (flightNo == null || depDate == null || depTime == null) {
            throw new IOException("Flight details not found in UNH segment.");
        }

        return new Flight(flightNo, depTime, formatDate(depDate), depPort, arrPort);
    }

    public static String formatDate(String input) throws Exception {
        SimpleDateFormat inputFormat = new SimpleDateFormat("yy/MM/dd");
        SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy");
        Date date = inputFormat.parse(input);
        return outputFormat.format(date);
    }
}
