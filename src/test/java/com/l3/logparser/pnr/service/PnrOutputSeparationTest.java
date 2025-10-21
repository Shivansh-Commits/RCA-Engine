package com.l3.logparser.pnr.service;

import com.l3.logparser.pnr.model.PnrMessage;
import com.l3.logparser.enums.MessageType;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import static org.testng.Assert.*;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.ArrayList;

/**
 * Test class for PNR output file separation functionality
 */
public class PnrOutputSeparationTest {

    private PnrExtractionService service;
    private Path tempDir;

    @BeforeClass
    public void setUp() throws IOException {
        service = new PnrExtractionService();
        tempDir = Files.createTempDirectory("pnr-separation-test");
    }

    @AfterClass
    public void tearDown() throws IOException {
        // Clean up temporary files
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        System.err.println("Warning: Could not delete " + path + ": " + e.getMessage());
                    }
                });
        }
    }

    @Test
    public void testSaveMessagesWithInputOutputSeparation() throws IOException {
        // Create test messages - both input and output
        List<PnrMessage> messages = new ArrayList<>();
        
        // Input message (multipart)
        PnrMessage inputMessage1 = new PnrMessage();
        inputMessage1.setMessageId("INPUT001");
        inputMessage1.setFlightNumber("EK0160");
        inputMessage1.setPartNumber(1);
        inputMessage1.setPartIndicator("C");
        inputMessage1.setDirection(MessageType.INPUT);
        inputMessage1.setRawContent("UNA:+.?*'UNH+INPUT001+PNRGOV:02:2+EK0160/250828/1200+01:C'TVL+280825+1200+ATH+DXB+EK0160'");
        messages.add(inputMessage1);
        
        PnrMessage inputMessage2 = new PnrMessage();
        inputMessage2.setMessageId("INPUT001");
        inputMessage2.setFlightNumber("EK0160");
        inputMessage2.setPartNumber(2);
        inputMessage2.setPartIndicator("F");
        inputMessage2.setDirection(MessageType.INPUT);
        inputMessage2.setRawContent("UNA:+.?*'UNH+INPUT001+PNRGOV:02:2+EK0160/250828/1200+02:F'TVL+280825+1200+ATH+DXB+EK0160'");
        messages.add(inputMessage2);
        
        // Output message (single part)
        PnrMessage outputMessage = new PnrMessage();
        outputMessage.setMessageId("OUTPUT001");
        outputMessage.setFlightNumber("EK0160");
        outputMessage.setPartNumber(1);
        outputMessage.setPartIndicator("F");
        outputMessage.setDirection(MessageType.OUTPUT);
        outputMessage.setRawContent("UNA:+.?*'UNH+OUTPUT001+PNRGOV:13:1:XS+EK0160/250829/1435/24+01:F'TVL+290825:1435:290825:2325+OSL+DXB+EK+0160'");
        messages.add(outputMessage);
        
        // Save messages
        boolean success = service.saveExtractedMessages(messages, tempDir.toString());
        assertTrue(success, "Save operation should succeed");
        
        // Verify directory structure
        Path inputDir = tempDir.resolve("input");
        Path outputDir = tempDir.resolve("output");
        
        assertTrue(Files.exists(inputDir), "Input directory should be created");
        assertTrue(Files.exists(outputDir), "Output directory should be created");
        assertTrue(Files.isDirectory(inputDir), "Input path should be a directory");
        assertTrue(Files.isDirectory(outputDir), "Output path should be a directory");
        
        // Verify input files
        Path inputFile = inputDir.resolve("PNR_EK0160_INPUT.txt");
        assertTrue(Files.exists(inputFile), "Input file should be created");
        
        String inputContent = Files.readString(inputFile);
        assertTrue(inputContent.contains("=== PNR INPUT Messages for Flight EK0160 ==="), "Input file should have header");
        assertTrue(inputContent.contains("=== Part 1 (C)"), "Input file should contain part 1");
        assertTrue(inputContent.contains("=== Part 2 (F)"), "Input file should contain part 2");
        assertTrue(inputContent.contains("Direction: INPUT"), "Input file should indicate direction");
        
        // Verify output files
        Path outputFile = outputDir.resolve("PNR_EK0160_OUTPUT.txt");
        assertTrue(Files.exists(outputFile), "Output file should be created");
        
        String outputContent = Files.readString(outputFile);
        assertTrue(outputContent.contains("=== PNR OUTPUT Messages for Flight EK0160 ==="), "Output file should have header");
        assertTrue(outputContent.contains("=== Part 1 (F)"), "Output file should contain part 1");
        assertTrue(outputContent.contains("Direction: OUTPUT"), "Output file should indicate direction");
        
        System.out.println("✅ Input and output files saved to separate directories successfully");
        System.out.println("   Input file: " + inputFile);
        System.out.println("   Output file: " + outputFile);
    }

    @Test
    public void testSaveOnlyInputMessages() throws IOException {
        Path testDir = tempDir.resolve("input-only-test");
        Files.createDirectories(testDir);
        
        // Create only input messages
        List<PnrMessage> messages = new ArrayList<>();
        
        PnrMessage inputMessage = new PnrMessage();
        inputMessage.setMessageId("INPUT001");
        inputMessage.setFlightNumber("QR512");
        inputMessage.setPartNumber(1);
        inputMessage.setPartIndicator("F");
        inputMessage.setDirection(MessageType.INPUT);
        inputMessage.setRawContent("UNA:+.?*'UNH+INPUT001+PNRGOV:02:2+QR512/250828/2045+01:F'");
        messages.add(inputMessage);
        
        boolean success = service.saveExtractedMessages(messages, testDir.toString());
        assertTrue(success, "Save operation should succeed");
        
        // Verify only input directory is created with content
        Path inputDir = testDir.resolve("input");
        Path outputDir = testDir.resolve("output");
        
        assertTrue(Files.exists(inputDir), "Input directory should be created");
        assertTrue(Files.exists(outputDir), "Output directory should be created (even if empty)");
        
        // Input directory should have files
        assertTrue(Files.list(inputDir).findFirst().isPresent(), "Input directory should contain files");
        
        // Output directory should be empty
        assertFalse(Files.list(outputDir).findFirst().isPresent(), "Output directory should be empty");
    }

    @Test
    public void testSaveOnlyOutputMessages() throws IOException {
        Path testDir = tempDir.resolve("output-only-test");
        Files.createDirectories(testDir);
        
        // Create only output messages
        List<PnrMessage> messages = new ArrayList<>();
        
        PnrMessage outputMessage = new PnrMessage();
        outputMessage.setMessageId("OUTPUT001");
        outputMessage.setFlightNumber("QR512");
        outputMessage.setPartNumber(1);
        outputMessage.setPartIndicator("F");
        outputMessage.setDirection(MessageType.OUTPUT);
        outputMessage.setRawContent("UNA:+.?*'UNH+OUTPUT001+PNRGOV:13:1:XS+QR512/250829/2045/24+01:F'");
        messages.add(outputMessage);
        
        boolean success = service.saveExtractedMessages(messages, testDir.toString());
        assertTrue(success, "Save operation should succeed");
        
        // Verify only output directory has content
        Path inputDir = testDir.resolve("input");
        Path outputDir = testDir.resolve("output");
        
        assertTrue(Files.exists(inputDir), "Input directory should be created");
        assertTrue(Files.exists(outputDir), "Output directory should be created");
        
        // Output directory should have files
        assertTrue(Files.list(outputDir).findFirst().isPresent(), "Output directory should contain files");
        
        // Input directory should be empty
        assertFalse(Files.list(inputDir).findFirst().isPresent(), "Input directory should be empty");
    }
}