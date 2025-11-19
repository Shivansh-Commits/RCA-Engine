package com.l3.logextractor.controller;

import com.l3.logextractor.config.AzureConfig;
import com.l3.logextractor.model.LogExtractionRequest;
import com.l3.logextractor.model.LogFileEntry;
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
        setupInputValidations();
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

    private void setupInputValidations() {
        // Flight number validation - max 8 characters, only alphanumeric and '+'
        flightNumberField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                // Remove any characters that aren't alphanumeric or '+'
                String filtered = newValue.replaceAll("[^a-zA-Z0-9+]", "");

                // Limit to 8 characters
                if (filtered.length() > 8) {
                    filtered = filtered.substring(0, 8);
                }

                // Only update if the value changed to avoid infinite loop
                if (!filtered.equals(newValue)) {
                    flightNumberField.setText(filtered);
                }
            }
        });

        // Make date field non-editable (only use date picker)
        incidentDatePicker.getEditor().setDisable(true);
        incidentDatePicker.getEditor().setOpacity(1);
    }

    private void initializeServices() {
        azureConfig = new AzureConfig();
        pipelineService = new AzurePipelineService(azureConfig);
        fileDownloadService = new FileDownloadService(azureConfig);
        // statusChecker will be created per extraction to avoid reuse issues
    }

    @FXML
    private void onExtractLogs() {
        String flightNumber = flightNumberField.getText().trim();
        LocalDate incidentDate = incidentDatePicker.getValue();
        String environment = azureConfig.getEnvironment();

        if (flightNumber.isEmpty()) {
            showAlert("Validation Error", "Please enter a flight number.");
            return;
        }

        if (flightNumber.length() < 3) {
            showAlert("Validation Error", "Flight number must be at least 3 characters long.");
            return;
        }

        if (incidentDate == null) {
            showAlert("Validation Error", "Please select an incident date using the date picker.");
            return;
        }

        if (environment == null || environment.trim().isEmpty()) {
            showAlert("Configuration Error", "Please configure the environment in Azure settings (⚙ Configure Azure button).");
            return;
        }

        // Create extraction request with environment
        LocalDateTime incidentDateTime = LocalDateTime.of(incidentDate, LocalTime.MIDNIGHT);
        LogExtractionRequest request = new LogExtractionRequest(flightNumber, incidentDateTime, environment);

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
        // Shutdown any existing status checker
        if (statusChecker != null && !statusChecker.isShutdown()) {
            statusChecker.shutdown();
        }

        // Create a new status checker for this extraction
        statusChecker = Executors.newSingleThreadScheduledExecutor();

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
            addLogMessage("Pipeline execution completed. Discovering available artifacts...");

            // Discover available artifacts but don't download them yet
            discoverAvailableArtifacts(result);

            downloadAllButton.setDisable(false);
            summaryLabel.setText("Ready for download. Select output directory and click download.");
        } else {
            statusLabel.setText("Pipeline failed");
            addLogMessage("Pipeline execution failed: " + result.getErrorMessage());
            summaryLabel.setText("Extraction failed.");
        }
    }

    private void discoverAvailableArtifacts(PipelineRunResult result) {
        Task<Void> discoveryTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                addLogMessage("Discovering available artifacts...");

                // Get list of available artifacts without downloading them
                List<FileDownloadService.ArtifactInfo> artifacts = fileDownloadService.getArtifacts(
                    result.getRunId(),
                    this::addLogMessage
                );

                Platform.runLater(() -> {
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                    if (artifacts.isEmpty()) {
                        addLogMessage("No artifacts found for this build.");
                        summaryLabel.setText("No artifacts available for download");
                        return;
                    }

                    // For each artifact, get the file list and add individual files to the table
                    for (FileDownloadService.ArtifactInfo artifact : artifacts) {
                        // Try to get file list from the artifact
                        List<String> fileNames = getFileNamesFromArtifact(artifact);

                        if (!fileNames.isEmpty()) {
                            // Add each individual file to the table
                            for (String fileName : fileNames) {
                                extractedFiles.add(new LogFileEntry(
                                    fileName,
                                    "0", // Size will be updated after download
                                    timestamp,
                                    "Available",
                                    artifact.getDownloadUrl() + "|" + fileName  // Store download URL and specific file name
                                ));
                            }
                            addLogMessage("Found " + fileNames.size() + " files in artifact: " + artifact.getName());
                        } else {
                            // Fallback: add the artifact itself
                            extractedFiles.add(new LogFileEntry(
                                artifact.getName(),
                                String.valueOf(artifact.getSize()),
                                timestamp,
                                "Available",
                                artifact.getDownloadUrl()
                            ));
                            addLogMessage("Added artifact: " + artifact.getName());
                        }
                    }

                    int totalFiles = extractedFiles.size();
                    addLogMessage("Total files ready for download: " + totalFiles);
                    summaryLabel.setText("Found " + totalFiles + " files. Select output directory to download.");
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
        // Shutdown any running status monitoring
        if (statusChecker != null && !statusChecker.isShutdown()) {
            statusChecker.shutdown();
        }

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
        extractButton.setDisable(false);
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

        ComboBox<String> environmentField = new ComboBox<>();
        environmentField.getItems().addAll("azure_ci2", "azure_ci5");
        environmentField.setValue(azureConfig.getEnvironment());

        grid.add(new Label("Organization:"), 0, 0);
        grid.add(organizationField, 1, 0);
        grid.add(new Label("Project:"), 0, 1);
        grid.add(projectField, 1, 1);
        grid.add(new Label("Pipeline ID:"), 0, 2);
        grid.add(pipelineIdField, 1, 2);
        grid.add(new Label("Branch:"), 0, 3);
        grid.add(branchField, 1, 3);
        grid.add(new Label("Environment:"), 0, 4);
        grid.add(environmentField, 1, 4);
        grid.add(new Label("Personal Access Token:"), 0, 5);
        grid.add(tokenField, 1, 5);

        // Add help text
        Label helpLabel = new Label(
            "Required Token Permissions:\n" +
            "• Build (read and execute)\n" +
            "• Release (read, write, execute and manage)\n\n" +
            "Token can be created at:\n" +
            "https://dev.azure.com/{organization}/_usersSettings/tokens"
        );
        helpLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");
        grid.add(helpLabel, 0, 6, 2, 1);

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
                azureConfig.setEnvironment(environmentField.getValue());
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
                addLogMessage("Environment: " + azureConfig.getEnvironment());
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

    /**
     * Get file names from a zip artifact by downloading and inspecting the zip
     */
    private List<String> getFileNamesFromArtifact(FileDownloadService.ArtifactInfo artifact) {
        List<String> fileNames = new java.util.ArrayList<>();

        try {
            // Get the file names from the artifact using the FileDownloadService
            fileNames = fileDownloadService.getFileNamesFromArtifact(artifact);
        } catch (Exception e) {
            addLogMessage("Could not extract file names from artifact " + artifact.getName() + ": " + e.getMessage());
        }

        return fileNames;
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

                try {
                    String filePath = fileEntry.getFilePath();
                    String downloadUrl;
                    String specificFileName = null;

                    // Check if this is a specific file within an artifact (contains |)
                    if (filePath != null && filePath.contains("|")) {
                        String[] parts = filePath.split("\\|", 2);
                        downloadUrl = parts[0];
                        specificFileName = parts[1];
                    } else {
                        downloadUrl = filePath;
                    }

                    // Create artifact info from the table entry
                    FileDownloadService.ArtifactInfo artifact = new FileDownloadService.ArtifactInfo();
                    artifact.setName(specificFileName != null ? specificFileName : fileEntry.getFileName());
                    artifact.setDownloadUrl(downloadUrl);

                    try {
                        artifact.setSize(Long.parseLong(fileEntry.getFileSize()));
                    } catch (NumberFormatException e) {
                        artifact.setSize(0);
                    }

                    // Download the artifact from Azure DevOps
                    boolean success = fileDownloadService.downloadSpecificFile(artifact, specificFileName, outputDir, this::addLogMessage);

                    Platform.runLater(() -> {
                        if (success) {
                            fileEntry.setStatus("Downloaded");

                            // Check if the original file was a .gz file and handle extraction
                            String originalFileName = fileEntry.getFileName();
                            String finalFileName = originalFileName;
                            Path downloadedFile;

                            // If original file was .gz, the actual extracted file will have .gz removed
                            if (originalFileName.toLowerCase().endsWith(".gz")) {
                                finalFileName = originalFileName.substring(0, originalFileName.length() - 3);
                                downloadedFile = Paths.get(outputDir).resolve(finalFileName);

                                // Update the table entry to reflect the extracted file
                                fileEntry.setFileName(finalFileName);
                                addLogMessage("File automatically extracted from .gz: " + originalFileName + " -> " + finalFileName);
                            } else {
                                downloadedFile = Paths.get(outputDir).resolve(originalFileName);
                            }

                            // Update the file path to the local downloaded file location
                            fileEntry.setFilePath(downloadedFile.toString());

                            // Update file size if possible
                            if (Files.exists(downloadedFile)) {
                                try {
                                    long newSize = Files.size(downloadedFile);
                                    fileEntry.setFileSize(String.valueOf(newSize));
                                } catch (Exception e) {
                                    // Keep original size if we can't read the new file
                                }
                            }

                            addLogMessage("Successfully downloaded: " + finalFileName + " to " + outputDir);
                        } else {
                            fileEntry.setStatus("Failed");
                            addLogMessage("Failed to download: " + fileEntry.getFileName());
                        }
                        extractedFilesTable.refresh();
                    });

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        fileEntry.setStatus("Failed");
                        addLogMessage("Failed to download " + fileEntry.getFileName() + ": " + e.getMessage());
                        extractedFilesTable.refresh();
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

        Thread downloadThread = new Thread(downloadTask);
        downloadThread.setDaemon(true);
        downloadThread.start();
    }

    private void loadFilePreview(LogFileEntry entry) {
        Task<String> previewTask = new Task<String>() {
            @Override
            protected String call() throws Exception {
                // If file has been downloaded, try to show actual content
                if ("Downloaded".equals(entry.getStatus()) && entry.getFilePath() != null && !entry.getFilePath().isEmpty()) {
                    Path sourceFile = Paths.get(entry.getFilePath());
                    if (Files.exists(sourceFile)) {
                        return fileDownloadService.getFilePreview(sourceFile.toString(), 50);
                    }
                }

                // If file is available but not downloaded yet, show info message
                if ("Available".equals(entry.getStatus())) {
                    String preview = String.format("=== File Information: %s ===\n", entry.getFileName());
                    preview += String.format("Flight: %s\n", flightNumberField.getText());
                    preview += String.format("Date: %s\n", incidentDatePicker.getValue());
                    preview += String.format("File Size: %s\n", formatFileSize(Long.parseLong(entry.getFileSize())));
                    preview += "Status: Ready for download\n";
                    preview += "=================================\n\n";
                    preview += "This file is available for download from Azure DevOps.\n";
                    preview += "Please select an output directory and click 'Download Selected' or 'Download All' to download this file.\n";
                    preview += "Once downloaded, you will be able to preview the actual file content here.";
                    return preview;
                }

                // Fallback preview
                String preview = String.format("=== Preview of %s ===\n", entry.getFileName());
                preview += String.format("Flight: %s\n", flightNumberField.getText());
                preview += String.format("Date: %s\n", incidentDatePicker.getValue());
                preview += "=================================\n";
                preview += "[File status: " + entry.getStatus() + "]\n";
                preview += "Unable to load preview for this file.";

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

    /**
     * Cleanup method to shutdown resources properly when application closes
     */
    public void cleanup() {
        try {
            if (statusChecker != null && !statusChecker.isShutdown()) {
                statusChecker.shutdown();
                if (!statusChecker.awaitTermination(5, TimeUnit.SECONDS)) {
                    statusChecker.shutdownNow();
                }
            }

            if (pipelineService != null) {
                pipelineService.close();
            }

            if (fileDownloadService != null) {
                fileDownloadService.close();
            }
        } catch (Exception e) {
            // Log error but don't throw exception during cleanup
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }

}
