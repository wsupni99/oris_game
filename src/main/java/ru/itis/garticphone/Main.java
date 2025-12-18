package ru.itis.garticphone;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage stage) {
        Label label = new Label("Gartic Phone Started!");
        Scene scene = new Scene(label, 400, 300);
        stage.setScene(scene);
        stage.setTitle("Gartic Phone");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}