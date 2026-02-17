package com.example.ui.controls;

import fr.cnrs.lacito.liftapi.model.LiftPronunciation;
import fr.cnrs.lacito.liftapi.model.LiftRelation;
import fr.cnrs.lacito.liftapi.model.LiftVariant;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Collection;

/**
 * Editor for a single {@link LiftVariant}.
 *
 * Displays: refId, forms MultiText, pronunciations, relations + ExtensibleWithFieldEditor.
 */
public final class VariantEditor extends VBox {

    private final TextField refIdField = new TextField();
    private final MultiTextEditor formsEditor = new MultiTextEditor();
    private final VBox pronunciationsBox = new VBox(6);
    private final VBox relationsBox = new VBox(6);
    private final ExtensibleWithFieldEditor extensibleEditor = new ExtensibleWithFieldEditor();

    public VariantEditor() {
        super(6);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #bbc; -fx-border-radius: 4; -fx-background-color: #f6f6fa; -fx-background-radius: 4;");

        refIdField.setPromptText("ref ID");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label("Ref ID"), 0, 0);
        grid.add(refIdField, 1, 0);
        GridPane.setHgrow(refIdField, Priority.ALWAYS);

        TitledPane formsPane = new TitledPane("Formes (MultiText)", formsEditor);
        formsPane.setExpanded(true);
        formsPane.setAnimated(false);

        TitledPane pronPane = new TitledPane("Prononciations", pronunciationsBox);
        pronPane.setExpanded(false);
        pronPane.setAnimated(false);

        TitledPane relPane = new TitledPane("Relations", relationsBox);
        relPane.setExpanded(false);
        relPane.setAnimated(false);

        TitledPane extPane = new TitledPane("Propriétés (dates, traits, annotations, champs)", extensibleEditor);
        extPane.setExpanded(false);
        extPane.setAnimated(false);

        getChildren().addAll(grid, formsPane, pronPane, relPane, extPane);
    }

    /**
     * @param v          the variant
     * @param objLangs   object-languages for variant forms and pronunciations
     * @param metaLangs  meta-languages for relations (usage) and inherited properties
     */
    public void setVariant(LiftVariant v, Collection<String> objLangs, Collection<String> metaLangs) {
        pronunciationsBox.getChildren().clear();
        relationsBox.getChildren().clear();

        if (v == null) {
            refIdField.setText("");
            formsEditor.setMultiText(null);
            extensibleEditor.setModel(null, metaLangs);
            return;
        }
        refIdField.setText(v.getRefId().orElse(""));
        formsEditor.setAvailableLanguages(objLangs);
        formsEditor.setMultiText(v.getForms());

        for (LiftPronunciation p : v.getPronunciations()) {
            PronunciationEditor pe = new PronunciationEditor();
            pe.setPronunciation(p, objLangs);
            pronunciationsBox.getChildren().add(pe);
        }

        for (LiftRelation r : v.getRelations()) {
            RelationEditor re = new RelationEditor();
            re.setRelation(r, metaLangs);
            relationsBox.getChildren().add(re);
        }

        extensibleEditor.setModel(v, metaLangs);
    }
}
