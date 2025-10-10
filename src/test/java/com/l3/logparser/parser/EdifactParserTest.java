package com.l3.logparser.parser;

import com.l3.logparser.model.EdifactMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EdifactParserTest {

    @Test
    public void testTS230PartNumberParsing() {
        EdifactParser parser = new EdifactParser();

        // Test data simulating the TS230 logs with different part numbers
        String logWithPart2 = "INFO  [2025-07-25T05:21:51,425][typeBListenerContainer-16][ID:e419cdbc-3c71-464c-84ed-704edbdd23dc] [trace.id:e419cdbc-3c71-464c-84ed-704edbdd23dc] - Forward.API_MESSAGE_HANDLER - MQ Message sent to [APITODAS] Variant [APIS_RAW] Message body [\r\n" +
                "$SOH$QK LONPPXS\r\n" +
                ".MUCKMTS 250521\r\n" +
                "$STX$UNA:(.) -\r\n" +
                "UNB(UNOA:4(1A:TS(IRLAPIS(250725:0121(1A8323504((APIS-\r\n" +
                "UNG(PAXLST(AIR TRANSAT:TS(IRLAPIS(250725:0121(1A8323505(UN(D:05B-\r\n" +
                "UNH(1(PAXLST:D:05B:UN:IATA(TS2302507251130(02-\r\n" +
                "BGM(745-\r\n" +
                "TDT(20(TS230-\r\n" +
                "UNZ(1(1A8323505-";

        String logWithPart8 = "INFO  [2025-07-25T05:21:51,424][typeBListenerContainer-19][ID:c2796f4c-8c1e-4bbd-83e5-356c6a9bc02c] [trace.id:c2796f4c-8c1e-4bbd-83e5-356c6a9bc02c] - Forward.API_MESSAGE_HANDLER - MQ Message sent to [APITODAS] Variant [APIS_RAW] Message body [\r\n" +
                "$SOH$QK LONPPXS\r\n" +
                ".MUCKMTS 250521\r\n" +
                "$STX$UNA:(.) -\r\n" +
                "UNB(UNOA:4(1A:TS(IRLAPIS(250725:0121(1A8323504((APIS-\r\n" +
                "UNG(PAXLST(AIR TRANSAT:TS(IRLAPIS(250725:0121(1A8323505(UN(D:05B-\r\n" +
                "UNH(1(PAXLST:D:05B:UN:IATA(TS2302507251130(08-\r\n" +
                "BGM(745-\r\n" +
                "TDT(20(TS230-\r\n" +
                "UNZ(1(1A8323505-";

        String logWithPart9 = "INFO  [2025-07-25T05:21:51,334][typeBListenerContainer-13][ID:8391dd24-7834-42aa-8944-0c4b3fcc4ede] [trace.id:8391dd24-7834-42aa-8944-0c4b3fcc4ede] - Forward.API_MESSAGE_HANDLER - MQ Message sent to [APITODAS] Variant [APIS_RAW] Message body [\r\n" +
                "$SOH$QK LONPPXS\r\n" +
                ".MUCKMTS 250521\r\n" +
                "$STX$UNA:(.) -\r\n" +
                "UNB(UNOA:4(1A:TS(IRLAPIS(250725:0121(1A8323504((APIS-\r\n" +
                "UNG(PAXLST(AIR TRANSAT:TS(IRLAPIS(250725:0121(1A8323505(UN(D:05B-\r\n" +
                "UNH(1(PAXLST:D:05B:UN:IATA(TS2302507251130(09-\r\n" +
                "BGM(745-\r\n" +
                "TDT(20(TS230-\r\n" +
                "UNZ(1(1A8323505-";

        String logWithPart11 = "INFO  [2025-07-25T05:21:51,478][apiListenerContainer-3][ID:a1d86ebe-a41d-467e-bbbf-601f82efcb04] [trace.id:a1d86ebe-a41d-467e-bbbf-601f82efcb04] - API_MESSAGE_HANDLER.Request - MQ Message received from [queue://APITODAS] Variant [APIS_RAW] Message body [\r\n" +
                "$SOH$QK LONPPXS\r\n" +
                ".MUCKMTS 250521\r\n" +
                "$STX$UNA:(.) -\r\n" +
                "UNB(UNOA:4(1A:TS(IRLAPIS(250725:0121(1A8323504((APIS-\r\n" +
                "UNG(PAXLST(AIR TRANSAT:TS(IRLAPIS(250725:0121(1A8323505(UN(D:05B-\r\n" +
                "UNH(1(PAXLST:D:05B:UN:IATA(TS2302507251130(11-\r\n" +
                "BGM(745-\r\n" +
                "TDT(20(TS230-\r\n" +
                "UNZ(1(1A8323505-";

        // Test parsing of part 2 (with leading zero - NOT WORKING)
        System.out.println("=== Testing Part 2 (Expected: NOT WORKING) ===");
        List<EdifactMessage> messages2 = parser.parseLogContent(logWithPart2, "TS230");
        System.out.println("Part 2 - Messages found: " + messages2.size());
        if (!messages2.isEmpty()) {
            System.out.println("Part 2 - Part Number: " + messages2.get(0).getPartNumber());
            System.out.println("Part 2 - Flight: " + messages2.get(0).getFlightNumber());
        }

        // Test parsing of part 8 (with leading zero - NOT WORKING)
        System.out.println("\n=== Testing Part 8 (Expected: NOT WORKING) ===");
        List<EdifactMessage> messages8 = parser.parseLogContent(logWithPart8, "TS230");
        System.out.println("Part 8 - Messages found: " + messages8.size());
        if (!messages8.isEmpty()) {
            System.out.println("Part 8 - Part Number: " + messages8.get(0).getPartNumber());
            System.out.println("Part 8 - Flight: " + messages8.get(0).getFlightNumber());
        }

        // Test parsing of part 9 (with leading zero - WORKING)
        System.out.println("\n=== Testing Part 9 (Expected: WORKING) ===");
        List<EdifactMessage> messages9 = parser.parseLogContent(logWithPart9, "TS230");
        System.out.println("Part 9 - Messages found: " + messages9.size());
        if (!messages9.isEmpty()) {
            System.out.println("Part 9 - Part Number: " + messages9.get(0).getPartNumber());
            System.out.println("Part 9 - Flight: " + messages9.get(0).getFlightNumber());
        }

        // Test parsing of part 11 (no leading zero - WORKING)
        System.out.println("\n=== Testing Part 11 (Expected: WORKING) ===");
        List<EdifactMessage> messages11 = parser.parseLogContent(logWithPart11, "TS230");
        System.out.println("Part 11 - Messages found: " + messages11.size());
        if (!messages11.isEmpty()) {
            System.out.println("Part 11 - Part Number: " + messages11.get(0).getPartNumber());
            System.out.println("Part 11 - Flight: " + messages11.get(0).getFlightNumber());
        }

        // Print summary
        System.out.println("\n=== SUMMARY ===");
        System.out.println("Part 2 (02): " + (messages2.isEmpty() ? "FAILED" : "SUCCESS"));
        System.out.println("Part 8 (08): " + (messages8.isEmpty() ? "FAILED" : "SUCCESS"));
        System.out.println("Part 9 (09): " + (messages9.isEmpty() ? "FAILED" : "SUCCESS"));
        System.out.println("Part 11 (11): " + (messages11.isEmpty() ? "FAILED" : "SUCCESS"));
    }
}
