package com.l3.logextractor.controller;

import com.l3.logextractor.model.SearchTemplate;
import com.l3.logextractor.service.SearchTemplateService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

import java.net.URL;
import java.util.*;

/**
 * Controller for the Search Template customization dialog
 */
public class SearchTemplateController implements Initializable {

    @FXML private TableView<SearchTemplate> templatesTable;
    @FXML private TableColumn<SearchTemplate, String> nameColumn;
    @FXML private TableColumn<SearchTemplate, String> descriptionColumn;
    @FXML private TableColumn<SearchTemplate, String> logFilesCountColumn;
    @FXML private TableColumn<SearchTemplate, String> activeColumn;

    @FXML private ListView<String> logFilesListView;

    @FXML private Button createButton;
    @FXML private Button editButton;
    @FXML private Button duplicateButton;
    @FXML private Button deleteButton;
    @FXML private Button activateButton;
    @FXML private Button addLogFileButton;
    @FXML private Button removeLogFileButton;
    @FXML private Button closeButton;

    @FXML private Label statusLabel;
    @FXML private Label templateInfoLabel;

    private SearchTemplateService templateManager;
    private ObservableList<SearchTemplate> templates;
    private ObservableList<String> logFiles;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        templateManager = new SearchTemplateService();
        templates = FXCollections.observableArrayList();
        logFiles = FXCollections.observableArrayList();

        setupTableColumns();
        setupTableSelection();
        loadTemplates();

        logFilesListView.setItems(logFiles);

