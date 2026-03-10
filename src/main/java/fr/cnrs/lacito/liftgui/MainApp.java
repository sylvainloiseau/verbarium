/**
 
* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui;

import fr.cnrs.lacito.liftgui.ui.I18n;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class MainApp extends Application {

    private static final Logger LOGGER = Logger.getLogger(MainApp.class.getName());
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        primaryStage.setTitle(I18n.get("app.title"));

        // Afficher l'écran de chargement
        Parent loadingRoot = (Parent) FXMLLoader.load(
            MainApp.class.getResource("/fr/cnrs/lacito/liftgui/ui/LoadingView.fxml")
        );
        Scene loadingScene = new Scene(loadingRoot, 600, 400);
        loadingScene.getStylesheets().add(
            MainApp.class.getResource("/fr/cnrs/lacito/liftgui/ui/loading.css").toExternalForm()
        );
        primaryStage.setScene(loadingScene);
        primaryStage.setMinWidth(500);
        primaryStage.setMinHeight(350);
        primaryStage.setMaximized(true);
        primaryStage.centerOnScreen();
        primaryStage.show();

        ProgressBar progressBar = (ProgressBar) loadingRoot.lookup("#progressBar");
        if (progressBar != null) {
            runLoadingSequence(progressBar);
        } else {
            Platform.runLater(() -> finishLoading());
        }
    }

    private void runLoadingSequence(ProgressBar progressBar) {
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO, e -> progressBar.setProgress(0)),
            new KeyFrame(Duration.millis(400), e -> progressBar.setProgress(0.3)),
            new KeyFrame(Duration.millis(800), e -> progressBar.setProgress(0.6)),
            new KeyFrame(Duration.millis(1200), e -> progressBar.setProgress(0.85))
        );
        timeline.setOnFinished(e -> {
            progressBar.setProgress(1.0);
            Platform.runLater(this::finishLoading);
        });
        timeline.play();
    }

    private void finishLoading() {
        try {
            loadScene();
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Erreur au chargement de l'application", ex);
        }
    }

    public static void reloadScene() {
        try { loadScene(); }
        catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur au rechargement de la scène", e);
        }
    }

    private static void loadScene() throws Exception {
        FXMLLoader loader = new FXMLLoader(
            MainApp.class.getResource("/fr/cnrs/lacito/liftgui/ui/MainView.fxml"),
            I18n.getBundle()
        );
        double w = primaryStage.getScene() != null ? primaryStage.getScene().getWidth() : 1200;
        double h = primaryStage.getScene() != null ? primaryStage.getScene().getHeight() : 720;
        Scene scene = new Scene(loader.load(), w, h);
        scene.getStylesheets().add(MainApp.class.getResource("/fr/cnrs/lacito/liftgui/ui/app.css").toExternalForm());
        primaryStage.setTitle(I18n.get("app.title"));
        primaryStage.setScene(scene);
    }

    public static void main(String[] args) {
        launch(args);
    }
}

