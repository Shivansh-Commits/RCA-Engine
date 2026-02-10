package com.l3.logparser.pnr.model;

import java.util.regex.Pattern;

/**
 * Model representing EDIFACT separators found in UNA segment
 * Format: UNA:+.?*' where:
 * : is sub-element separator
 * + is element separator  
 * . is decimal separator
 * ? is release indicator
 * * is reserved separator
 * ' is terminator separator
 */
public class PnrSeparators {
    private char subElementSeparator = ':';
    private char elementSeparator = '+';
    private char decimalSeparator = '.';
    private char releaseIndicator = '?';
    private char reservedSeparator = '*';
    private char terminatorSeparator = '\'';
    private boolean isUnaPresent = false;
    private String rawUnaSegment;

    // Callback for detailed logging (set by parser)
    private static java.util.function.Consumer<String> logCallback;

    // Default separators (most common)
    public static final PnrSeparators DEFAULT = new PnrSeparators();

    /**
     * Set logging callback for detailed separator detection logs
     */
    public static void setLogCallback(java.util.function.Consumer<String> callback) {
        logCallback = callback;
    }

    /**
     * Log detailed separator detection message
     */
    private static void logDetail(String message) {
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }

    public PnrSeparators() {}

    /**
     * Create separators from UNA segment
     * @param unaSegment UNA segment like "UNA:+.?*'"
     */
    public static PnrSeparators fromUnaSegment(String unaSegment) {
        PnrSeparators separators = new PnrSeparators();
        
        if (unaSegment != null && unaSegment.startsWith("UNA") && unaSegment.length() >= 9) {
            logDetail("    Found UNA segment: " + unaSegment);

            separators.isUnaPresent = true;
            separators.rawUnaSegment = unaSegment;
            
            // Extract separators from positions 3-8
            separators.subElementSeparator = unaSegment.charAt(3);
            separators.elementSeparator = unaSegment.charAt(4);
            separators.decimalSeparator = unaSegment.charAt(5);
            separators.releaseIndicator = unaSegment.charAt(6);
            separators.reservedSeparator = unaSegment.charAt(7);
            separators.terminatorSeparator = unaSegment.charAt(8);

            logDetail("    Extracted separators from UNA segment - Element: '" + separators.elementSeparator +
                     "', Sub-element: '" + separators.subElementSeparator +
                     "', Terminator: '" + separators.terminatorSeparator + "'");
            logDetail("    Using separators from UNA: " + separators.toString());
        }
        
        return separators;
    }

    /**
     * Create separators from UNB segment when UNA is not present
     * @param unbSegment UNB segment like "UNB+IATA:1+EK+NR+250829:1357+00000000000154++PNRGOV'"
     */
   /* public static PnrSeparators fromUnbSegment(String unbSegment) {
        PnrSeparators separators = new PnrSeparators();
        separators.isUnaPresent = false;

        if (unbSegment == null || !unbSegment.startsWith("UNB") || unbSegment.length() <= 3) {
            // Invalid UNB segment, using default separators
            return separators;
        }

        try {
            logDetail("    UNA segment not found, so using default separators");
            logDetail("    Found UNB segment: " + unbSegment);

            // First character after UNB is the element separator
            separators.elementSeparator = unbSegment.charAt(3);


            // Look for sub-element separator in the first element after UNB
            // First element is between position 4 and the next element separator
            int firstElementEnd = unbSegment.indexOf(separators.elementSeparator, 4);
            if (firstElementEnd > 4) {
                String firstElement = unbSegment.substring(4, firstElementEnd);

                // Sub-element separator is typically ':' - look for it in the first element
                for (int i = 0; i < firstElement.length(); i++) {
                    char c = firstElement.charAt(i);
                    // Check if this character could be a separator (not alphanumeric)
                    if (!Character.isLetterOrDigit(c) && c != separators.elementSeparator) {
                        separators.subElementSeparator = c;
                        logDetail("    Detected sub-element separator from UNB: '" + separators.subElementSeparator + "'");
                        break;
                    }
                }
            }

            // Find terminator by looking for the segment terminator
            // It's typically the last non-whitespace character or ' character
            String trimmed = unbSegment.trim();
            if (!trimmed.isEmpty()) {
                char lastChar = trimmed.charAt(trimmed.length() - 1);
                // Terminator is typically ' or other special characters
                if (!Character.isLetterOrDigit(lastChar) && lastChar != separators.elementSeparator) {
                    separators.terminatorSeparator = lastChar;
                } else {
                    // Look backwards for the terminator
                    for (int i = trimmed.length() - 1; i >= 0; i--) {
                        char c = trimmed.charAt(i);
                        if (c == '\'' || c == '~' || c == '!' || c == '|') {
                            separators.terminatorSeparator = c;
                            break;
                        }
                    }
                }
            }

            logDetail("    Detected terminator separator from UNB: '" + separators.terminatorSeparator + "'");
            logDetail("    Extracted separators from UNB segment - Element: '" + separators.elementSeparator +
                     "', Sub-element: '" + separators.subElementSeparator +
                     "', Terminator: '" + separators.terminatorSeparator + "'");
            logDetail("    Using separators from UNB: " + separators.toString());

        } catch (Exception e) {
            // Silent error handling - return default separators
        }

        return separators;
    }*/

    // Getters and Setters
    public char getSubElementSeparator() { return subElementSeparator; }
    public void setSubElementSeparator(char subElementSeparator) { this.subElementSeparator = subElementSeparator; }

    public char getElementSeparator() { return elementSeparator; }
    public void setElementSeparator(char elementSeparator) { this.elementSeparator = elementSeparator; }

    public char getDecimalSeparator() { return decimalSeparator; }
    public void setDecimalSeparator(char decimalSeparator) { this.decimalSeparator = decimalSeparator; }

    public char getReleaseIndicator() { return releaseIndicator; }
    public void setReleaseIndicator(char releaseIndicator) { this.releaseIndicator = releaseIndicator; }

    public char getReservedSeparator() { return reservedSeparator; }
    public void setReservedSeparator(char reservedSeparator) { this.reservedSeparator = reservedSeparator; }

    public char getTerminatorSeparator() { return terminatorSeparator; }
    public void setTerminatorSeparator(char terminatorSeparator) { this.terminatorSeparator = terminatorSeparator; }

    public boolean isUnaPresent() { return isUnaPresent; }
    public void setUnaPresent(boolean unaPresent) { isUnaPresent = unaPresent; }

    public String getRawUnaSegment() { return rawUnaSegment; }
    public void setRawUnaSegment(String rawUnaSegment) { this.rawUnaSegment = rawUnaSegment; }

    /**
     * Split a segment into elements using the detected element separator
     */
    public String[] splitElements(String segment) {
        return segment.split(Pattern.quote(String.valueOf(elementSeparator)));
    }

    /**
     * Split an element into sub-elements using the detected sub-element separator
     */
    public String[] splitSubElements(String element) {
        return element.split(Pattern.quote(String.valueOf(subElementSeparator)));
    }

    @Override
    public String toString() {
        return "PnrSeparators{" +
                "subElement='" + subElementSeparator + '\'' +
                ", element='" + elementSeparator + '\'' +
                ", terminator='" + terminatorSeparator + '\'' +
                ", unaPresent=" + isUnaPresent +
                '}';
    }
}
