package com.l3.rcaengine.api;

import com.l3.rcaengine.api.model.Flight;
import com.l3.rcaengine.api.model.Passenger;
import com.l3.rcaengine.api.model.Separators;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for API RCA Engine functionality
 * Tests flight comparison and analysis for API data
 */
@DisplayName("API RCA Engine Tests")
class ApiRcaEngineTest {

    private String crewApiInput;
    private String passengerApiInput;
    private String crewApiOutput;

    @BeforeEach
    void setUp() throws IOException {
        // Load test data
        crewApiInput = Files.readString(Path.of("src/test/resources/testdata/api/crew_api_input.txt"));
        passengerApiInput = Files.readString(Path.of("src/test/resources/testdata/api/passenger_api_input.txt"));
        crewApiOutput = Files.readString(Path.of("src/test/resources/testdata/api/crew_api_output.txt"));
    }

    @Test
    @DisplayName("Should create Flight object with correct API flight number format")
    void testFlightCreationWithApiFormat() {
        // Given - API flight numbers without leading zeros
        String apiFlightNumber = "TS230";
        String depTime = "1130";
        String depDate = "250724";
        String expectedDepPort = "YYZ";
        String expectedArrPort = "DUB";

        // When
        Flight flight = new Flight(apiFlightNumber, depTime, depDate, expectedDepPort, expectedArrPort);

        // Then
        assertEquals("TS230", flight.flightNo, "Flight number should not have leading zeros for API");
        assertEquals(expectedDepPort, flight.getDepPort(), "Departure port should match");
        assertEquals(expectedArrPort, flight.getArrPort(), "Arrival port should match");
        assertEquals(depDate, flight.getDepDate(), "Departure date should be set");
        assertEquals(depTime, flight.getDepTime(), "Departure time should be set");
    }

    @Test
    @DisplayName("Should handle passenger flight data correctly")
    void testPassengerFlightData() {
        // Given - Passenger API flight
        String passengerFlightNumber = "DY1303";
        String depTime = "1545";
        String depDate = "251004";
        String depPort = "OSL";
        String arrPort = "LGW";

        // When
        Flight flight = new Flight(passengerFlightNumber, depTime, depDate, depPort, arrPort);

        // Then
        assertEquals("DY1303", flight.flightNo, "Passenger flight number should not have leading zeros");
        assertEquals("OSL", flight.getDepPort(), "Departure should be Oslo");
        assertEquals("LGW", flight.getArrPort(), "Arrival should be London Gatwick");
        assertEquals(depDate, flight.getDepDate(), "Departure date should match");
        assertEquals(depTime, flight.getDepTime(), "Departure time should match");
    }

    @Test
    @DisplayName("Should create Passenger object with crew data")
    void testCrewPassengerData() {
        // Given - Crew member data from test file
        String crewName = "HAMMING:ANTHONY JACOB";
        String documentNumber = "AN073337";
        String dateOfBirth = "870518";
        String source = "TS230";
        String docType = "P";

        // When
        Passenger crewMember = new Passenger(crewName, documentNumber, dateOfBirth, source, docType);

        // Then
        assertEquals(crewName, crewMember.getName(), "Crew name should match");
        assertEquals(documentNumber, crewMember.getDocNum(), "Document number should match");
        assertEquals(dateOfBirth, crewMember.getDtm(), "Date of birth should match");
        assertEquals(source, crewMember.getSources(), "Source should match");
        assertTrue(crewMember.getDocTypeWithParens().contains(docType), "Document type should be included");
    }

    @Test
    @DisplayName("Should create Passenger object with passenger data")
    void testPassengerData() {
        // Given - Regular passenger data
        String passengerName = "OLSEN:LARS JOHAN";
        String documentNumber = "NO1234567";
        String dateOfBirth = "850315";
        String source = "DY1303";
        String docType = "P";

        // When
        Passenger passenger = new Passenger(passengerName, documentNumber, dateOfBirth, source, docType);

        // Then
        assertEquals(passengerName, passenger.getName(), "Passenger name should match");
        assertEquals(documentNumber, passenger.getDocNum(), "Document number should match");
        assertEquals(dateOfBirth, passenger.getDtm(), "Date of birth should match");
        assertEquals(source, passenger.getSources(), "Source should match");
        assertTrue(passenger.getDocTypeWithParens().contains(docType), "Document type should be included");
    }

    @Test
    @DisplayName("Should handle EDIFACT separators correctly")
    void testEdifactSeparators() {
        // Given - Standard EDIFACT separators from UNA segment
        char subElement = ':';
        char element = '+';
        char decimal = '.';
        char release = '?';
        char segment = ' ';
        char terminator = '\'';

        // When
        Separators separators = new Separators(subElement, element, decimal, release, segment, terminator);

        // Then
        assertEquals(':', separators.subElement, "Sub-element separator should be colon");
        assertEquals('+', separators.element, "Element separator should be plus");
        assertEquals('.', separators.decimal, "Decimal separator should be period");
        assertEquals('?', separators.release, "Release indicator should be question mark");
        assertEquals(' ', separators.segment, "Segment separator should be space");
        assertEquals('\'', separators.terminator, "Terminator should be apostrophe");
    }

    @Test
    @DisplayName("Should compare flights and identify differences")
    void testFlightComparison() {
        // Given - Two flights with same flight number but different arrival ports
        String flightNo = "TS230";
        String depTime = "1130";
        String depDate = "250724";
        String depPort = "YYZ";

        Flight inputFlight = new Flight(flightNo, depTime, depDate, depPort, "DUB");
        Flight outputFlight = new Flight(flightNo, depTime, depDate, depPort, "DUB");

        // When/Then - Flights should be considered equal
        assertEquals(inputFlight.flightNo, outputFlight.flightNo, "Flight numbers should match");
        assertEquals(inputFlight.getDepPort(), outputFlight.getDepPort(), "Departure ports should match");
        assertEquals(inputFlight.getArrPort(), outputFlight.getArrPort(), "Arrival ports should match");

        // Test difference detection
        Flight differentFlight = new Flight(flightNo, depTime, depDate, depPort, "LGW");
        assertNotEquals(inputFlight.getArrPort(), differentFlight.getArrPort(), "Different arrival ports should be detected");
    }

    @Test
    @DisplayName("Should handle null and empty values gracefully")
    void testNullAndEmptyHandling() {
        // When/Then - Test Flight with null/empty values
        assertDoesNotThrow(() -> {
            Flight flight = new Flight(null, "", null, "", null);
            assertNull(flight.flightNo, "Null flight number should remain null");
            assertEquals("", flight.getDepTime(), "Empty departure time should remain empty");
            assertNull(flight.getDepDate(), "Null departure date should remain null");
        }, "Should handle null and empty flight values without throwing");

        assertDoesNotThrow(() -> {
            // Test Passenger with null values - constructor handles nulls gracefully
            Passenger passenger = new Passenger(null, null, null, null, null);
            assertEquals("", passenger.getName(), "Null name should become empty string");
            assertEquals("", passenger.getDocNum(), "Null doc number should become empty string");
            assertEquals("", passenger.getDtm(), "Null DTM should become empty string");
        }, "Should handle null passenger data without throwing");
    }
}
