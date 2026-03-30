package com.l3.logparser.pnr.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for PnrSeparators extraction from UNA and UNB segments
 */
public class PnrSeparatorsTest {

    @Test
    public void testFromUnaSegment() {
        String unaSegment = "UNA:+.?*'";
        PnrSeparators separators = PnrSeparators.fromUnaSegment(unaSegment);

        assertEquals(':', separators.getSubElementSeparator());
        assertEquals('+', separators.getElementSeparator());
        assertEquals('.', separators.getDecimalSeparator());
        assertEquals('?', separators.getReleaseIndicator());
        assertEquals('*', separators.getReservedSeparator());
        assertEquals('\'', separators.getTerminatorSeparator());
        assertTrue(separators.isUnaPresent());
    }

    @Test
    public void testFromUnbSegmentStandard() {
        String unbSegment = "UNB+IATA:1+1A+NORAPI+251027:1258+0002+++X'";
        PnrSeparators separators = PnrSeparators.fromUnbSegment(unbSegment);

        assertEquals('+', separators.getElementSeparator());
        assertEquals(':', separators.getSubElementSeparator());
        assertEquals('\'', separators.getTerminatorSeparator());
        assertFalse(separators.isUnaPresent());
    }

    @Test
    public void testFromUnbSegmentAlternative() {
        String unbSegment = "UNB+IATA:1+EK+NR+250829:1357+00000000000154++PNRGOV'";
        PnrSeparators separators = PnrSeparators.fromUnbSegment(unbSegment);

        assertEquals('+', separators.getElementSeparator());
        assertEquals(':', separators.getSubElementSeparator());
        assertEquals('\'', separators.getTerminatorSeparator());
        assertFalse(separators.isUnaPresent());
    }

    @Test
    public void testFromUnbSegmentWithoutColon() {
        // Edge case: UNB without sub-element separator in first element
        String unbSegment = "UNB+UNOA+SENDER+RECEIVER+250829+12345'";
        PnrSeparators separators = PnrSeparators.fromUnbSegment(unbSegment);

        assertEquals('+', separators.getElementSeparator());
        assertEquals('\'', separators.getTerminatorSeparator());
        // Should still have default sub-element separator
        assertEquals(':', separators.getSubElementSeparator());
    }

    @Test
    public void testFromUnbSegmentInvalid() {
        String unbSegment = "INVALID";
        PnrSeparators separators = PnrSeparators.fromUnbSegment(unbSegment);

        // Should return default separators
        assertEquals('+', separators.getElementSeparator());
        assertEquals(':', separators.getSubElementSeparator());
        assertEquals('\'', separators.getTerminatorSeparator());
    }

    @Test
    public void testFromUnbSegmentNull() {
        PnrSeparators separators = PnrSeparators.fromUnbSegment(null);

        // Should return default separators
        assertEquals('+', separators.getElementSeparator());
        assertEquals(':', separators.getSubElementSeparator());
        assertEquals('\'', separators.getTerminatorSeparator());
    }

    @Test
    public void testSplitElements() {
        PnrSeparators separators = PnrSeparators.fromUnbSegment("UNB+IATA:1+EK+NR'");
        String segment = "UNH+00001+PNRGOV:11:1:IA+EK0160/290825+01:F'";

        String[] elements = separators.splitElements(segment);
        assertTrue(elements.length >= 5);
        assertEquals("UNH", elements[0]);
        assertEquals("00001", elements[1]);
    }

    @Test
    public void testSplitSubElements() {
        PnrSeparators separators = PnrSeparators.fromUnbSegment("UNB+IATA:1+EK+NR'");
        String element = "IATA:1:2:3";

        String[] subElements = separators.splitSubElements(element);
        assertEquals(4, subElements.length);
        assertEquals("IATA", subElements[0]);
        assertEquals("1", subElements[1]);
        assertEquals("2", subElements[2]);
        assertEquals("3", subElements[3]);
    }
}

