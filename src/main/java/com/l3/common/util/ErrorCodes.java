package com.l3.common.util;

/**
 * Centralized error codes for L3 Engine modules
 * This class defines all standardized error codes used across the application
 */
public class ErrorCodes {

    // Log Extraction Errors (LE001-LE005)
    public static final String LE001 = "LE001";
    public static final String LE002 = "LE002";
    public static final String LE003 = "LE003";
    public static final String LE004 = "LE004";
    public static final String LE005 = "LE005";
    public static final String LE006 = "LE006";
    public static final String LE007 = "LE007";



    // Log Parser Errors (LP001-LP005)
    public static final String LP001 = "LP001";
    public static final String LP002 = "LP002";
    public static final String LP003 = "LP003";
    public static final String LP004 = "LP004";
    public static final String LP005 = "LP005";
    public static final String LP006 = "LP006";


    // RCA Engine Errors (RCA001-RCA005)
    public static final String RCA001 = "RCA001";
    public static final String RCA002 = "RCA002";
    public static final String RCA003 = "RCA003";
    public static final String RCA004 = "RCA004";
    public static final String RCA005 = "RCA005";


    /**
     * Get human-readable description for error code
     */
    public static String getErrorDescription(String errorCode) {
        switch (errorCode) {
            // Log Extraction Errors
            case LE001: return "Azure configuration missing";
            case LE002: return "Invalid credentials";
            case LE003: return "Pipeline trigger failed";
            case LE004: return "Network connectivity error";
            case LE005: return "Insufficient permissions";
            case LE006: return "Required variables missing";
            case LE007: return "Invalid directory path";

            // Log Parser Errors
            case LP001: return "Invalid directory path";
            case LP002: return "No EDIFACT messages found";
            case LP003: return "Message parsing failed";
            case LP004: return "Multi-node consolidation error";
            case LP005: return "File format not supported";
            case LP006: return "Required variables missing";

            // RCA Engine Errors
            case RCA001: return "Expected files not found";
            case RCA002: return "File parsing error";
            case RCA003: return "Data validation failed";
            case RCA004: return "Excel export failed";
            case RCA005: return "Memory allocation error";


            default: return "Unknown error";
        }
    }

    /**
     * Get detailed resolution steps for error code
     */
    public static String getErrorResolution(String errorCode) {
        switch (errorCode) {
            // Log Extraction Errors
            case LE001: return "Please configure Azure settings using the ⚙ Configure Azure button. Enter your organization URL, project name, and Personal Access Token.";
            case LE002: return "Check your Azure DevOps credentials. Ensure your Personal Access Token is valid and has the correct permissions (Build and Release).";
            case LE003: return "Pipeline execution failed. Check if the flight operated on the specified date and verify your Azure configuration.";
            case LE004: return "Network connection error. Check your internet connection and ensure access to Azure DevOps is not blocked by firewall.";
            case LE005: return "Insufficient Azure permissions. Contact your Azure DevOps administrator to grant Build and Release permissions to your account.";
            case LE006: return "Required fields are missing. Ensure all necessary fields are filled including flight no. and date.";
            case LE007: return "The specified log directory path is invalid or inaccessible. Check the folder exists and you have read permissions.";

            // Log Parser Errors
            case LP001: return "The specified log directory path is invalid or inaccessible. Check the folder exists and you have read permissions.";
            case LP002: return "No EDIFACT messages found in the log files. Verify the logs contain passenger data and check flight number/date accuracy.";
            case LP003: return "Failed to parse EDIFACT messages. The message format may be corrupted or non-standard. Contact development team.";
            case LP004: return "Multi-node consolidation failed. Check if n1/, n2/, n3/ subdirectories exist or disable multi-node mode.";
            case LP005: return "The log file format is not supported. Ensure you're using standard application log files.";
            case LP006: return "Required fields are missing. Ensure all necessary fields are filled including Flight no. , Date and Data type";

            // RCA Engine Errors
            case RCA001: return "No passenger/crew files found in the specified directory. Verify the file name pattern is followed and ensure they are present in the given location.";
            case RCA002: return "Error parsing passenger data files. Check file format and ensure files weren't manually modified.";
            case RCA003: return "Data validation failed during processing. Some passenger records may have invalid or missing information.";
            case RCA004: return "Failed to export Excel report. Check disk space and ensure the output directory is writable.";
            case RCA005: return "Insufficient memory to process large dataset. Close other applications or try processing smaller data sets.";

            default: return "Contact technical support for assistance.";
        }
    }
}
