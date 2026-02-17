package com.example.ui.controls;

import fr.cnrs.lacito.liftapi.model.LiftEtymology;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Collection;

/**
 * Editor for a single {@link LiftEtymology}.
 *
 * Displays: type, source, forms MultiText, glosses MultiText + ExtensibleWithFieldEditor.
 */
public final class EtymologyEditor extends VBox {

    private final TextField typeField = new TextField();
    private final TextField sourceField = new TextField();
    private final MultiTextEditor formsEditor = new MultiTextEditor();
    private final MultiTextEditor glossesEditor = new MultiTextEditor();
    private final ExtensibleWithFieldEditor extensibleEditor = new ExtensibleWithFieldEditor();

    public EtymologyEditor() {
        super(6);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #abc; -fx-border-radius: 4; -fx-background-color: #f4f8fa; -fx-background-radius: 4;");

        typeField.setEditable(false);
        typeField.setPromptText("type");
        sourceField.setEditable(false);
        sourceField.setPromptText("source");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label("Type"), 0, 0);
        grid.add(typeField, 1, 0);
        grid.add(new Label("Source"), 0, 1);
        grid.add(sourceField, 1, 1);
        GridPane.setHgrow(typeField, Priority.ALWAYS);
        GridPane.setHgrow(sourceField, Priority.ALWAYS);

        TitledPane formsPane = new TitledPane("Formes (MultiText)", formsEditor);
        formsPane.setExpanded(true);
        formsPane.setAnimated(false);

        TitledPane glossesPane = new TitledPane("Glosess (MultiText)", glossesEditor);
        glossesPane.setExpanded(false);
        glossesPane.setAnimated(false);

        TitledPane extPane = new TitledPane("Propriétés (dates, traits, annotations, champs)", extensibleEditor);
        extPane.setExpanded(false);
        extPane.setAnimated(false);

        getChildren().addAll(grid, formsPane, glossesPane, extPane);
    }

    /**
     * @param ety        the etymology
     * @param objLangs   object-languages for etymology forms
     * @param metaLangs  meta-languages for glosses and inherited properties
     */
    public void setEtymology(LiftEtymology ety, Collection<String> objLangs, Collection<String> metaLangs) {
        if (ety == null) {
            typeField.setText("");
            sourceField.setText("");
            formsEditor.setMultiText(null);
            glossesEditor.setMultiText(null);
            extensibleEditor.setModel(null, metaLangs);
            return;
        }
        typeField.setText(ety.getType() != null ? ety.getType() : "");
        sourceField.setText(ety.getSource() != null ? ety.getSource() : "");
        formsEditor.setAvailableLanguages(objLangs);
        formsEditor.setMultiText(ety.getForms());
        glossesEditor.setAvailableLanguages(metaLangs);
        glossesEditor.setMultiText(ety.getGloss());
        extensibleEditor.setModel(ety, metaLangs);
    }
}
