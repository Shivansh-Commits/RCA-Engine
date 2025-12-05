package com.l3.common.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Enhanced error handling utility with standardized error codes
 * Provides consistent error display and logging across all L3 Engine modules
 */
public class ErrorHandler {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Show standardized error dialog with error code
     */
    public static void showError(String errorCode, String context) {
        String description = ErrorCodes.getErrorDescription(errorCode);
        String resolution = ErrorCodes.getErrorResolution(errorCode);

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("L3 Engine Error - " + errorCode);
        alert.setHeaderText(description);

        // Create detailed content
        VBox content = new VBox(10);

        // Error details
        Text contextLabel = new Text("Error Details:");
        contextLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        TextArea contextArea = new TextArea(context);
        contextArea.setEditable(false);
        contextArea.setPrefRowCount(3);
        contextArea.setWrapText(true);

        // Resolution steps
        Text resolutionLabel = new Text("How to Fix:");
        resolutionLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        TextArea resolutionArea = new TextArea(resolution);
        resolutionArea.setEditable(false);
        resolutionArea.setPrefRowCount(4);
        resolutionArea.setWrapText(true);

        content.getChildren().addAll(contextLabel, contextArea, resolutionLabel, resolutionArea);

        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setPrefWidth(600);
        alert.showAndWait();

        // Log the error
        logError(errorCode, description, context);
    }

    /**
     * Show error with exception details
     */
    public static void showError(String errorCode, Exception exception) {
        String description = ErrorCodes.getErrorDescription(errorCode);
        String resolution = ErrorCodes.getErrorResolution(errorCode);

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("L3 Engine Error - " + errorCode);
        alert.setHeaderText(description);

        // Create expandable exception details
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);
        String exceptionText = sw.toString();

        VBox content = new VBox(10);

        // Exception message
        Text messageLabel = new Text("Error Message:");
        messageLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        TextArea messageArea = new TextArea(exception.getMessage() != null ? exception.getMessage() : "No message available");
        messageArea.setEditable(false);
        messageArea.setPrefRowCount(2);
        messageArea.setWrapText(true);

        // Resolution steps
        Text resolutionLabel = new Text("How to Fix:");
        resolutionLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        TextArea resolutionArea = new TextArea(resolution);
        resolutionArea.setEditable(false);
        resolutionArea.setPrefRowCount(4);
        resolutionArea.setWrapText(true);

        content.getChildren().addAll(messageLabel, messageArea, resolutionLabel, resolutionArea);

        // Expandable stack trace
        TextArea textArea = new TextArea(exceptionText);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        GridPane expandableContent = new GridPane();
        expandableContent.setMaxWidth(Double.MAX_VALUE);
        expandableContent.add(textArea, 0, 0);

        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setExpandableContent(expandableContent);
        alert.getDialogPane().setPrefWidth(600);
        alert.showAndWait();

        // Log the error with exception
        logError(errorCode, description, exception.getMessage() + "\n" + exceptionText);
    }

    /**
     * Show warning with error code
     */
    public static void showWarning(String errorCode, String context) {
        String description = ErrorCodes.getErrorDescription(errorCode);

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("L3 Engine Warning - " + errorCode);
        alert.setHeaderText(description);
        alert.setContentText(context);
        alert.showAndWait();

        // Log the warning
        logError(errorCode, description, context);
    }

    /**
     * Show confirmation dialog for potentially destructive actions
     */
    public static boolean showConfirmation(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * Show simple information dialog
     */
    public static void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Log error to console (can be enhanced to write to file)
     */
    private static void logError(String errorCode, String description, String details) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        System.err.printf("[%s] ERROR %s: %s%n", timestamp, errorCode, description);
        if (details != null && !details.trim().isEmpty()) {
            System.err.printf("Details: %s%n", details);
        }
        System.err.println("---");
    }

    /**
     * Validate flight number format and show appropriate error if invalid
     */
    public static boolean validateFlightNumber(String flightNumber) {
        if (flightNumber == null || flightNumber.trim().isEmpty()) {
            showError(ErrorCodes.LP003, "Flight number is required but was not provided.");
            return false;
        }

        if (flightNumber.trim().length() < 3) {
            showError(ErrorCodes.LP003, "Flight number must be at least 3 characters long. Current: '" + flightNumber + "'");
            return false;
        }

        return true;
    }

    /**
     * Check if directory exists and is accessible
     */
    public static boolean validateDirectory(String directoryPath) {
        if (directoryPath == null || directoryPath.trim().isEmpty()) {
            showError(ErrorCodes.LP001, "Directory path is required but was not provided.");
            return false;
        }

        java.io.File dir = new java.io.File(directoryPath);
        if (!dir.exists()) {
            showError(ErrorCodes.LP001, "Directory does not exist: " + directoryPath);
            return false;
        }

        if (!dir.isDirectory()) {
            showError(ErrorCodes.LP001, "Path is not a directory: " + directoryPath);
            return false;
        }

        if (!dir.canRead()) {
            showError(ErrorCodes.LP001, "Cannot read directory (permission denied): " + directoryPath);
            return false;
        }

        return true;
    }
}
