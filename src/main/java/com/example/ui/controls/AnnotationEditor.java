package com.example.ui.controls;

import fr.cnrs.lacito.liftapi.model.LiftAnnotation;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.*;

/**
 * Editor for a single {@link LiftAnnotation}.
 * Uses ComboBox for name (read-only, shows known annotation names)
 * and an editable ComboBox for value.
 */
public final class AnnotationEditor extends VBox {

    private final ComboBox<String> nameCombo = new ComboBox<>();
    private final ComboBox<String> valueCombo = new ComboBox<>();
    private final TextField whoField = new TextField();
    private final TextField whenField = new TextField();
    private final MultiTextEditor textEditor = new MultiTextEditor();

    private LiftAnnotation annotation;

    public AnnotationEditor() {
        super(6);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #ddd; -fx-border-radius: 4; -fx-background-color: #fafafa; -fx-background-radius: 4;");

        nameCombo.setEditable(false);
        nameCombo.setMaxWidth(Double.MAX_VALUE);
        nameCombo.setPromptText("nom de l'annotation");

        valueCombo.setEditable(true);
        valueCombo.setMaxWidth(Double.MAX_VALUE);
        valueCombo.setPromptText("valeur");

        whoField.setPromptText("qui");
        whenField.setPromptText("quand");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        int r = 0;
        grid.add(new Label("Nom"), 0, r);
        grid.add(nameCombo, 1, r++);
        grid.add(new Label("Valeur"), 0, r);
        grid.add(valueCombo, 1, r++);
        grid.add(new Label("Qui"), 0, r);
        grid.add(whoField, 1, r++);
        grid.add(new Label("Quand"), 0, r);
        grid.add(whenField, 1, r++);
        GridPane.setHgrow(nameCombo, Priority.ALWAYS);
        GridPane.setHgrow(valueCombo, Priority.ALWAYS);
        GridPane.setHgrow(whoField, Priority.ALWAYS);
        GridPane.setHgrow(whenField, Priority.ALWAYS);

        TitledPane textPane = new TitledPane("Texte (MultiText)", textEditor);
        textPane.setExpanded(false);
        textPane.setAnimated(false);

        getChildren().addAll(grid, textPane);
    }

    /**
     * @param a                the annotation to edit
     * @param availableLangs   languages for the MultiTextEditor
     * @param annotationNames  all known annotation names in the dictionary
     */
    public void setAnnotation(LiftAnnotation a, Collection<String> availableLangs, Collection<String> annotationNames) {
        if (annotation != null) {
            valueCombo.getEditor().textProperty().unbindBidirectional(annotation.valueProperty());
        }
        this.annotation = a;
        if (a == null) {
            nameCombo.getItems().clear();
            valueCombo.getItems().clear();
            whoField.setText("");
            whenField.setText("");
            textEditor.setMultiText(null);
            return;
        }

        nameCombo.setItems(FXCollections.observableArrayList(
            annotationNames instanceof List ? (List<String>) annotationNames : new ArrayList<>(annotationNames)));
        nameCombo.setValue(a.getName());

        valueCombo.setValue(a.getValue().orElse(""));
        valueCombo.getEditor().textProperty().bindBidirectional(a.valueProperty());

        whoField.setText(a.getWho().orElse(""));
        whenField.setText(a.getWhen().orElse(""));
        textEditor.setAvailableLanguages(availableLangs);
        textEditor.setMultiText(a.getText());
    }

    /** Backward-compatible overload. */
    public void setAnnotation(LiftAnnotation a, Collection<String> availableLangs) {
        setAnnotation(a, availableLangs, Set.of());
    }
}
