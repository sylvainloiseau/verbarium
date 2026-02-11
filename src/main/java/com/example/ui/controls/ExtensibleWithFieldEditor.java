package com.example.ui.controls;

import fr.cnrs.lacito.liftapi.model.AbstractExtensibleWithField;
import fr.cnrs.lacito.liftapi.model.LiftField;
import javafx.geometry.Insets;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;

import java.util.Collection;
import java.util.List;

/**
 * Programmatic editor for {@link AbstractExtensibleWithField}.
 *
 * Extends {@link ExtensibleWithoutFieldEditor} by also displaying
 * the list of {@link LiftField}s (each via a {@link FieldEditor}).
 */
public class ExtensibleWithFieldEditor extends ExtensibleWithoutFieldEditor {

    private final VBox fieldsBox = new VBox(6);

    public ExtensibleWithFieldEditor() {
        super();

        TitledPane fieldsPane = new TitledPane("Champs (Field)", fieldsBox);
        fieldsPane.setExpanded(false);
        fieldsPane.setAnimated(false);

        getChildren().add(fieldsPane);
    }

    public void setModel(AbstractExtensibleWithField model, Collection<String> availableLangs) {
        // Delegate dates + traits + annotations to the parent editor
        super.setModel(model, availableLangs);

        fieldsBox.getChildren().clear();
        if (model == null) return;

        List<LiftField> fields = model.getFields();
        if (fields != null) {
            for (LiftField f : fields) {
                FieldEditor fe = new FieldEditor();
                fe.setField(f, availableLangs);
                fieldsBox.getChildren().add(fe);
            }
        }
    }
}
