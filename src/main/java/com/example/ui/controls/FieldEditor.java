package com.example.ui.controls;

import fr.cnrs.lacito.liftapi.model.LiftField;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Collection;

/**
 * Programmatic editor for a {@link LiftField}.
 *
 * Displays: name (read-only) + MultiTextEditor for the field's text
 * + ExtensibleWithoutFieldEditor (dates, traits, annotations) since
 * LiftField extends AbstractExtensibleWithoutField.
 */
public final class FieldEditor extends VBox {

    private final TextField nameField = new TextField();
    private final MultiTextEditor textEditor = new MultiTextEditor();
    private final ExtensibleWithoutFieldEditor extensibleEditor = new ExtensibleWithoutFieldEditor();

    public FieldEditor() {
        super(6);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #bcd; -fx-border-radius: 4; -fx-background-color: #f0f4f8; -fx-background-radius: 4;");

        nameField.setEditable(false);
        nameField.setPromptText("nom du champ");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label("Nom"), 0, 0);
        grid.add(nameField, 1, 0);
        GridPane.setHgrow(nameField, Priority.ALWAYS);

        TitledPane textPane = new TitledPane("Texte (MultiText)", textEditor);
        textPane.setExpanded(true);
        textPane.setAnimated(false);

        TitledPane extPane = new TitledPane("Propriétés (dates, traits, annotations)", extensibleEditor);
        extPane.setExpanded(false);
        extPane.setAnimated(false);

        getChildren().addAll(grid, textPane, extPane);
    }

    public void setField(LiftField f, Collection<String> availableLangs) {
        if (f == null) {
            nameField.setText("");
            textEditor.setMultiText(null);
            extensibleEditor.setModel(null, availableLangs);
            return;
        }
        nameField.setText(f.getName() != null ? f.getName() : "");
        textEditor.setAvailableLanguages(availableLangs);
        textEditor.setMultiText(f.getText());
        extensibleEditor.setModel(f, availableLangs);
    }
}
