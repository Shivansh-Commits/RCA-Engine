package com.l3.launcher;

import com.l3.rcaengine.controller.MainApp;
import com.l3.logparser.MessageExtractorApplication;
import com.l3.logextractor.LogExtractionApplication;
import com.l3.common.util.VersionUtil;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller for the L3 Engine Launcher FXML view
 */
public class LauncherController implements Initializable {

    @FXML
    private VBox root;

    @FXML
    private Label titleLabel;

    @FXML
    private Label descLabel;

    @FXML
    private Label versionLabel;

    @FXML
    private Label helpButton;

    @FXML
    private Button logParserButton;

    @FXML
    private Button dataEngineButton;

    @FXML
    private Button logExtractionButton;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Set the version label from POM
        if (versionLabel != null) {
            versionLabel.setText(VersionUtil.getFormattedVersion());
        }
    }

    @FXML
    private void openLogParserModule() {
        try {
            MessageExtractorApplication messageExtractorApp = new MessageExtractorApplication();
            Stage logParserStage = new Stage();
            logParserStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/L3_engine_logo.png")));
            messageExtractorApp.start(logParserStage);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to launch Message Extractor Module: " + e.getMessage());
        }
    }

    @FXML
    private void openDataEngineModule() {
        try {
            MainApp dataEngineApp = new MainApp();
            Stage rcaEngineStage = new Stage();
            rcaEngineStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/L3_engine_logo.png")));
            dataEngineApp.start(rcaEngineStage);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to launch API/PNR Data Engine module: " + e.getMessage());
        }
    }

    @FXML
    private void openLogExtractionModule() {
        try {
            LogExtractionApplication logExtractionApp = new LogExtractionApplication();
            Stage logExtractionStage = new Stage();
            logExtractionStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/L3_engine_logo.png")));
            logExtractionApp.start(logExtractionStage);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to launch Log Extraction module: " + e.getMessage());
        }
    }

    @FXML
    private void onShowHelp(MouseEvent event) {
        showApplicationHelp();
    }

    private void showApplicationHelp() {
        Alert helpDialog = new Alert(Alert.AlertType.INFORMATION);
        helpDialog.setTitle("About L3 Engine");
        helpDialog.setHeaderText("L3 Engine - Flight Data Processing Platform");
        String helpText = "🚀 L3 Engine Suite - Comprehensive Flight Data Processing Platform\n\n" +

                         "PURPOSE:\n" +
                         "L3 Engine is a comprehensive suite designed for airline operations and flight data analysis. " +
                         "It provides automated tools for log extraction, data parsing, and root cause analysis " +
                         "to streamline flight operations and incident investigation workflows.\n\n" +

                         "AVAILABLE MODULES:\n\n" +

                         "🗂️ Log Extraction Engine:\n" +
                         "   • Automated flight log extraction from Azure DevOps pipelines\n" +
                         "   • Flight-specific log collection based on flight number and dates\n" +
                         "   • Real-time pipeline monitoring and artifact management\n\n" +

                         "🔍 Log Parser Engine:\n" +
                         "   • Advanced EDIFACT/PNR message extraction from flight logs\n" +
                         "   • Support for API and PNR data types with intelligent filtering\n" +
                         "   • Multi-node processing capabilities for large datasets\n\n" +

                         "⚙️ RCA Engine (Root Cause Analysis):\n" +
                         "   • Comprehensive passenger data processing and analysis\n" +
                         "   • Automated discrepancy detection and data reconciliation\n" +
                         "   • Advanced reporting with Excel export functionality\n\n" +

                         "KEY FEATURES:\n" +
                         "• Seamless Azure DevOps integration\n" +
                         "• Real-time processing and monitoring\n" +
                         "• Intelligent data validation and error handling\n" +
                         "• Comprehensive reporting and export capabilities\n" +
                         "• User-friendly interfaces for all skill levels\n\n" +

                         "AUTHORS:\n" +
                         "Developed by:\n" +
                         "- Shivansh Singh Bhadouria (Shivansh.Bhadouria@sita.aero)\n"+
                         "- Ujjawal Chaudhary (Ujjawal.1@sita.aero)\n"+

                         "VERSION: " + VersionUtil.getFormattedVersion() + "\n" +
                         "Built for modern airline operations and data analysis workflows.";

        // Create a scrollable TextArea for the help content
        TextArea textArea = new TextArea(helpText);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(20);
        textArea.setPrefColumnCount(60);

        // Create a ScrollPane to contain the TextArea
        ScrollPane scrollPane = new ScrollPane(textArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPrefSize(650, 500);

        // Set the custom content in the dialog
        helpDialog.getDialogPane().setContent(scrollPane);
        helpDialog.getDialogPane().setPrefWidth(700);
        helpDialog.getDialogPane().setPrefHeight(600);

        // Remove the default content text since we're using custom content
        helpDialog.setContentText(null);

        helpDialog.showAndWait();
    }
}
