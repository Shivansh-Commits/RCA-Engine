package com.l3.api;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class SplashScreenController {
    @FXML
    private ProgressBar progressBar;
    @FXML
    private ImageView logoImageView;
    @FXML
    private VBox splashContent;

    @FXML
    public void initialize() {
        // Load the specified image
        logoImageView.setImage(new Image(getClass().getResource("/images/idhmYHKKeL_1759401346656.jpeg").toExternalForm()));

        // Add fade-in effect
        splashContent.getStyleClass().add("splash-content");
        FadeTransition fadeIn = new FadeTransition(Duration.seconds(0.8), splashContent);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        fadeIn.play();
    }

    public void startProgress(Runnable onFinish) {
        // Smooth progress bar animation
        Timeline timeline = new Timeline();

        // Add multiple keyframes for smoother animation
        for (int i = 0; i <= 20; i++) {
            final double progress = i / 20.0;
            timeline.getKeyFrames().add(
                new KeyFrame(Duration.seconds(i * 0.1),
                    e -> progressBar.setProgress(progress))
            );
        }

        timeline.setOnFinished(e -> {
            // Small delay before transition
            Timeline delay = new Timeline(new KeyFrame(Duration.seconds(0.3), event -> onFinish.run()));
            delay.play();
        });

        timeline.play();
    }
}
