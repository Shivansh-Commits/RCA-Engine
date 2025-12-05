package com.l3.logparser.controller;

import com.l3.logparser.config.ApiPatternConfig;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dialog for editing individual message patterns
 */
public class PatternEditDialog extends Dialog<ApiPatternConfig.MessagePattern> {

    private TextField nameField;
    private ComboBox<String> typeComboBox;
    private TextField valueField;
    private CheckBox enabledCheckBox;

    // For multiple conditions
    private TableView<ConditionRow> conditionsTable;
    private ObservableList<ConditionRow> conditionData;
    private VBox conditionsContainer;

    public PatternEditDialog(ApiPatternConfig.MessagePattern existingPattern) {
        setTitle("Edit Message Pattern");
        setHeaderText("Configure message start pattern");

        // Set the button types
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // Create the content
        VBox content = createContent(existingPattern);
        getDialogPane().setContent(content);

        // Set minimum size
        getDialogPane().setMinWidth(600);
        getDialogPane().setMinHeight(500);

        // Convert result
        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return createPatternFromInput();
            }
            return null;
        });

        // Enable/disable save button
        Button saveButton = (Button) getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (!isInputValid()) {
                event.consume();
                showValidationError();
            }
        });
    }

    private VBox createContent(ApiPatternConfig.MessagePattern existingPattern) {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        // Basic pattern info
        content.getChildren().add(new Label("Pattern Name:"));
        nameField = new TextField();
        nameField.setPromptText("Enter pattern name");
        content.getChildren().add(nameField);

        content.getChildren().add(new Label("Pattern Type:"));
        typeComboBox = new ComboBox<>(FXCollections.observableArrayList("contains", "startsWith", "multiple"));
        typeComboBox.setValue("contains");
        content.getChildren().add(typeComboBox);

        content.getChildren().add(new Label("Pattern Value:"));
        valueField = new TextField();
        valueField.setPromptText("Enter pattern value (for contains/startsWith)");
        content.getChildren().add(valueField);

        enabledCheckBox = new CheckBox("Enabled");
        enabledCheckBox.setSelected(true);
        content.getChildren().add(enabledCheckBox);

        // Conditions section (for multiple type)
        conditionsContainer = new VBox(5);
        conditionsContainer.getChildren().add(new Label("Conditions (for multiple pattern type):"));

        createConditionsTable();
        conditionsContainer.getChildren().add(conditionsTable);

        HBox conditionButtons = new HBox(5);
        Button addConditionBtn = new Button("Add Condition");
        Button removeConditionBtn = new Button("Remove Selected");
        addConditionBtn.setOnAction(e -> addCondition());
        removeConditionBtn.setOnAction(e -> removeSelectedCondition());
        conditionButtons.getChildren().addAll(addConditionBtn, removeConditionBtn);
        conditionsContainer.getChildren().add(conditionButtons);

        content.getChildren().add(conditionsContainer);

        // Type change listener
        typeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isMultiple = "multiple".equals(newVal);
            valueField.setDisable(isMultiple);
            conditionsContainer.setVisible(isMultiple);
            conditionsContainer.setManaged(isMultiple);
        });

        // Load existing pattern if provided
        if (existingPattern != null) {
            loadExistingPattern(existingPattern);
        }

        // Initialize visibility
        String initialType = typeComboBox.getValue();
        boolean isMultiple = "multiple".equals(initialType);
        valueField.setDisable(isMultiple);
        conditionsContainer.setVisible(isMultiple);
        conditionsContainer.setManaged(isMultiple);

        return content;
    }

    private void createConditionsTable() {
        conditionData = FXCollections.observableArrayList();
        conditionsTable = new TableView<>(conditionData);
        conditionsTable.setEditable(true);
        conditionsTable.setPrefHeight(200);

        TableColumn<ConditionRow, String> typeColumn = new TableColumn<>("Type");
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeColumn.setCellFactory(ComboBoxTableCell.forTableColumn("contains", "startsWith"));
        typeColumn.setOnEditCommit(event -> event.getRowValue().setType(event.getNewValue()));
        typeColumn.setPrefWidth(120);

        TableColumn<ConditionRow, String> valueColumn = new TableColumn<>("Value");
        valueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));
        valueColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        valueColumn.setOnEditCommit(event -> event.getRowValue().setValue(event.getNewValue()));
        valueColumn.setPrefWidth(400);

        conditionsTable.getColumns().addAll(typeColumn, valueColumn);
    }

    private void addCondition() {
        conditionData.add(new ConditionRow("contains", ""));
    }

    private void removeSelectedCondition() {
        ConditionRow selected = conditionsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            conditionData.remove(selected);
        }
    }

    private void loadExistingPattern(ApiPatternConfig.MessagePattern pattern) {
        nameField.setText(pattern.getName());
        typeComboBox.setValue(pattern.getType());
        valueField.setText(pattern.getValue());
        enabledCheckBox.setSelected(pattern.isEnabled());

        // Load conditions
        conditionData.clear();
        if (pattern.getConditions() != null) {
            for (ApiPatternConfig.MessagePattern.Condition condition : pattern.getConditions()) {
                conditionData.add(new ConditionRow(condition.getType(), condition.getValue()));
            }
        }
    }

    private boolean isInputValid() {
        if (nameField.getText().trim().isEmpty()) {
            return false;
        }

        String type = typeComboBox.getValue();
        if ("multiple".equals(type)) {
            return !conditionData.isEmpty() && conditionData.stream().allMatch(c -> !c.getValue().trim().isEmpty());
        } else {
            return !valueField.getText().trim().isEmpty();
        }
    }

    private void showValidationError() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Validation Error");
        alert.setHeaderText("Invalid Input");
        alert.setContentText("Please ensure all required fields are filled:\n" +
                            "- Pattern name is required\n" +
                            "- Pattern value is required (for contains/startsWith)\n" +
                            "- At least one condition with value is required (for multiple)");
        alert.showAndWait();
    }

    private ApiPatternConfig.MessagePattern createPatternFromInput() {
        String name = nameField.getText().trim();
        String type = typeComboBox.getValue();
        String value = valueField.getText().trim();
        boolean enabled = enabledCheckBox.isSelected();

        ApiPatternConfig.MessagePattern pattern = new ApiPatternConfig.MessagePattern(name, type, value, enabled);

        // Add conditions for multiple type
        if ("multiple".equals(type)) {
            List<ApiPatternConfig.MessagePattern.Condition> conditions = new ArrayList<>();
            for (ConditionRow row : conditionData) {
                if (!row.getValue().trim().isEmpty()) {
                    conditions.add(new ApiPatternConfig.MessagePattern.Condition(row.getType(), row.getValue()));
                }
            }
            pattern.setConditions(conditions);
        }

        return pattern;
    }

    /**
     * Helper class for condition table rows
     */
    public static class ConditionRow {
        private String type;
        private String value;

        public ConditionRow(String type, String value) {
            this.type = type;
            this.value = value;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
