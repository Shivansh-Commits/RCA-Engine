# PNR Data Extraction Module

## Overview

This module provides specialized functionality for extracting PNR (Passenger Name Record) data from `MessageMHPNRGOV.log*` files. It handles the unique EDIFACT message structure used in PNR communications, including UNA separators and TVL flight detail segments.

## Key Features

- **Automatic UNA Separator Detection**: Dynamically parses UNA segments to identify separators for each message
- **Multi-Part Message Support**: Handles message parts with proper identification (C=first part, F=last part)
- **TVL Flight Details Extraction**: Extracts comprehensive flight information from TVL segments
- **File-based Output**: Saves extracted message parts to organized input folder structure
- **Completeness Analysis**: Validates message completeness and identifies missing parts
- **Deduplication**: Removes duplicate messages across multiple log files

## Architecture

### Package Structure
```
com.l3.logparser.pnr/
├── model/
│   ├── PnrMessage.java           # PNR message representation
│   └── PnrFlightDetails.java     # Flight details from TVL segments
├── parser/
│   └── PnrEdifactParser.java     # EDIFACT parser for PNR messages
├── service/
│   └── PnrExtractionService.java # Main extraction service
└── PnrExtractionTestApp.java     # Test application
```

### Key Classes

#### PnrMessage
Represents a single PNR message part with:
- Message ID and part number
- Part indicator (C/F for first/last)
- Flight details from TVL segment
- Raw EDIFACT content
- Timestamp and trace ID from log entry

#### PnrFlightDetails
Contains flight information extracted from TVL segments:
- Departure/arrival dates and times (ddmmyy/hhmm format)
- Airport codes (departure/arrival)
- Airline code and flight number
- Formatted display methods

#### PnrEdifactParser
Handles EDIFACT parsing specific to PNR messages:
- UNA separator detection and parsing
- UNH segment analysis for message ID and parts
- TVL segment parsing for flight details
- Timestamp and trace ID extraction

#### PnrExtractionService
Main service providing extraction functionality:
- Log file discovery and processing
- Message deduplication and grouping
- Completeness analysis
- File saving with organized naming

## Usage Examples

### Basic Extraction
```java
PnrExtractionService service = new PnrExtractionService();
PnrExtractionResult result = service.extractPnrMessages(
    "C:/logs", 
    "EI0202"  // Flight number
);

if (result.isSuccess()) {
    System.out.println("Found " + result.getExtractedMessages().size() + " message parts");
    result.getMessageGroups().forEach((messageId, parts) -> {
        System.out.println("Message " + messageId + ": " + parts.size() + " parts");
    });
}
```

### Extract and Save to Input Folder
```java
PnrExtractionService service = new PnrExtractionService();
PnrExtractionResult result = service.extractAndSaveMessages(
    "C:/logs",      // Log directory
    "C:/output",    // Output directory (will create 'input' subfolder)
    "EI0202"        // Flight number
);

if (result.isSuccess()) {
    System.out.println("Saved files:");
    result.getSavedFiles().forEach(System.out::println);
}
```

### Extract All Flights
```java
PnrExtractionService service = new PnrExtractionService();
PnrExtractionResult result = service.extractAllPnrMessages("C:/logs");
```

## Message Format Understanding

### UNA Separator Structure
```
UNA:+.? '
   |||||| 
   |||||+-- Terminator separator (')
   ||||+--- Reserved separator (space)
   |||+---- Release indicator (?)
   ||+----- Decimal separator (.)
   |+------ Element separator (+)
   +------- Sub-element separator (:)
```

### Part Identification in UNH
```
UNH+068010082521+PNRGOV:13:1:IA+EI0680/100825/0615/01+01:C'
                                                        ^^
                                                        ||
                                                        |+-- C=First part
                                                        +--- Part number (01)

UNH+068010082521+PNRGOV:13:1:IA+EI0680/100825/0615/01+23:F'
                                                        ^^
                                                        ||
                                                        |+-- F=Last part  
                                                        +--- Part number (23)
```

