# PNR Data Issue Debugging Guide

## Changes Applied for Debugging

### 🔍 **Debug Logging Added**
I've enhanced the `PnrgovProcessor` to provide detailed logging information:

1. **Enabled Logging**: Set `config.setEnableLogging(true)` to capture debug information
2. **Added Debug Output**: Shows actual counts from comparison engine vs UI display
3. **Deduplication Logic**: Added passenger key tracking to prevent duplicates

### 📊 **Debug Information Now Shown**
When you run the PNR processing, you'll see debug logs showing:

```
=== COMPARISON RESULTS DEBUG ===
Total Input Passengers: [actual count]
Total Output Passengers: [actual count]  
Dropped Passenger Count: [should be 4]
Processed Passenger Count: [should be 0]
Added Passenger Count: [actual count]
Input Passengers List Size: [actual list size]
Output Passengers List Size: [actual list size]
Dropped Passengers List Size: [should be 4]
Processed Passengers List Size: [should be 0]
===============================
Input passengers added to UI: [final UI count]
Output passengers added to UI: [final UI count]
Dropped passengers added to UI: [final UI count - should be 4]
Processed/Duplicate passengers added to UI: [final UI count - should be 0]
```

### 🚀 **Testing Instructions**

1. **Run PNR Processing** with your test data
2. **Check Console/Log Output** for the debug information above  
3. **Compare the Numbers**:
   - If "Dropped Passenger Count" shows 4 but "Dropped passengers added to UI" shows 5, the issue is in UI conversion
   - If "Dropped Passenger Count" shows 5, the issue is in the core comparison logic
   - If "Processed Passenger Count" shows 0 but UI shows 4, we've found the duplicate issue

### 🔧 **What the Deduplication Does**

The new logic prevents the same passenger from appearing multiple times by:
- Creating a unique key: `passenger.getName() + "|" + passenger.getPnrRloc()`
- Tracking seen passengers in each category
- Only adding passengers that haven't been seen before

### 📋 **Expected Results After This Fix**

If the issue was **UI-level duplicates**:
- ✅ Each table should show unique passengers only
- ✅ Dropped count should be exactly 4
- ✅ Duplicate count should be exactly 0

If the issue is **Core Logic** (comparison engine):
- The debug logs will show incorrect counts from `result.getDroppedPassengerCount()`
- This means we need to examine `PnrgovComparator.java` logic

### 🐛 **Next Steps Based on Debug Output**

**Scenario A**: Debug shows correct counts, UI was wrong
- ✅ **Fixed!** The deduplication resolved the display issue

**Scenario B**: Debug shows wrong counts from comparison engine  
- 🔍 **Need to investigate**: `PnrgovComparator.java` comparison logic
- Check key generation in `generatePassengerKey()` method
- Check passenger extraction in `extractPnrAndPassengers()` method

**Scenario C**: Files have actual duplicate data
- 🔍 **Need to investigate**: Input EDIFACT files may contain duplicate passenger records
- May need to add deduplication at the extraction level

---

**Please run the test and share the debug output!** This will help us pinpoint exactly where the issue is occurring. 🕵️‍♂️