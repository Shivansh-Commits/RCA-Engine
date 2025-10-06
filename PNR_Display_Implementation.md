# PNR Display Implementation Guide

## Overview
Successfully implemented PNR-specific display functionality for the L3-Engine application. When users select "PNR" from the data type dropdown, the UI automatically adapts to show PNR-relevant information.

## PNR Display Format

### Input Passengers Table
| No. | Passenger Name | Locator | Source | Count |
|-----|---------------|---------|--------|-------|
| 1   | SMITH/JOHN    | ABC123  | input.txt | 1 |
| 2   | DOE/JANE      | XYZ789  | input.txt | 1 |

### Output Passengers Table  
| No. | Passenger Name | Locator | Source | Count |
|-----|---------------|---------|--------|-------|
| 1   | SMITH/JOHN    | ABC123  | output.txt | 1 |
| 2   | BROWN/MIKE    | DEF456  | output.txt | 1 |

### Dropped PNRs Table
| No. | Passenger Name | Locator | Source | Count |
|-----|---------------|---------|--------|-------|
| 1   | DOE/JANE      | XYZ789  | input.txt | 1 |

### Duplicate PNRs Table
| No. | Passenger Name | Locator | Source | Count |
|-----|---------------|---------|--------|-------|
| 1   | SMITH/JOHN    | ABC123  | Input + Output | 1 |
| 2   | BROWN/MIKE    | DEF456  | Output Only | 1 |

**Note:** The "Duplicate" table in PNR context shows:
- **Input + Output**: Passengers that appear in both files (successfully processed)
- **Output Only**: Passengers that only appear in output (newly added)

## PNR Statistics Display

### Statistics Panel Shows:
- **Total Input PNRs**: Count of unique PNRs in input files
- **Total Unique PNRs**: Count of unique PNR locators (same as input)
- **Total Output PNRs**: Count of unique PNRs in output files  
- **Dropped PNRs**: Count of PNRs that were in input but not in output
- **Duplicate PNRs**: Count of PNRs that appear in multiple contexts

## Dynamic UI Adaptation

### When PNR is Selected:
1. **Column Headers Change:**
   - "NAD (Passenger Name)" → "Passenger Name"
   - "DTM" → "Locator" 
   - "DOC" column → Hidden (empty)
   - "RecordedKey" column → Hidden (empty)

2. **Statistics Labels Change:**
   - "Total Input Passengers" → "Total Input PNRs"
   - "Total Unique Input Passengers" → "Total Unique PNRs"
   - "Total Output Passengers" → "Total Output PNRs"
   - "Dropped Passengers" → "Dropped PNRs"
   - "Duplicate Passengers" → "Duplicate PNRs"

### When API is Selected:
- UI automatically reverts to original API terminology
- All original column headers and statistics labels restored

## Technical Implementation

### Key Methods Added:
- `updateUIForPNRMode()` - Updates UI for PNR display
- `updateUIForAPIMode()` - Restores UI for API display
- `convertPnrTableRows()` - Converts PNR data to table format

### Data Mapping:
- **No**: Row number
- **Passenger Name**: Extracted from TIF segments
- **Locator**: PNR record locator (RLOC)
- **Source**: File name or "Input + Output" for matches
- **Count**: Always 1 for PNR records

## Usage Instructions

1. **Launch L3-Engine Application**
2. **Select "PNR" from Data Type dropdown**
3. **Choose folder with EDIFACT files** (must contain "input" and "output" in filenames)
4. **Click Process button**
5. **View PNR-specific results** in automatically adapted tables

## File Requirements

### Input Files:
- Must contain "input" in filename (e.g., `pnr_input_20241005.edi`)
- Valid EDIFACT format with PNRGOV message type
- Contains UNA, UNB, UNH segments with passenger data

### Output Files:
- Must contain "output" in filename (e.g., `pnr_output_20241005.edi`)
- Same EDIFACT format as input
- Contains processed passenger records

## Benefits

### User Experience:
- **Seamless switching** between API and PNR modes
- **Context-appropriate terminology** for each data type
- **Consistent interface** with dynamic adaptation

### Data Analysis:
- **Clear visualization** of PNR processing results
- **Easy identification** of dropped and duplicate records
- **Source tracking** for duplicate analysis

### Development:
- **Modular architecture** allows easy extension
- **Reusable UI components** for both API and PNR
- **Clean separation** of concerns between data types

---

**Status**: ✅ Fully Implemented and Ready for Testing  
**Integration**: Complete with existing L3-Engine JavaFX application  
**Compatibility**: Maintains full API functionality while adding PNR capabilities