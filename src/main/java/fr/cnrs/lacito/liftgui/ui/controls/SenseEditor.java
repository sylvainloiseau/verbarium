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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Editor for a single {@link LiftSense}.
 *
 * Displays: id, grammaticalInfo (ComboBox, non-editable), definition MultiText, gloss MultiText,
 * examples (ExampleEditor each), relations (RelationEditor each),
 * sub-senses (recursive SenseEditor each),
 * + NotableEditor for inherited properties (notes, fields, traits, annotations, dates).
 */
public final class SenseEditor extends VBox {

    private final TextField idField = new TextField();
    /** Non-editable ComboBox for grammatical info — values come from header range grammatical-info */
    private final ComboBox<String> grammaticalInfoCombo = new ComboBox<>();
    private final GridPane identityBlock = new GridPane();
    private final MultiTextEditor definitionEditor = new MultiTextEditor();
    private final MultiTextEditor glossEditor = new MultiTextEditor();
    private final VBox examplesBox = new VBox(6);
    private final VBox relationsBox = new VBox(6);
    private final VBox reversalsBox = new VBox(6);
    private final VBox subSensesBox = new VBox(6);
    private final TitledPane subSensesPane;
    private final NotableEditor notableEditor = new NotableEditor();
    /** Types from header range {@code lexical-relation} for {@link RelationEditor}. */
    private List<String> relationTypes = List.of();
    /** Currently displayed sense — used by the save listener. */
    private LiftSense currentSense = null;
    private Runnable onGramInfoChanged = null;

