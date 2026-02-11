package com.example.ui.controls;

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
 * Programmatic editor for a single {@link LiftTrait}.
 *
 * Displays: name (read-only), value (bound to valueProperty) + an AnnotationEditor for each annotation.
 */
public final class TraitEditor extends VBox {

    private final TextField nameField = new TextField();
    private final TextField valueField = new TextField();
    private final VBox annotationsBox = new VBox(6);

    private LiftTrait trait;

    public TraitEditor() {
        super(6);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #cde; -fx-border-radius: 4; -fx-background-color: #f5f8fc; -fx-background-radius: 4;");

        nameField.setEditable(false);
        nameField.setPromptText("nom");
        valueField.setPromptText("valeur");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label("Nom"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Valeur"), 0, 1);
        grid.add(valueField, 1, 1);
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(valueField, Priority.ALWAYS);

        TitledPane annoPane = new TitledPane("Annotations", annotationsBox);
        annoPane.setExpanded(false);
        annoPane.setAnimated(false);

        getChildren().addAll(grid, annoPane);
    }

    public void setTrait(LiftTrait t, Collection<String> availableLangs) {
        // Unbind previous
        if (trait != null) {
            valueField.textProperty().unbindBidirectional(trait.valueProperty());
        }
        this.trait = t;
        if (t == null) {
            nameField.setText("");
            valueField.setText("");
            annotationsBox.getChildren().clear();
            return;
        }
        nameField.setText(t.getName() != null ? t.getName() : "");
        valueField.textProperty().bindBidirectional(t.valueProperty());

        // Annotations
        annotationsBox.getChildren().clear();
        List<LiftAnnotation> annos = t.getAnnotations();
        if (annos != null) {
            for (LiftAnnotation a : annos) {
                AnnotationEditor ae = new AnnotationEditor();
                ae.setAnnotation(a, availableLangs);
                annotationsBox.getChildren().add(ae);
            }
        }
    }
}
