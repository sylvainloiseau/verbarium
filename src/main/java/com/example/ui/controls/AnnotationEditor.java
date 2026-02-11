package com.example.ui.controls;

import fr.cnrs.lacito.liftapi.model.LiftAnnotation;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Collection;

/**
 * Programmatic editor for a single {@link LiftAnnotation}.
 *
 * Displays: name (read-only), value, who, when + MultiTextEditor for its text.
 */
public final class AnnotationEditor extends VBox {

    private final TextField nameField = new TextField();
    private final TextField valueField = new TextField();
    private final TextField whoField = new TextField();
    private final TextField whenField = new TextField();
    private final MultiTextEditor textEditor = new MultiTextEditor();

    private LiftAnnotation annotation;

    public AnnotationEditor() {
        super(6);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #ddd; -fx-border-radius: 4; -fx-background-color: #fafafa; -fx-background-radius: 4;");

        nameField.setEditable(false);
        nameField.setPromptText("nom");
        valueField.setPromptText("valeur");
        whoField.setPromptText("qui");
        whenField.setPromptText("quand");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        int r = 0;
        grid.add(new Label("Nom"), 0, r);
        grid.add(nameField, 1, r++);
        grid.add(new Label("Valeur"), 0, r);
        grid.add(valueField, 1, r++);
        grid.add(new Label("Qui"), 0, r);
        grid.add(whoField, 1, r++);
        grid.add(new Label("Quand"), 0, r);
        grid.add(whenField, 1, r++);
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(valueField, Priority.ALWAYS);
        GridPane.setHgrow(whoField, Priority.ALWAYS);
        GridPane.setHgrow(whenField, Priority.ALWAYS);

        TitledPane textPane = new TitledPane("Texte (MultiText)", textEditor);
        textPane.setExpanded(false);
        textPane.setAnimated(false);

        getChildren().addAll(grid, textPane);
    }

    public void setAnnotation(LiftAnnotation a, Collection<String> availableLangs) {
        this.annotation = a;
        if (a == null) {
            nameField.setText("");
            valueField.setText("");
            whoField.setText("");
            whenField.setText("");
            textEditor.setMultiText(null);
            return;
        }
        nameField.setText(a.getName() != null ? a.getName() : "");
        valueField.setText(a.getValue().orElse(""));
        whoField.setText(a.getWho().orElse(""));
        whenField.setText(a.getWhen().orElse(""));
        textEditor.setMultiText(a.getText());
        textEditor.setAvailableLanguages(availableLangs);
    }
}
