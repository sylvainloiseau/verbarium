package com.example.ui.controls;

import fr.cnrs.lacito.liftapi.model.LiftRelation;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Collection;

/**
 * Editor for a single {@link LiftRelation}.
 *
 * Displays: type, refID, order, usage MultiText + ExtensibleWithFieldEditor.
 */
public final class RelationEditor extends VBox {

    private final TextField typeField = new TextField();
    private final TextField refIdField = new TextField();
    private final TextField orderField = new TextField();
    private final MultiTextEditor usageEditor = new MultiTextEditor();
    private final ExtensibleWithFieldEditor extensibleEditor = new ExtensibleWithFieldEditor();

    public RelationEditor() {
        super(6);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #bca; -fx-border-radius: 4; -fx-background-color: #f8faf4; -fx-background-radius: 4;");

        typeField.setEditable(false);
        typeField.setPromptText("type");
        refIdField.setPromptText("ref ID");
        orderField.setPromptText("ordre");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        int r = 0;
        grid.add(new Label("Type"), 0, r);
        grid.add(typeField, 1, r++);
        grid.add(new Label("Ref ID"), 0, r);
        grid.add(refIdField, 1, r++);
        grid.add(new Label("Ordre"), 0, r);
        grid.add(orderField, 1, r++);
        GridPane.setHgrow(typeField, Priority.ALWAYS);
        GridPane.setHgrow(refIdField, Priority.ALWAYS);
        GridPane.setHgrow(orderField, Priority.ALWAYS);

        TitledPane usagePane = new TitledPane("Usage (MultiText)", usageEditor);
        usagePane.setExpanded(false);
        usagePane.setAnimated(false);

        TitledPane extPane = new TitledPane("Propriétés (dates, traits, annotations, champs)", extensibleEditor);
        extPane.setExpanded(false);
        extPane.setAnimated(false);

        getChildren().addAll(grid, usagePane, extPane);
    }

    public void setRelation(LiftRelation rel, Collection<String> langs) {
        if (rel == null) {
            typeField.setText("");
            refIdField.setText("");
            orderField.setText("");
            usageEditor.setMultiText(null);
            extensibleEditor.setModel(null, langs);
            return;
        }
        typeField.setText(rel.getType() != null ? rel.getType() : "");
        refIdField.setText(rel.getRefID().orElse(""));
        orderField.setText(rel.getOrder().map(String::valueOf).orElse(""));
        usageEditor.setAvailableLanguages(langs);
        usageEditor.setMultiText(rel.getUsage());
        extensibleEditor.setModel(rel, langs);
    }
}
