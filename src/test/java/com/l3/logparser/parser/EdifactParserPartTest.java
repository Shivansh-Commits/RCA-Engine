package com.l3.logparser.parser;

import com.l3.logparser.model.EdifactMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for EDIFACT parser part number recognition
 */
public class EdifactParserPartTest {

    @Test
    public void testPartNumberRecognition() {
        EdifactParser parser = new EdifactParser();

        // Test data from the user's log showing different part numbers
        String logContent = """
            INFO [2025-07-25T05:21:51,064][apiListenerContainer-5][ID:2920b7f7-718a-4c4b-9f69-7ecc94e1aa8c] [trace.id:2920b7f7-718a-4c4b-9f69-7ecc94e1aa8c] - API_MESSAGE_HANDLER.Request - MQ Message received from [queue://APITODAS] Variant [APIS_RAW] Message body [
             SOH QK LONPPXS
             .MUCKMTS 250521
             STX UNA:(.) -
             UNB(UNOA:4(1A:TS(IRLAPIS(250725:0121(1A8323504((APIS-
             UNG(PAXLST(AIR TRANSAT:TS(IRLAPIS(250725:0121(1A8323505(UN(D:05B-
             UNH(1(PAXLST:D:05B:UN:IATA(TS2302507251130(05-
             BGM(745-
             NAD(MS(((AIR TRANSAT SECURITY CALL CENTER-
             COM(151490603302652:TE(15149876373:FX-
             TDT(20(TS230-
             LOC(125(YYZ-
             DTM(189:2507242355:201-
             LOC(87(DUB-
             DTM(232:2507251130:201-
            
            INFO [2025-07-25T05:21:51,113][apiListenerContainer-4][ID:93b681f4-8d46-434c-9cbe-402d72b2551b] [trace.id:93b681f4-8d46-434c-9cbe-402d72b2551b] - API_MESSAGE_HANDLER.Request - MQ Message received from [queue://APITODAS] Variant [APIS_RAW] Message body [
             SOH QK LONPPXS
             .MUCKMTS 250521
             STX UNA:(.) -
             UNB(UNOA:4(1A:TS(IRLAPIS(250725:0121(1A8323504((APIS-
             UNG(PAXLST(AIR TRANSAT:TS(IRLAPIS(250725:0121(1A8323505(UN(D:05B-
             UNH(1(PAXLST:D:05B:UN:IATA(TS2302507251130(13-
             BGM(745-
             TDT(20(TS230-
            
            INFO [2025-07-25T05:21:51,334][typeBListenerContainer-13][ID:8391dd24-7834-42aa-8944-0c4b3fcc4ede] [trace.id:8391dd24-7834-42aa-8944-0c4b3fcc4ede] - Forward.API_MESSAGE_HANDLER - MQ Message sent to [APITODAS] Variant [APIS_RAW] Message body [
             SOH QK LONPPXS
             .MUCKMTS 250521
             STX UNA:(.) -
             UNB(UNOA:4(1A:TS(IRLAPIS(250725:0121(1A8323504((APIS-
             UNG(PAXLST(AIR TRANSAT:TS(IRLAPIS(250725:0121(1A8323505(UN(D:05B-
             UNH(1(PAXLST:D:05B:UN:IATA(TS2302507251130(09-
             BGM(745-
             TDT(20(TS230-
            
            INFO [2025-07-25T05:21:51,370][apiListenerContainer-2][ID:8391dd24-7834-42aa-8944-0c4b3fcc4ede] [trace.id:8391dd24-7834-42aa-8944-0c4b3fcc4ede] - API_MESSAGE_HANDLER.Request - MQ Message received from [queue://APITODAS] Variant [APIS_RAW] Message body [
             SOH QK LONPPXS
             .MUCKMTS 250521
             STX UNA:(.) -
             UNH(1(PAXLST:D:05B:UN:IATA(TS2302507251130(08-
             BGM(745-
             TDT(20(TS230-
            
            INFO [2025-07-25T05:21:51,419][typeBListenerContainer-2][ID:a1d86ebe-a41d-467e-bbbf-601f82efcb04] [trace.id:a1d86ebe-a41d-467e-bbbf-601f82efcb04] - Forward.API_MESSAGE_HANDLER - MQ Message sent to [APITODAS] Variant [APIS_RAW] Message body [
             UNH(1(PAXLST:D:05B:UN:IATA(TS2302507251130(02-
             BGM(745-
             TDT(20(TS230-
            """;

        // Parse the log content
        List<EdifactMessage> messages = parser.parseLogContent(logContent, "TS230");

        // Should find messages with different part numbers
        System.out.println("Found " + messages.size() + " messages");

        for (EdifactMessage message : messages) {
            System.out.println("Message ID: " + message.getMessageId());
            System.out.println("Part Number: " + message.getPartNumber());
            System.out.println("Part Indicator: " + message.getPartIndicator());
            System.out.println("Flight Number: " + message.getFlightNumber());
            System.out.println("---");
        }

        // Verify that we found messages and they have correct part numbers
        assertTrue(messages.size() > 0, "Should find at least one message");

        // Check that we have different part numbers
        boolean foundPart05 = messages.stream().anyMatch(m -> m.getPartNumber() == 5);
        boolean foundPart13 = messages.stream().anyMatch(m -> m.getPartNumber() == 13);
        boolean foundPart09 = messages.stream().anyMatch(m -> m.getPartNumber() == 9);
        boolean foundPart08 = messages.stream().anyMatch(m -> m.getPartNumber() == 8);
        boolean foundPart02 = messages.stream().anyMatch(m -> m.getPartNumber() == 2);

        System.out.println("Found part 05: " + foundPart05);
        System.out.println("Found part 13: " + foundPart13);
        System.out.println("Found part 09: " + foundPart09);
        System.out.println("Found part 08: " + foundPart08);
        System.out.println("Found part 02: " + foundPart02);

        assertTrue(foundPart05, "Should find part 05");
        assertTrue(foundPart13, "Should find part 13");
        assertTrue(foundPart09, "Should find part 09");
        assertTrue(foundPart08, "Should find part 08");
        assertTrue(foundPart02, "Should find part 02");
    }
}
