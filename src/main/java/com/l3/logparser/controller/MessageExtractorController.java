package com.l3.logparser.controller;

import com.l3.logparser.api.model.EdifactMessage;
import com.l3.logparser.api.model.FlightDetails;
import com.l3.logparser.api.service.MessageExtractionService;
import com.l3.logparser.pnr.service.PnrExtractionService;
import com.l3.logparser.pnr.model.PnrMessage;
import com.l3.logparser.pnr.model.PnrFlightDetails;
import com.l3.logparser.enums.DataType;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Controller for the Message Extractor UI
 * Handles both API and PNR data extraction
 */
public class MessageExtractorController implements Initializable {

    @FXML private TextField logDirectoryField;
    @FXML private Button browseButton;
    @FXML private TextField flightNumberField;
    @FXML private TextField departureDateField;
    @FXML private TextField departureAirportField;
    @FXML private TextField arrivalAirportField;
    @FXML private ComboBox<DataType> dataTypeComboBox;
    @FXML private Button processButton;
    @FXML private Button clearButton;
    @FXML private Button saveButton;
    @FXML private TextField outputDirectoryField;
    @FXML private Button browseOutputButton;
    @FXML private ToggleButton debugToggleButton;

    // Results area
    @FXML private TableView<MessageTableRow> resultsTable;
    @FXML private TableColumn<MessageTableRow, Integer> partColumn;
    @FXML private TableColumn<MessageTableRow, String> flightColumn;
    @FXML private TableColumn<MessageTableRow, String> dateColumn;
    @FXML private TableColumn<MessageTableRow, String> departureColumn;
    @FXML private TableColumn<MessageTableRow, String> arrivalColumn;
    @FXML private TableColumn<MessageTableRow, String> typeColumn;
    @FXML private TableColumn<MessageTableRow, String> indicatorColumn;

    @FXML private TextArea logArea;
    @FXML private TextArea messagePreviewArea;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    @FXML private Label summaryLabel;

