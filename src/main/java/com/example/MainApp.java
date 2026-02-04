package com.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/com/example/ui/MainView.fxml"));
        Scene scene = new Scene(loader.load(), 1200, 720);
        scene.getStylesheets().add(MainApp.class.getResource("/com/example/ui/app.css").toExternalForm());

        stage.setTitle("Multilingual Dictionary Editor");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

