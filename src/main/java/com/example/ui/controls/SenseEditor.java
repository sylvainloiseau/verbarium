package com.example.ui.controls;

import fr.cnrs.lacito.liftapi.model.*;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Collection;

/**
 * Editor for a single {@link LiftSense}.
 *
 * Displays: id, grammaticalInfo, definition MultiText, gloss MultiText,
 * examples (ExampleEditor each), relations (RelationEditor each),
 * sub-senses (recursive SenseEditor each),
 * + NotableEditor for inherited properties (notes, fields, traits, annotations, dates).
 */
public final class SenseEditor extends VBox {

    private final TextField idField = new TextField();
    private final TextField grammaticalInfoField = new TextField();
    private final MultiTextEditor definitionEditor = new MultiTextEditor();
    private final MultiTextEditor glossEditor = new MultiTextEditor();
    private final VBox examplesBox = new VBox(6);
    private final VBox relationsBox = new VBox(6);
    private final VBox reversalsBox = new VBox(6);
    private final VBox subSensesBox = new VBox(6);
    private final NotableEditor notableEditor = new NotableEditor();

    public SenseEditor() {
        super(6);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #aab; -fx-border-radius: 4; -fx-background-color: #f4f4fa; -fx-background-radius: 4;");

        idField.setEditable(false);
        idField.setPromptText("id");
        grammaticalInfoField.setPromptText("info grammaticale");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label("ID"), 0, 0);
        grid.add(idField, 1, 0);
        grid.add(new Label("Gram. Info"), 0, 1);
        grid.add(grammaticalInfoField, 1, 1);
        GridPane.setHgrow(idField, Priority.ALWAYS);
        GridPane.setHgrow(grammaticalInfoField, Priority.ALWAYS);

        TitledPane defPane = new TitledPane("Définition (MultiText)", definitionEditor);
        defPane.setExpanded(true);
        defPane.setAnimated(false);

        TitledPane glossPane = new TitledPane("Gloss (MultiText)", glossEditor);
        glossPane.setExpanded(true);
        glossPane.setAnimated(false);

        TitledPane exPane = new TitledPane("Exemples", examplesBox);
        exPane.setExpanded(true);
        exPane.setAnimated(false);

        TitledPane relPane = new TitledPane("Relations", relationsBox);
        relPane.setExpanded(false);
        relPane.setAnimated(false);

        TitledPane revPane = new TitledPane("Reversals", reversalsBox);
        revPane.setExpanded(false);
        revPane.setAnimated(false);

        TitledPane subPane = new TitledPane("Sous-sens", subSensesBox);
        subPane.setExpanded(false);
        subPane.setAnimated(false);

        TitledPane extPane = new TitledPane("Propriétés héritées (notes, champs, traits, annotations, dates)", notableEditor);
        extPane.setExpanded(false);
        extPane.setAnimated(false);

        getChildren().addAll(grid, defPane, glossPane, exPane, relPane, revPane, subPane, extPane);
    }

    /**
     * Populate with proper separation of meta-languages vs object-languages.
     * @param sense       the sense to display
     * @param metaLangs   meta-languages for definition, gloss, relations, notes, annotations, reversal
     * @param objLangs    object-languages for examples
     */
    public void setSense(LiftSense sense, Collection<String> metaLangs, Collection<String> objLangs) {
        examplesBox.getChildren().clear();
        relationsBox.getChildren().clear();
        reversalsBox.getChildren().clear();
        subSensesBox.getChildren().clear();

        if (sense == null) {
            idField.setText("");
            grammaticalInfoField.setText("");
            definitionEditor.setMultiText(null);
            glossEditor.setMultiText(null);
            notableEditor.setModel(null, metaLangs);
            return;
        }
        idField.setText(sense.getId().orElse(""));
        grammaticalInfoField.setText(sense.getGrammaticalInfo().map(GrammaticalInfo::getValue).orElse(""));

        definitionEditor.setAvailableLanguages(metaLangs);
        definitionEditor.setMultiText(sense.getDefinition());

        glossEditor.setAvailableLanguages(metaLangs);
        glossEditor.setMultiText(sense.getGloss());

        // Examples — object-languages for example text, meta-languages for translations
        for (LiftExample ex : sense.getExamples()) {
            ExampleEditor ee = new ExampleEditor();
            ee.setExample(ex, objLangs, metaLangs);
            examplesBox.getChildren().add(ee);
        }

        // Relations — meta-languages for usage
        for (LiftRelation rel : sense.getRelations()) {
            RelationEditor re = new RelationEditor();
            re.setRelation(rel, metaLangs);
            relationsBox.getChildren().add(re);
        }

        // Reversals — meta-languages
        for (LiftReversal rev : sense.getReversals()) {
            ReversalEditor rve = new ReversalEditor();
            rve.setReversal(rev, metaLangs);
            reversalsBox.getChildren().add(rve);
        }

        // Sub-senses (recursive)
        for (LiftSense sub : sense.getSubSenses()) {
            SenseEditor se = new SenseEditor();
            se.setSense(sub, metaLangs, objLangs);
            subSensesBox.getChildren().add(se);
        }

        notableEditor.setModel(sense, metaLangs);
    }
}
