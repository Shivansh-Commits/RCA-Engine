package com.l3.logparser;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Main application class for the Message Extractor module
 * This module extracts API/PNR data from raw log files
 */
public class MessageExtractorApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(MessageExtractorApplication.class.getResource("/fxml/logparser/log-parser-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1200, 800);

        primaryStage.setTitle("L3 Engine - Message Extractor Module");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
