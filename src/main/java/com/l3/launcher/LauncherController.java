package com.l3.launcher;

import com.l3.rcaengine.controller.MainApp;
import com.l3.logparser.MessageExtractorApplication;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Controller for the L3 Engine Launcher FXML view
 */
public class LauncherController {

    @FXML
    private VBox root;

    @FXML
    private Label titleLabel;

    @FXML
    private Label descLabel;

    @FXML
    private Button logParserButton;

    @FXML
    private Button dataEngineButton;

    @FXML
    private void openLogParserModule() {
        try {
            MessageExtractorApplication messageExtractorApp = new MessageExtractorApplication();
            Stage messageExtractorStage = new Stage();
            messageExtractorApp.start(messageExtractorStage);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to launch Message Extractor Module: " + e.getMessage());
        }
    }

    @FXML
    private void openDataEngineModule() {
        try {
            MainApp dataEngineApp = new MainApp();
            Stage dataEngineStage = new Stage();
            dataEngineApp.start(dataEngineStage);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to launch API/PNR Data Engine module: " + e.getMessage());
        }
    }
}
