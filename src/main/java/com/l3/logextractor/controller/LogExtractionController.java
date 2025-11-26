package com.l3.logextractor.controller;

import com.l3.logextractor.config.AzureConfig;
import com.l3.logextractor.model.LogExtractionRequest;
import com.l3.logextractor.model.LogFileEntry;
import com.l3.logextractor.model.PipelineRunResult;
import com.l3.logextractor.service.AzurePipelineService;
import com.l3.logextractor.service.FileDownloadService;
import com.l3.common.util.VersionUtil;
import com.l3.common.util.PropertiesUtil;
import com.l3.common.util.ErrorHandler;
import com.l3.common.util.ErrorCodes;
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
    @FXML private Label versionLabel;

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

        // Show configuration status after UI is fully initialized
        showConfigurationStatus();
    }

    private void setupUI() {
        // Set default values
        incidentDatePicker.setValue(LocalDate.now());
        statusLabel.setText("Ready");
        progressBar.setVisible(false);

        // Set version label
        if (versionLabel != null) {
            versionLabel.setText(VersionUtil.getFormattedVersion());
        }

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

    private void showConfigurationStatus() {
        // Show configuration load status
        if (azureConfig.configFileExists()) {
            addLogMessage("Azure configuration loaded from: " + azureConfig.getConfigFileLocation());
            addLogMessage("Loaded configuration - Organization: " + azureConfig.getOrganization() +
                         ", Project: " + azureConfig.getProject() +
                         ", Environment: " + azureConfig.getEnvironment());
        } else {
            addLogMessage("No saved configuration found. Using default values.");
            addLogMessage("Click '⚙ Configure Azure' to set up Azure DevOps connection.");
        }
    }

    @FXML
    private void onExtractLogs() {
        String flightNumber = flightNumberField.getText().trim();
        LocalDate incidentDate = incidentDatePicker.getValue();
        String environment = azureConfig.getEnvironment();
        // Validation checks with standardized error codes
        if (flightNumber == null || flightNumber.trim().isEmpty()) {
            ErrorHandler.showError(ErrorCodes.LE006, "Flight number field is empty. Please enter a valid flight number (minimum 3 characters).");
            return;
        }

        if (flightNumber.trim().length() < 3) {
            ErrorHandler.showError(ErrorCodes.LE006, "Flight number '" + flightNumber + "' is too short. Flight numbers must be at least 3 characters long (e.g., WF123, AI101).");
            return;
        }

        if (incidentDate == null) {
            ErrorHandler.showError(ErrorCodes.LE006, "Incident date is required. Please select a date using the date picker.");
            return;
        }

        if (environment == null || environment.trim().isEmpty()) {
            ErrorHandler.showError(ErrorCodes.LE001, "Azure environment is not configured. Please click the ⚙ Configure Azure button to set up your Azure DevOps connection.");
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
                        String errorDetails = currentRun != null ? currentRun.getErrorMessage() : "Unknown pipeline failure";
                        ErrorHandler.showError(ErrorCodes.LE003, "Pipeline execution failed. " + errorDetails);
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
        organizationField.setPrefWidth(250);
        TextField projectField = new TextField(azureConfig.getProject());
        projectField.setPrefWidth(250);
        TextField pipelineIdField = new TextField(azureConfig.getPipelineId());
        pipelineIdField.setPrefWidth(250);
        TextField branchField = new TextField(azureConfig.getBranch());
        branchField.setPrefWidth(250);
        PasswordField tokenField = new PasswordField();
        tokenField.setText(azureConfig.getPersonalAccessToken());
        tokenField.setPrefWidth(250);

        ComboBox<String> environmentField = new ComboBox<>();
        List<String> environments = PropertiesUtil.getPropertyAsList("azure.environments");
        if (environments.isEmpty()) {
            // Fallback to default values if not found in properties
            environmentField.getItems().addAll("azure_ci2", "azure_ci5","SF2_QA","SF2_Prod");
        } else {
            environmentField.getItems().addAll(environments);
        }
        environmentField.setValue(azureConfig.getEnvironment());
        environmentField.setPrefWidth(250);

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
        String configFileStatus = azureConfig.configFileExists()
            ? "✅ Configuration file: " + azureConfig.getConfigFileLocation()
            : "📝 Configuration will be saved to: " + azureConfig.getConfigFileLocation();

        Label helpLabel = new Label(
            "Required Token Permissions:\n" +
            "• Build (read and execute)\n" +
            "• Release (read, write, execute and manage)\n\n" +
            "Token can be created at:\n" +
            "https://dev.azure.com/{organization}/_usersSettings/tokens\n\n"
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
            testAzureConfiguration(
                organizationField.getText().trim(),
                projectField.getText().trim(),
                pipelineIdField.getText().trim(),
                branchField.getText().trim(),
                environmentField.getValue(),
                tokenField.getText().trim()
            );
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

                // Save configuration to file
                boolean saved = azureConfig.saveToFile();
                if (saved) {
                    addLogMessage("Azure configuration updated and saved successfully.");
                    addLogMessage("Configuration file: " + azureConfig.getConfigFileLocation());
                } else {
                    addLogMessage("Azure configuration updated but could not be saved to file.");
                    ErrorHandler.showWarning(ErrorCodes.LE001, "Configuration was applied successfully but could not be saved to properties file. Your settings will work for this session but will be lost when the application restarts.");
                }

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

    private void testAzureConfiguration(String organization, String project, String pipelineId, String branch, String environment, String token) {
        Task<String> testTask = new Task<String>() {
            @Override
            protected String call() throws Exception {

                // 1. COMPLETENESS VALIDATION
                if (organization == null || organization.trim().isEmpty()) {
                    return "❌ Organization field is required.";
                }
                if (project == null || project.trim().isEmpty()) {
                    return "❌ Project field is required.";
                }
                if (pipelineId == null || pipelineId.trim().isEmpty()) {
                    return "❌ Pipeline ID field is required.";
                }
                if (branch == null || branch.trim().isEmpty()) {
                    return "❌ Branch field is required.";
                }
                if (environment == null || environment.trim().isEmpty()) {
                    return "❌ Environment field is required.";
                }
                if (token == null || token.trim().isEmpty()) {
                    return "❌ Personal Access Token field is required.";
                }

                // Clean the inputs - use new variables to maintain effectively final requirement
                final String cleanOrg = organization.trim();
                final String cleanProject = project.trim();
                final String cleanPipelineId = pipelineId.trim();
                final String cleanBranch = branch.trim();
                final String cleanEnvironment = environment.trim();
                final String cleanToken = token.trim();

                // 2. ORGANIZATION VALIDATION
                // Filter: Only alphanumeric, hyphens, and underscores
                // Length: 1-255 characters
                if (!cleanOrg.matches("^[a-zA-Z0-9-_]+$")) {
                    return "❌ Organization name invalid.\n" +
                           "Allowed: Letters, numbers, hyphens (-), underscores (_)\n" +
                           "Current: Contains invalid characters";
                }
                if (cleanOrg.length() < 1 || cleanOrg.length() > 255) {
                    return "❌ Organization name length invalid.\n" +
                           "Required: 1-255 characters\n" +
                           "Current: " + cleanOrg.length() + " characters";
                }

                // 3. PROJECT VALIDATION
                // Filter: Only alphanumeric, hyphens, underscores, and spaces
                // Length: 1-64 characters
                if (!cleanProject.matches("^[a-zA-Z0-9-_ ]+$")) {
                    return "❌ Project name invalid.\n" +
                           "Allowed: Letters, numbers, hyphens (-), underscores (_), spaces\n" +
                           "Current: Contains invalid characters";
                }
                if (cleanProject.length() < 1 || cleanProject.length() > 64) {
                    return "❌ Project name length invalid.\n" +
                           "Required: 1-64 characters\n" +
                           "Current: " + cleanProject.length() + " characters";
                }

                // 4. PIPELINE ID VALIDATION
                // Filter: Only numeric digits
                // Length: 1-10 digits
                if (!cleanPipelineId.matches("^\\d{1,10}$")) {
                    return "❌ Pipeline ID invalid.\n" +
                           "Required: Numeric digits only (1-10 digits)\n" +
                           "Current: '" + cleanPipelineId + "' contains non-numeric characters or wrong length";
                }

                // 5. BRANCH VALIDATION
                // Filter: Valid Git branch name format
                // Length: 1-250 characters
                // Pattern: No spaces at start/end, no consecutive slashes, valid Git characters
                if (!cleanBranch.matches("^[a-zA-Z0-9/_.-]+$")) {
                    return "❌ Branch name invalid.\n" +
                           "Allowed: Letters, numbers, forward slashes (/), underscores (_), dots (.), hyphens (-)\n" +
                           "Current: Contains invalid characters";
                }
                if (cleanBranch.length() < 1 || cleanBranch.length() > 250) {
                    return "❌ Branch name length invalid.\n" +
                           "Required: 1-250 characters\n" +
                           "Current: " + cleanBranch.length() + " characters";
                }
                if (cleanBranch.startsWith("/") || cleanBranch.endsWith("/")) {
                    return "❌ Branch name invalid.\n" +
                           "Branch names cannot start or end with forward slash (/)";
                }
                if (cleanBranch.contains("//")) {
                    return "❌ Branch name invalid.\n" +
                           "Branch names cannot contain consecutive forward slashes (//)";
                }

                // 6. ENVIRONMENT VALIDATION
                // Filter: Must be exactly one of the predefined values
                List<String> validEnvironments = PropertiesUtil.getPropertyAsList("azure.environments");
                if (validEnvironments.isEmpty()) {
                    // Fallback to default values if not found in properties
                    validEnvironments = List.of("azure_ci2", "azure_ci5","SF2_QA","SF2_Prod");
                }

                if (!validEnvironments.contains(cleanEnvironment)) {
                    String validEnvironmentsStr = String.join(", ", validEnvironments.stream()
                        .map(env -> "'" + env + "'").toArray(String[]::new));
                    return "❌ Environment invalid.\n" +
                           "Required: Must be one of " + validEnvironmentsStr + "\n" +
                           "Current: '" + cleanEnvironment + "'";
                }

                // 7. PERSONAL ACCESS TOKEN VALIDATION
                // Azure DevOps supports multiple token formats:
                // - Legacy tokens: 52 characters
                // - New tokens: 84-85 characters with "AZDO" signature near the end
                if (cleanToken.length() == 52) {
                    // Legacy Azure DevOps token format
                    if (!cleanToken.matches("^[A-Za-z0-9+/=]+$")) {
                        return "❌ Personal Access Token format invalid (Legacy 52-char format).\n" +
                               "Allowed: Letters, numbers, plus (+), forward slash (/), equals (=)\n" +
                               "Current: Contains invalid characters\n" +
                               "Note: Ensure you copied the token correctly from Azure DevOps";
                    }
                } else if (cleanToken.length() >= 84 && cleanToken.length() <= 85) {
                    // New Azure DevOps token format with AZDO signature
                    if (!cleanToken.matches("^[A-Za-z0-9+/=_-]+$")) {
                        return "❌ Personal Access Token format invalid (New format).\n" +
                               "Allowed: Letters, numbers, plus (+), forward slash (/), equals (=), underscore (_), hyphen (-)\n" +
                               "Current: Contains invalid characters\n" +
                               "Note: Ensure you copied the token correctly from Azure DevOps";
                    }

                    // Check for AZDO signature - it should be present somewhere in the last part of the token
                    if (!cleanToken.contains("AZDO")) {
                        return "❌ Personal Access Token format invalid (New format).\n" +
                               "Expected: 'AZDO' signature in the token\n" +
                               "Current: No AZDO signature found\n" +
                               "Note: This appears to be a corrupted Azure DevOps token";
                    }
                } else {
                    return "❌ Personal Access Token length invalid.\n" +
                           "Azure DevOps supports these token formats:\n" +
                           "• Legacy tokens: 52 characters\n" +
                           "• New tokens: 84-85 characters (with AZDO signature)\n" +
                           "Current: " + cleanToken.length() + " characters\n" +
                           "Note: Ensure you copied the complete token from Azure DevOps";
                }

                // 8. ADDITIONAL CHECKS
                // Check for common mistakes
                if (cleanOrg.toLowerCase().contains("http")) {
                    return "❌ Organization should not contain URLs.\n" +
                           "Use only the organization name, not the full URL.";
                }

                String tokenType = cleanToken.length() == 52 ? "Legacy" : "New";
                return "✅ All configuration fields are valid!\n\n" +
                       "Validation Results:\n" +
                       "• Organization: '" + cleanOrg + "' ✓\n" +
                       "• Project: '" + cleanProject + "' ✓\n" +
                       "• Pipeline ID: " + cleanPipelineId + " ✓\n" +
                       "• Branch: '" + cleanBranch + "' ✓\n" +
                       "• Environment: '" + cleanEnvironment + "' ✓\n" +
                       "• Personal Access Token: Valid " + tokenType + " format (" + cleanToken.length() + " chars) ✓\n\n" +
                       "All fields pass format validation.\n" +
                       "Click 'Save' to store configuration and enable actual pipeline testing.";
            }
        };

        testTask.setOnSucceeded(e -> {
            ErrorHandler.showInfo("Configuration Validation", testTask.getValue());
        });

        testTask.setOnFailed(e -> {
            Throwable exception = testTask.getException();
            String errorMessage = exception.getMessage();
            String errorCode = ErrorCodes.LE002; // Default to credentials error

            // Determine specific error type based on exception message
            if (errorMessage.contains("network") || errorMessage.contains("connection") || errorMessage.contains("timeout")) {
                errorCode = ErrorCodes.LE004;
            } else if (errorMessage.contains("permission") || errorMessage.contains("unauthorized") || errorMessage.contains("403")) {
                errorCode = ErrorCodes.LE005;
            } else if (errorMessage.contains("authentication") || errorMessage.contains("token") || errorMessage.contains("401")) {
                errorCode = ErrorCodes.LE002;
            }

            // Cast Throwable to Exception or handle as string if not an Exception
            if (exception instanceof Exception) {
                ErrorHandler.showError(errorCode, (Exception) exception);
            } else {
                ErrorHandler.showError(errorCode, "Configuration validation failed: " + errorMessage);
            }
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
        if (outputDir == null || outputDir.trim().isEmpty()) {
            ErrorHandler.showError(ErrorCodes.LE007, "Output directory is required for downloading files. Please click 'Browse' to select a folder where the extracted files will be saved.");
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
        if (outputDir == null || outputDir.trim().isEmpty()) {
            ErrorHandler.showError(ErrorCodes.LE007, "Output directory is required for downloading files. Please click 'Browse' to select a folder where the extracted files will be saved.");
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
