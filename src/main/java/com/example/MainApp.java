package com.example;

import com.example.ui.I18n;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class MainApp extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        loadScene();
        stage.show();
    }

    public static void reloadScene() {
        try { loadScene(); }
        catch (Exception e) { e.printStackTrace(); }
    }

    private static void loadScene() throws Exception {
        FXMLLoader loader = new FXMLLoader(
            MainApp.class.getResource("/com/example/ui/MainView.fxml"),
            I18n.getBundle()
        );
        double w = primaryStage.getScene() != null ? primaryStage.getScene().getWidth() : 1200;
        double h = primaryStage.getScene() != null ? primaryStage.getScene().getHeight() : 720;
        Scene scene = new Scene(loader.load(), w, h);
        scene.getStylesheets().add(MainApp.class.getResource("/com/example/ui/app.css").toExternalForm());
        primaryStage.setTitle(I18n.get("app.title"));
        primaryStage.setScene(scene);
    }

    public static void main(String[] args) {
        launch(args);
    }
}

