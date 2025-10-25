package com.l3.apipnrengine.pnr.utils;

/**
 * EDIFACT separators parser - replicates PowerShell Parse-Separators functionality
 */
public class EdifactSeparators {
    
    private final char subElement;
    private final char element;
    private final char decimal;
    private final char release;
    private final char segment;
    private final char terminator;
    
    private static final int MAX_UNA_LINES = 8;
    
    public EdifactSeparators(char subElement, char element, char decimal, char release, char segment, char terminator) {
        this.subElement = subElement;
        this.element = element;
        this.decimal = decimal;
        this.release = release;
        this.segment = segment;
        this.terminator = terminator;
    }
    
    /**
     * Parse separators from EDIFACT content
     * Dynamically extracts UNA separator characters from any format
     * Enhanced to handle malformed UNA segments robustly
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
        
        String searchContent = firstLines.toString();
        
        // Find UNA position
        int unaIndex = searchContent.indexOf("UNA");
        if (unaIndex == -1) {
            return getDefault();
        }
        
        // UNA should be followed by exactly 6 separator characters
        int separatorStart = unaIndex + 3; // Skip "UNA"
        
        // Enhanced parsing with malformed UNA detection
        return parseUnaSegment(searchContent, separatorStart);
    }
    
    /**
     * Parse UNA segment with robust handling of malformed segments
     */
    private static EdifactSeparators parseUnaSegment(String content, int separatorStart) {
        // Check if we have enough characters for analysis
        if (separatorStart >= content.length()) {
            return getDefault();
        }
        
        // Look ahead to see if we can find UNB to detect malformed UNA
        int unbIndex = content.indexOf("UNB", separatorStart);
        
        // Case 1: Standard UNA with 6 characters
        if (separatorStart + 6 <= content.length()) {
            String potentialUna = content.substring(separatorStart, separatorStart + 6);
            
            // Check if this looks like a valid UNA segment
            if (isValidUnaPattern(potentialUna, content, separatorStart + 6)) {
                return new EdifactSeparators(
                    potentialUna.charAt(0), // SubElement separator
                    potentialUna.charAt(1), // Element separator
                    potentialUna.charAt(2), // Decimal notation
                    potentialUna.charAt(3), // Release character
                    potentialUna.charAt(4), // Segment separator
                    potentialUna.charAt(5)  // Segment terminator
                );
            }
        }
        
        // Case 2: Malformed UNA - try to reconstruct
        if (unbIndex > separatorStart) {
            return reconstructMalformedUna(content, separatorStart, unbIndex);
        }
        
        // Case 3: Fallback to available characters with padding
        return parseWithPadding(content, separatorStart);
    }
    
    /**
     * Validate if a UNA pattern looks correct
     */
    private static boolean isValidUnaPattern(String unaChars, String content, int nextPos) {
        // Basic validation: 6th character should be followed by UNB or end
        if (nextPos < content.length()) {
            String next3 = content.substring(nextPos, Math.min(nextPos + 3, content.length()));
            return next3.startsWith("UNB") || next3.contains("\n");
        }
        return true; // End of content is acceptable
    }
    
    /**
     * Reconstruct separators from malformed UNA (missing space)
     */
    private static EdifactSeparators reconstructMalformedUna(String content, int separatorStart, int unbIndex) {
        int availableChars = unbIndex - separatorStart;
        
        if (availableChars >= 3) {
            // Extract what we have
            String partial = content.substring(separatorStart, separatorStart + Math.min(availableChars, 6));
            
            // Check for common malformed patterns
            if (partial.length() >= 5) {
                // Pattern: UNA:+.?' (missing space)
                if (partial.matches(":[+][.][?]['].*")) {
                    return new EdifactSeparators(
                        partial.charAt(0), // SubElement separator (:)
                        partial.charAt(1), // Element separator (+)
                        partial.charAt(2), // Decimal notation (.)
                        partial.charAt(3), // Release character (?)
                        ' ',               // Segment separator (space) - RECONSTRUCTED
                        partial.charAt(4)  // Segment terminator (')
                    );
                }
            }
            
            // Handle shorter patterns by looking at standard IATA structure
            if (partial.length() >= 4 && partial.startsWith(":+.?")) {
                // Very likely IATA standard, add missing space and quote
                return new EdifactSeparators(':', '+', '.', '?', ' ', '\'');
            }
        }
        
        return getDefault();
    }
    
    /**
     * Parse with padding for incomplete UNA
     */
    private static EdifactSeparators parseWithPadding(String content, int separatorStart) {
        int availableChars = content.length() - separatorStart;
        if (availableChars < 1) {
            return getDefault();
        }
        
        StringBuilder chars = new StringBuilder();
        for (int i = 0; i < availableChars && i < 6; i++) {
            chars.append(content.charAt(separatorStart + i));
        }
        
        // Pad to 6 characters if needed
        while (chars.length() < 6) {
            chars.append("'"); // Default terminator
        }
        
        return new EdifactSeparators(
            chars.charAt(0), // SubElement separator
            chars.charAt(1), // Element separator
            chars.charAt(2), // Decimal notation
            chars.charAt(3), // Release character
            chars.charAt(4), // Segment separator
            chars.charAt(5)  // Segment terminator
        );
    }
    
    /**
     * Get default IATA standard separators
     */
    public static EdifactSeparators getDefault() {
        return new EdifactSeparators(':', '+', '.', '?', '*', '\'');
    }
    
    /**
     * Generate UNA segment
     */
    public String generateUnaSegment() {
        return "UNA" + subElement + element + decimal + release + segment + terminator;
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
            "- Segment Separator: '%c' (ASCII %d)\n" +
            "- Segment Terminator: '%c' (ASCII %d)\n" +
            "\nUNA Segment: %s",
            subElement, (int) subElement,
            element, (int) element,
            decimal, (int) decimal,
            release, (int) release,
            segment, (int) segment,
            terminator, (int) terminator,
            generateUnaSegment()
        );
    }
    
    // Getters
    public char getSubElement() { return subElement; }
    public char getElement() { return element; }
    public char getDecimal() { return decimal; }
    public char getRelease() { return release; }
    public char getSegment() { return segment; }
    public char getTerminator() { return terminator; }
    
    @Override
    public String toString() {
        return "EdifactSeparators{" +
                "subElement=" + subElement +
                ", element=" + element +
                ", decimal=" + decimal +
                ", release=" + release +
                ", segment=" + segment +
                ", terminator=" + terminator +
                '}';
    }
}