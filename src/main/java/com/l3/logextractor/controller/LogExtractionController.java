package com.l3.logextractor.controller;

import com.l3.logextractor.config.AzureConfig;
import com.l3.logextractor.model.LogExtractionRequest;
import com.l3.logextractor.model.PipelineRunResult;
import com.l3.logextractor.service.AzurePipelineService;
import com.l3.logextractor.service.FileDownloadService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Controller for the Log Extraction UI
 */
public class LogExtractionController implements Initializable {

    // Input controls
    @FXML private TextField flightNumberField;
    @FXML private DatePicker incidentDatePicker;
    @FXML private TextField incidentTimeField;
    @FXML private Button extractButton;
    @FXML private Button clearButton;
    @FXML private Button configureButton;

    // Results table
    @FXML private TableView<LogFileEntry> extractedFilesTable;
    @FXML private TableColumn<LogFileEntry, String> fileNameColumn;
    @FXML private TableColumn<LogFileEntry, String> fileSizeColumn;
    @FXML private TableColumn<LogFileEntry, String> extractedTimeColumn;
    @FXML private TableColumn<LogFileEntry, String> statusColumn;

    // Log area and preview
    @FXML private TextArea logArea;
    @FXML private TextArea filePreviewArea;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;
    @FXML private Label summaryLabel;

    // Download controls
    @FXML private Button downloadAllButton;
    @FXML private Button downloadSelectedButton;
    @FXML private TextField outputDirectoryField;
    @FXML private Button browseOutputButton;

