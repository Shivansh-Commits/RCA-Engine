# Cross-File Duplicate Detection Fix

## Problem Statement
The duplicate detection was incorrectly finding passengers that appeared multiple times within the **same merged input file**, but the requirement was to find passengers that appear across **different input files** before merging.

**Example of Required Logic:**
- `input_1.edi` contains: PNR `YZXWT` with passenger `JOHN DOE`
- `input_2.edi` contains: PNR `YZXWT` with passenger `JOHN DOE`
- This should be considered a **duplicate** (same PNR across different input files)

## Root Cause
- **Old Logic**: Counted passenger key occurrences in the merged file
- **Issue**: Found passengers appearing multiple times after merging (which could be legitimate within same file)
- **Missing**: Cross-file analysis to detect duplicates across different source files

## Solution Implemented

### New Duplicate Detection Algorithm
1. **Track Source Files**: Use the existing `passenger.getSource()` to identify which original file each passenger came from
2. **Group by Passenger Key**: Create a map of `passengerKey -> Set<sourceFiles>`
3. **Identify Cross-File Duplicates**: Mark passengers as duplicates only if they appear in **multiple different source files**

### Code Changes

#### Updated `findDuplicatePassengers()` method:
```java
// NEW: Map passenger keys to source files
Map<String, Set<String>> passengerSourceMap = new HashMap<>();

// NEW: Group passengers by key and track source files  
for (PassengerRecord passenger : inputData.getPassengers()) {
    String key = generatePassengerKey(passenger);
    String sourceFile = passenger.getSource();
    passengerSourceMap.computeIfAbsent(key, k -> new HashSet<>()).add(sourceFile);
}

// NEW: Find keys appearing in multiple source files
if (sources.size() > 1) {
    duplicateKeys.add(key);
    // This passenger appears across different input files = TRUE duplicate
}
```

## Expected Behavior

### ✅ **Scenarios that WILL be marked as duplicates:**
- Same PNR appears in `input-1.edi` and `input-2.edi`
- Same passenger name + PNR combination across different source files

### ✅ **Scenarios that will NOT be marked as duplicates:**
- Multiple passengers with same name in same file (legitimate)
- Passengers with different PNRs even if same name
- Any passengers appearing only within single input file

## Testing
The enhanced debug logging will show:
- Which source file each passenger comes from
- How many source files each passenger key appears in
- Only cross-file duplicates will be marked

**Debug Output Format:**
```
Key: 'YZXWT|JOHNDOE' appears in 2 source file(s): [input-1.edi, input-2.edi]
  -> MARKED AS DUPLICATE (appears across 2 different input files)
```

## Result
- **Zero duplicates expected** if no PNRs actually appear across different input files
- **Only true cross-file duplicates** will be shown in the duplicate section
- **Accurate duplicate count** reflecting actual cross-file duplication