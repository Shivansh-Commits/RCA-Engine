package com.l3.apipnrengine.controller;

import com.l3.apipnrengine.api.model.Passenger;
import com.l3.apipnrengine.api.utils.FileParser;
import com.l3.apipnrengine.api.utils.ParseResult;
import com.l3.apipnrengine.pnr.PnrgovProcessor;
import com.l3.apipnrengine.common.reporting.ExcelReportGenerator;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.*;

public class MainController {

    @FXML private Button chooseFolderBtn;
    @FXML private Button processBtn;
    @FXML private Button clearBtn;
    @FXML private Button exportBtn; // New Excel export button
    @FXML private Label totalInputPassengersValue;
    @FXML private Label totalUniqueInputPassengersValue;
    @FXML private Label totalOutputPassengersValue;
    @FXML private Label droppedPassengersValue;
    @FXML private Label duplicatePassengersValue;
    @FXML private Label newPnrsValue;
    
    @FXML private Label totalInputPassengersLabel;
    @FXML private Label totalUniqueInputPassengersLabel;
    @FXML private Label totalOutputPassengersLabel;
    @FXML private Label droppedPassengersLabel;
    @FXML private Label duplicatePassengersLabel;
    @FXML private Label newPnrsLabel;
    @FXML private Label flightNumber;
    @FXML private Label departureDate;
    @FXML private Label departurePort;
    @FXML private Label arrivalPort;
    @FXML private ComboBox<String> dataTypeComboBox;
    @FXML private ComboBox<String> recordTypeComboBox;
    @FXML private ListView<String> filesProcessedList;
    @FXML private ListView<String> warningsList;

    @FXML private TableView<TableRow> inputPaxTable;
    @FXML private TableColumn<TableRow, Number> inputPaxColNo;
    @FXML private TableColumn<TableRow, String> inputPaxColName;
    @FXML private TableColumn<TableRow, String> inputPaxColDTM;
    @FXML private TableColumn<TableRow, String> inputPaxColDOC;
    @FXML private TableColumn<TableRow, String> inputPaxColRecordedKey;
    @FXML private TableColumn<TableRow, String> inputPaxColSource;
    @FXML private TableColumn<TableRow, Number> inputPaxColCount;

    @FXML private TableView<TableRow> outputPaxTable;
    @FXML private TableColumn<TableRow, Number> outputPaxColNo;
    @FXML private TableColumn<TableRow, String> outputPaxColName;
    @FXML private TableColumn<TableRow, String> outputPaxColDTM;
    @FXML private TableColumn<TableRow, String> outputPaxColDOC;
    @FXML private TableColumn<TableRow, String> outputPaxColRecordedKey;
    @FXML private TableColumn<TableRow, String> outputPaxColSource;
    @FXML private TableColumn<TableRow, Number> outputPaxColCount;

    @FXML private TableView<TableRow> droppedPassengersTable;
    @FXML private TableColumn<TableRow, Number> droppedColNo;
    @FXML private TableColumn<TableRow, String> droppedColName;
    @FXML private TableColumn<TableRow, String> droppedColDTM;
    @FXML private TableColumn<TableRow, String> droppedColDOC;
    @FXML private TableColumn<TableRow, String> droppedColRecordedKey;
    @FXML private TableColumn<TableRow, String> droppedColSource;
    @FXML private TableColumn<TableRow, Number> droppedColCount;

    @FXML private TableView<TableRow> duplicatePassengersTable;
    @FXML private TableColumn<TableRow, Number> duplicateColNo;
    @FXML private TableColumn<TableRow, String> duplicateColName;
    @FXML private TableColumn<TableRow, String> duplicateColDTM;
    @FXML private TableColumn<TableRow, String> duplicateColDOC;
    @FXML private TableColumn<TableRow, String> duplicateColRecordedKey;
    @FXML private TableColumn<TableRow, String> duplicateColSource;
    @FXML private TableColumn<TableRow, Number> duplicateColCount;

    @FXML private TextField inputSearchField;
    @FXML private TextField outputSearchField;

    private File selectedFolder;