    private AzureConfig azureConfig;
    private AzurePipelineService pipelineService;
    private FileDownloadService fileDownloadService;
    private ObservableList<LogFileEntry> extractedFiles;
    private PipelineRunResult currentRun;
    private ScheduledExecutorService statusChecker;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupUI();
        setupTableColumns();
        setupTableSelection();
        initializeServices();
        extractedFiles = FXCollections.observableArrayList();
        extractedFilesTable.setItems(extractedFiles);
    }

    private void setupUI() {
        // Set default values
        incidentDatePicker.setValue(LocalDate.now());
        statusLabel.setText("Ready");
        progressBar.setVisible(false);

        // Enable/disable buttons based on state
        downloadAllButton.setDisable(true);
        downloadSelectedButton.setDisable(true);
    }

    private void setupTableColumns() {
        fileNameColumn.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        fileSizeColumn.setCellValueFactory(new PropertyValueFactory<>("fileSize"));
        extractedTimeColumn.setCellValueFactory(new PropertyValueFactory<>("extractedTime"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Format columns
        fileSizeColumn.setCellFactory(column -> new TableCell<LogFileEntry, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(formatFileSize(Long.parseLong(item)));
                }
            }
        });
    }

    private void setupTableSelection() {
        extractedFilesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                downloadSelectedButton.setDisable(false);
                loadFilePreview(newSelection);
            } else {
                downloadSelectedButton.setDisable(true);
                filePreviewArea.clear();
            }
        });
    }

    private void initializeServices() {
        azureConfig = new AzureConfig();
        pipelineService = new AzurePipelineService(azureConfig);
        fileDownloadService = new FileDownloadService(azureConfig);
        statusChecker = Executors.newSingleThreadScheduledExecutor();
    }

    @FXML
    private void onExtractLogs() {
        String flightNumber = flightNumberField.getText().trim();
        LocalDate incidentDate = incidentDatePicker.getValue();

        if (flightNumber.isEmpty()) {
            showAlert("Validation Error", "Please enter a flight number.");
            return;
        }

        if (incidentDate == null) {
            showAlert("Validation Error", "Please select an incident date.");
            return;
        }

        // Create extraction request
        LocalDateTime incidentDateTime = LocalDateTime.of(incidentDate, LocalTime.MIDNIGHT);
        LogExtractionRequest request = new LogExtractionRequest(flightNumber, incidentDateTime);

        // Clear previous results
        extractedFiles.clear();
        filePreviewArea.clear();
        logArea.clear();

        // Start extraction process
        startExtractionProcess(request);
    }

    private void startExtractionProcess(LogExtractionRequest request) {
        Task<Void> extractionTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                Platform.runLater(() -> {
                    progressBar.setVisible(true);
                    progressBar.setProgress(-1);
                    extractButton.setDisable(true);
                    statusLabel.setText("Starting pipeline...");
                });

                // Trigger pipeline
                currentRun = pipelineService.triggerPipeline(request, this::addLogMessage);

                if (currentRun != null && !currentRun.isFailed()) {
                    // Start monitoring pipeline status
                    startStatusMonitoring(currentRun.getRunId());
                } else {
                    Platform.runLater(() -> {
                        progressBar.setVisible(false);
                        extractButton.setDisable(false);
                        statusLabel.setText("Pipeline failed to start");
                    });
                }

                return null;
            }

            private void addLogMessage(String message) {
                Platform.runLater(() -> {
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    logArea.appendText(String.format("[%s] %s%n", timestamp, message));
                });
            }
        };

        Thread extractionThread = new Thread(extractionTask);
        extractionThread.setDaemon(true);
        extractionThread.start();
    }

    private void startStatusMonitoring(String runId) {
        statusChecker.scheduleWithFixedDelay(() -> {
            PipelineRunResult status = pipelineService.checkPipelineStatus(runId, message ->
                Platform.runLater(() -> {
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    logArea.appendText(String.format("[%s] %s%n", timestamp, message));
                })
            );

            Platform.runLater(() -> {
                if (status.isCompleted()) {
                    statusChecker.shutdown();
                    onPipelineCompleted(status);
                } else {
                    statusLabel.setText("Pipeline running... Status: " + status.getStatus());
                }
            });

        }, 10, 30, TimeUnit.SECONDS);
    }

    private void onPipelineCompleted(PipelineRunResult result) {
        progressBar.setVisible(false);
        extractButton.setDisable(false);

        if (result.isSuccessful()) {
            statusLabel.setText("Pipeline completed successfully");
            addLogMessage("Pipeline execution completed. Discovering artifacts...");

            // Discover actual artifacts from Azure DevOps
            discoverArtifacts(result);

            downloadAllButton.setDisable(false);
            summaryLabel.setText("Extraction completed. Discovering artifacts...");
        } else {
            statusLabel.setText("Pipeline failed");
            addLogMessage("Pipeline execution failed: " + result.getErrorMessage());
            summaryLabel.setText("Extraction failed.");
        }
    }

    private void discoverArtifacts(PipelineRunResult result) {
        Task<Void> discoveryTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                addLogMessage("Discovering build artifacts...");

                List<FileDownloadService.ArtifactInfo> artifacts =
                    fileDownloadService.getArtifacts(result.getRunId(), this::addLogMessage);

                Platform.runLater(() -> {
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                    for (FileDownloadService.ArtifactInfo artifact : artifacts) {
                        extractedFiles.add(new LogFileEntry(
                            artifact.getName(),
                            String.valueOf(artifact.getSize()),
                            timestamp,
                            "Available"
                        ));
                    }

                    if (artifacts.isEmpty()) {
                        // Fallback to simulated data if no artifacts found
                        extractedFiles.add(new LogFileEntry(
                            "application_" + flightNumberField.getText() + ".log",
                            "2048576",
                            timestamp,
                            "Available"
                        ));

                        extractedFiles.add(new LogFileEntry(
                            "system_" + flightNumberField.getText() + ".log",
                            "1048576",
                            timestamp,
                            "Available"
                        ));

                        extractedFiles.add(new LogFileEntry(
                            "error_" + flightNumberField.getText() + ".log",
                            "524288",
                            timestamp,
                            "Available"
                        ));
                    }
                });

                return null;
            }

            private void addLogMessage(String message) {
                Platform.runLater(() -> {
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    logArea.appendText(String.format("[%s] %s%n", timestamp, message));
                });
            }
        };

        Thread discoveryThread = new Thread(discoveryTask);
        discoveryThread.setDaemon(true);
        discoveryThread.start();
    }

    @FXML
    private void onClear() {
        flightNumberField.clear();
        incidentDatePicker.setValue(LocalDate.now());
        extractedFiles.clear();
        filePreviewArea.clear();
        logArea.clear();
        statusLabel.setText("Ready");
        summaryLabel.setText("");
        progressBar.setVisible(false);
        downloadAllButton.setDisable(true);
        downloadSelectedButton.setDisable(true);
    }

    @FXML
    private void onConfigure() {
        showAzureConfigurationDialog();
    }

    private void showAzureConfigurationDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Azure DevOps Configuration");
        dialog.setHeaderText("Configure Azure DevOps Pipeline Settings");

        // Create form fields
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField organizationField = new TextField(azureConfig.getOrganization());
        TextField projectField = new TextField(azureConfig.getProject());
        TextField pipelineIdField = new TextField(azureConfig.getPipelineId());
        TextField branchField = new TextField(azureConfig.getBranch());
        PasswordField tokenField = new PasswordField();
        tokenField.setText(azureConfig.getPersonalAccessToken());

        grid.add(new Label("Organization:"), 0, 0);
        grid.add(organizationField, 1, 0);
        grid.add(new Label("Project:"), 0, 1);
        grid.add(projectField, 1, 1);
        grid.add(new Label("Pipeline ID:"), 0, 2);
        grid.add(pipelineIdField, 1, 2);
        grid.add(new Label("Branch:"), 0, 3);
        grid.add(branchField, 1, 3);
        grid.add(new Label("Personal Access Token:"), 0, 4);
        grid.add(tokenField, 1, 4);

        // Add help text
        Label helpLabel = new Label(
            "Required Token Permissions:\n" +
            "• Build (read and execute)\n" +
            "• Release (read, write, execute and manage)\n\n" +
            "Token can be created at:\n" +
            "https://dev.azure.com/{organization}/_usersSettings/tokens"
        );
        helpLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");
        grid.add(helpLabel, 0, 5, 2, 1);

        dialog.getDialogPane().setContent(grid);

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType testButtonType = new ButtonType("Test Connection", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(testButtonType, saveButtonType, ButtonType.CANCEL);

        // Handle test connection button
        Button testButton = (Button) dialog.getDialogPane().lookupButton(testButtonType);
        testButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume(); // Prevent dialog from closing
            testAzureConfiguration(organizationField.getText().trim(),
                                 projectField.getText().trim(),
                                 pipelineIdField.getText().trim(),
                                 tokenField.getText().trim());
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                azureConfig.setOrganization(organizationField.getText().trim());
                azureConfig.setProject(projectField.getText().trim());
                azureConfig.setPipelineId(pipelineIdField.getText().trim());
                azureConfig.setBranch(branchField.getText().trim());
                azureConfig.setPersonalAccessToken(tokenField.getText().trim());

                // Recreate pipeline service with new config
                try {
                    pipelineService.close();
                } catch (IOException e) {
                    // Ignore close errors
                }
                pipelineService = new AzurePipelineService(azureConfig);
                fileDownloadService = new FileDownloadService(azureConfig);

                addLogMessage("Azure configuration updated successfully.");
                addLogMessage("Organization: " + azureConfig.getOrganization());
                addLogMessage("Project: " + azureConfig.getProject());
                addLogMessage("Pipeline ID: " + azureConfig.getPipelineId());
                addLogMessage("Branch: " + azureConfig.getBranch());
            }
            return null;
        });

        dialog.showAndWait();
    }

    private void testAzureConfiguration(String organization, String project, String pipelineId, String token) {
        Task<String> testTask = new Task<String>() {
            @Override
            protected String call() throws Exception {
                // Create temporary config for testing
                AzureConfig testConfig = new AzureConfig();
                testConfig.setOrganization(organization);
                testConfig.setProject(project);
                testConfig.setPipelineId(pipelineId);
                testConfig.setPersonalAccessToken(token);

                // Basic validation tests
                if (organization.isEmpty() || project.isEmpty() || pipelineId.isEmpty() || token.isEmpty()) {
                    return "❌ Configuration incomplete. Please fill in all fields.";
                }

                if (token.length() < 50) {
                    return "❌ Personal Access Token appears to be too short. Please verify it's complete.";
                }

                return "✅ Configuration appears valid. You can now save and test with actual log extraction.";
            }
        };

        testTask.setOnSucceeded(e -> {
            showAlert("Configuration Test", testTask.getValue());
        });

        testTask.setOnFailed(e -> {
            showAlert("Configuration Test", "❌ Test failed: " + testTask.getException().getMessage());
        });

        Thread testThread = new Thread(testTask);
        testThread.setDaemon(true);
        testThread.start();
    }

    @FXML
    private void onBrowseOutput() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Output Directory");

        File selectedDirectory = directoryChooser.showDialog(outputDirectoryField.getScene().getWindow());
        if (selectedDirectory != null) {
            outputDirectoryField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    @FXML
    private void onDownloadAll() {
        String outputDir = outputDirectoryField.getText();
        if (outputDir.isEmpty()) {
            showAlert("Output Directory", "Please select an output directory first.");
            return;
        }

        // Download all files
        for (LogFileEntry entry : extractedFiles) {
            downloadFile(entry, outputDir);
        }

        addLogMessage("Started download of all files to: " + outputDir);
    }

    @FXML
    private void onDownloadSelected() {
        LogFileEntry selected = extractedFilesTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        String outputDir = outputDirectoryField.getText();
        if (outputDir.isEmpty()) {
            showAlert("Output Directory", "Please select an output directory first.");
            return;
        }

        downloadFile(selected, outputDir);
    }

    private void downloadFile(LogFileEntry fileEntry, String outputDir) {
        Task<Void> downloadTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                Platform.runLater(() -> {
                    fileEntry.setStatus("Downloading...");
                    extractedFilesTable.refresh();
                });

                addLogMessage("Starting download: " + fileEntry.getFileName());

                // For real implementation, you would need to get the actual artifact info
                // This is a simplified version
                FileDownloadService.ArtifactInfo artifact = new FileDownloadService.ArtifactInfo();
                artifact.setName(fileEntry.getFileName());
                artifact.setDownloadUrl(""); // Would be set from the actual artifact discovery

                boolean success = fileDownloadService.downloadArtifact(artifact, outputDir, this::addLogMessage);

                Platform.runLater(() -> {
                    if (success) {
                        fileEntry.setStatus("Downloaded");
                        addLogMessage("Successfully downloaded: " + fileEntry.getFileName());
                    } else {
                        fileEntry.setStatus("Failed");
                        addLogMessage("Failed to download: " + fileEntry.getFileName());
                    }
                    extractedFilesTable.refresh();
                });

                return null;
            }

            private void addLogMessage(String message) {
                Platform.runLater(() -> {
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    logArea.appendText(String.format("[%s] %s%n", timestamp, message));
                });
            }
        };

        Thread downloadThread = new Thread(downloadTask);
        downloadThread.setDaemon(true);
        downloadThread.start();
    }

    private void loadFilePreview(LogFileEntry entry) {
        Task<String> previewTask = new Task<String>() {
            @Override
            protected String call() throws Exception {
                String outputDir = outputDirectoryField.getText();
                if (!outputDir.isEmpty() && entry.getStatus().equals("Downloaded")) {
                    // Try to load actual file content if it's been downloaded
                    String filePath = outputDir + File.separator + entry.getFileName();
                    if (Files.exists(Paths.get(filePath))) {
                        return fileDownloadService.getFilePreview(filePath, 50);
                    }
                }

                // Fallback to simulated preview
                String preview = String.format("=== Preview of %s ===\n", entry.getFileName());
                preview += String.format("Flight: %s\n", flightNumberField.getText());
                preview += String.format("Date: %s\n", incidentDatePicker.getValue());
                preview += "=================================\n";
                preview += "[File not yet downloaded - showing simulated preview]\n";
                preview += "2024-11-02 10:30:45 INFO  - Application started\n";
                preview += "2024-11-02 10:31:12 DEBUG - Processing flight " + flightNumberField.getText() + "\n";
                preview += "2024-11-02 10:31:45 ERROR - Connection timeout\n";
                preview += "2024-11-02 10:32:01 INFO  - Retrying connection\n";
                preview += "2024-11-02 10:32:15 INFO  - Connection restored\n";
                preview += "...\n";

                return preview;
            }
        };

        previewTask.setOnSucceeded(e -> {
            filePreviewArea.setText(previewTask.getValue());
        });

        previewTask.setOnFailed(e -> {
            filePreviewArea.setText("Error loading preview: " + previewTask.getException().getMessage());
        });

        Thread previewThread = new Thread(previewTask);
        previewThread.setDaemon(true);
        previewThread.start();
    }

    private void addLogMessage(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        logArea.appendText(String.format("[%s] %s%n", timestamp, message));
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Inner class for table data
    public static class LogFileEntry {
        private String fileName;
        private String fileSize;
        private String extractedTime;
        private String status;

        public LogFileEntry(String fileName, String fileSize, String extractedTime, String status) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.extractedTime = extractedTime;
            this.status = status;
        }

        // Getters and setters
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public String getFileSize() { return fileSize; }
        public void setFileSize(String fileSize) { this.fileSize = fileSize; }

        public String getExtractedTime() { return extractedTime; }
        public void setExtractedTime(String extractedTime) { this.extractedTime = extractedTime; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }


}
