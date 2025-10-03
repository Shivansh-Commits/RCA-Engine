package com.l3.engine.controller;

import com.l3.engine.model.Passenger;
import com.l3.engine.apiutils.FileParser;
import com.l3.engine.apiutils.ParseResult;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.*;

public class MainController {

    @FXML private Button chooseFolderBtn;
    @FXML private Button processBtn;
    @FXML private Button clearBtn;
    @FXML private Label totalInputPassengersValue;
    @FXML private Label totalUniqueInputPassengersValue;
    @FXML private Label totalOutputPassengersValue;
    @FXML private Label droppedPassengersValue;
    @FXML private Label duplicatePassengersValue;
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

    private File selectedFolder;

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
        inputPaxTable.setItems(FXCollections.observableArrayList());

        processBtn.setOnAction(e -> onProcess());
        chooseFolderBtn.setOnAction(e -> onChooseFolder());
        clearBtn.setOnAction(e -> onClear());
    }

    private void onChooseFolder() {
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
        arrivalPort.setText("———");
        departurePort.setText("———");
        departureDate.setText("———");
        flightNumber.setText("———");
        warningsList.getItems().clear();
        selectedFolder = null;
    }

    private void processAPI(String recordType,String dataType){
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

        // Populating Output Pax Table (outputPassengers values)
        List<TableRow> outputRows = new ArrayList<>();
        int j = 1;
        for (Passenger p : result.getOutputPassengers().values()) {
            outputRows.add(new TableRow(j++, p.getName(), p.getDtm(), p.getDocTypeWithParens(), p.getRecordedKey(), p.getSources(), p.getCount()));
        }
        outputPaxTable.setItems(FXCollections.observableArrayList(outputRows));

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

    private void processPNR(String recordType,String dataType){


        showAlert("Info", "PNR processing to be implemented.");
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

    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(title);
        a.setContentText(msg);
        a.showAndWait();
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
    }
}
