package com.example.ui.controls;

import fr.cnrs.lacito.liftapi.model.AbstractExtensibleWithoutField;
import fr.cnrs.lacito.liftapi.model.LiftAnnotation;
import fr.cnrs.lacito.liftapi.model.LiftTrait;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Collection;
import java.util.List;

/**
 * Programmatic editor for any {@link AbstractExtensibleWithoutField}.
 *
 * Displays: dateCreation, dateModification, list of Traits (TraitEditor), list of Annotations (AnnotationEditor).
 * This component is reused inside FieldEditor, NoteEditor, etc.
 */
public class ExtensibleWithoutFieldEditor extends VBox {

    private final TextField dateCreatedField = new TextField();
    private final TextField dateModifiedField = new TextField();
    private final VBox traitsBox = new VBox(6);
    private final VBox annotationsBox = new VBox(6);

    public ExtensibleWithoutFieldEditor() {
        super(8);
        setPadding(new Insets(4));

        dateCreatedField.setEditable(false);
        dateCreatedField.setPromptText("(non définie)");
        dateModifiedField.setEditable(false);
        dateModifiedField.setPromptText("(non définie)");

        GridPane datesGrid = new GridPane();
        datesGrid.setHgap(8);
        datesGrid.setVgap(6);
        datesGrid.add(new Label("Date création"), 0, 0);
        datesGrid.add(dateCreatedField, 1, 0);
        datesGrid.add(new Label("Date modification"), 0, 1);
        datesGrid.add(dateModifiedField, 1, 1);
        GridPane.setHgrow(dateCreatedField, Priority.ALWAYS);
        GridPane.setHgrow(dateModifiedField, Priority.ALWAYS);

        TitledPane traitsPane = new TitledPane("Traits", traitsBox);
        traitsPane.setExpanded(false);
        traitsPane.setAnimated(false);

        TitledPane annotationsPane = new TitledPane("Annotations", annotationsBox);
        annotationsPane.setExpanded(false);
        annotationsPane.setAnimated(false);

        getChildren().addAll(datesGrid, traitsPane, annotationsPane);
    }

    public void setModel(AbstractExtensibleWithoutField model, Collection<String> availableLangs) {
        traitsBox.getChildren().clear();
        annotationsBox.getChildren().clear();

        if (model == null) {
            dateCreatedField.setText("");
            dateModifiedField.setText("");
            return;
        }

        dateCreatedField.setText(model.getDateCreated().orElse(""));
        dateModifiedField.setText(model.getDateModified().orElse(""));

        // Traits
        List<LiftTrait> traits = model.getTraits();
        if (traits != null) {
            for (LiftTrait t : traits) {
                TraitEditor te = new TraitEditor();
                te.setTrait(t, availableLangs);
                traitsBox.getChildren().add(te);
            }
        }

        // Annotations
        List<LiftAnnotation> annos = model.getAnnotations();
        if (annos != null) {
            for (LiftAnnotation a : annos) {
                AnnotationEditor ae = new AnnotationEditor();
                ae.setAnnotation(a, availableLangs);
                annotationsBox.getChildren().add(ae);
            }
        }
    }
}
