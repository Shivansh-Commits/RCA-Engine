# Duplicate Logic Changes

## Problem
The duplicate section was incorrectly showing "processed passengers" (passengers that appear in both input and output files) instead of actual duplicate passengers (passengers that appear multiple times within the input data).

## Solution
Fixed the duplicate detection logic to properly identify passengers that appear multiple times in the input data only.

## Changes Made

### 1. ComparisonResult.java
- Added `duplicatePassengerKeys` field to store actual duplicate passenger keys
- Updated constructor to accept duplicate passenger keys parameter
- Added `getDuplicatePassengerKeys()` getter method
- Added `getDuplicatePassengers()` method to return duplicate passenger records

### 2. PnrgovComparator.java  
- Added `findDuplicatePassengers(PnrData inputData)` method that:
  - Counts occurrences of each passenger key in input data
  - Identifies passengers that appear more than once
  - Returns Set of duplicate passenger keys
- Updated `performComparison()` method to:
  - Call `findDuplicatePassengers()` to find actual duplicates
  - Pass duplicate keys to ComparisonResult constructor

### 3. PnrgovProcessor.java
- Updated duplicate passenger processing section to:
  - Use `result.getDuplicatePassengers()` instead of `result.getProcessedPassengers()`
  - Change source from "Input + Output" to "Input File"
  - Change status from "🔄 PROCESSED" to "🔄 DUPLICATE"
  - Updated logging to show "Duplicate passengers" instead of "Processed/Duplicate passengers"
- Added debug logging for duplicate passenger count

## New Logic
**Duplicate passengers**: Passengers that appear multiple times within the input data itself
**Processed passengers**: Passengers that exist in both input and output files (successfully processed)

These are now two separate categories with different meanings and purposes.

## Expected Behavior
- Duplicate section will now show only passengers that appear multiple times in the input file
- If there are no actual duplicates in the input, the duplicate section will be empty (0 count)
- The processed passengers remain a separate internal tracking for successful processing