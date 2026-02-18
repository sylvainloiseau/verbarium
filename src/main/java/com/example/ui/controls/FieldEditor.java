package com.example.ui.controls;

import fr.cnrs.lacito.liftapi.model.LiftField;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.*;

/**
 * Editor for a {@link LiftField}.
 * Uses a ComboBox for the field name/type (read-only, shows known field types).
 */
public final class FieldEditor extends VBox {

    private final ComboBox<String> nameCombo = new ComboBox<>();
    private final MultiTextEditor textEditor = new MultiTextEditor();
    private final ExtensibleWithoutFieldEditor extensibleEditor = new ExtensibleWithoutFieldEditor();

    public FieldEditor() {
        super(6);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #bcd; -fx-border-radius: 4; -fx-background-color: #f0f4f8; -fx-background-radius: 4;");

        nameCombo.setEditable(false);
        nameCombo.setMaxWidth(Double.MAX_VALUE);
        nameCombo.setPromptText("type de champ");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label("Type"), 0, 0);
        grid.add(nameCombo, 1, 0);
        GridPane.setHgrow(nameCombo, Priority.ALWAYS);

        TitledPane textPane = new TitledPane("Texte (MultiText)", textEditor);
        textPane.setExpanded(true);
        textPane.setAnimated(false);

        TitledPane extPane = new TitledPane("Propriétés (dates, traits, annotations)", extensibleEditor);
        extPane.setExpanded(false);
        extPane.setAnimated(false);

        getChildren().addAll(grid, textPane, extPane);
    }

    /**
     * @param f               the field to edit
     * @param availableLangs  languages for the MultiTextEditor
     * @param fieldTypes      all known field type names in the dictionary
     */
    public void setField(LiftField f, Collection<String> availableLangs, Collection<String> fieldTypes) {
        if (f == null) {
            nameCombo.getItems().clear();
            textEditor.setMultiText(null);
            extensibleEditor.setModel(null, availableLangs);
            return;
        }
        nameCombo.setItems(FXCollections.observableArrayList(
            fieldTypes instanceof List ? (List<String>) fieldTypes : new ArrayList<>(fieldTypes)));
        nameCombo.setValue(f.getName());
        textEditor.setAvailableLanguages(availableLangs);
        textEditor.setMultiText(f.getText());
        extensibleEditor.setModel(f, availableLangs);
    }

    /** Backward-compatible overload. */
    public void setField(LiftField f, Collection<String> availableLangs) {
        setField(f, availableLangs, List.of());
    }
}
