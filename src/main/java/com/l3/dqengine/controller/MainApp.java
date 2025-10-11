package com.l3.dqengine.controller;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;


public class MainApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        // Load splash screen first
        FXMLLoader splashLoader = new FXMLLoader(getClass().getResource("/com/l3/dqengine/api/SplashScreen.fxml"));
        Scene splashScene = new Scene(splashLoader.load());

        primaryStage.setTitle("SITA API/PNR DQ Engine");
        primaryStage.setScene(splashScene);
        primaryStage.setResizable(false);
        primaryStage.centerOnScreen();
        primaryStage.show();

        // Get splash controller and start progress
        SplashScreenController splashController = splashLoader.getController();
        splashController.startProgress(() -> {
            try {
                // Load main application window
                FXMLLoader mainLoader = new FXMLLoader(getClass().getResource("/com/l3/dqengine/api/MainView.fxml"));
                Scene mainScene = new Scene(mainLoader.load());

                primaryStage.setTitle("L3 Engine");
                primaryStage.setScene(mainScene);
                primaryStage.setMaximized(true);
                primaryStage.setResizable(true);
                primaryStage.centerOnScreen();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}