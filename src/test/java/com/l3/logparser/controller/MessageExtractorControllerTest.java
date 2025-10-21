package com.l3.logparser.controller;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Test class for MessageExtractorController formatting functionality
 */
public class MessageExtractorControllerTest {

    /**
     * Test the EDIFACT message formatting functionality
     */
    @Test
    public void testEdifactMessageFormatting() {
        // Create a mock controller to test the private method
        MessageExtractorController controller = new MessageExtractorController();
        
        // Test data - sample EDIFACT PNR message content
        String rawEdifactMessage = "UNA:+.?'UNB+IATB:1+6XPNRGOV+6XPNRJOI+200828:1200+00000001'UNH+1+PNRGOV:02:2'TVL+280825+1200+ATH+DXB+EK0160'";
        
        // Expected result with line breaks after segment terminators
        String expectedFormatted = "UNA:+.?'\nUNB+IATB:1+6XPNRGOV+6XPNRJOI+200828:1200+00000001'\nUNH+1+PNRGOV:02:2'\nTVL+280825+1200+ATH+DXB+EK0160'\n";
        
        // Use reflection to access the private method
        try {
            java.lang.reflect.Method formatMethod = MessageExtractorController.class.getDeclaredMethod("formatEdifactMessage", String.class);
            formatMethod.setAccessible(true);
            String actualFormatted = (String) formatMethod.invoke(controller, rawEdifactMessage);
            
            assertEquals(actualFormatted, expectedFormatted, "EDIFACT message should be formatted with segments on separate lines");
        } catch (Exception e) {
            fail("Failed to test formatEdifactMessage method: " + e.getMessage());
        }
    }

    /**
     * Test formatting with null or empty input
     */
    @Test
    public void testEdifactMessageFormattingWithNullOrEmpty() {
        MessageExtractorController controller = new MessageExtractorController();
        
        try {
            java.lang.reflect.Method formatMethod = MessageExtractorController.class.getDeclaredMethod("formatEdifactMessage", String.class);
            formatMethod.setAccessible(true);
            
            // Test null input
            String result = (String) formatMethod.invoke(controller, (String) null);
            assertNull(result, "Null input should return null");
            
            // Test empty input
            result = (String) formatMethod.invoke(controller, "");
            assertEquals(result, "", "Empty input should return empty string");
            
            // Test whitespace only
            result = (String) formatMethod.invoke(controller, "   ");
            assertEquals(result, "   ", "Whitespace-only input should be returned as-is");
            
        } catch (Exception e) {
            fail("Failed to test formatEdifactMessage method with null/empty input: " + e.getMessage());
        }
    }
}