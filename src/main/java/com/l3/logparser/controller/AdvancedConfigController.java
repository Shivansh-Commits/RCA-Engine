package com.l3.logparser.controller;

import com.l3.logparser.config.AdvancedParserConfig;
import com.l3.logparser.config.ApiPatternConfig;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Stage;
import javafx.util.converter.BooleanStringConverter;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for the Advanced Configuration Dialog
 */
public class AdvancedConfigController implements Initializable {

    // Pattern Management
    @FXML private TableView<PatternTableRow> patternsTable;
    @FXML private TableColumn<PatternTableRow, String> patternNameColumn;
    @FXML private TableColumn<PatternTableRow, String> patternTypeColumn;
    @FXML private TableColumn<PatternTableRow, String> patternValueColumn;
    @FXML private TableColumn<PatternTableRow, Boolean> patternEnabledColumn;
    @FXML private Button addPatternButton;
    @FXML private Button removePatternButton;
    @FXML private Button editPatternButton;

    // Segment Codes
    @FXML private TextField bgmPassengerField;
    @FXML private TextField bgmCrewField;
    @FXML private TextField locDepartureField;
    @FXML private TextField locArrivalField;
    @FXML private TextField dtmDepartureField;
    @FXML private TextField dtmArrivalField;
    @FXML private TextField tdtFlightPositionField;

    // Dialog controls
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private Button resetButton;

