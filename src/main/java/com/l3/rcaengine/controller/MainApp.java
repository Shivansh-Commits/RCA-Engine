package com.l3.rcaengine.controller;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;


public class MainApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        // Load main application window directly (splash screen removed)
        FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("/com/l3/rcaengine/api/rca-engine.fxml"));
        Scene mainScene = new Scene(mainLoader.load());

        primaryStage.setTitle("L3 Engine");
        primaryStage.setScene(mainScene);
        primaryStage.setMaximized(true);
        primaryStage.setResizable(true);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}