    private MessageExtractionService messageExtractionService;
    private PnrExtractionService pnrExtractionService;
    private MessageExtractionService.ExtractionResult lastResult;
    private boolean debugMode = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        messageExtractionService = new MessageExtractionService();
        pnrExtractionService = new PnrExtractionService();
        setupTableColumns();
        setupTableSelection();
        setupUI();
        setupDataTypeComboBox();
    }

    @FXML
    private void onDebugToggle() {
        debugMode = debugToggleButton.isSelected();
        if (debugMode) {
            debugToggleButton.setText("ON");
            addLogMessage("Debug mode enabled.");
        } else {
            debugToggleButton.setText("OFF");
            addLogMessage("Debug mode disabled.");
        }
    }

    private void setupTableColumns() {
        partColumn.setCellValueFactory(new PropertyValueFactory<>("partNumber"));
        flightColumn.setCellValueFactory(new PropertyValueFactory<>("flightNumber"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("departureDateTime"));
        departureColumn.setCellValueFactory(new PropertyValueFactory<>("departureAirport"));
        arrivalColumn.setCellValueFactory(new PropertyValueFactory<>("arrivalAirport"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("dataType"));
        indicatorColumn.setCellValueFactory(new PropertyValueFactory<>("partIndicator"));
    }

    private void setupTableSelection() {
        resultsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                EdifactMessage message = newSelection.getOriginalMessage();
                String formattedContent = formatEdifactMessage(message.getRawContent());
                messagePreviewArea.setText(formattedContent);
            } else {
                messagePreviewArea.clear();
            }
        });
    }

    /**
     * Formats EDIFACT message content by placing each segment on a separate line.
     * Replaces segment terminator characters (') with line breaks for better readability.
     *
     * @param rawContent The raw EDIFACT message content
     * @return Formatted message with segments on separate lines
     */
    private String formatEdifactMessage(String rawContent) {
        if (rawContent == null || rawContent.trim().isEmpty()) {
            return rawContent;
        }

        // Replace EDIFACT segment terminators (') with line breaks
        // The segment terminator is typically the last character in each segment
        return rawContent.replace("'", "'\n");
    }

    private void setupUI() {
        saveButton.setDisable(true);
        progressBar.setVisible(false);
        statusLabel.setText("Ready");

        // Set placeholder text
        departureDateField.setPromptText("YYMMDD (e.g., 250814)");
        flightNumberField.setPromptText("e.g., MS775");
        departureAirportField.setPromptText("e.g., CAI");
        arrivalAirportField.setPromptText("e.g., DUB");
    }

    private void setupDataTypeComboBox() {
        dataTypeComboBox.setItems(FXCollections.observableArrayList(DataType.values()));
        dataTypeComboBox.setValue(DataType.API); // Default to API for backward compatibility
        dataTypeComboBox.setPromptText("Select data type to extract");
    }

    @FXML
    private void onBrowseLogDirectory() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Log Directory");

        File selectedDirectory = directoryChooser.showDialog(getStage());
        if (selectedDirectory != null) {
            logDirectoryField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    private void onBrowseOutputDirectory() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Output Directory");

        File selectedDirectory = directoryChooser.showDialog(getStage());
        if (selectedDirectory != null) {
            outputDirectoryField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    private void onProcessLogs() {
        String logDirectory = logDirectoryField.getText().trim();
        String flightNumber = flightNumberField.getText().trim();
        String departureDate = departureDateField.getText().trim();
        String departureAirport = departureAirportField.getText().trim();
        String arrivalAirport = arrivalAirportField.getText().trim();
        DataType selectedDataType = dataTypeComboBox.getValue();

        if (logDirectory.isEmpty()) {
            showAlert("Error", "Please select a log directory");
            return;
        }

        if (flightNumber.isEmpty()) {
            showAlert("Error", "Please enter a flight number");
            return;
        }

        if (selectedDataType == null) {
            showAlert("Error", "Please select a data type to extract");
            return;
        }

        // Clear previous results
        clearResults();

        // Create and run extraction task
        Task<MessageExtractionService.ExtractionResult> task = new Task<MessageExtractionService.ExtractionResult>() {
            @Override
            protected MessageExtractionService.ExtractionResult call() throws Exception {
                if (selectedDataType == DataType.PNR) {
                    // Use PNR service for proper filtering and analysis
                    PnrExtractionService.PnrExtractionResult pnrResult = pnrExtractionService.extractPnrMessages(
                        logDirectory, flightNumber, departureDate, departureAirport, arrivalAirport);

                    // Convert PNR result to generic result format
                    MessageExtractionService.ExtractionResult genericResult =
                        new MessageExtractionService.ExtractionResult();
                    genericResult.setRequestedDataType(selectedDataType);
                    genericResult.setSuccess(pnrResult.isSuccess());

                    // Set flight number and log directory from PNR result
                    genericResult.setFlightNumber(pnrResult.getFlightNumber());
                    genericResult.setLogDirectoryPath(pnrResult.getLogDirectoryPath());

                    // Convert PNR messages to generic EDIFACT messages
                    List<EdifactMessage> edifactMessages = convertPnrToEdifactMessages(pnrResult.getExtractedMessages());
                    genericResult.setExtractedMessages(edifactMessages);

                    // Copy warnings and errors
                    genericResult.getWarnings().addAll(pnrResult.getWarnings());
                    genericResult.getErrors().addAll(pnrResult.getErrors());

                    return genericResult;
                } else {
                    // Use generic service for other data types
                    return messageExtractionService.extractMessages(
                        logDirectory, flightNumber, departureDate, departureAirport, arrivalAirport, selectedDataType,
                        debugMode, (msg) -> Platform.runLater(() -> addLogMessage(msg)));
                }
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    lastResult = getValue();
                    displayResults(lastResult);
                    progressBar.setVisible(false);
                    processButton.setDisable(false);
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    showAlert("Error", "Extraction failed: " + getException().getMessage());
                    progressBar.setVisible(false);
                    processButton.setDisable(false);
                    statusLabel.setText("Extraction failed");
                });
            }
        };

        // Show progress and disable button
        progressBar.setVisible(true);
        processButton.setDisable(true);
        statusLabel.setText("Extracting messages...");

        // Run task in background thread
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onClearAll() {
        // Clear all input fields
        logDirectoryField.clear();
        flightNumberField.clear();
        departureDateField.clear();
        departureAirportField.clear();
        arrivalAirportField.clear();
        outputDirectoryField.clear();
        dataTypeComboBox.setValue(DataType.API);

        // Clear results
        clearResults();

        statusLabel.setText("Ready");
    }

    @FXML
    private void onSaveExtracted() {
        if (lastResult == null || lastResult.getExtractedMessages().isEmpty()) {
            showAlert("Error", "No extracted messages to save");
            return;
        }

        String outputDirectory = outputDirectoryField.getText().trim();
        if (outputDirectory.isEmpty()) {
            showAlert("Error", "Please select an output directory");
            return;
        }

        boolean success;
        DataType dataType = lastResult != null ? lastResult.getRequestedDataType() : null;

        if (dataType == DataType.PNR) {
            // Use PNR service for proper directory separation
            List<PnrMessage> pnrMessages = convertToPnrMessages(lastResult.getExtractedMessages());
            success = pnrExtractionService.saveExtractedMessages(pnrMessages, outputDirectory);
        } else {
            // Use generic service for other message types
            success = messageExtractionService.saveExtractedMessages(
                lastResult.getExtractedMessages(), outputDirectory);
        }

        if (success) {
            showAlert("Success", "Extracted messages saved successfully to:\n" + outputDirectory);
            addLogMessage("Messages saved to: " + outputDirectory);
        } else {
            showAlert("Error", "Failed to save extracted messages");
        }
    }

    /**
     * Convert EdifactMessage list to PnrMessage list for PNR-specific operations
     */
    private List<PnrMessage> convertToPnrMessages(List<EdifactMessage> edifactMessages) {
        List<PnrMessage> pnrMessages = new ArrayList<>();

        for (EdifactMessage edifact : edifactMessages) {
            PnrMessage pnrMessage = new PnrMessage();

            // Copy common properties
            pnrMessage.setDirection(edifact.getDirection());
            pnrMessage.setPartNumber(edifact.getPartNumber());
            pnrMessage.setPartIndicator(edifact.getPartIndicator());
            pnrMessage.setFlightNumber(edifact.getFlightNumber());
            pnrMessage.setMessageId(edifact.getMessageId());
            pnrMessage.setRawContent(edifact.getRawContent());
            // Note: EdifactMessage doesn't have timestamp/traceId fields
            // These would need to be set from the original log parsing context
            pnrMessage.setLogTimestamp(null);
            pnrMessage.setLogTraceId(null);
            pnrMessage.setMessageType("PNRGOV");

            // Set multipart flags
            pnrMessage.setLastPart("F".equals(edifact.getPartIndicator()));
            pnrMessage.setMultipart(edifact.getPartNumber() > 1 || "C".equals(edifact.getPartIndicator()));

            pnrMessages.add(pnrMessage);
        }

        return pnrMessages;
    }

    /**
     * Convert PnrMessage list to EdifactMessage list for UI display
     */
    private List<EdifactMessage> convertPnrToEdifactMessages(List<PnrMessage> pnrMessages) {
        List<EdifactMessage> edifactMessages = new ArrayList<>();

        for (PnrMessage pnr : pnrMessages) {
            EdifactMessage edifact = new EdifactMessage();

            // Copy common properties
            edifact.setDirection(pnr.getDirection());
            edifact.setPartNumber(pnr.getPartNumber());
            edifact.setPartIndicator(pnr.getPartIndicator());
            edifact.setFlightNumber(pnr.getFlightNumber());
            edifact.setMessageId(pnr.getMessageId());
            edifact.setRawContent(pnr.getRawContent());
            edifact.setMessageType(pnr.getMessageType());
            edifact.setLastPart(pnr.isLastPart());

            // Convert flight details if available
            if (pnr.getFlightDetails() != null) {
                FlightDetails flightDetails = new FlightDetails();
                PnrFlightDetails pnrDetails = pnr.getFlightDetails();
                flightDetails.setDepartureDate(pnrDetails.getDepartureDate());
                flightDetails.setDepartureTime(pnrDetails.getDepartureTime());
                flightDetails.setArrivalDate(pnrDetails.getArrivalDate());
                flightDetails.setArrivalTime(pnrDetails.getArrivalTime());
                flightDetails.setDepartureAirport(pnrDetails.getDepartureAirport());
                flightDetails.setArrivalAirport(pnrDetails.getArrivalAirport());
                flightDetails.setFlightNumber(pnrDetails.getFlightNumber());
                // Note: FlightDetails doesn't have airlineCode field, so we skip it
                edifact.setFlightDetails(flightDetails);
            }

            edifactMessages.add(edifact);
        }

        return edifactMessages;
    }

    private void clearResults() {
        resultsTable.getItems().clear();
        logArea.clear();
        messagePreviewArea.clear();
        summaryLabel.setText("");
        saveButton.setDisable(true);
        lastResult = null;
    }

    private void displayResults(MessageExtractionService.ExtractionResult result) {
        // Update summary
        String summary = String.format("Found %d messages (%s), %d parts processed from %d files",
            result.getMessageCount(), result.getRequestedDataType().getDisplayName(),
            result.getPartCount(), result.getProcessedFiles().size());
        summaryLabel.setText(summary);

        // Sort extracted messages by direction (INPUT first, then OUTPUT) and then by part number
        List<EdifactMessage> sortedMessages = result.getExtractedMessages().stream()
                .sorted((m1, m2) -> {
                    // First sort by direction (INPUT comes before OUTPUT)
                    String type1 = m1.getDirection() != null ? m1.getDirection().toString() : "INPUT";
                    String type2 = m2.getDirection() != null ? m2.getDirection().toString() : "INPUT";
                    int typeComparison = type1.compareTo(type2);

                    if (typeComparison != 0) {
                        return typeComparison;
                    }

                    // Then sort by part number
                    return Integer.compare(m1.getPartNumber(), m2.getPartNumber());
                })
                .collect(java.util.stream.Collectors.toList());

        // Populate results table with sorted messages
        ObservableList<MessageTableRow> tableData = FXCollections.observableArrayList();
        String requestedDataTypeDisplay = result.getRequestedDataType().getDisplayName();
        for (EdifactMessage message : sortedMessages) {
            tableData.add(new MessageTableRow(message, requestedDataTypeDisplay));
        }
        resultsTable.setItems(tableData);

        // Add log messages
        addLogMessage("=== EXTRACTION RESULTS ===");
        addLogMessage("Flight: " + result.getFlightNumber());
        addLogMessage("Data Type: " + result.getRequestedDataType().getDisplayName());
        addLogMessage("Log Directory: " + result.getLogDirectoryPath());

        // Count INPUT vs OUTPUT messages
        long inputCount = sortedMessages.stream().filter(m -> {
            String direction = m.getDirection() != null ? m.getDirection().toString() : "INPUT";
            return "INPUT".equals(direction);
        }).count();
        long outputCount = sortedMessages.stream().filter(m -> {
            String direction = m.getDirection() != null ? m.getDirection().toString() : "INPUT";
            return "OUTPUT".equals(direction);
        }).count();
        addLogMessage("Input messages: " + inputCount + ", Output messages: " + outputCount);

        for (String file : result.getProcessedFiles()) {
            addLogMessage("Processed: " + file);
        }

        for (String warning : result.getWarnings()) {
            addLogMessage("WARNING: " + warning);
        }

        for (String error : result.getErrors()) {
            addLogMessage("ERROR: " + error);
        }

        if (result.isSuccess() && !result.getExtractedMessages().isEmpty()) {
            statusLabel.setText("Extraction completed successfully");
            saveButton.setDisable(false);
        } else {
            statusLabel.setText("Extraction completed with issues");
        }
    }

    private void addLogMessage(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        logArea.appendText(String.format("[%s] %s%n", timestamp, message));
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private Stage getStage() {
        return (Stage) logDirectoryField.getScene().getWindow();
    }

    /**
     * Table row wrapper for EdifactMessage
     */
    public static class MessageTableRow {
        private final EdifactMessage originalMessage;
        private final int partNumber;
        private final String flightNumber;
        private final String departureDateTime;
        private final String departureAirport;
        private final String arrivalAirport;
        private final String dataType;
        private final String partIndicator;

        public MessageTableRow(EdifactMessage message) {
            this(message, message.getDataType() != null ? message.getDataType() : "Unknown");
        }

        public MessageTableRow(EdifactMessage message, String requestedDataType) {
            this.originalMessage = message;
            this.partNumber = message.getPartNumber();

            FlightDetails details = message.getFlightDetails();
            if (details != null) {
                this.flightNumber = details.getFlightNumber() != null ? details.getFlightNumber() : "N/A";
                this.departureDateTime = formatDateTime(details.getDepartureDate(), details.getDepartureTime());
                this.departureAirport = details.getDepartureAirport() != null ? details.getDepartureAirport() : "N/A";
                this.arrivalAirport = details.getArrivalAirport() != null ? details.getArrivalAirport() : "N/A";
                this.dataType = requestedDataType; // Use the requested data type instead of message-level type
            } else {
                this.flightNumber = message.getFlightNumber() != null ? message.getFlightNumber() : "N/A";
                this.departureDateTime = "N/A";
                this.departureAirport = "N/A";
                this.arrivalAirport = "N/A";
                this.dataType = requestedDataType; // Use the requested data type instead of message-level type
            }

            // Set part indicator based on message direction and part type
            if (message.getDirection() == com.l3.logparser.enums.MessageType.OUTPUT) {
                // Output messages always show F(Output)
                this.partIndicator = "F(Output)";
            } else {
                // Input messages: show actual part indicator (C for continuation, F for final)
                String baseIndicator = message.getPartIndicator() != null ? message.getPartIndicator() : "C";
                this.partIndicator = baseIndicator;
            }
        }

        private String formatDateTime(String date, String time) {
            if (date == null || date.isEmpty()) {
                return "N/A";
            }
            if (time == null || time.isEmpty()) {
                return date;
            }
            return date + " " + time;
        }

        // Getters for JavaFX PropertyValueFactory
        public EdifactMessage getOriginalMessage() { return originalMessage; }
        public int getPartNumber() { return partNumber; }
        public String getFlightNumber() { return flightNumber; }
        public String getDepartureDateTime() { return departureDateTime; }
        public String getDepartureAirport() { return departureAirport; }
        public String getArrivalAirport() { return arrivalAirport; }
        public String getDataType() { return dataType; }
        public String getPartIndicator() { return partIndicator; }
    }
}

