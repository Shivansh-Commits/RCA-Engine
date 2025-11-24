package com.l3;

import com.l3.logparser.api.parser.EdifactParser;
import com.l3.rcaengine.api.model.Flight;
import com.l3.rcaengine.api.model.Passenger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Summary test demonstrating the key functionality of L3 Engine components
 * Tests the basic differentiation between API and PNR data formats
 */
@DisplayName("L3 Engine Summary Tests")
class L3EngineSummaryTest {

    @Test
    @DisplayName("Should demonstrate API vs PNR flight number format differences")
    void testFlightNumberFormatDifferences() {
        // API Format (without leading zeros)
        String[] apiFlightNumbers = {"TS230", "DY1303", "MS775", "AC1234"};

        // PNR Format (with leading zeros)
        String[] pnrFlightNumbers = {"TS0230", "DY01303", "MS0775", "AC01234"};

        // Verify API format
        for (String apiFlightNo : apiFlightNumbers) {
            assertFalse(apiFlightNo.matches(".*[A-Z]0\\d+"),
                "API flight " + apiFlightNo + " should not have leading zeros");

            // Should be able to create Flight object with API format
            Flight apiFlight = new Flight(apiFlightNo, "1200", "250101", "YYZ", "LHR");
            assertEquals(apiFlightNo, apiFlight.flightNo);
        }

        // Verify PNR format
        for (String pnrFlightNo : pnrFlightNumbers) {
            assertTrue(pnrFlightNo.matches(".*[A-Z]0\\d+"),
                "PNR flight " + pnrFlightNo + " should have leading zeros");
        }

        // Demonstrate the key difference
        assertEquals("TS230", apiFlightNumbers[0], "API format: TS230");
        assertEquals("TS0230", pnrFlightNumbers[0], "PNR format: TS0230");
    }

    @Test
    @DisplayName("Should demonstrate basic LogParser functionality")
    void testLogParserBasicFunctionality() {
        // Given
        EdifactParser parser = new EdifactParser();

        // When - Test with simple content
        var messages = parser.parseLogContent("Simple log without EDIFACT", "TEST123");

        // Then
        assertNotNull(parser, "Parser should be instantiated");
        assertNotNull(messages, "Parser should return non-null result");
        assertTrue(messages.isEmpty(), "Non-EDIFACT content should result in empty list");
    }

    @Test
    @DisplayName("Should demonstrate basic RCA Engine model usage")
    void testRcaEngineModels() {
        // Flight creation with API format
        Flight flight = new Flight("TS230", "1130", "250724", "YYZ", "DUB");
        assertEquals("TS230", flight.flightNo, "Flight number should match API format");
        assertEquals("YYZ", flight.getDepPort(), "Departure port should be set");
        assertEquals("DUB", flight.getArrPort(), "Arrival port should be set");

        // Passenger creation
        Passenger passenger = new Passenger("DOE:JOHN", "P123456789", "850101", "TS230", "P");
        assertEquals("DOE:JOHN", passenger.getName(), "Passenger name should be set");
        assertEquals("P123456789", passenger.getDocNum(), "Document number should be set");
        assertTrue(passenger.getDocTypeWithParens().contains("P"), "Document type should be included");

        // Demonstrate passenger count functionality
        assertEquals(1, passenger.getCount(), "Initial count should be 1");
        passenger.incrementCount();
        assertEquals(2, passenger.getCount(), "Count should increment");
    }

    @Test
    @DisplayName("Should demonstrate test data validation")
    void testDataValidation() {
        // Verify test data contains expected API patterns
        String testContent = "TS230/250724/1130"; // API format from test data

        assertTrue(testContent.contains("TS230"), "Should contain flight number TS230");
        assertFalse(testContent.contains("TS0230"), "Should not contain PNR format TS0230");

        // Date format validation
        assertTrue(testContent.contains("250724"), "Should contain date in YYMMDD format");

        // Time format validation
        assertTrue(testContent.contains("1130"), "Should contain time in HHMM format");
    }
}
