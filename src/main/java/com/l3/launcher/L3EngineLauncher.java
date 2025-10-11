package com.l3.launcher;

import com.l3.logparser.MessageExtractorApplication;
import com.l3.dqengine.controller.MainApp;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Main launcher for L3 Engine modules
 * Provides a unified entry point to access different modules
 */
public class L3EngineLauncher extends Application {

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background-color: #f5f5f5;");

        Label titleLabel = new Label("L3 Engine - Module Launcher");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #333;");

        Label descLabel = new Label("Select a module to launch:");
        descLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");

        // Log Parser Module Button
        Button logParserButton = new Button("Log Parser Module");
        logParserButton.setStyle(
            "-fx-background-color: #4CAF50; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-padding: 10 20 10 20; " +
            "-fx-background-radius: 5;"
        );
        logParserButton.setPrefWidth(250);
        logParserButton.setOnAction(e -> openLogParserModule());

        // API/PNR Data Engine Button (placeholder for existing module)
        Button dataEngineButton = new Button("API/PNR Data Engine");
        dataEngineButton.setStyle(
            "-fx-background-color: #2196F3; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-padding: 10 20 10 20; " +
            "-fx-background-radius: 5;"
        );
        dataEngineButton.setPrefWidth(250);
        dataEngineButton.setOnAction(e -> openDataEngineModule());

        root.getChildren().addAll(
            titleLabel,
            descLabel,
            logParserButton,
            dataEngineButton
        );

        Scene scene = new Scene(root, 400, 300);
        primaryStage.setTitle("L3 Engine - Module Launcher");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

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

    public static void main(String[] args) {
        launch(args);
    }
}
