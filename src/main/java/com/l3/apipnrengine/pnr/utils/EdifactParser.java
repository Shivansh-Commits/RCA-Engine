package com.l3.apipnrengine.pnr.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * EDIFACT parser utilities - replicates PowerShell parsing functions
 */
public class EdifactParser {
    
    /**
     * Parse RCI segment to extract locator
     */
    public static String parseRci(String rciSegment, EdifactSeparators separators) {
        if (rciSegment == null || !rciSegment.startsWith("RCI")) {
            return null;
        }
        
        // Remove RCI prefix and split by element separator
        String payload = rciSegment.substring(3);
        String[] elements = splitEdifactElement(payload, separators, "Element");
        
        if (elements.length < 2) {
            return null;
        }
        
        // Second element contains the locator after first component separator
        String locatorElement = elements[1];
        String[] components = splitEdifactElement(locatorElement, separators, "SubElement");
        
        if (components.length >= 2) {
            return components[1]; // Return the locator
        }
        
        return null;
    }
    
    /**
     * Parse TIF segment to extract passenger name
     */
    public static TifData parseTif(String tifSegment, EdifactSeparators separators) {
        if (tifSegment == null || !tifSegment.startsWith("TIF")) {
            return new TifData("", "", "");
        }
        
        // Remove TIF prefix and split by element separator
        String payload = tifSegment.substring(3);
        String[] elements = splitEdifactElement(payload, separators, "Element");
        
        String surname = "";
        String given = "";
        
        // Handle the case where payload starts with separator (elements[0] will be empty)
        int surnameIndex = (elements.length > 0 && elements[0].isEmpty()) ? 1 : 0;
        int givenIndex = surnameIndex + 1;
        
        if (elements.length > surnameIndex) {
            // Surname element - may contain both surname and given name separated by subElement separator
            String[] surnameComponents = splitEdifactElement(elements[surnameIndex], separators, "SubElement");
            if (surnameComponents.length > 0) {
                surname = surnameComponents[0];
            }
            if (surnameComponents.length > 1) {
                given = surnameComponents[1];
            }
        }
        
        // If given name wasn't found in surname element, try next element
        if (given.isEmpty() && elements.length > givenIndex) {
            // Given name element (may contain additional info after component separator)
            String[] givenComponents = splitEdifactElement(elements[givenIndex], separators, "SubElement");
            if (givenComponents.length > 0) {
                given = givenComponents[0];
            }
        }
        
        String display = given.isEmpty() ? surname : surname + "/" + given;
        
        return new TifData(surname, given, display.trim());
    }
    
    /**
     * Parse TVL segment to extract flight information
     */
    public static TvlData parseTvl(String tvlSegment, EdifactSeparators separators) {
        if (tvlSegment == null || !tvlSegment.startsWith("TVL")) {
            return null;
        }
        
        // Remove TVL prefix and split by element separator
        String payload = tvlSegment.substring(3);
        String[] elements = splitEdifactElement(payload, separators, "Element");
        
        if (elements.length < 6) {
            return null;
        }
        
        // Handle the case where payload starts with separator (elements[0] will be empty)
        int startIndex = (elements[0].isEmpty()) ? 1 : 0;
        
        if (elements.length < (startIndex + 5)) {
            return null;
        }
        
        // TVL format: TVL+datetime+origin+destination+airline+flightnum
        String datetime = elements[startIndex];
        String origin = elements[startIndex + 1];
        String destination = elements[startIndex + 2];
        String airline = elements[startIndex + 3];
        String flightNum = elements[startIndex + 4];
        
        return new TvlData(datetime, origin, destination, airline, flightNum, tvlSegment);
    }
    
    /**
     * Split EDIFACT element by separator type
     */
    private static String[] splitEdifactElement(String element, EdifactSeparators separators, String separatorType) {
        if (element == null || element.trim().isEmpty()) {
            return new String[0];
        }
        
        char sep = "SubElement".equals(separatorType) ? separators.getSubElement() : separators.getElement();
        String escapedSep = Pattern.quote(String.valueOf(sep));
        
        String[] splitResult = element.split(escapedSep);
        
        // Ensure we always return a proper array
        List<String> resultList = new ArrayList<>();
        if (splitResult != null) {
            for (String item : splitResult) {
                resultList.add(item);
            }
        }
        
        return resultList.toArray(new String[0]);
    }
    
    /**
     * TIF data container
     */
    public static class TifData {
        private final String surname;
        private final String given;
        private final String display;
        
        public TifData(String surname, String given, String display) {
            this.surname = surname != null ? surname : "";
            this.given = given != null ? given : "";
            this.display = display != null ? display : "";
        }
        
        public String getSurname() { return surname; }
        public String getGiven() { return given; }
        public String getDisplay() { return display; }
        
        @Override
        public String toString() {
            return "TifData{" +
                    "surname='" + surname + '\'' +
                    ", given='" + given + '\'' +
                    ", display='" + display + '\'' +
                    '}';
        }
    }
    
    /**
     * TVL data container
     */
    public static class TvlData {
        private final String dateTime;
        private final String origin;
        private final String destination;
        private final String airline;
        private final String flightNumber;
        private final String raw;
        
        public TvlData(String dateTime, String origin, String destination, 
                      String airline, String flightNumber, String raw) {
            this.dateTime = dateTime != null ? dateTime : "";
            this.origin = origin != null ? origin : "";
            this.destination = destination != null ? destination : "";
            this.airline = airline != null ? airline : "";
            this.flightNumber = flightNumber != null ? flightNumber : "";
            this.raw = raw != null ? raw : "";
        }
        
        public String getDateTime() { return dateTime; }
        public String getOrigin() { return origin; }
        public String getDestination() { return destination; }
        public String getAirline() { return airline; }
        public String getFlightNumber() { return flightNumber; }
        public String getRaw() { return raw; }
        
        @Override
        public String toString() {
            return "TvlData{" +
                    "dateTime='" + dateTime + '\'' +
                    ", origin='" + origin + '\'' +
                    ", destination='" + destination + '\'' +
                    ", airline='" + airline + '\'' +
                    ", flightNumber='" + flightNumber + '\'' +
                    '}';
        }
    }
}