    private AdvancedParserConfig config;
    private ObservableList<PatternTableRow> patternData;
    private boolean saved = false;
    private Stage dialogStage;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupPatternsTable();
        this.config = new AdvancedParserConfig();
        loadConfigurationData();
    }

    public void setConfig(AdvancedParserConfig config) {
        this.config = config;
        loadConfigurationData();
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public boolean isSaved() {
        return saved;
    }

    public AdvancedParserConfig getConfig() {
        return config;
    }

    private void setupPatternsTable() {
        patternData = FXCollections.observableArrayList();
        patternsTable.setItems(patternData);

        // Configure columns
        patternNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        patternNameColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        patternNameColumn.setOnEditCommit(event -> {
            PatternTableRow row = event.getRowValue();
            row.setName(event.getNewValue());
        });

        patternTypeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        patternValueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));

        patternEnabledColumn.setCellValueFactory(new PropertyValueFactory<>("enabled"));
        patternEnabledColumn.setCellFactory(col -> {
            TableCell<PatternTableRow, Boolean> cell = new TableCell<PatternTableRow, Boolean>() {
                private final CheckBox checkBox = new CheckBox();

                {
                    checkBox.setOnAction(event -> {
                        PatternTableRow row = getTableRow().getItem();
                        if (row != null) {
                            row.setEnabled(checkBox.isSelected());
                        }
                    });
                }

                @Override
                protected void updateItem(Boolean item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        checkBox.setSelected(item != null && item);
                        setGraphic(checkBox);
                    }
                }
            };
            return cell;
        });

        // Enable table editing
        patternsTable.setEditable(true);
    }

    private void loadConfigurationData() {
        // Load patterns
        patternData.clear();
        List<ApiPatternConfig.MessagePattern> patterns = config.getApiConfig().getMessageStartPatterns();
        for (ApiPatternConfig.MessagePattern pattern : patterns) {
            patternData.add(new PatternTableRow(pattern));
        }

        // Load segment codes
        ApiPatternConfig.SegmentCodes codes = config.getApiConfig().getSegmentCodes();
        bgmPassengerField.setText(codes.getBgmPassengerCode());
        bgmCrewField.setText(codes.getBgmCrewCode());
        locDepartureField.setText(codes.getLocDepartureCode());
        locArrivalField.setText(codes.getLocArrivalCode());
        dtmDepartureField.setText(codes.getDtmDepartureCode());
        dtmArrivalField.setText(codes.getDtmArrivalCode());
        tdtFlightPositionField.setText(String.valueOf(codes.getTdtFlightPosition()));
    }

    @FXML
    private void onAddPattern() {
        PatternEditDialog dialog = new PatternEditDialog(null);
        dialog.showAndWait().ifPresent(pattern -> {
            patternData.add(new PatternTableRow(pattern));
        });
    }

    @FXML
    private void onRemovePattern() {
        PatternTableRow selected = patternsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            patternData.remove(selected);
        }
    }

    @FXML
    private void onEditPattern() {
        PatternTableRow selected = patternsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            PatternEditDialog dialog = new PatternEditDialog(selected.toMessagePattern());
            dialog.showAndWait().ifPresent(pattern -> {
                selected.updateFromPattern(pattern);
                patternsTable.refresh();
            });
        }
    }

    @FXML
    private void onSave() {
        if (saveConfiguration()) {
            saved = true;
            dialogStage.close();
        }
    }

    @FXML
    private void onCancel() {
        saved = false;
        dialogStage.close();
    }

    @FXML
    private void onReset() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Reset Configuration");
        alert.setHeaderText("Reset to Default Values");
        alert.setContentText("Are you sure you want to reset all configuration to default values?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                config = new AdvancedParserConfig();
                loadConfigurationData();
            }
        });
    }

    private boolean saveConfiguration() {
        try {
            // Save patterns
            List<ApiPatternConfig.MessagePattern> patterns = new ArrayList<>();
            for (PatternTableRow row : patternData) {
                patterns.add(row.toMessagePattern());
            }
            config.getApiConfig().setMessageStartPatterns(patterns);

            // Save segment codes
            ApiPatternConfig.SegmentCodes codes = config.getApiConfig().getSegmentCodes();
            codes.setBgmPassengerCode(bgmPassengerField.getText());
            codes.setBgmCrewCode(bgmCrewField.getText());
            codes.setLocDepartureCode(locDepartureField.getText());
            codes.setLocArrivalCode(locArrivalField.getText());
            codes.setDtmDepartureCode(dtmDepartureField.getText());
            codes.setDtmArrivalCode(dtmArrivalField.getText());

            try {
                int position = Integer.parseInt(tdtFlightPositionField.getText());
                codes.setTdtFlightPosition(position);
            } catch (NumberFormatException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Invalid Input");
                alert.setHeaderText("TDT Flight Position Error");
                alert.setContentText("TDT Flight Position must be a valid number.");
                alert.showAndWait();
                return false;
            }

            // Save to file
            boolean saveSuccess = config.saveToFile();
            if (!saveSuccess) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Save Error");
                alert.setHeaderText("Configuration Save Failed");
                alert.setContentText("Could not save configuration to file.");
                alert.showAndWait();
                return false;
            }

            return true;

        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Save Error");
            alert.setHeaderText("Unexpected Error");
            alert.setContentText("An error occurred while saving: " + e.getMessage());
            alert.showAndWait();
            return false;
        }
    }

    /**
     * Table row representation of a message pattern
     */
    public static class PatternTableRow {
        private SimpleStringProperty name;
        private SimpleStringProperty type;
        private SimpleStringProperty value;
        private boolean enabled;
        private List<ApiPatternConfig.MessagePattern.Condition> conditions;

        public PatternTableRow(ApiPatternConfig.MessagePattern pattern) {
            this.name = new SimpleStringProperty(pattern.getName());
            this.type = new SimpleStringProperty(pattern.getType());
            this.enabled = pattern.isEnabled();
            this.conditions = new ArrayList<>(pattern.getConditions());

            String displayValue;
            if ("multiple".equals(pattern.getType()) && pattern.getConditions() != null && !pattern.getConditions().isEmpty()) {
                displayValue = pattern.getConditions().size() + " condition(s)";
            } else {
                displayValue = pattern.getValue() != null ? pattern.getValue() : "";
            }
            this.value = new SimpleStringProperty(displayValue);
        }

        public void updateFromPattern(ApiPatternConfig.MessagePattern pattern) {
            this.name.set(pattern.getName());
            this.type.set(pattern.getType());
            this.enabled = pattern.isEnabled();
            this.conditions = new ArrayList<>(pattern.getConditions());

            String displayValue;
            if ("multiple".equals(pattern.getType()) && pattern.getConditions() != null && !pattern.getConditions().isEmpty()) {
                displayValue = pattern.getConditions().size() + " condition(s)";
            } else {
                displayValue = pattern.getValue() != null ? pattern.getValue() : "";
            }
            this.value.set(displayValue);
        }

        public ApiPatternConfig.MessagePattern toMessagePattern() {
            ApiPatternConfig.MessagePattern pattern = new ApiPatternConfig.MessagePattern(
                getName(), getType(), getValue(), isEnabled()
            );
            pattern.setConditions(new ArrayList<>(conditions));
            return pattern;
        }

        // Property getters for table binding
        public String getName() { return name.get(); }
        public void setName(String name) { this.name.set(name); }
        public SimpleStringProperty nameProperty() { return name; }

        public String getType() { return type.get(); }
        public void setType(String type) { this.type.set(type); }
        public SimpleStringProperty typeProperty() { return type; }

        public String getValue() { return value.get(); }
        public void setValue(String value) { this.value.set(value); }
        public SimpleStringProperty valueProperty() { return value; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public List<ApiPatternConfig.MessagePattern.Condition> getConditions() { return conditions; }
        public void setConditions(List<ApiPatternConfig.MessagePattern.Condition> conditions) { this.conditions = conditions; }
    }
}
