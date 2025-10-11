package com.l3.dqengine.pnr.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logger utility for PNRGOV operations
 */
public class PnrgovLogger {
    
    private final boolean enableLogging;
    private final File logFile;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public PnrgovLogger(boolean enableLogging) {
        this.enableLogging = enableLogging;
        
        if (enableLogging) {
            // Create logs directory if it doesn't exist
            File reportsDir = new File("PNRGOV_Reports");
            if (!reportsDir.exists()) {
                reportsDir.mkdirs();
            }
            this.logFile = new File(reportsDir, "PNRGOV_Validation_Log.txt");
        } else {
            this.logFile = null;
        }
    }
    
    public void info(String message) {
        log(message, "INFO");
    }
    
    public void debug(String message) {
        // Skip debug logging for performance - only log to file if enabled
        if (enableLogging) {
            log(message, "DEBUG");
        }
    }
    
    public void warn(String message) {
        log(message, "WARN");
        System.out.println("⚠️ " + message);
    }
    
    public void error(String message) {
        log(message, "ERROR");
        System.err.println("❌ " + message);
    }
    
    private void log(String message, String level) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String logEntry = String.format("[%s] [%s] %s", timestamp, level, message);
        
        // Always write to console for important messages
        if ("ERROR".equals(level) || "WARN".equals(level)) {
            System.out.println(logEntry);
        }
        
        // Write to log file if logging is enabled
        if (enableLogging && logFile != null) {
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write(logEntry + System.lineSeparator());
            } catch (IOException e) {
                System.err.println("Failed to write to log file: " + e.getMessage());
            }
        }
    }
    
    public void logValidation(String message, String level) {
        log(message, level);
    }
}