        // Initially disable edit/delete/activate buttons
        updateButtonStates();
    }

    private void setupTableColumns() {
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));

        logFilesCountColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(String.valueOf(cellData.getValue().getLogFiles().size())));

        activeColumn.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().isActive() ? "✔ Active" : ""));

        // Style the active column
        activeColumn.setCellFactory(column -> new TableCell<SearchTemplate, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                }
            }
        });
    }

    private void setupTableSelection() {
        templatesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                displayTemplateDetails(newSelection);
                updateButtonStates();
            } else {
                logFiles.clear();
                templateInfoLabel.setText("");
                updateButtonStates();
            }
        });
    }

    private void loadTemplates() {
        templates.clear();
        templates.addAll(templateManager.getAllTemplates());
        templatesTable.setItems(templates);

        // Select the active template if exists
        templateManager.getActiveTemplate().ifPresent(activeTemplate -> {
            templatesTable.getSelectionModel().select(activeTemplate);
        });
    }

    private void displayTemplateDetails(SearchTemplate template) {
        logFiles.clear();
        logFiles.addAll(template.getLogFiles());

        String info = String.format("Template: %s | Log Files: %d | Status: %s",
            template.getName(),
            template.getLogFiles().size(),
            template.isActive() ? "Active" : "Inactive");
        templateInfoLabel.setText(info);
    }

    private void updateButtonStates() {
        SearchTemplate selected = templatesTable.getSelectionModel().getSelectedItem();
        boolean hasSelection = selected != null;

        editButton.setDisable(!hasSelection);
        duplicateButton.setDisable(!hasSelection);
        deleteButton.setDisable(!hasSelection || templates.size() <= 1); // Can't delete if only one template
        activateButton.setDisable(!hasSelection || (hasSelection && selected.isActive()));

        removeLogFileButton.setDisable(!hasSelection || logFilesListView.getSelectionModel().getSelectedItem() == null);
    }

    @FXML
    private void onCreateTemplate() {
        Dialog<SearchTemplate> dialog = new Dialog<>();
        dialog.setTitle("Create New Search Template");
        dialog.setHeaderText("Define a new search template for log extraction");

        // Set up the dialog pane
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Create form fields
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField nameField = new TextField();
        nameField.setPromptText("Template Name");

        TextField descriptionField = new TextField();
        descriptionField.setPromptText("Description");

        TextArea logFilesArea = new TextArea();
        logFilesArea.setPromptText("Enter log file names (one per line)\nExample:\ndcs.log\nams.log\nforwarder.log");
        logFilesArea.setPrefRowCount(8);
        GridPane.setVgrow(logFilesArea, Priority.ALWAYS);

        grid.add(new Label("Template Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Description:"), 0, 1);
        grid.add(descriptionField, 1, 1);
        grid.add(new Label("Log Files:"), 0, 2);
        grid.add(logFilesArea, 1, 2);

        dialogPane.setContent(grid);

        // Request focus on name field by default
        javafx.application.Platform.runLater(nameField::requestFocus);

        // Convert result to SearchTemplate when OK is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                String name = nameField.getText().trim();
                String description = descriptionField.getText().trim();
                String logFilesText = logFilesArea.getText().trim();

                if (name.isEmpty()) {
                    showAlert("Validation Error", "Template name is required.");
                    return null;
                }

                List<String> logFilesList = new ArrayList<>();
                if (!logFilesText.isEmpty()) {
                    String[] lines = logFilesText.split("\\r?\\n");
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) {
                            logFilesList.add(trimmed);
                        }
                    }
                }

                if (logFilesList.isEmpty()) {
                    showAlert("Validation Error", "At least one log file is required.");
                    return null;
                }

                return new SearchTemplate(name, description, logFilesList);
            }
            return null;
        });

        Optional<SearchTemplate> result = dialog.showAndWait();
        result.ifPresent(template -> {
            templateManager.createTemplate(template);
            loadTemplates();
            statusLabel.setText("Template '" + template.getName() + "' created successfully");
        });
    }

    @FXML
    private void onEditTemplate() {
        SearchTemplate selected = templatesTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Dialog<SearchTemplate> dialog = new Dialog<>();
        dialog.setTitle("Edit Search Template");
        dialog.setHeaderText("Modify template: " + selected.getName());

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField nameField = new TextField(selected.getName());
        TextField descriptionField = new TextField(selected.getDescription());

        TextArea logFilesArea = new TextArea();
        logFilesArea.setText(String.join("\n", selected.getLogFiles()));
        logFilesArea.setPrefRowCount(8);
        GridPane.setVgrow(logFilesArea, Priority.ALWAYS);

        grid.add(new Label("Template Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Description:"), 0, 1);
        grid.add(descriptionField, 1, 1);
        grid.add(new Label("Log Files:"), 0, 2);
        grid.add(logFilesArea, 1, 2);

        dialogPane.setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                String name = nameField.getText().trim();
                String description = descriptionField.getText().trim();
                String logFilesText = logFilesArea.getText().trim();

                if (name.isEmpty()) {
                    showAlert("Validation Error", "Template name is required.");
                    return null;
                }

                List<String> logFilesList = new ArrayList<>();
                if (!logFilesText.isEmpty()) {
                    String[] lines = logFilesText.split("\\r?\\n");
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) {
                            logFilesList.add(trimmed);
                        }
                    }
                }

                if (logFilesList.isEmpty()) {
                    showAlert("Validation Error", "At least one log file is required.");
                    return null;
                }

                SearchTemplate updated = new SearchTemplate(name, description, logFilesList);
                updated.setId(selected.getId());
                updated.setActive(selected.isActive());
                updated.setCreatedDate(selected.getCreatedDate());

                return updated;
            }
            return null;
        });

        Optional<SearchTemplate> result = dialog.showAndWait();
        result.ifPresent(template -> {
            templateManager.updateTemplate(template);
            loadTemplates();
            templatesTable.getSelectionModel().select(template);
            statusLabel.setText("Template '" + template.getName() + "' updated successfully");
        });
    }

    @FXML
    private void onDuplicateTemplate() {
        SearchTemplate selected = templatesTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        SearchTemplate duplicate = selected.copy();
        templateManager.createTemplate(duplicate);
        loadTemplates();
        templatesTable.getSelectionModel().select(duplicate);
        statusLabel.setText("Template duplicated as '" + duplicate.getName() + "'");
    }

    @FXML
    private void onDeleteTemplate() {
        SearchTemplate selected = templatesTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        if (templates.size() <= 1) {
            showAlert("Cannot Delete", "You must have at least one search template.");
            return;
        }

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Confirm Deletion");
        confirmDialog.setHeaderText("Delete Template");
        confirmDialog.setContentText("Are you sure you want to delete the template '" + selected.getName() + "'?");

        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            templateManager.deleteTemplate(selected.getId());
            loadTemplates();
            statusLabel.setText("Template '" + selected.getName() + "' deleted");
        }
    }

    @FXML
    private void onActivateTemplate() {
        SearchTemplate selected = templatesTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        templateManager.activateTemplate(selected.getId());
        loadTemplates();
        templatesTable.getSelectionModel().select(selected);
        statusLabel.setText("Template '" + selected.getName() + "' activated");
    }

    @FXML
    private void onAddLogFile() {
        SearchTemplate selected = templatesTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Log File");
        dialog.setHeaderText("Add a new log file to the template");
        dialog.setContentText("Log file name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(logFileName -> {
            String trimmed = logFileName.trim();
            if (!trimmed.isEmpty() && !selected.getLogFiles().contains(trimmed)) {
                selected.getLogFiles().add(trimmed);
                templateManager.updateTemplate(selected);
                displayTemplateDetails(selected);
                statusLabel.setText("Log file '" + trimmed + "' added");
            }
        });
    }

    @FXML
    private void onRemoveLogFile() {
        SearchTemplate selected = templatesTable.getSelectionModel().getSelectedItem();
        String selectedLogFile = logFilesListView.getSelectionModel().getSelectedItem();

        if (selected == null || selectedLogFile == null) return;

        if (selected.getLogFiles().size() <= 1) {
            showAlert("Cannot Remove", "A template must have at least one log file.");
            return;
        }

        selected.getLogFiles().remove(selectedLogFile);
        templateManager.updateTemplate(selected);
        displayTemplateDetails(selected);
        statusLabel.setText("Log file '" + selectedLogFile + "' removed");
    }

    @FXML
    private void onClose() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Get the template manager instance
     */
    public SearchTemplateService getTemplateManager() {
        return templateManager;
    }
}

