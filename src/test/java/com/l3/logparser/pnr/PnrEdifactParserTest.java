package com.l3.logparser.pnr;

import com.l3.logparser.pnr.parser.PnrEdifactParser;
import com.l3.logparser.pnr.model.*;
import com.l3.logparser.enums.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PnrEdifactParser
 */
class PnrEdifactParserTest {

    private PnrEdifactParser parser;

    @BeforeEach
    void setUp() {
        parser = new PnrEdifactParser();
    }

    @Test
    void testParseSinglePartPnrMessage() {
        String logContent = """
            INFO [2025-08-28T12:35:09,873][pnrGovResponseListenerContainer-28][ID:e0636a4e-25ae-4212-9594-6c843e3450f8] [trace.id:e0636a4e-25ae-4212-9594-6c843e3450f8] - PNRGOV_MESSAGE_HANDLER.Request - MQ Message received from [topic://SPLIT.PNRGOV_PNR_PUSH] Variant [RAW_TEXT] Message body [ 
            UNA:+.?*' 
            UNB+IATA:1+EK+NR+250828:1235+00000000000149++PNRGOV' 
            UNG+PNRGOV+EK+NR+250828:1235+00000000000149+IA+11:1' 
            UNH+00000000000154+PNRGOV:11:1:IA+EK0160/290825/1435+01:F' 
            MSG+:22' 
            ORG+EK:DXB' 
            TVL+290825:1435:290825:2325+OSL+DXB+EK+0160' 
            EQN+188'
            UNT+15+00000000000154'
            UNE+1+00000000000149'
            UNZ+1+00000000000149'
            """;

        List<PnrMessage> messages = parser.parseLogContent(logContent, "EK0160");

        assertEquals(1, messages.size());
        
        PnrMessage message = messages.get(0);
        assertEquals("00000000000154", message.getMessageId());
        assertEquals("00000000000154", message.getMessageReferenceNumber());
        assertEquals("EK0160", message.getFlightNumber());
        assertEquals(1, message.getPartNumber());
        assertEquals("F", message.getPartIndicator());
        assertTrue(message.isLastPart());
        assertFalse(message.isMultipart());
        assertEquals("PNRGOV", message.getMessageType());
        assertEquals(MessageType.INPUT, message.getDirection());

        // Check flight details
        PnrFlightDetails flightDetails = message.getFlightDetails();
        assertNotNull(flightDetails);
        assertEquals("290825", flightDetails.getDepartureDate());
        assertEquals("1435", flightDetails.getDepartureTime());
        assertEquals("290825", flightDetails.getArrivalDate());
        assertEquals("2325", flightDetails.getArrivalTime());
        assertEquals("OSL", flightDetails.getDepartureAirport());
        assertEquals("DXB", flightDetails.getArrivalAirport());
        assertEquals("EK", flightDetails.getAirlineCode());
        assertEquals("0160", flightDetails.getFlightNumber());
    }

    @Test
    void testParseMultipartPnrMessage() {
        String logContent = """
            INFO [2025-08-28T12:35:09,873][pnrGovResponseListenerContainer-28][ID:e0636a4e-25ae-4212-9594-6c843e3450f8] [trace.id:e0636a4e-25ae-4212-9594-6c843e3450f8] - PNRGOV_MESSAGE_HANDLER.Request - MQ Message received from [topic://SPLIT.PNRGOV_PNR_PUSH] Variant [RAW_TEXT] Message body [ 
            UNA:+.?*' 
            UNB+IATA:1+EK+NR+250828:1235+00000000000149++PNRGOV' 
            UNG+PNRGOV+EK+NR+250828:1235+00000000000149+IA+11:1' 
            UNH+00000000000149+PNRGOV:11:1:IA+EK0160/290825/1435+01:C' 
            MSG+:22' 
            ORG+EK:DXB' 
            TVL+290825:1435:290825:2325+OSL+DXB+EK+0160' 
            EQN+188'
            UNT+15+00000000000149'
            UNE+1+00000000000149'
            UNZ+1+00000000000149'
            """;

        List<PnrMessage> messages = parser.parseLogContent(logContent, "EK0160");

        assertEquals(1, messages.size());
        
        PnrMessage message = messages.get(0);
        assertEquals("00000000000149", message.getMessageReferenceNumber());
        assertEquals(1, message.getPartNumber());
        assertEquals("C", message.getPartIndicator());
        assertFalse(message.isLastPart());
        assertTrue(message.isMultipart());
    }