    public SenseEditor() {
        super(6);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #aab; -fx-border-radius: 4; -fx-background-color: #f4f4fa; -fx-background-radius: 4;");

        grammaticalInfoCombo.setEditable(false);
        grammaticalInfoCombo.setPromptText("info grammaticale");
        grammaticalInfoCombo.setMaxWidth(Double.MAX_VALUE);

        grammaticalInfoCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (currentSense != null && newVal != null && !newVal.equals(oldVal)) {
                currentSense.setGrammaticalInfo(newVal.trim());
                if (onGramInfoChanged != null) onGramInfoChanged.run();
            }
        });

        Label gramTitle = new Label(I18n.get("editor.section.gramInfo"));
        gramTitle.getStyleClass().add("editor-section-title");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label("Gram. Info"), 0, 0);
        grid.add(grammaticalInfoCombo, 1, 0);
        GridPane.setHgrow(grammaticalInfoCombo, Priority.ALWAYS);

        Label semanticTitle = new Label(I18n.get("editor.section.semanticContent"));
        semanticTitle.getStyleClass().add("editor-section-title");

        definitionEditor.setFixedLanguageRows(true);
        glossEditor.setFixedLanguageRows(true);

        TitledPane defPane = new TitledPane("Définition (MultiText)", definitionEditor);
        defPane.setExpanded(true);
        defPane.setAnimated(false);

        TitledPane glossPane = new TitledPane("Gloss (MultiText)", glossEditor);
        glossPane.setExpanded(true);
        glossPane.setAnimated(false);

        Label examplesTitle = new Label(I18n.get("editor.section.examplesRelations"));
        examplesTitle.getStyleClass().add("editor-section-title");

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

        Label metadataTitle = new Label(I18n.get("editor.section.metadata"));
        metadataTitle.getStyleClass().add("editor-section-title");

        TitledPane extPane = new TitledPane("Propriétés héritées (notes, champs, traits, annotations, dates)", notableEditor);
        extPane.setExpanded(false);
        extPane.setAnimated(false);

        identityBlock.setHgap(8);
        identityBlock.setVgap(6);
        identityBlock.setStyle("-fx-background-color: #e8e8e8; -fx-padding: 8; -fx-background-radius: 4;");
        TitledPane identityPane = new TitledPane(I18n.get("editor.identity"), identityBlock);
        identityPane.setExpanded(true);
        identityPane.setAnimated(false);

        getChildren().addAll(gramTitle, grid, semanticTitle, defPane, glossPane, examplesTitle, exPane, relPane, revPane, subSensesPane, metadataTitle, extPane, identityPane);
    }

    public void setRelationTypes(List<String> relationTypes) {
        this.relationTypes = relationTypes == null ? List.of() : List.copyOf(relationTypes);
    }

    public void setOnGramInfoChanged(Runnable callback) {
        this.onGramInfoChanged = callback;
    }
    /**
     * Set the allowed grammatical info values from the header range "grammatical-info".
     * Call this before setSense() so the ComboBox is populated.
     */
    public void setGrammaticalInfoValues(List<String> values) {
        grammaticalInfoCombo.getItems().setAll(values == null ? List.of() : values);
    }

    /**
     * Populate with proper separation of meta-languages vs object-languages.
     * @param sense       the sense to display
     * @param metaLangs   meta-languages for definition, gloss, relations, notes, annotations, reversal
     * @param objLangs    object-languages for examples
     */
    public void setSense(LiftSense sense, Collection<String> metaLangs, Collection<String> objLangs) {
        setSense(sense, metaLangs, objLangs, null, List.of());
    }

    /**
     * Same as above with optional callback to create annotations on MultiText (definition, gloss).
     */
    public void setSense(LiftSense sense, Collection<String> metaLangs, Collection<String> objLangs,
                         BiConsumer<String, fr.cnrs.lacito.liftapi.model.MultiText> onAddAnnotation,
                         Collection<String> knownAnnotationNames) {
        setSense(sense, metaLangs, objLangs, onAddAnnotation, knownAnnotationNames, s -> null, ex -> null);
    }

    /**
     * Same with optional add actions for trait/annotation/field/note in NotableEditor.
     */
    public void setSense(LiftSense sense, Collection<String> metaLangs, Collection<String> objLangs,
                         BiConsumer<String, fr.cnrs.lacito.liftapi.model.MultiText> onAddAnnotation,
                         Collection<String> knownAnnotationNames, Function<LiftSense, ExtensibleAddActions> addActionsFactory,
                         java.util.function.Function<fr.cnrs.lacito.liftapi.model.LiftExample, ExtensibleAddActions> exampleAddActionsFactory) {
        ExtensibleAddActions addActions = addActionsFactory != null ? addActionsFactory.apply(sense) : null;
        examplesBox.getChildren().clear();
        relationsBox.getChildren().clear();
        reversalsBox.getChildren().clear();
        subSensesBox.getChildren().clear();

        if (sense == null) {
            currentSense = null;
            grammaticalInfoCombo.setValue(null);
            definitionEditor.setMultiText(null);
            definitionEditor.setOnAddAnnotation(null, null);
            glossEditor.setMultiText(null);
            glossEditor.setOnAddAnnotation(null, null);
            notableEditor.setModel(null, metaLangs);
            identityBlock.getChildren().clear();
            return;
        }

        // Set currentSense BEFORE setting combo value to avoid spurious save on load
        currentSense = null;         String gramVal = sense.getGrammaticalInfo().map(GrammaticalInfo::getValue).orElse(null);
        // Add current value to combo if not already present (handles values not in header range)
        if (gramVal != null && !gramVal.isBlank() && !grammaticalInfoCombo.getItems().contains(gramVal)) {
            grammaticalInfoCombo.getItems().add(0, gramVal);
        }
        grammaticalInfoCombo.setValue(gramVal);
        currentSense = sense; // re-enable listener after loading

        // Identity block: ID, dates (read-only, grey)
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
        if (onAddAnnotation != null) {
            definitionEditor.setOnAddAnnotation(onAddAnnotation, knownAnnotationNames);
        }

        glossEditor.setAvailableLanguages(metaLangs);
        glossEditor.setMultiText(sense.getGloss());
        if (onAddAnnotation != null) {
            glossEditor.setOnAddAnnotation(onAddAnnotation, knownAnnotationNames);
        }

        // Examples — object-languages for example text, meta-languages for translations
        for (LiftExample ex : sense.getExamples()) {
            ExampleEditor ee = new ExampleEditor();
            ExtensibleAddActions exAddActions = exampleAddActionsFactory != null ? exampleAddActionsFactory.apply(ex) : null;
            ee.setExample(ex, objLangs, metaLangs, onAddAnnotation, knownAnnotationNames, exAddActions);
            examplesBox.getChildren().add(ee);
        }

        // Relations — meta-languages for usage
        for (LiftRelation rel : sense.getRelations()) {
            RelationEditor re = new RelationEditor();
            re.setRelation(rel, metaLangs, relationTypes);
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
            se.setGrammaticalInfoValues(new java.util.ArrayList<>(grammaticalInfoCombo.getItems()));
            se.setSense(sub, metaLangs, objLangs, onAddAnnotation, knownAnnotationNames, addActionsFactory, exampleAddActionsFactory);
            subSensesBox.getChildren().add(se);
        }
        subSensesPane.setExpanded(!sense.getSubSenses().isEmpty());

        notableEditor.setModel(sense, metaLangs, addActions);
    }
}