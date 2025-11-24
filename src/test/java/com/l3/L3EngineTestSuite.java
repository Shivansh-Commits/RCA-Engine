package com.l3;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Test Suite for L3 Engine
 * Runs all API tests (PNR tests are disabled for now)
 *
 * Includes:
 * - API LogParser unit tests
 * - API LogParser integration tests
 * - API RCA Engine tests
 */
@Suite
@SuiteDisplayName("L3 Engine Test Suite - API Focus")
@SelectPackages({
    "com.l3.logparser.api",
    "com.l3.rcaengine.api"
    // PNR packages excluded for now: "com.l3.logparser.pnr", "com.l3.rcaengine.pnr"
})
public class L3EngineTestSuite {
    // Test suite configuration
    //
    // Current focus: API data testing
    // - Flight numbers without leading zeros (e.g., TS230, DY1303)
    // - Basic parsing and RCA engine functionality
    // - Integration tests for real-world usage
}
