package com.l3.logparser.pnr;

import com.l3.logparser.pnr.service.PnrExtractionService;
import com.l3.logparser.pnr.service.PnrExtractionService.PnrExtractionResult;
import com.l3.logparser.pnr.model.PnrMessage;

import java.util.Scanner;

/**
 * Test application for PNR extraction functionality
 * Allows testing the PNR extraction service with different parameters
 */
public class PnrExtractionTestApp {

    private final PnrExtractionService pnrService;

    public PnrExtractionTestApp() {
        this.pnrService = new PnrExtractionService();
    }

    public static void main(String[] args) {
        PnrExtractionTestApp app = new PnrExtractionTestApp();
        app.runInteractiveTest();
    }

    /**
     * Run interactive test mode
     */
    public void runInteractiveTest() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== PNR Extraction Test Application ===");
        System.out.println();

        while (true) {
            try {
                System.out.println("Select an option:");
                System.out.println("1. Extract PNR messages for specific flight");
                System.out.println("2. Extract all PNR messages from directory");
                System.out.println("3. Extract and save messages to input folder");
                System.out.println("4. Exit");
                System.out.print("Choice (1-4): ");

                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1":
                        testSpecificFlightExtraction(scanner);
                        break;
                    case "2":
                        testAllMessagesExtraction(scanner);
                        break;
                    case "3":
                        testExtractAndSave(scanner);
                        break;
                    case "4":
                        System.out.println("Goodbye!");
                        return;
                    default:
                        System.out.println("Invalid choice. Please select 1-4.");
                }

                System.out.println();

            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Test extraction for specific flight
     */
    private void testSpecificFlightExtraction(Scanner scanner) {
        System.out.print("Enter log directory path: ");
        String logDir = scanner.nextLine().trim();

        System.out.print("Enter flight number (e.g., EI0202, 0202): ");
        String flightNumber = scanner.nextLine().trim();

        System.out.println("\nExtracting PNR messages...");
        PnrExtractionResult result = pnrService.extractPnrMessages(logDir, flightNumber);

        displayResults(result);
    }

    /**
     * Test extraction of all messages
     */
    private void testAllMessagesExtraction(Scanner scanner) {
        System.out.print("Enter log directory path: ");
        String logDir = scanner.nextLine().trim();

        System.out.println("\nExtracting all PNR messages...");
        PnrExtractionResult result = pnrService.extractAllPnrMessages(logDir);

        displayResults(result);
    }

    /**
     * Test extract and save functionality
     */
    private void testExtractAndSave(Scanner scanner) {
        System.out.print("Enter log directory path: ");
        String logDir = scanner.nextLine().trim();

        System.out.print("Enter output directory path: ");
        String outputDir = scanner.nextLine().trim();

        System.out.print("Enter flight number (or press Enter for all flights): ");
        String flightNumber = scanner.nextLine().trim();
        if (flightNumber.isEmpty()) {
            flightNumber = null;
        }

        System.out.println("\nExtracting and saving PNR messages...");
        PnrExtractionResult result = pnrService.extractAndSaveMessages(logDir, outputDir, flightNumber);

        displayResults(result);

        if (!result.getSavedFiles().isEmpty()) {
            System.out.println("\nSaved files:");
            result.getSavedFiles().forEach(file -> System.out.println("  - " + file));
        }
    }

    /**
     * Display extraction results
     */
    private void displayResults(PnrExtractionResult result) {
        System.out.println("\n" + result.getSummary());

        // Display errors
        if (result.hasErrors()) {
            System.out.println("\nErrors:");
            result.getErrors().forEach(error -> System.out.println("  ❌ " + error));
        }

        // Display warnings
        if (result.hasWarnings()) {
            System.out.println("\nWarnings:");
            result.getWarnings().forEach(warning -> System.out.println("  ⚠️  " + warning));
        }

        // Display info messages
        if (!result.getInfo().isEmpty()) {
            System.out.println("\nInfo:");
            result.getInfo().forEach(info -> System.out.println("  ℹ️  " + info));
        }

        // Display processed files
        if (!result.getProcessedFiles().isEmpty()) {
            System.out.println("\nProcessed files:");
            result.getProcessedFiles().forEach(file -> System.out.println("  - " + file));
        }

        // Display extracted messages summary
        if (!result.getExtractedMessages().isEmpty()) {
            System.out.println("\nExtracted Messages:");
            result.getMessageGroups().forEach((messageId, parts) -> {
                System.out.printf("  Message ID: %s (%d parts)\n", messageId, parts.size());
                parts.forEach(part -> {
                    String flightInfo = part.getFlightDetails() != null ?
                        part.getFlightDetails().getDisplayName() : "No flight details";
                    System.out.printf("    Part %d (%s) - %s\n",
                        part.getPartNumber(),
                        part.getPartIndicator() != null ? part.getPartIndicator() : "?",
                        flightInfo);
                });
            });
        }

        // Offer to display raw message content
        if (!result.getExtractedMessages().isEmpty()) {
            System.out.print("\nWould you like to see raw message content? (y/N): ");
            Scanner scanner = new Scanner(System.in);
            String showRaw = scanner.nextLine().trim().toLowerCase();

            if ("y".equals(showRaw) || "yes".equals(showRaw)) {
                displayRawMessages(result);
            }
        }
    }

    /**
     * Display raw message content
     */
    private void displayRawMessages(PnrExtractionResult result) {
        System.out.println("\n=== RAW MESSAGE CONTENT ===");

        result.getMessageGroups().forEach((messageId, parts) -> {
            System.out.println("\n--- Message ID: " + messageId + " ---");
            parts.forEach(part -> {
                System.out.printf("\n-- Part %d (%s) --\n",
                    part.getPartNumber(),
                    part.getPartIndicator() != null ? part.getPartIndicator() : "?");

                if (part.getTimestamp() != null) {
                    System.out.println("Timestamp: " + part.getTimestamp());
                }
                if (part.getTraceId() != null) {
                    System.out.println("Trace ID: " + part.getTraceId());
                }

                System.out.println("Content:");
                System.out.println(part.getRawContent());
                System.out.println();
            });
        });
    }

    /**
     * Run automated test with sample data
     */
    public void runAutomatedTest(String logDir, String outputDir) {
        System.out.println("=== Automated PNR Extraction Test ===");

        // Test 1: Extract specific flight
        System.out.println("\n1. Testing specific flight extraction (EI0202)...");
        PnrExtractionResult result1 = pnrService.extractPnrMessages(logDir, "0202");
        System.out.println(result1.getSummary());

        // Test 2: Extract all messages
        System.out.println("\n2. Testing all messages extraction...");
        PnrExtractionResult result2 = pnrService.extractAllPnrMessages(logDir);
        System.out.println(result2.getSummary());

        // Test 3: Extract and save
        if (outputDir != null) {
            System.out.println("\n3. Testing extract and save functionality...");
            PnrExtractionResult result3 = pnrService.extractAndSaveMessages(logDir, outputDir, "0202");
            System.out.println(result3.getSummary());

            if (!result3.getSavedFiles().isEmpty()) {
                System.out.println("Files saved:");
                result3.getSavedFiles().forEach(file -> System.out.println("  - " + file));
            }
        }

        System.out.println("\n=== Test Complete ===");
    }
}
