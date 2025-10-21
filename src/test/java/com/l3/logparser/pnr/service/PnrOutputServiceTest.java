package com.l3.logparser.pnr.service;

import com.l3.logparser.pnr.model.*;
import com.l3.logparser.pnr.service.PnrExtractionService.PnrExtractionResult;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import static org.testng.Assert.*;

import java.io.*;
import java.nio.file.*;
import java.util.List;

/**
 * Test class for PNR output message extraction at service level
 */
public class PnrOutputServiceTest {

    private PnrExtractionService service;
    private Path tempDir;
    private Path outputLogFile;

    @BeforeClass
    public void setUp() throws IOException {
        service = new PnrExtractionService();
        
        // Create temporary directory for test files
        tempDir = Files.createTempDirectory("pnr-output-test");
        
        // Create a sample MessageForwarder.log file with output messages
        outputLogFile = tempDir.resolve("MessageForwarder.log.2025-08-28-12");
        
        String outputLogContent = 
            "INFO  [2025-08-28T12:38:23,379][fwdListenerContainer-4][ID:69ef7fc9-8328-46a9-93f2-541d9d9570ca] " +
            "[trace.id:69ef7fc9-8328-46a9-93f2-541d9d9570ca] - " +
            "Forward.BUSINESS_RULES_PROCESSOR - " +
            "MQ Message sent to [TO.NO.PNR.OUT] Variant [RAW_TEXT] Message body [\n" +
            "UNA:+.?*'\n" +
            "UNB+IATA:1+EK+NR+250828:1238+00000003539989'\n" +
            "UNG+PNRGOV+SITA+NR+250828:1238+00000003557989+XS+13:1'\n" +
            "UNH+00000003574989+PNRGOV:13:1:XS+EK0160/250829/1435/24+01:F'\n" +
            "MSG+:22'\n" +
            "ORG+EK:SITA+++:IATA'\n" +
            "TVL+290825:1435:290825:2325+OSL+DXB+EK+0160'\n" +
            "EQN+188'\n" +
            "]\n" +
            "\n" +
            "INFO  [2025-08-28T12:40:15,521][fwdListenerContainer-5][ID:70ef7fc9-8328-46a9-93f2-541d9d9570cb] " +
            "[trace.id:70ef7fc9-8328-46a9-93f2-541d9d9570cb] - " +
            "Forward.BUSINESS_RULES_PROCESSOR - " +
            "MQ Message sent to [TO.NO.PNR.OUT] Message body [\n" +
            "UNA:+.?*'\n" +
            "UNB+IATA:1+QR+NR+250828:1240+00000003540000'\n" +
            "UNH+00000003575000+PNRGOV:13:1:XS+QR512/250829/2045/24+01:F'\n" +
            "TVL+290825:2045:290825:0525+DOH+DXB+QR+512'\n" +
            "]";
        
        Files.write(outputLogFile, outputLogContent.getBytes());
    }

    @AfterClass
    public void tearDown() throws IOException {
        // Clean up temporary files
        if (Files.exists(outputLogFile)) {
            Files.delete(outputLogFile);
        }
        if (Files.exists(tempDir)) {
            Files.delete(tempDir);
        }
    }

    @Test
    public void testExtractOutputMessages() {
        PnrExtractionResult result = service.extractPnrMessages(
            tempDir.toString(), 
            "EK0160", 
            null, null, null
        );

        assertTrue(result.isSuccess(), "Extraction should be successful");
        assertNotNull(result.getExtractedMessages(), "Extracted messages should not be null");
        
        // Should find the EK0160 output message
        List<PnrMessage> messages = result.getExtractedMessages();
        assertEquals(messages.size(), 1, "Should find exactly one EK0160 message");
        
        PnrMessage message = messages.get(0);
        assertEquals(message.getFlightNumber(), "EK0160", "Flight number should match");
        assertEquals(message.getDirection(), com.l3.logparser.enums.MessageType.OUTPUT, "Should be OUTPUT message");
        assertFalse(message.isMultipart(), "Output messages should not be multipart");
        assertEquals(message.getPartIndicator(), "F", "Should have F (final) indicator");
        
        // Verify processed files include output file
        List<String> processedFiles = result.getProcessedFiles();
        assertTrue(processedFiles.stream().anyMatch(f -> f.contains("MessageForwarder.log") && f.contains("OUTPUT")), 
                   "Should process MessageForwarder.log file marked as OUTPUT");
    }

