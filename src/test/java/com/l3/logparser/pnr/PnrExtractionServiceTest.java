package com.l3.logparser.pnr;

import com.l3.logparser.pnr.service.PnrExtractionService;
import com.l3.logparser.pnr.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for PnrExtractionService using sample PNR log data
 */
class PnrExtractionServiceTest {

    private PnrExtractionService service;

    @BeforeEach
    void setUp() {
        service = new PnrExtractionService();
    }

    @Test
    void testPnrExtractionWithSampleData(@TempDir Path tempDir) throws Exception {
        // Create sample PNR log file similar to MessageMHPNRGOV.log
        String samplePnrData = """
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
            
            INFO [2025-08-28T12:35:10,123][pnrGovResponseListenerContainer-29][ID:e0636a4e-25ae-4212-9594-6c843e3450f9] [trace.id:e0636a4e-25ae-4212-9594-6c843e3450f9] - PNRGOV_MESSAGE_HANDLER.Request - MQ Message received from [topic://SPLIT.PNRGOV_PNR_PUSH] Variant [RAW_TEXT] Message body [ 
            UNA:+.?*' 
            UNB+IATA:1+EK+NR+250828:1235+00000000000149++PNRGOV' 
            UNG+PNRGOV+EK+NR+250828:1235+00000000000149+IA+11:1' 
            UNH+00000000000149+PNRGOV:11:1:IA+EK0160/290825/1435+02:F' 
            MSG+:23' 
            ORG+EK:DXB' 
            TVL+290825:1435:290825:2325+OSL+DXB+EK+0160' 
            EQN+200'
            UNT+15+00000000000149'
            UNE+1+00000000000149'
            UNZ+1+00000000000149'
            
            INFO [2025-08-28T12:36:15,456][pnrGovResponseListenerContainer-30][ID:f1234567-89ab-cdef-1234-567890abcdef] [trace.id:f1234567-89ab-cdef-1234-567890abcdef] - PNRGOV_MESSAGE_HANDLER.Request - MQ Message received from [topic://SPLIT.PNRGOV_PNR_PUSH] Variant [RAW_TEXT] Message body [ 
            UNA:+.?*' 
            UNB+IATA:1+QR+NR+250828:1236+00000000000200++PNRGOV' 
            UNG+PNRGOV+QR+NR+250828:1236+00000000000200+IA+11:1' 
            UNH+00000000000200+PNRGOV:11:1:IA+QR512/290825/2045+01:F' 
            MSG+:24' 
            ORG+QR:DOH' 
            TVL+290825:2045:300825:0630+DOH+LHR+QR+0512' 
            EQN+150'
            UNT+15+00000000000200'
            UNE+1+00000000000200'
            UNZ+1+00000000000200'
            """;

        Path logFile = tempDir.resolve("MessageMHPNRGOV.log.2025-08-28-12");
        Files.writeString(logFile, samplePnrData);

        // Test extraction for EK0160 flight
        PnrExtractionService.PnrExtractionResult result = service.extractPnrMessages(
            tempDir.toString(), "EK0160", null, null, null);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getExtractedMessages().size());
        assertEquals(1, result.getMultipartGroups().size());

        // Verify the multipart group
        PnrMultipartGroup group = result.getMultipartGroups().get(0);
        assertEquals("00000000000149_EK0160", group.getGroupId());
        assertEquals(2, group.getActualPartCount());
        assertTrue(group.isComplete());
        assertTrue(group.hasAllParts());

        // Verify individual messages
        List<PnrMessage> messages = result.getExtractedMessages();
        
        // First part
        PnrMessage part1 = messages.stream()
            .filter(m -> m.getPartNumber() == 1)
            .findFirst()
            .orElse(null);
        assertNotNull(part1);
        assertEquals("00000000000149", part1.getMessageReferenceNumber());
        assertEquals("C", part1.getPartIndicator());
        assertFalse(part1.isLastPart());
        assertTrue(part1.isMultipart());

        // Second part
        PnrMessage part2 = messages.stream()
            .filter(m -> m.getPartNumber() == 2)
            .findFirst()
            .orElse(null);
        assertNotNull(part2);
        assertEquals("00000000000149", part2.getMessageReferenceNumber());
        assertEquals("F", part2.getPartIndicator());
        assertTrue(part2.isLastPart());
        assertTrue(part2.isMultipart());

        // Verify flight details
        PnrFlightDetails flightDetails = part1.getFlightDetails();
        assertNotNull(flightDetails);
        assertEquals("290825", flightDetails.getDepartureDate());
        assertEquals("1435", flightDetails.getDepartureTime());
        assertEquals("290825", flightDetails.getArrivalDate());
        assertEquals("2325", flightDetails.getArrivalTime());
        assertEquals("OSL", flightDetails.getDepartureAirport());
        assertEquals("DXB", flightDetails.getArrivalAirport());
        assertEquals("EK", flightDetails.getAirlineCode());
        assertEquals("0160", flightDetails.getFlightNumber());

        // Test extraction for QR512 flight (single part)
        PnrExtractionService.PnrExtractionResult result2 = service.extractPnrMessages(
            tempDir.toString(), "QR512", null, null, null);

        assertTrue(result2.isSuccess());
        assertEquals(1, result2.getExtractedMessages().size());
        assertEquals(1, result2.getMultipartGroups().size());

        PnrMessage singleMessage = result2.getExtractedMessages().get(0);
        assertEquals("F", singleMessage.getPartIndicator());
        assertTrue(singleMessage.isLastPart());
        assertFalse(singleMessage.isMultipart());

        System.out.println("✅ PNR extraction test completed successfully!");
        System.out.println("   - Multipart messages: " + result.getMultipartGroups().size());
        System.out.println("   - Single part messages: " + result2.getExtractedMessages().size());
        System.out.println("   - Total messages processed: " + (result.getExtractedMessages().size() + result2.getExtractedMessages().size()));
    }

    @Test
    void testSaveExtractedMessages(@TempDir Path tempDir) throws Exception {
        // Create a simple test message
        PnrMessage message = new PnrMessage();
        message.setMessageId("12345");
        message.setFlightNumber("EK0160");
        message.setPartNumber(1);
        message.setPartIndicator("F");
        message.setLastPart(true);
        message.setRawContent("UNA:+.?*'\nUNB+IATA:1+EK+NR+250828:1235+12345++PNRGOV'\nUNH+12345+PNRGOV:11:1:IA+EK0160/290825/1435+01:F'\nTVL+290825:1435:290825:2325+OSL+DXB+EK+0160'\nUNT+15+12345'\nUNZ+1+12345'");

        List<PnrMessage> messages = List.of(message);

        boolean success = service.saveExtractedMessages(messages, tempDir.toString());
        assertTrue(success);

        // Verify the file was created with individual part naming
        Path inputDir = tempDir.resolve("input");
        assertTrue(Files.exists(inputDir));

        Path savedFile = inputDir.resolve("PNR_EK0160_Part1_F_INPUT.txt");
        assertTrue(Files.exists(savedFile));

        String content = Files.readString(savedFile);
        assertTrue(content.contains("Part 1"));
        assertTrue(content.contains("Message ID: 12345"));
        assertTrue(content.contains("UNA:+.?*'"));

        System.out.println("✅ PNR file saving test completed successfully!");
    }
}