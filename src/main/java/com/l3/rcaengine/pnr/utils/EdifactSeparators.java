package com.l3.rcaengine.pnr.utils;

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
     * Parse UNA segment with robust handling of different patterns
     * Ensures complete isolation between files - each file gets its own separators
     */
    private static EdifactSeparators parseUnaSegment(String content, int separatorStart) {
        // Check if we have enough characters for analysis
        if (separatorStart >= content.length()) {
            return getDefault();
        }
        
        // Standard case: try to extract exactly 6 characters after UNA
        if (separatorStart + 6 <= content.length()) {
            String unaChars = content.substring(separatorStart, separatorStart + 6);
            
            // Validate that this looks like a proper UNA pattern
            if (isValidUnaSequence(unaChars, content, separatorStart + 6)) {
                return new EdifactSeparators(
                    unaChars.charAt(0), // SubElement separator
                    unaChars.charAt(1), // Element separator
                    unaChars.charAt(2), // Decimal notation
                    unaChars.charAt(3), // Release character
                    unaChars.charAt(4), // Segment separator (reserved)
                    unaChars.charAt(5)  // Segment terminator
                );
            }
        }
        
        // Fallback: try to find UNB and work backwards
        int unbIndex = content.indexOf("UNB", separatorStart);
        if (unbIndex > separatorStart) {
            return reconstructFromAvailableChars(content, separatorStart, unbIndex);
        }
        
        // Last resort: use default
        return getDefault();
    }
    
    /**
     * Validate if a 6-character UNA sequence looks correct
     */
    private static boolean isValidUnaSequence(String unaChars, String content, int nextPos) {
        // Basic sanity checks
        if (unaChars.length() != 6) {
            return false;
        }
        
        // Check that characters are not control characters (except space)
        for (int i = 0; i < 6; i++) {
            char c = unaChars.charAt(i);
            if (c < 32 && c != 10 && c != 13) { // Allow space, LF, CR
                return false;
            }
        }
        
        // Check if followed by UNB or reasonable content
        if (nextPos < content.length()) {
            String following = content.substring(nextPos, Math.min(nextPos + 3, content.length()));
            return following.startsWith("UNB") || following.contains("\n") || following.contains("\r");
        }
        
        return true; // End of content is acceptable
    }
    
    /**
     * Reconstruct separators from available characters between UNA and UNB
     */
    private static EdifactSeparators reconstructFromAvailableChars(String content, int separatorStart, int unbIndex) {
        int availableLength = unbIndex - separatorStart;
        
        if (availableLength < 5) {
            return getDefault(); // Not enough data
        }
        
        String available = content.substring(separatorStart, Math.min(separatorStart + 6, unbIndex));
        
        // Pad to 6 characters if needed
        while (available.length() < 6) {
            available += "'"; // Default terminator
        }
        
        return new EdifactSeparators(
            available.charAt(0), // SubElement separator
            available.charAt(1), // Element separator
            available.charAt(2), // Decimal notation
            available.charAt(3), // Release character
            available.charAt(4), // Segment separator (reserved)
            available.charAt(5)  // Segment terminator
        );
    }
    
    /**
     * Get default EDIFACT standard separators for files without UNA segment
     * Component separator: :
     * Element separator: +
     * Decimal mark: .
     * Release indicator: ?
     * Reserved: *
     * Segment terminator: '
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
            "EDIFACT Separator Configuration:\n" +
            "- Component Data Element Separator (SubElement): '%c' (ASCII %d)\n" +
            "- Data Element Separator (Element): '%c' (ASCII %d)\n" +
            "- Decimal Notation: '%c' (ASCII %d)\n" +
            "- Release Character: '%c' (ASCII %d)\n" +
            "- Reserved: '%c' (ASCII %d)\n" +
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