    @Test
    void testParseMessageWithoutUna() {
        String logContent = """
            INFO [2025-08-28T12:35:09,873] - PNRGOV_MESSAGE_HANDLER.Request - MQ Message received 
            UNB+IATA:1+EK+NR+250829:1357+00000000000154++PNRGOV' 
            UNG+PNRGOV+EK+NR+250829:1357+00000000000154+IA+11:1' 
            UNH+00000000000154+PNRGOV:11:1:IA+EK0160/290825/1435+01:F' 
            MSG+:22' 
            TVL+290825:1435:290825:2325+OSL+DXB+EK+0160' 
            UNT+15+00000000000154'
            UNE+1+00000000000154'
            UNZ+1+00000000000154'
            """;

        List<PnrMessage> messages = parser.parseLogContent(logContent, "EK0160");

        assertEquals(1, messages.size());
        
        PnrMessage message = messages.get(0);
        assertNotNull(message.getSeparators());
        assertFalse(message.getSeparators().isUnaPresent());
        assertEquals('+', message.getSeparators().getElementSeparator());
        assertEquals(':', message.getSeparators().getSubElementSeparator());
    }

    @Test
    void testFilterByFlightNumber() {
        String logContent = """
            UNA:+.?*' 
            UNB+IATA:1+EK+NR+250828:1235+00000000000149++PNRGOV' 
            UNH+00000000000154+PNRGOV:11:1:IA+EK0160/290825/1435+01:F' 
            TVL+290825:1435:290825:2325+OSL+DXB+EK+0160' 
            UNT+15+00000000000154'
            UNZ+1+00000000000149'
            """;

        // Test with matching flight number
        List<PnrMessage> matchingMessages = parser.parseLogContent(logContent, "EK0160");
        assertEquals(1, matchingMessages.size());

        // Test with non-matching flight number
        List<PnrMessage> nonMatchingMessages = parser.parseLogContent(logContent, "QR123");
        assertEquals(0, nonMatchingMessages.size());

        // Test with null flight number (should return all)
        List<PnrMessage> allMessages = parser.parseLogContent(logContent, null);
        assertEquals(1, allMessages.size());
    }

    @Test
    void testParsePartNumberOnly() {
        String logContent = """
            UNA:+.?*' 
            UNB+IATA:1+EK+NR+250828:1235+00000000000149++PNRGOV' 
            UNH+011906092521+PNRGOV:13:1:IA+EI0119/060925/1600/01+06' 
            TVL+060925:1600:070925:0800+DUB+LHR+EI+0119' 
            UNT+15+011906092521'
            UNZ+1+00000000000149'
            """;

        List<PnrMessage> messages = parser.parseLogContent(logContent, "EI0119");

        assertEquals(1, messages.size());
        
        PnrMessage message = messages.get(0);
        assertEquals(6, message.getPartNumber());
        assertEquals("C", message.getPartIndicator()); // Should be C since part > 1
        assertFalse(message.isLastPart());
        assertTrue(message.isMultipart());
    }

    @Test
    void testParseFinalMultipart() {
        String logContent = """
            UNA:+.?*' 
            UNB+IATA:1+EK+NR+250828:1235+00000000000149++PNRGOV' 
            UNH+068010082521+PNRGOV:13:1:IA+EI0680/100825/0615/01+11:F' 
            TVL+100825:0615:100825:1200+DUB+LGW+EI+0680' 
            UNT+15+068010082521'
            UNZ+1+00000000000149'
            """;

        List<PnrMessage> messages = parser.parseLogContent(logContent, "EI0680");

        assertEquals(1, messages.size());
        
        PnrMessage message = messages.get(0);
        assertEquals(11, message.getPartNumber());
        assertEquals("F", message.getPartIndicator());
        assertTrue(message.isLastPart());
        assertTrue(message.isMultipart()); // Part 11 indicates multipart
    }
}