    @Test
    public void testExtractMultipleOutputMessages() {
        PnrExtractionResult result = service.extractPnrMessages(
            tempDir.toString(), 
            null, // No flight filter - get all
            null, null, null
        );

        assertTrue(result.isSuccess(), "Extraction should be successful");
        
        List<PnrMessage> messages = result.getExtractedMessages();
        assertEquals(messages.size(), 2, "Should find both output messages");
        
        // Verify both messages are output type
        for (PnrMessage message : messages) {
            assertEquals(message.getDirection(), com.l3.logparser.enums.MessageType.OUTPUT, "All should be OUTPUT messages");
            assertFalse(message.isMultipart(), "Output messages should not be multipart");
            assertEquals(message.getPartIndicator(), "F", "Should have F (final) indicator");
        }
        
        // Verify different flights
        List<String> flightNumbers = messages.stream()
            .map(PnrMessage::getFlightNumber)
            .distinct()
            .sorted()
            .toList();
        assertEquals(flightNumbers.size(), 2, "Should have 2 different flights");
        assertTrue(flightNumbers.contains("EK0160"), "Should contain EK0160");
        assertTrue(flightNumbers.contains("QR512"), "Should contain QR512");
    }

    @Test
    public void testOutputMessageGrouping() {
        PnrExtractionResult result = service.extractPnrMessages(
            tempDir.toString(), 
            null, // No flight filter
            null, null, null
        );

        assertTrue(result.isSuccess(), "Extraction should be successful");
        
        List<PnrMultipartGroup> groups = result.getMultipartGroups();
        assertNotNull(groups, "Groups should not be null");
        assertEquals(groups.size(), 2, "Should have 2 groups (one for each output message)");
        
        // Each output message should be in its own group and marked as complete
        for (PnrMultipartGroup group : groups) {
            assertEquals(group.getParts().size(), 1, "Each output group should have exactly 1 part");
            assertTrue(group.isComplete(), "Output groups should be complete");
            assertTrue(group.hasAllParts(), "Output groups should have all parts");
            
            PnrMessage message = group.getParts().get(0);
            assertEquals(message.getDirection(), com.l3.logparser.enums.MessageType.OUTPUT, "Should be OUTPUT");
        }
        
        // Should have no warnings about incomplete messages
        assertTrue(result.getWarnings().isEmpty(), "Should have no warnings for complete output messages");
    }

    @Test
    public void testCombinedInputAndOutputProcessing() throws IOException {
        // Create an input log file as well
        Path inputLogFile = tempDir.resolve("MessageMHPNRGOV.log.2025-08-28-12");
        
        String inputLogContent = 
            "2025-08-28T12:35:15,123 [trace.id:input-trace-123] INFO - PNRGOV_PNR_PUSH - " +
            "UNA:+.?*'UNB+IATB:1+6XPNRGOV+6XPNRJOI+250828:1200+00000001'UNH+1+PNRGOV:02:2+EK0160/250828/1200+01:C'" +
            "TVL+280825+1200+ATH+DXB+EK0160'" +
            "\n" +
            "2025-08-28T12:35:16,456 [trace.id:input-trace-124] INFO - PNRGOV_PNR_PUSH - " +
            "UNA:+.?*'UNB+IATB:1+6XPNRGOV+6XPNRJOI+250828:1200+00000001'UNH+1+PNRGOV:02:2+EK0160/250828/1200+02:F'" +
            "TVL+280825+1200+ATH+DXB+EK0160'";
        
        Files.write(inputLogFile, inputLogContent.getBytes());
        
        try {
            PnrExtractionResult result = service.extractPnrMessages(
                tempDir.toString(), 
                "EK0160", 
                null, null, null
            );

            assertTrue(result.isSuccess(), "Extraction should be successful");
            
            List<PnrMessage> messages = result.getExtractedMessages();
            assertEquals(messages.size(), 3, "Should find 2 input parts + 1 output message");
            
            // Count input vs output
            long inputCount = messages.stream()
                .filter(m -> m.getDirection() == com.l3.logparser.enums.MessageType.INPUT)
                .count();
            long outputCount = messages.stream()
                .filter(m -> m.getDirection() == com.l3.logparser.enums.MessageType.OUTPUT)
                .count();
                
            assertEquals(inputCount, 2, "Should have 2 input messages");
            assertEquals(outputCount, 1, "Should have 1 output message");
            
            // Verify processed files include both types
            List<String> processedFiles = result.getProcessedFiles();
            assertTrue(processedFiles.stream().anyMatch(f -> f.contains("MessageMHPNRGOV.log") && f.contains("INPUT")), 
                       "Should process input file");
            assertTrue(processedFiles.stream().anyMatch(f -> f.contains("MessageForwarder.log") && f.contains("OUTPUT")), 
                       "Should process output file");
            
        } finally {
            Files.delete(inputLogFile);
        }
    }
}