### TVL Flight Details Format
```
TVL+100825:0630:100825:0735+DUB+MAN+EI+0202'
    |     |    |     |     |   |   |  |
    |     |    |     |     |   |   |  +-- Flight number
    |     |    |     |     |   |   +----- Airline code
    |     |    |     |     |   +--------- Arrival airport
    |     |    |     |     +------------- Departure airport
    |     |    |     +------------------- Arrival time (hhmm)
    |     |    +------------------------- Arrival date (ddmmyy)
    |     +------------------------------ Departure time (hhmm)
    +------------------------------------ Departure date (ddmmyy)
```

Note: Dates are in ddmmyy format where:
- 10 = day (10th)
- 08 = month (August) 
- 25 = year (2025)

## File Organization

### Input Files
The module processes files matching the pattern `MessageMHPNRGOV.log*`:
- `MessageMHPNRGOV.log`
- `MessageMHPNRGOV.log.1`
- `MessageMHPNRGOV.log.2`
- etc.

### Output Files
Extracted message parts are saved with descriptive filenames:
```
input/
├── PNR_020210082521_EI0202_250810_part01_first.edifact
├── PNR_020210082521_EI0202_250810_part02.edifact
├── PNR_020210082521_EI0202_250810_part03.edifact
└── PNR_020210082521_EI0202_250810_part23_last.edifact
```

Filename components:
- `PNR_` - Prefix identifying PNR data
- `020210082521` - Message ID from UNH segment
- `EI0202` - Flight number (airline + number)
- `100825` - Departure date (ddmmyy format: 10=day, 08=month, 25=year)
- `part01` - Part number (zero-padded)
- `_first/_last` - Indicators for first/last parts

## Testing

### Interactive Testing
Run the test application for manual testing:
```java
java com.l3.logparser.pnr.PnrExtractionTestApp
```

The test application provides:
1. Extract messages for specific flight
2. Extract all messages from directory  
3. Extract and save to input folder
4. Interactive result viewing

### Automated Testing
```java
PnrExtractionTestApp app = new PnrExtractionTestApp();
app.runAutomatedTest("C:/logs", "C:/output");
```

## Integration Notes

This module is designed as a standalone component that can be:
1. **Used independently** for PNR data extraction
2. **Integrated with existing API extraction** logic
3. **Extended for PNR output extraction** (future enhancement)

The module follows the same patterns as the existing API extraction logic but with PNR-specific:
- File patterns (`MessageMHPNRGOV.log*` vs `das.log*`, etc.)
- Message structure (PNRGOV vs PAXLST)
- Parsing rules (TVL vs TDT segments)
- Part identification (C/F indicators vs different patterns)

## Error Handling

The module provides comprehensive error handling:
- **File Access Errors**: Clear messages for missing directories/files
- **Parsing Errors**: Graceful handling of malformed EDIFACT
- **UNA Detection**: Fallback to standard separators if UNA parsing fails
- **Part Analysis**: Warnings for incomplete message sequences

## Performance Considerations

- **Large File Support**: Streams large log files (>100MB) in chunks
- **Memory Efficient**: Processes one log entry at a time
- **Deduplication**: Efficient HashMap-based duplicate removal
- **Pattern Matching**: Optimized regex patterns for EDIFACT parsing

## Future Enhancements

1. **PNR Output Extraction**: Extend for processing PNR response/output messages
2. **Advanced Filtering**: Add date range and route-based filtering
3. **Batch Processing**: Support for processing multiple directories
4. **Export Formats**: Support for CSV/JSON export in addition to EDIFACT files
5. **UI Integration**: JavaFX interface similar to existing API extraction UI

This module provides a solid foundation for PNR data processing and can be easily extended for additional PNR-related functionality as requirements evolve.
