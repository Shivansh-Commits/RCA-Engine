package com.l3.apipnrengine.pnr.utils;

import java.util.regex.Pattern;

/**
 * EDIFACT separators parser - replicates PowerShell Parse-Separators functionality
 */
public class EdifactSeparators {
    
    private final char subElement;
    private final char element;
    private final char decimal;
    private final char release;
    private final char segment;
    
    private static final int MAX_UNA_LINES = 8;
    
    public EdifactSeparators(char subElement, char element, char decimal, char release, char segment) {
        this.subElement = subElement;
        this.element = element;
        this.decimal = decimal;
        this.release = release;
        this.segment = segment;
    }
    
    /**
     * Parse separators from EDIFACT content
     */
    public static EdifactSeparators parse(String content) {
        if (content == null || content.trim().isEmpty()) {
            return getDefault();
        }
        
        // Look at first 8 lines for UNA segment
        String[] lines = content.replace("\r", "\n").split("\n");
        StringBuilder firstLines = new StringBuilder();
        
        for (int i = 0; i < Math.min(MAX_UNA_LINES, lines.length); i++) {
            firstLines.append(lines[i]).append("\n");
        }
        
        // Find UNA segment with exactly 6 characters after UNA
        Pattern unaPattern = Pattern.compile("UNA(.{6})");
        java.util.regex.Matcher matcher = unaPattern.matcher(firstLines.toString());
        
        if (matcher.find()) {
            String chars = matcher.group(1);
            if (chars.length() >= 6) {
                return new EdifactSeparators(
                    chars.charAt(0), // SubElement separator
                    chars.charAt(1), // Element separator  
                    chars.charAt(2), // Decimal notation
                    chars.charAt(3), // Release character
                    chars.charAt(5)  // Segment terminator (skip position 4 - reserved)
                );
            }
        }
        
        return getDefault();
    }
    
    /**
     * Get default IATA standard separators
     */
    public static EdifactSeparators getDefault() {
        return new EdifactSeparators(':', '+', '.', '?', '\'');
    }
    
    /**
     * Generate UNA segment
     */
    public String generateUnaSegment() {
        return "UNA" + subElement + element + decimal + release + " " + segment;
    }
    
    /**
     * Get separator information string
     */
    public String getSeparatorInfo() {
        return String.format(
            "IATA EDIFACT Separator Configuration:\n" +
            "- Component Data Element Separator (SubElement): '%c' (ASCII %d)\n" +
            "- Data Element Separator (Element): '%c' (ASCII %d)\n" +
            "- Decimal Notation: '%c' (ASCII %d)\n" +
            "- Release Character: '%c' (ASCII %d)\n" +
            "- Segment Terminator: '%c' (ASCII %d)\n" +
            "\nUNA Segment: %s",
            subElement, (int) subElement,
            element, (int) element,
            decimal, (int) decimal,
            release, (int) release,
            segment, (int) segment,
            generateUnaSegment()
        );
    }
    
    // Getters
    public char getSubElement() { return subElement; }
    public char getElement() { return element; }
    public char getDecimal() { return decimal; }
    public char getRelease() { return release; }
    public char getSegment() { return segment; }
    
    @Override
    public String toString() {
        return "EdifactSeparators{" +
                "subElement=" + subElement +
                ", element=" + element +
                ", decimal=" + decimal +
                ", release=" + release +
                ", segment=" + segment +
                '}';
    }
}