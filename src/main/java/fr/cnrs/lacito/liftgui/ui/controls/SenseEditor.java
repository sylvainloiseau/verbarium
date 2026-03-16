
/**
 
* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.model.*;
import fr.cnrs.lacito.liftgui.ui.I18n;
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
    private final GridPane identityBlock = new GridPane();
    private final MultiTextEditor definitionEditor = new MultiTextEditor();
    private final MultiTextEditor glossEditor = new MultiTextEditor();
    private final VBox examplesBox = new VBox(6);
    private final VBox relationsBox = new VBox(6);
    private final VBox reversalsBox = new VBox(6);
    private final VBox subSensesBox = new VBox(6);
    private final TitledPane subSensesPane;
    private final NotableEditor notableEditor = new NotableEditor();

    public SenseEditor() {
        super(6);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #aab; -fx-border-radius: 4; -fx-background-color: #f4f4fa; -fx-background-radius: 4;");

        grammaticalInfoField.setPromptText("info grammaticale");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label("Gram. Info"), 0, 0);
        grid.add(grammaticalInfoField, 1, 0);
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

        subSensesPane = new TitledPane("Sous-sens", subSensesBox);
        subSensesPane.setExpanded(false);
        subSensesPane.setAnimated(false);

        TitledPane extPane = new TitledPane("Propriétés héritées (notes, champs, traits, annotations, dates)", notableEditor);
        extPane.setExpanded(false);
        extPane.setAnimated(false);

        identityBlock.setHgap(8);
        identityBlock.setVgap(6);
        identityBlock.setStyle("-fx-background-color: #e8e8e8; -fx-padding: 8; -fx-background-radius: 4;");
        TitledPane identityPane = new TitledPane(I18n.get("editor.identity"), identityBlock);
        identityPane.setExpanded(true);
        identityPane.setAnimated(false);

        getChildren().addAll(grid, defPane, glossPane, exPane, relPane, revPane, subSensesPane, extPane, identityPane);
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
            grammaticalInfoField.setText("");
            definitionEditor.setMultiText(null);
            glossEditor.setMultiText(null);
            notableEditor.setModel(null, metaLangs);
            identityBlock.getChildren().clear();
            return;
        }
        grammaticalInfoField.setText(sense.getGrammaticalInfo().map(GrammaticalInfo::getValue).orElse(""));

        // Bloc identity en bas : ID, dates (gris, comme Entries)
        identityBlock.getChildren().clear();
        idField.setText(sense.getId().orElse(""));
        idField.setEditable(false);
        idField.setStyle("-fx-text-fill: #666; -fx-background-color: #f0f0f0;");
        identityBlock.add(new Label(I18n.get("field.id")), 0, 0);
        identityBlock.add(idField, 1, 0);
        identityBlock.add(new Label(I18n.get("field.dateCreated")), 0, 1);
        TextField dateCreatedField = new TextField(sense.getDateCreated().orElse(""));
        dateCreatedField.setEditable(false);
        dateCreatedField.setStyle("-fx-text-fill: #666; -fx-background-color: #f0f0f0;");
        identityBlock.add(dateCreatedField, 1, 1);
        identityBlock.add(new Label(I18n.get("field.dateModified")), 0, 2);
        TextField dateModifiedField = new TextField(sense.getDateModified().orElse(""));
        dateModifiedField.setEditable(false);
        dateModifiedField.setStyle("-fx-text-fill: #666; -fx-background-color: #f0f0f0;");
        identityBlock.add(dateModifiedField, 1, 2);
        GridPane.setHgrow(idField, Priority.ALWAYS);
        GridPane.setHgrow(dateCreatedField, Priority.ALWAYS);
        GridPane.setHgrow(dateModifiedField, Priority.ALWAYS);

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
        subSensesPane.setExpanded(!sense.getSubSenses().isEmpty());

        notableEditor.setModel(sense, metaLangs);
    }
}