    private ObservableList<TableRow> allInputRows = FXCollections.observableArrayList();
    private ObservableList<TableRow> allOutputRows = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        inputPaxColNo.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getNo()));
        inputPaxColName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        inputPaxColDTM.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDtm()));
        inputPaxColDOC.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDoc()));
        inputPaxColRecordedKey.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRecordedKey()));
        inputPaxColSource.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSource()));
        inputPaxColCount.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getCount()));

        outputPaxColNo.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getNo()));
        outputPaxColName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        outputPaxColDTM.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDtm()));
        outputPaxColDOC.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDoc()));
        outputPaxColRecordedKey.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRecordedKey()));
        outputPaxColSource.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSource()));
        outputPaxColCount.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getCount()));

        droppedColNo.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getNo()));
        droppedColName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        droppedColDTM.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDtm()));
        droppedColDOC.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDoc()));
        droppedColRecordedKey.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRecordedKey()));
        droppedColSource.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSource()));
        droppedColCount.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getCount()));

        duplicateColNo.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getNo()));
        duplicateColName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        duplicateColDTM.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDtm()));
        duplicateColDOC.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDoc()));
        duplicateColRecordedKey.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRecordedKey()));
        duplicateColSource.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSource()));
        duplicateColCount.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getCount()));

        dataTypeComboBox.getSelectionModel().select("API"); // default
        recordTypeComboBox.getSelectionModel().select("PAX");
        
        // Add listener to disable PAX/CREW when PNR is selected
        dataTypeComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if ("PNR".equals(newVal)) {
                recordTypeComboBox.setDisable(true);
                recordTypeComboBox.getSelectionModel().select("PAX"); // Default to PAX when disabled
            } else {
                recordTypeComboBox.setDisable(false);
            }
        });
        
        // Initialize NEW PNRs section as hidden (only show in PNR mode)
        newPnrsLabel.setVisible(false);
        newPnrsValue.setVisible(false);
        
        inputPaxTable.setItems(FXCollections.observableArrayList());

        processBtn.setOnAction(e -> onProcess());
        chooseFolderBtn.setOnAction(e -> onChooseFolder());
        clearBtn.setOnAction(e -> onClear());
        exportBtn.setOnAction(e -> onExport()); // Export button action

        inputSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterInputTable(newVal);
        });
        outputSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterOutputTable(newVal);
        });
    }

    private void onChooseFolder() {
        onClear();
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select folder with input/output files");
        Stage stage = (Stage) chooseFolderBtn.getScene().getWindow();
        File dir = chooser.showDialog(stage);
        if (dir != null && dir.isDirectory()) {
            selectedFolder = dir;
        }
    }

    private void onClear() {
        inputPaxTable.getItems().clear();
        outputPaxTable.getItems().clear();
        droppedPassengersTable.getItems().clear();
        duplicatePassengersTable.getItems().clear();
        totalOutputPassengersValue.setText("———");
        totalInputPassengersValue.setText("———");
        totalUniqueInputPassengersValue.setText("———");
        droppedPassengersValue.setText("———");
        duplicatePassengersValue.setText("———");
        newPnrsValue.setText("———");
        arrivalPort.setText("———");
        departurePort.setText("———");
        departureDate.setText("———");
        flightNumber.setText("———");
        warningsList.getItems().clear();
        selectedFolder = null;
        inputSearchField.setText("");
        outputSearchField.setText("");
    }

    private void processAPI(String recordType,String dataType){
        // Update table headers for API mode
        updateTableHeadersForAPIMode();
        
        // Update count labels for API mode
        updateCountLabelsForAPIMode();
        
        FileParser parser = new FileParser(recordType,dataType);  // pass to parser

        // read folder
        ParseResult result;
        try {
            result = parser.parseFolder(selectedFolder);
        } catch (Exception ex) {
            ex.printStackTrace();
            showAlert("Error", "Failed to parse files: " + ex.getMessage());
            return;
        }


        //Setting flight details
        flightNumber.setText(result.getFlightNumber());
        departureDate.setText(result.getDepartureDate());
        departurePort.setText(result.getDepartureAirport());
        arrivalPort.setText(result.getArrivalAirport());


        // Populating Input Pax Table (globalInputPassengers values)
        List<TableRow> rows = new ArrayList<>();
        int i = 1;
        for (Passenger p : result.getGlobalInputPassengers().values()) {
            rows.add(new TableRow(i++, p.getName(), p.getDtm(), p.getDocTypeWithParens(), p.getRecordedKey(), p.getSources(), p.getCount()));
        }

        inputPaxTable.setItems(FXCollections.observableArrayList(rows));
        allInputRows.setAll(rows);

        // Populating Output Pax Table (outputPassengers values)
        List<TableRow> outputRows = new ArrayList<>();
        int j = 1;
        for (Passenger p : result.getOutputPassengers().values()) {
            outputRows.add(new TableRow(j++, p.getName(), p.getDtm(), p.getDocTypeWithParens(), p.getRecordedKey(), p.getSources(), p.getCount()));
        }
        outputPaxTable.setItems(FXCollections.observableArrayList(outputRows));
        allOutputRows.setAll(outputRows);

        // Populating Results Summary
        int totalInputAll = result.getTotalInputAll();
        int totalUnique = result.getGlobalInputPassengers().size();
        int totalOutput = result.getTotalOutput();
        int dropped = result.getDropped().size();
        int dup = result.getDuplicatePassengers().size();

        totalInputPassengersValue.setText(String.valueOf(totalInputAll));
        totalUniqueInputPassengersValue.setText(String.valueOf(totalUnique));
        totalOutputPassengersValue.setText(String.valueOf(totalOutput));
        droppedPassengersValue.setText(String.valueOf(dropped));
        duplicatePassengersValue.setText(String.valueOf(dup));



        //Processed files List
        filesProcessedList.setItems(FXCollections.observableArrayList(result.getProcessedFiles()));

        // Warnings List
        ObservableList<String> warnings = FXCollections.observableArrayList();
        warnings.addAll(result.getAllInvalidNads());
        warnings.addAll(result.getAllInvalidDocs());
        warnings.addAll(result.getAllMissingSegments());
        warningsList.setItems(warnings);

        // Show dropped pax List
        if (!result.getDropped().isEmpty()) {
            List<TableRow> droppedRows = new ArrayList<>();
            int k = 1;
            for (Passenger p : result.getDropped()) {
                droppedRows.add(new TableRow(k++, p.getName(), p.getDtm(), p.getDocTypeWithParens(), p.getRecordedKey(), p.getSources(), p.getCount()));
            }
            droppedPassengersTable.setItems(FXCollections.observableArrayList(droppedRows));
        }

        // Show duplicate pax List
        if (!result.getDuplicatePassengers().isEmpty()) {
            List<TableRow> duplicateRows = new ArrayList<>();
            int l = 1;
            for (Passenger p : result.getDuplicatePassengers().values()) {
                duplicateRows.add(new TableRow(l++, p.getName(), p.getDtm(), p.getDocTypeWithParens(), p.getRecordedKey(), p.getSources(), p.getCount()));
            }
            duplicatePassengersTable.setItems(FXCollections.observableArrayList(duplicateRows));
        }
    }

    private void processPNR(String recordType, String dataType) {
        try {
            // Use PnrgovProcessor for PNR comparison
            PnrgovProcessor processor = new PnrgovProcessor();
            PnrgovProcessor.PnrgovResult result = processor.processFolder(selectedFolder);
            
            updateUIForPNRMode(result);
            
        } catch (Exception ex) {
            ex.printStackTrace();
            showAlert("Error", "Failed to process PNR files: " + ex.getMessage());
        }
    }
    
    private void updateUIForPNRMode(PnrgovProcessor.PnrgovResult result) {
        // Update table headers for PNR mode
        updateTableHeadersForPNRMode();
        
        // Update count labels for PNR mode
        updateCountLabelsForPNRMode();
        
        // Set flight details
        flightNumber.setText(result.getFlightNumber());
        departureDate.setText(result.getDepartureDate());
        departurePort.setText(result.getDepartureAirport());
        arrivalPort.setText(result.getArrivalAirport());
        
        // Update count displays
        totalInputPassengersValue.setText(String.valueOf(result.getTotalInputPnrs()));
        totalUniqueInputPassengersValue.setText(String.valueOf(result.getTotalInputPnrs())); // PNRs are unique
        totalOutputPassengersValue.setText(String.valueOf(result.getTotalOutputPnrs()));
        droppedPassengersValue.setText(String.valueOf(result.getDroppedCount()));
        duplicatePassengersValue.setText(String.valueOf(result.getDuplicateCount()));
        newPnrsValue.setText(String.valueOf(result.getNewPnrCount()));
        
        // Update processed files list
        filesProcessedList.setItems(FXCollections.observableArrayList(result.getProcessedFiles()));
        
        // Update warnings list
        ObservableList<String> warnings = FXCollections.observableArrayList();
        warnings.addAll(result.getAllInvalidNads());
        warnings.addAll(result.getAllInvalidDocs());
        warnings.addAll(result.getAllMissingSegments());
        warningsList.setItems(warnings);
        
        // Populate input PNR table using compatible TableRow structure
        if (result.getInputPassengers() != null && !result.getInputPassengers().isEmpty()) {
            List<TableRow> inputRows = new ArrayList<>();
            int i = 1;
            for (PnrgovProcessor.PnrgovTableRow pnrRow : result.getInputPassengers()) {
                // Map to TableRow: No, Name, Locator(DTM), Doc(""), RecordedKey(""), Source, Count
                inputRows.add(new TableRow(i++, pnrRow.getName(), pnrRow.getPnrRloc(), "", 
                    "", pnrRow.getSource(), pnrRow.getCount()));
            }
            inputPaxTable.setItems(FXCollections.observableArrayList(inputRows));
            allInputRows.setAll(inputRows);
        }
        
        // Populate output PNR table using compatible TableRow structure
        if (result.getOutputPassengers() != null && !result.getOutputPassengers().isEmpty()) {
            List<TableRow> outputRows = new ArrayList<>();
            int j = 1;
            for (PnrgovProcessor.PnrgovTableRow pnrRow : result.getOutputPassengers()) {
                outputRows.add(new TableRow(j++, pnrRow.getName(), pnrRow.getPnrRloc(), "", 
                    "", pnrRow.getSource(), pnrRow.getCount()));
            }
            outputPaxTable.setItems(FXCollections.observableArrayList(outputRows));
            allOutputRows.setAll(outputRows);
        }
        
        // Show dropped PNRs using compatible TableRow structure
        if (result.getDroppedPassengers() != null && !result.getDroppedPassengers().isEmpty()) {
            List<TableRow> droppedRows = new ArrayList<>();
            int k = 1;
            for (PnrgovProcessor.PnrgovTableRow pnrRow : result.getDroppedPassengers()) {
                droppedRows.add(new TableRow(k++, pnrRow.getName(), pnrRow.getPnrRloc(), "", 
                    "", pnrRow.getSource(), pnrRow.getCount()));
            }
            droppedPassengersTable.setItems(FXCollections.observableArrayList(droppedRows));
        }
        
        // Show duplicate PNRs using compatible TableRow structure
        if (result.getDuplicatePassengers() != null && !result.getDuplicatePassengers().isEmpty()) {
            List<TableRow> duplicateRows = new ArrayList<>();
            int l = 1;
            for (PnrgovProcessor.PnrgovTableRow pnrRow : result.getDuplicatePassengers()) {
                duplicateRows.add(new TableRow(l++, pnrRow.getName(), pnrRow.getPnrRloc(), "", 
                    "", pnrRow.getSource(), pnrRow.getCount()));
            }
            duplicatePassengersTable.setItems(FXCollections.observableArrayList(duplicateRows));
        }
    }

    private void onProcess() {

        if (selectedFolder == null) {
            showAlert("No folder chosen", "Choose a folder containing input and output files first.");
            return;
        }

        String dataType = dataTypeComboBox.getValue(); // "API" or "PNR"
        String recordType = recordTypeComboBox.getValue();  // "pax" or "crew"

        if(dataType.equals("API"))
        {
            processAPI(recordType,dataType);
        }

        if(dataType.equals("PNR"))
        {
            processPNR(recordType,dataType);
        }

    }

    private void onExport() {
        // Check if there's any data to export
        if (inputPaxTable.getItems().isEmpty() &&
            outputPaxTable.getItems().isEmpty() &&
            droppedPassengersTable.getItems().isEmpty() &&
            duplicatePassengersTable.getItems().isEmpty()) {
            showAlert("No Data", "Please process data first before exporting.");
            return;
        }

        // Show file chooser to select export location
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Excel Report");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        // Set default filename with timestamp
        String defaultName = "Data_Quality_Report_" +
            new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".xlsx";
        fileChooser.setInitialFileName(defaultName);

        Stage stage = (Stage) exportBtn.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            try {
                // Get flight details
                String flightNo = flightNumber.getText() != null ? flightNumber.getText() : "";
                String depDate = departureDate.getText() != null ? departureDate.getText() : "";
                String depPort = departurePort.getText() != null ? departurePort.getText() : "";
                String arrPort = arrivalPort.getText() != null ? arrivalPort.getText() : "";

                // Get summary counts
                int totalInput = parseIntFromLabel(totalInputPassengersValue.getText());
                int totalOutput = parseIntFromLabel(totalOutputPassengersValue.getText());
                int dropped = parseIntFromLabel(droppedPassengersValue.getText());
                int duplicates = parseIntFromLabel(duplicatePassengersValue.getText());

                // Collect warnings from warningsList
                List<String> warnings = new ArrayList<>();
                if (warningsList.getItems() != null && !warningsList.getItems().isEmpty()) {
                    warnings.addAll(warningsList.getItems());
                }

                // Generate comprehensive Excel report with warnings
                ExcelReportGenerator.generateReport(
                    file.getAbsolutePath(),
                    inputPaxTable,
                    outputPaxTable,
                    droppedPassengersTable,
                    duplicatePassengersTable,
                    flightNo,
                    depDate,
                    depPort,
                    arrPort,
                    totalInput,
                    totalOutput,
                    dropped,
                    duplicates,
                    warnings
                );

                String successMessage = "Data quality report exported successfully to:\n" + file.getAbsolutePath();
                if (!warnings.isEmpty()) {
                    successMessage += "\n\nWarnings sheet included with " + warnings.size() + " warning(s).";
                }
                showAlert("Export Successful", successMessage);

            } catch (Exception e) {
                e.printStackTrace();
                showAlert("Export Error", "Failed to export report: " + e.getMessage());
            }
        }
    }

    private int parseIntFromLabel(String text) {
        if (text == null || text.equals("———") || text.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(title);
        a.setContentText(msg);
        a.showAndWait();
    }
    
    private void updateTableHeadersForPNRMode() {
        // Update headers for PNR mode: No | Passenger Name | Locator | Source | Count
        inputPaxColName.setText("Passenger Name");
        inputPaxColDTM.setText("Locator");
        inputPaxColDOC.setVisible(false); // Hide DOC column for PNR
        inputPaxColRecordedKey.setVisible(false); // Hide RecordedKey column for PNR
        
        outputPaxColName.setText("Passenger Name");
        outputPaxColDTM.setText("Locator");
        outputPaxColDOC.setVisible(false);
        outputPaxColRecordedKey.setVisible(false);
        
        droppedColName.setText("Passenger Name");
        droppedColDTM.setText("Locator");
        droppedColDOC.setVisible(false);
        droppedColRecordedKey.setVisible(false);
        
        duplicateColName.setText("Passenger Name");
        duplicateColDTM.setText("Locator");
        duplicateColDOC.setVisible(false);
        duplicateColRecordedKey.setVisible(false);
    }
    
    private void updateTableHeadersForAPIMode() {
        // Restore headers for API mode: No | NAD (Passenger Name) | DTM | DOC | RecordedKey | Source | Count
        inputPaxColName.setText("NAD (Passenger Name)");
        inputPaxColDTM.setText("DTM");
        inputPaxColDOC.setVisible(true);
        inputPaxColRecordedKey.setVisible(true);
        
        outputPaxColName.setText("NAD (Passenger Name)");
        outputPaxColDTM.setText("DTM");
        outputPaxColDOC.setVisible(true);
        outputPaxColRecordedKey.setVisible(true);
        
        droppedColName.setText("NAD (Passenger Name)");
        droppedColDTM.setText("DTM");
        droppedColDOC.setVisible(true);
        droppedColRecordedKey.setVisible(true);
        
        duplicateColName.setText("NAD (Passenger Name)");
        duplicateColDTM.setText("DTM");
        duplicateColDOC.setVisible(true);
        duplicateColRecordedKey.setVisible(true);
    }
    
    private void updateCountLabelsForPNRMode() {
        // Update count labels for PNR mode
        totalInputPassengersLabel.setText("Total Input PNRs");
        totalUniqueInputPassengersLabel.setText("Total Unique PNRs");
        totalOutputPassengersLabel.setText("Total Output PNRs");
        droppedPassengersLabel.setText("Dropped PNRs");
        duplicatePassengersLabel.setText("Duplicate PNRs");
        newPnrsLabel.setText("New PNRs");
        
        // Show NEW PNRs section for PNR mode
        newPnrsLabel.setVisible(true);
        newPnrsValue.setVisible(true);
    }
    
    private void updateCountLabelsForAPIMode() {
        // Restore count labels for API mode
        totalInputPassengersLabel.setText("Total Input Passengers");
        totalUniqueInputPassengersLabel.setText("Total Unique Input Passengers");
        totalOutputPassengersLabel.setText("Total Output Passengers");
        droppedPassengersLabel.setText("Dropped Passengers");
        duplicatePassengersLabel.setText("Duplicate Passengers");
        
        // Hide NEW PNRs section for API mode
        newPnrsLabel.setVisible(false);
        newPnrsValue.setVisible(false);
    }

    // TableRow used only by the UI
    public static class TableRow {
        private final int no;
        private final String name;
        private final String dtm;
        private final String doc;
        private final String recordedKey;
        private final String source;
        private final int count;

        public TableRow(int no, String name, String dtm, String doc, String recordedKey, String source, int count) {
            this.no = no;
            this.name = name;
            this.dtm = dtm;
            this.doc = doc;
            this.recordedKey = recordedKey;
            this.source = source;
            this.count = count;
        }

        public int getNo() { return no; }
        public String getName() { return name; }
        public String getDtm() { return dtm; }
        public String getDoc() { return doc; }
        public String getRecordedKey() { return recordedKey; }
        public String getSource() { return source; }
        public int getCount() { return count; }

        @Override
        public String toString() {
            return (name + " " + dtm + " " + doc + " " + recordedKey + " " + source + " " + count);
        }
    }
    
    // PNR-specific TableRow for better display structure
    public static class PnrTableRow {
        private final int no;
        private final String passengerName;
        private final String locator;
        private final String source;
        private final int count;

        public PnrTableRow(int no, String passengerName, String locator, String source, int count) {
            this.no = no;
            this.passengerName = passengerName != null ? passengerName : "";
            this.locator = locator != null ? locator : "";
            this.source = source != null ? source : "";
            this.count = count;
        }

        public int getNo() { return no; }
        public String getPassengerName() { return passengerName; }
        public String getLocator() { return locator; }
        public String getSource() { return source; }
        public int getCount() { return count; }
        
        // For compatibility with existing TableView structure
        public String getName() { return passengerName; }
        public String getDtm() { return locator; }
        public String getDoc() { return ""; }
        public String getRecordedKey() { return ""; }
    }

    private void filterInputTable(String filter) {
        if (filter == null || filter.isEmpty()) {
            inputPaxTable.setItems(allInputRows);
            return;
        }
        String lower = filter.toLowerCase();
        ObservableList<TableRow> filtered = allInputRows.filtered(row ->
            row.toString().toLowerCase().contains(lower)
        );
        inputPaxTable.setItems(filtered);
    }

    private void filterOutputTable(String filter) {
        if (filter == null || filter.isEmpty()) {
            outputPaxTable.setItems(allOutputRows);
            return;
        }
        String lower = filter.toLowerCase();
        ObservableList<TableRow> filtered = allOutputRows.filtered(row ->
            row.toString().toLowerCase().contains(lower)
        );
        outputPaxTable.setItems(filtered);
    }
}
