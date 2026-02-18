package com.example.ui.controls;

import fr.cnrs.lacito.liftapi.model.LiftAnnotation;
import fr.cnrs.lacito.liftapi.model.LiftTrait;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.*;

/**
 * Editor for a single {@link LiftTrait}.
 * Uses a ComboBox for the name (read-only, shows known trait names)
 * and an editable ComboBox for the value (pre-filled with known values for that trait name).
 */
public final class TraitEditor extends VBox {

    private final ComboBox<String> nameCombo = new ComboBox<>();
    private final ComboBox<String> valueCombo = new ComboBox<>();
    private final VBox annotationsBox = new VBox(6);

    private LiftTrait trait;

    public TraitEditor() {
        super(6);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #cde; -fx-border-radius: 4; -fx-background-color: #f5f8fc; -fx-background-radius: 4;");

        nameCombo.setEditable(false);
        nameCombo.setMaxWidth(Double.MAX_VALUE);
        nameCombo.setPromptText("nom du trait");

        valueCombo.setEditable(true);
        valueCombo.setMaxWidth(Double.MAX_VALUE);
        valueCombo.setPromptText("valeur");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label("Nom"), 0, 0);
        grid.add(nameCombo, 1, 0);
        grid.add(new Label("Valeur"), 0, 1);
        grid.add(valueCombo, 1, 1);
        GridPane.setHgrow(nameCombo, Priority.ALWAYS);
        GridPane.setHgrow(valueCombo, Priority.ALWAYS);

        TitledPane annoPane = new TitledPane("Annotations", annotationsBox);
        annoPane.setExpanded(false);
        annoPane.setAnimated(false);

        getChildren().addAll(grid, annoPane);
    }

    /**
     * @param t               the trait to edit
     * @param availableLangs  languages for annotation sub-editors
     * @param traitNames      all known trait names in the dictionary
     * @param valuesForName   known values for each trait name (name -> set of values)
     */
    public void setTrait(LiftTrait t, Collection<String> availableLangs,
                         Collection<String> traitNames, Map<String, Set<String>> valuesForName) {
        if (trait != null) {
            valueCombo.getEditor().textProperty().unbindBidirectional(trait.valueProperty());
        }
        this.trait = t;
        if (t == null) {
            nameCombo.getItems().clear();
            valueCombo.getItems().clear();
            annotationsBox.getChildren().clear();
            return;
        }

        // Name combo: show all known names, select current
        nameCombo.setItems(FXCollections.observableArrayList(
            traitNames instanceof List ? (List<String>) traitNames : new ArrayList<>(traitNames)));
        nameCombo.setValue(t.getName());

        // Value combo: fill with known values for this trait name, bind to model
        Set<String> knownValues = valuesForName != null ? valuesForName.getOrDefault(t.getName(), Set.of()) : Set.of();
        valueCombo.setItems(FXCollections.observableArrayList(new TreeSet<>(knownValues)));
        valueCombo.setValue(t.getValue());
        valueCombo.getEditor().textProperty().bindBidirectional(t.valueProperty());

        annotationsBox.getChildren().clear();
        List<LiftAnnotation> annos = t.getAnnotations();
        if (annos != null) {
            for (LiftAnnotation a : annos) {
                AnnotationEditor ae = new AnnotationEditor();
                ae.setAnnotation(a, availableLangs, Set.of());
                annotationsBox.getChildren().add(ae);
            }
        }
    }

    /** Backward-compatible overload (no dropdown data). */
    public void setTrait(LiftTrait t, Collection<String> availableLangs) {
        setTrait(t, availableLangs, List.of(), Map.of());
    }
}
