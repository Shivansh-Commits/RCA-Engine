package com.l3.logextractor.controller;

import com.l3.logextractor.config.AzureConfig;
import com.l3.logextractor.model.LogExtractionRequest;
import com.l3.logextractor.model.LogFileEntry;
import com.l3.logextractor.model.PipelineRunResult;
import com.l3.logextractor.service.AzurePipelineService;
import com.l3.logextractor.service.FileDownloadService;
import com.l3.common.util.VersionUtil;
import com.l3.common.util.PropertiesUtil;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.*;
import java.util.stream.Stream;

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
    @FXML private Button unzipAllButton;
    @FXML private TextField outputDirectoryField;
    @FXML private Button browseOutputButton;

    private AzureConfig azureConfig;
    private AzurePipelineService pipelineService;
    private FileDownloadService fileDownloadService;
    private ObservableList<LogFileEntry> extractedFiles;
    private PipelineRunResult currentRun;
    private ScheduledExecutorService statusChecker;
    private List<FileDownloadService.ArtifactInfo> currentArtifacts;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupUI();
        setupTableColumns();
        setupTableSelection();
        setupInputValidations();
        initializeServices();
        extractedFiles = FXCollections.observableArrayList();
        extractedFilesTable.setItems(extractedFiles);
        
        // Add a test entry to verify table is working (will be cleared later)\n        addLogMessage(\"\ud83d\udd27 Initializing table with test entry...\");\n        extractedFiles.add(new LogFileEntry(\n            \"test-file.log\", \n            \"1024\", \n            LocalDateTime.now().format(DateTimeFormatter.ofPattern(\"yyyy-MM-dd HH:mm:ss\")),\n            \"Test\",\n            \"test://url\"\n        ));\n        addLogMessage(\"\ud83d\udccb Table initialized with \" + extractedFiles.size() + \" test items\");\n        \n        // Clear test entry after a short delay\n        Platform.runLater(() -> {\n            extractedFiles.clear();\n            addLogMessage(\"\ud83e\uddf9 Cleared test entries, table ready for real data\");\n        });

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
        unzipAllButton.setDisable(true);
    }

    private void setupTableColumns() {
        fileNameColumn.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        fileSizeColumn.setCellValueFactory(new PropertyValueFactory<>("fileSize"));
        extractedTimeColumn.setCellValueFactory(new PropertyValueFactory<>("extractedTime"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Set minimum column widths to ensure content is visible
        fileNameColumn.setMinWidth(200);
        fileSizeColumn.setMinWidth(100);
        extractedTimeColumn.setMinWidth(150);
        statusColumn.setMinWidth(100);
        
        // Format file size column with improved error handling
        fileSizeColumn.setCellFactory(column -> new TableCell<LogFileEntry, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.trim().isEmpty()) {
                    setText(null);
                } else {
                    try {
                        String trimmedItem = item.trim();
                        long bytes = Long.parseLong(trimmedItem);
                        
                        if (bytes < 0) {
                            setText("Unknown");
                        } else if (bytes == 0) {
                            setText("0 B");
                        } else {
                            setText(formatFileSize(bytes));
                        }
                    } catch (NumberFormatException e) {
                        // For debugging: show the raw value
                        setText("[" + item + "]");
                        System.out.println("DEBUG: Failed to parse file size: '" + item + "'");
                    }
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
            // Enable unzip button only if output directory is set
            if (!outputDirectoryField.getText().isEmpty()) {
                unzipAllButton.setDisable(false);
            }
            summaryLabel.setText("Ready for download. Select output directory and click download.");
        } else {
            statusLabel.setText("Pipeline failed");
            addLogMessage("Pipeline execution failed: " + result.getErrorMessage());
            summaryLabel.setText("Extraction failed.");
        }
    }

    private void discoverAvailableArtifacts(PipelineRunResult result) {

        addLogMessage("Discovering available artifacts...");

        CompletableFuture
                .supplyAsync(() -> fileDownloadService.getArtifacts(result.getRunId(), this::addLogMessage), executor)
                .thenCompose(artifacts -> {

                    currentArtifacts = artifacts;

                    if (artifacts.isEmpty()) {
                        Platform.runLater(() -> {
                            addLogMessage("No artifacts found for this build.");
                            summaryLabel.setText("No artifacts available for download");
                        });
                        return CompletableFuture.completedFuture(null);
                    }

                    // Process all artifacts in background thread
                    return CompletableFuture.supplyAsync(() -> {
                        List<LogFileEntry> entries = new ArrayList<>();

                        for (FileDownloadService.ArtifactInfo artifact : artifacts) {

                            addLogMessage("Analyzing artifact: " + artifact.getName() +
                                    " (Size: " + formatFileSize(artifact.getSize()) + ")");

                            List<FileDownloadService.FileInfo> fileInfos =
                                    fileDownloadService.getFileInfoFromArtifact(artifact);

                            addLogMessage(" Retrieved " + fileInfos.size() + " files from artifact analysis");

                            String timestamp = LocalDateTime.now()
                                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                            for (FileDownloadService.FileInfo fileInfo : fileInfos) {
                                entries.add(new LogFileEntry(
                                        fileInfo.getName(),
                                        String.valueOf(fileInfo.getSize()),
                                        timestamp,
                                        "Available",
                                        artifact.getDownloadUrl() + "|" + fileInfo.getName()
                                ));
                            }
                        }

                        return entries;
                    }, executor);
                })
                .thenAccept(entries -> {

                    if (entries == null) return;

                    Platform.runLater(() -> {
                        extractedFiles.clear();
                        extractedFiles.addAll(entries);

                        extractedFilesTable.refresh();

                        fileSizeColumn.setVisible(false);
                        fileSizeColumn.setVisible(true);

                        summaryLabel.setText("Found " + entries.size() +
                                " files. Select output directory to download.");

                        addLogMessage(" Total files ready for download: " + entries.size());
                    });
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> addLogMessage("Error during artifact discovery: " + ex.getMessage()));
                    return null;
                });
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
        currentArtifacts = null;
        downloadAllButton.setDisable(true);
        downloadSelectedButton.setDisable(true);
        unzipAllButton.setDisable(true);
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
                    showAlert("Warning", "Configuration was applied but could not be saved to file. Settings will be lost when application restarts.");
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
                    return " Organization field is required.";
                }
                if (project == null || project.trim().isEmpty()) {
                    return " Project field is required.";
                }
                if (pipelineId == null || pipelineId.trim().isEmpty()) {
                    return " Pipeline ID field is required.";
                }
                if (branch == null || branch.trim().isEmpty()) {
                    return " Branch field is required.";
                }
                if (environment == null || environment.trim().isEmpty()) {
                    return " Environment field is required.";
                }
                if (token == null || token.trim().isEmpty()) {
                    return " Personal Access Token field is required.";
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
                    return " Organization name invalid.\n" +
                           "Allowed: Letters, numbers, hyphens (-), underscores (_)\n" +
                           "Current: Contains invalid characters";
                }
                if (cleanOrg.length() < 1 || cleanOrg.length() > 255) {
                    return " Organization name length invalid.\n" +
                           "Required: 1-255 characters\n" +
                           "Current: " + cleanOrg.length() + " characters";
                }

                // 3. PROJECT VALIDATION
                // Filter: Only alphanumeric, hyphens, underscores, and spaces
                // Length: 1-64 characters
                if (!cleanProject.matches("^[a-zA-Z0-9-_ ]+$")) {
                    return " Project name invalid.\n" +
                           "Allowed: Letters, numbers, hyphens (-), underscores (_), spaces\n" +
                           "Current: Contains invalid characters";
                }
                if (cleanProject.length() < 1 || cleanProject.length() > 64) {
                    return " Project name length invalid.\n" +
                           "Required: 1-64 characters\n" +
                           "Current: " + cleanProject.length() + " characters";
                }

                // 4. PIPELINE ID VALIDATION
                // Filter: Only numeric digits
                // Length: 1-10 digits
                if (!cleanPipelineId.matches("^\\d{1,10}$")) {
                    return " Pipeline ID invalid.\n" +
                           "Required: Numeric digits only (1-10 digits)\n" +
                           "Current: '" + cleanPipelineId + "' contains non-numeric characters or wrong length";
                }

                // 5. BRANCH VALIDATION
                // Filter: Valid Git branch name format
                // Length: 1-250 characters
                // Pattern: No spaces at start/end, no consecutive slashes, valid Git characters
                if (!cleanBranch.matches("^[a-zA-Z0-9/_.-]+$")) {
                    return " Branch name invalid.\n" +
                           "Allowed: Letters, numbers, forward slashes (/), underscores (_), dots (.), hyphens (-)\n" +
                           "Current: Contains invalid characters";
                }
                if (cleanBranch.length() < 1 || cleanBranch.length() > 250) {
                    return " Branch name length invalid.\n" +
                           "Required: 1-250 characters\n" +
                           "Current: " + cleanBranch.length() + " characters";
                }
                if (cleanBranch.startsWith("/") || cleanBranch.endsWith("/")) {
                    return " Branch name invalid.\n" +
                           "Branch names cannot start or end with forward slash (/)";
                }
                if (cleanBranch.contains("//")) {
                    return " Branch name invalid.\n" +
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
                    return " Environment invalid.\n" +
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
                        return " Personal Access Token format invalid (Legacy 52-char format).\n" +
                               "Allowed: Letters, numbers, plus (+), forward slash (/), equals (=)\n" +
                               "Current: Contains invalid characters\n" +
                               "Note: Ensure you copied the token correctly from Azure DevOps";
                    }
                } else if (cleanToken.length() >= 84 && cleanToken.length() <= 85) {
                    // New Azure DevOps token format with AZDO signature
                    if (!cleanToken.matches("^[A-Za-z0-9+/=_-]+$")) {
                        return " Personal Access Token format invalid (New format).\n" +
                               "Allowed: Letters, numbers, plus (+), forward slash (/), equals (=), underscore (_), hyphen (-)\n" +
                               "Current: Contains invalid characters\n" +
                               "Note: Ensure you copied the token correctly from Azure DevOps";
                    }

                    // Check for AZDO signature - it should be present somewhere in the last part of the token
                    if (!cleanToken.contains("AZDO")) {
                        return " Personal Access Token format invalid (New format).\n" +
                               "Expected: 'AZDO' signature in the token\n" +
                               "Current: No AZDO signature found\n" +
                               "Note: This appears to be a corrupted Azure DevOps token";
                    }
                } else {
                    return " Personal Access Token length invalid.\n" +
                           "Azure DevOps supports these token formats:\n" +
                           "• Legacy tokens: 52 characters\n" +
                           "• New tokens: 84-85 characters (with AZDO signature)\n" +
                           "Current: " + cleanToken.length() + " characters\n" +
                           "Note: Ensure you copied the complete token from Azure DevOps";
                }

                // 8. ADDITIONAL CHECKS
                // Check for common mistakes
                if (cleanOrg.toLowerCase().contains("http")) {
                    return " Organization should not contain URLs.\n" +
                           "Use only the organization name, not the full URL.";
                }

                String tokenType = cleanToken.length() == 52 ? "Legacy" : "New";
                return " All configuration fields are valid!\n\n" +
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
            showAlert("Configuration Validation", testTask.getValue());
        });

        testTask.setOnFailed(e -> {
            showAlert("Configuration Validation", " Validation failed: " + testTask.getException().getMessage());
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

        if (currentArtifacts == null || currentArtifacts.isEmpty()) {
            showAlert("No Artifacts", "No artifacts available for download. Please run extraction first.");
            return;
        }

        downloadAllButton.setDisable(true);
        addLogMessage("Starting parallel download of all artifacts to: " + outputDir);

        // Update all file statuses to "Downloading"
        Platform.runLater(() -> {
            LogFileEntry selectedEntry = extractedFilesTable.getSelectionModel().getSelectedItem();
            for (LogFileEntry entry : extractedFiles) {
                entry.setStatus("Downloading");
            }
            extractedFilesTable.refresh();

            // Refresh preview if a file is selected
            if (selectedEntry != null) {
                loadFilePreview(selectedEntry);
            }
        });

        Task<Void> downloadTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    // Download each artifact and update individual file statuses
                    ExecutorService executor = Executors.newFixedThreadPool(4); // or dynamic based on CPU cores

                    List<CompletableFuture<Boolean>> futures = new ArrayList<>();

                    for (FileDownloadService.ArtifactInfo artifact : currentArtifacts) {

                        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {

                            // Download + extract this artifact in parallel (your existing logic)
                            boolean success = fileDownloadService.downloadAllArtifactsParallel(
                                    List.of(artifact),
                                    outputDir,
                                    this::addLogMessage
                            );

                            // Update UI on FX thread
                            Platform.runLater(() ->
                                    updateFileStatusesForArtifact(
                                            artifact,
                                            success ? "Downloaded" : "Download Failed"
                                    )
                            );

                            return success;

                        }, executor);

                        futures.add(future);
                    }

// Wait for ALL artifacts to finish
                    CompletableFuture<Void> allDone = CompletableFuture.allOf(
                            futures.toArray(new CompletableFuture[0])
                    );

                    boolean allSuccess = allDone.thenApply(v ->
                            futures.stream().allMatch(f -> {
                                try { return f.get(); } catch (Exception e) { return false; }
                            })
                    ).join();

                    executor.shutdown();
                    final boolean finalSuccess = allSuccess;
                    Platform.runLater(() -> {
                        if (finalSuccess) {
                            addLogMessage("All artifacts downloaded successfully! You can now click 'Unzip All' to extract .gz files.");
                            unzipAllButton.setDisable(false);
                        } else {
                            addLogMessage("Some artifacts failed to download. Check the logs for details.");
                        }
                        downloadAllButton.setDisable(false);
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        addLogMessage("Error during download: " + e.getMessage());
                        // Update all files to failed status
                        for (LogFileEntry entry : extractedFiles) {
                            if ("Downloading".equals(entry.getStatus())) {
                                entry.setStatus("Download Failed");
                            }
                        }
                        extractedFilesTable.refresh();
                        downloadAllButton.setDisable(false);
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

    @FXML
    private void onUnzipAll() {
        String outputDir = outputDirectoryField.getText();
        if (outputDir.isEmpty()) {
            showAlert("Output Directory", "Please select an output directory first.");
            return;
        }

        unzipAllButton.setDisable(true);
        addLogMessage("Starting to unzip all .gz files in: " + outputDir);

        // Update status of .gz files to "Unzipping"
        Platform.runLater(() -> {
            LogFileEntry selectedEntry = extractedFilesTable.getSelectionModel().getSelectedItem();
            boolean shouldRefreshPreview = false;
            
            for (LogFileEntry entry : extractedFiles) {
                if ("Downloaded".equals(entry.getStatus()) && entry.getFileName().toLowerCase().endsWith(".gz")) {
                    entry.setStatus("Unzipping");
                    // Check if this is the selected file
                    if (selectedEntry != null && selectedEntry.equals(entry)) {
                        shouldRefreshPreview = true;
                    }
                }
            }
            extractedFilesTable.refresh();
            
            // Refresh preview if selected file status changed
            if (shouldRefreshPreview && selectedEntry != null) {
                loadFilePreview(selectedEntry);
            }
        });

        Task<Void> unzipTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    boolean success = fileDownloadService.unzipAllGzFiles(outputDir, (message) -> {
                        this.addLogMessage(message);
                        // Check if message indicates successful extraction of a specific file
                        if (message.contains("Successfully extracted and removed .gz file:")) {
                            String[] parts = message.split(" -> ");
                            if (parts.length == 2) {
                                String extractedFileName = parts[1].trim();
                                Platform.runLater(() -> {
                                    updateFileStatusAfterUnzip(extractedFileName);
                                });
                            }
                        }
                    });
                    
                    Platform.runLater(() -> {
                        // Update any remaining unzipping files to completed status
                        for (LogFileEntry entry : extractedFiles) {
                            if ("Unzipping".equals(entry.getStatus())) {
                                entry.setStatus(success ? "Extracted" : "Unzip Failed");
                            }
                        }
                        extractedFilesTable.refresh();
                        
                        if (success) {
                            addLogMessage("All .gz files have been successfully extracted and original .gz files deleted!");
                        } else {
                            addLogMessage("Some .gz files failed to extract. Check the logs for details.");
                        }
                        unzipAllButton.setDisable(false);
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        addLogMessage("Error during unzip: " + e.getMessage());
                        // Update all unzipping files to failed status
                        for (LogFileEntry entry : extractedFiles) {
                            if ("Unzipping".equals(entry.getStatus())) {
                                entry.setStatus("Unzip Failed");
                            }
                        }
                        extractedFilesTable.refresh();
                        unzipAllButton.setDisable(false);
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

        Thread unzipThread = new Thread(unzipTask);
        unzipThread.setDaemon(true);
        unzipThread.start();
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
        
        addLogMessage(" Inspecting artifact: " + artifact.getName());

        try {
            // Get the file names from the artifact using the FileDownloadService
            fileNames = fileDownloadService.getFileNamesFromArtifact(artifact);
            addLogMessage(" Found " + fileNames.size() + " files in artifact inspection");
            
            if (fileNames.isEmpty()) {
                // If we can't get individual files, add the artifact itself
                addLogMessage(" No individual files found, using artifact name as fallback");
                fileNames.add(artifact.getName());
            } else {
                // Log the first few file names for debugging
                for (int i = 0; i < Math.min(3, fileNames.size()); i++) {
                    addLogMessage("   File " + (i+1) + ": " + fileNames.get(i));
                }
                if (fileNames.size() > 3) {
                    addLogMessage("  ... and " + (fileNames.size() - 3) + " more files");
                }
            }
        } catch (Exception e) {
            addLogMessage(" Could not extract file names from artifact " + artifact.getName() + ": " + e.getMessage());
            // Fallback: add the artifact name itself
            addLogMessage(" Using artifact name as fallback: " + artifact.getName());
            fileNames.add(artifact.getName());
        }

        return fileNames;
    }

    /**
     * Update status of files that belong to a specific artifact
     */
    private void updateFileStatusesForArtifact(FileDownloadService.ArtifactInfo artifact, String status) {
        LogFileEntry selectedEntry = extractedFilesTable.getSelectionModel().getSelectedItem();
        boolean shouldRefreshPreview = false;
        
        for (LogFileEntry entry : extractedFiles) {
            // Check if this file belongs to the current artifact
            if (entry.getFilePath() != null && entry.getFilePath().contains(artifact.getDownloadUrl())) {
                entry.setStatus(status);
                // Check if the currently selected file's status changed
                if (selectedEntry != null && selectedEntry.equals(entry)) {
                    shouldRefreshPreview = true;
                }
            }
        }
        extractedFilesTable.refresh();
        
        // Refresh preview if selected file status changed
        if (shouldRefreshPreview && selectedEntry != null) {
            loadFilePreview(selectedEntry);
        }
    }

    /**
     * Update file status after unzip operation
     */
    private void updateFileStatusAfterUnzip(String extractedFileName) {
        LogFileEntry selectedEntry = extractedFilesTable.getSelectionModel().getSelectedItem();
        boolean shouldRefreshPreview = false;
        
        for (LogFileEntry entry : extractedFiles) {
            // Find the .gz file and update its status
            if (entry.getFileName().equals(extractedFileName + ".gz") || 
                entry.getFileName().equals(extractedFileName)) {
                entry.setStatus("Extracted");
                
                // Check if this is the currently selected file
                if (selectedEntry != null && selectedEntry.equals(entry)) {
                    shouldRefreshPreview = true;
                }
                
                // Update the filename to the extracted version (without .gz)
                if (entry.getFileName().endsWith(".gz")) {
                    entry.setFileName(extractedFileName);
                }
                break;
            }
        }
        extractedFilesTable.refresh();
        
        // Refresh preview if selected file status changed
        if (shouldRefreshPreview && selectedEntry != null) {
            loadFilePreview(selectedEntry);
        }
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
                return LogExtractionController.this.generatePreviewContent(entry);
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
        String logEntry = String.format("[%s] %s%n", timestamp, message);
        
        // Ensure UI update happens on JavaFX Application Thread
        if (Platform.isFxApplicationThread()) {
            logArea.appendText(logEntry);
            // Auto-scroll to bottom
            logArea.setScrollTop(Double.MAX_VALUE);
        } else {
            Platform.runLater(() -> {
                logArea.appendText(logEntry);
                // Auto-scroll to bottom
                logArea.setScrollTop(Double.MAX_VALUE);
            });
        }
    }

    /**
     * Format file size in human readable format
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * Generate preview content based on file status and availability
     */
    private String generatePreviewContent(LogFileEntry entry) {
        String fileName = entry.getFileName();
        String status = entry.getStatus();
        String outputDir = outputDirectoryField.getText();
        
        // Header for all preview types
        String header = String.format("=== File Preview: %s ===\n", fileName);
        header += String.format("Flight: %s\n", flightNumberField.getText());
        header += String.format("Date: %s\n", incidentDatePicker.getValue());
        header += String.format("File Size: %s\n", formatFileSize(Long.parseLong(entry.getFileSize())));
        header += String.format("Status: %s\n", status);
        header += "=" + "=".repeat(50) + "\n\n";
        
        switch (status) {
            case "Available":
                return header + 
                    " FILE NOT DOWNLOADED YET\n\n" +
                    " This file is available for download from Azure DevOps.\n\n" +
                    " Instructions:\n" +
                    "1. Select an output directory above\n" +
                    "2. Click 'Download All' or 'Download Selected' to download this file\n" +
                    "3. Once downloaded, you can preview the file content here\n\n" +
                    " Note: You need to download and extract the file first to see its content.";
                    
            case "Downloading":
                return header + 
                    " FILE DOWNLOAD IN PROGRESS\n\n" +
                    " Please wait while the file is being downloaded...\n\n" +
                    " Download may take some time for large files.\n" +
                    "Once download is complete, you can preview the file content.";
                    
            case "Downloaded":
                // Check if it's a .gz file
                if (fileName.toLowerCase().endsWith(".gz")) {
                    return header + 
                        " FILE DOWNLOADED (COMPRESSED)\n\n" +
                        " This file is downloaded but still compressed (.gz format).\n\n" +
                        " Instructions:\n" +
                        "1. Click 'Unzip All' button to extract compressed files\n" +
                        "2. After extraction, you can preview the file content here\n\n" +
                        " Note: You need to unzip the file first to see its content.";
                } else {
                    // Try to load actual file content for non-gz files
                    return loadActualFileContent(entry, header);
                }
                
            case "Unzipping":
                return header + 
                    " FILE EXTRACTION IN PROGRESS\n\n" +
                    " Please wait while the file is being extracted from .gz format...\n\n" +
                    " Extraction in progress. Large files may take some time.";
                    
            case "Extracted":
                // Load actual file content
                header="";
                return loadActualFileContent(entry, header);
                
            case "Download Failed":
                return header + 
                    " DOWNLOAD FAILED\n\n" +
                    " Failed to download this file.\n\n" +
                    " You can try downloading again by clicking 'Download Selected' or 'Download All'.";
                    
            case "Unzip Failed":
                return header + 
                    " EXTRACTION FAILED\n\n" +
                    " Failed to extract this compressed file.\n\n" +
                    " You can try extracting again by clicking 'Unzip All'.";
                    
            default:
                return header + 
                    " UNKNOWN STATUS\n\n" +
                    String.format("File status: %s\n\n", status) +
                    "Unable to determine the current state of this file.";
        }
    }
    
    /**
     * Load actual file content for preview
     */
    private String loadActualFileContent(LogFileEntry entry, String header) {
        try {
            String outputDir = outputDirectoryField.getText();
            if (outputDir.isEmpty()) {
                return header + 
                    " NO OUTPUT DIRECTORY SET\n\n" +
                    "Please set an output directory to locate the downloaded file.";
            }
            
            // Construct file path
            String fileName = entry.getFileName();
            // Remove .gz extension if present for extracted files
            if ("Extracted".equals(entry.getStatus()) && fileName.endsWith(".gz")) {
                fileName = fileName.substring(0, fileName.length() - 3);
            }
            
            // Try to find the file in the directory structure
            Path foundFilePath = findFileInDirectory(outputDir, fileName);
            
            if (foundFilePath != null && Files.exists(foundFilePath)) {
                // Get file size for display
                long actualSize = Files.size(foundFilePath);
//
                  String  preview="";
                
                // Load file content preview
                String content = fileDownloadService.getFilePreview(foundFilePath.toString(), 50);
                
                if (content != null && !content.trim().isEmpty()) {
                    preview += content;
                    
                    // Add footer
                    preview += "\n\n" + "-".repeat(100) + "\n";
                } else {
                    preview += " File is empty or contains no readable text content.";
                }
                
                return preview;
            } else {
                // File not found, provide detailed search information
                Path expectedPath = Paths.get(outputDir).resolve(fileName);
                return header + 
                    " FILE NOT FOUND ON DISK\n\n" +
                    String.format("Expected location: %s\n", expectedPath.toString()) +
                    String.format("Searched in directory: %s\n\n", outputDir) +
                    " The file may be in a subdirectory. Checking common locations...\n" +
                    getFileSearchInfo(outputDir, fileName) +
                    "\n\nThe file may have been moved or deleted. Try downloading again.";
            }
        } catch (Exception e) {
            return header + 
                " ERROR READING FILE\n\n" +
                String.format("Error: %s\n\n", e.getMessage()) +
                "Unable to read file content for preview.";
        }
    }
    
    /**
     * Find a file in the directory structure by searching recursively
     */
    private Path findFileInDirectory(String rootDir, String fileName) {
        try {
            Path rootPath = Paths.get(rootDir);
            if (!Files.exists(rootPath)) {
                return null;
            }
            
            // Use Files.walk to search recursively through the directory structure
            try (Stream<Path> pathStream = Files.walk(rootPath)) {
                return pathStream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(fileName))
                    .findFirst()
                    .orElse(null);
            }
        } catch (Exception e) {
            addLogMessage("Error searching for file " + fileName + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Get information about file search in directory structure
     */
    private String getFileSearchInfo(String rootDir, String fileName) {
        try {
            Path rootPath = Paths.get(rootDir);
            if (!Files.exists(rootPath)) {
                return " Root directory does not exist: " + rootDir;
            }
            
            StringBuilder searchInfo = new StringBuilder();
            List<Path> foundSimilar = new ArrayList<>();
            List<String> subdirectories = new ArrayList<>();
            
            // Search for similar files and subdirectories
            try (Stream<Path> pathStream = Files.walk(rootPath, 3)) { // Limit depth to avoid deep recursion
                pathStream.forEach(path -> {
                    if (Files.isDirectory(path) && !path.equals(rootPath)) {
                        subdirectories.add(rootPath.relativize(path).toString());
                    } else if (Files.isRegularFile(path)) {
                        String foundFileName = path.getFileName().toString();
                        if (foundFileName.toLowerCase().contains(fileName.toLowerCase().substring(0, Math.min(5, fileName.length())))) {
                            foundSimilar.add(rootPath.relativize(path));
                        }
                    }
                });
            }
            
            if (!subdirectories.isEmpty()) {
                searchInfo.append("\n Found subdirectories:\n");
                for (int i = 0; i < Math.min(5, subdirectories.size()); i++) {
                    searchInfo.append("  - ").append(subdirectories.get(i)).append("\n");
                }
                if (subdirectories.size() > 5) {
                    searchInfo.append("  ... and ").append(subdirectories.size() - 5).append(" more\n");
                }
            }
            
            if (!foundSimilar.isEmpty()) {
                searchInfo.append("\n Found similar files:\n");
                for (int i = 0; i < Math.min(3, foundSimilar.size()); i++) {
                    searchInfo.append("  - ").append(foundSimilar.get(i).toString()).append("\n");
                }
                if (foundSimilar.size() > 3) {
                    searchInfo.append("  ... and ").append(foundSimilar.size() - 3).append(" more\n");
                }
            }
            
            return searchInfo.toString();
            
        } catch (Exception e) {
            return " Error searching directory: " + e.getMessage();
        }
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
