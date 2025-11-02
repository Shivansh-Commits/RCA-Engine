package com.l3.logextractor;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Main application class for the Log Extraction module
 * This module extracts logs from servers via Azure Pipeline API calls
 */
public class LogExtractionApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(LogExtractionApplication.class.getResource("/com/l3/rcaengine/api/log-extraction-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1200, 800);

        primaryStage.setTitle("L3 Engine - Log Extraction Module");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
