package com.l3.logextractor;

import com.l3.logextractor.controller.LogExtractionController;
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

    private LogExtractionController controller;

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(LogExtractionApplication.class.getResource("/com/l3/rcaengine/api/log-extraction-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());

        // Get the controller for cleanup purposes
        controller = fxmlLoader.getController();

        primaryStage.setTitle("L3 Engine - Log Extraction Module");
        primaryStage.setScene(scene);
        primaryStage.setMaximized(true);
        primaryStage.setResizable(true);
        primaryStage.centerOnScreen();

        // Handle window closing event to cleanup resources
        primaryStage.setOnCloseRequest(event -> {
            if (controller != null) {
                controller.cleanup();
            }
        });

        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        // Cleanup when application is stopping
        if (controller != null) {
            controller.cleanup();
        }
        super.stop();
    }

    public static void main(String[] args) {
        launch();
    }
}
