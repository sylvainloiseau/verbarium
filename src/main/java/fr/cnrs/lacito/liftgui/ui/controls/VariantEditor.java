
/**
 
* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.model.LiftPronunciation;
import fr.cnrs.lacito.liftapi.model.LiftRelation;
import fr.cnrs.lacito.liftapi.model.LiftVariant;
import fr.cnrs.lacito.liftapi.model.MultiText;
import fr.cnrs.lacito.liftgui.ui.I18n;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Editor for a single {@link LiftVariant}.
 *
 * Displays: refId, forms MultiText, pronunciations, relations + ExtensibleWithFieldEditor.
 */
public final class VariantEditor extends VBox {

    private final TextField refIdField = new TextField();
    private final MultiTextEditor parentEntryFormsEditor = new MultiTextEditor();
    private final MultiTextEditor formsEditor = new MultiTextEditor();
    private final VBox pronunciationsBox = new VBox(6);
    private final VBox relationsBox = new VBox(6);
    private final ExtensibleWithFieldEditor extensibleEditor = new ExtensibleWithFieldEditor();
    /** Types from header range {@code lexical-relation} for {@link RelationEditor}. */
    private List<String> relationTypes = List.of();

    public VariantEditor() {
        super(6);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #bbc; -fx-border-radius: 4; -fx-background-color: #f6f6fa; -fx-background-radius: 4;");

        refIdField.setPromptText("ref ID");
        refIdField.setVisible(false);
        Label refIdLabel = new Label("Ref ID");
        refIdLabel.setVisible(false);

        TitledPane parentFormsPane = new TitledPane("Entrée parent (lexical-unit)", parentEntryFormsEditor);
        parentFormsPane.setExpanded(true);
        parentFormsPane.setAnimated(false);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(refIdLabel, 0, 0);
        grid.add(refIdField, 1, 0);
        GridPane.setHgrow(refIdField, Priority.ALWAYS);

        parentEntryFormsEditor.setFixedLanguageRows(true);
        formsEditor.setFixedLanguageRows(true);

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

        getChildren().addAll(parentFormsPane, grid, formsPane, pronPane, relPane, extPane);
    }

    public void setRelationTypes(List<String> relationTypes) {
        this.relationTypes = relationTypes == null ? List.of() : List.copyOf(relationTypes);
    }

    /**
     * @param v          the variant
     * @param objLangs   object-languages for variant forms and pronunciations
     * @param metaLangs  meta-languages for relations (usage) and inherited properties
     * @param addActions optional callbacks for adding pronunciation, relation, trait, annotation, field
     */
    public void setVariant(LiftVariant v, Collection<String> objLangs, Collection<String> metaLangs) {
        setVariant(v, objLangs, metaLangs, null);
    }

    public void setVariant(LiftVariant v, Collection<String> objLangs, Collection<String> metaLangs, ExtensibleAddActions addActions) {
        pronunciationsBox.getChildren().clear();
        relationsBox.getChildren().clear();

        if (v == null) {
            refIdField.setText("");
            parentEntryFormsEditor.setMultiText(null);
            formsEditor.setMultiText(null);
            extensibleEditor.setModel(null, metaLangs, null);
            return;
        }
        refIdField.setText(v.getRefId().orElse(""));
        LiftVariant variant = v;
        MultiText parentForms = variant.getParent() != null ? variant.getParent().getForms() : null;
        parentEntryFormsEditor.setAvailableLanguages(objLangs);
        parentEntryFormsEditor.setMultiText(parentForms);
        parentEntryFormsEditor.setReadOnly(true);
        formsEditor.setAvailableLanguages(objLangs);
        formsEditor.setMultiText(v.getForms());
        formsEditor.setReadOnly(true);

        if (addActions != null) {
            FlowPane pronAddRow = new FlowPane(6, 4);
            Button addPronBtn = new Button(I18n.get("btn.addPronunciation"));
            addPronBtn.getStyleClass().add("example-add-button");
            addPronBtn.setOnAction(e -> {
                addActions.addPronunciation();
                addActions.refresh();
            });
            pronAddRow.getChildren().add(addPronBtn);
            pronunciationsBox.getChildren().add(pronAddRow);

            FlowPane relAddRow = new FlowPane(6, 4);
            Button addRelBtn = new Button(I18n.get("btn.addRelation"));
            addRelBtn.getStyleClass().add("example-add-button");
            addRelBtn.setOnAction(e -> {
                List<String> types = addActions.getKnownRelationTypes();
                Optional<String> typeOpt;
                if (types.isEmpty()) {
                    TextInputDialog tid = new TextInputDialog();
                    tid.setTitle(I18n.get("btn.addRelation"));
                    tid.setHeaderText(I18n.get("col.type"));
                    typeOpt = tid.showAndWait();
                } else {
                    ChoiceDialog<String> dlg = new ChoiceDialog<>(types.get(0), types);
                    dlg.setTitle(I18n.get("btn.addRelation"));
                    dlg.setHeaderText(I18n.get("col.type"));
                    typeOpt = dlg.showAndWait();
                }
                typeOpt.filter(t -> t != null && !t.isBlank()).ifPresent(type -> {
                    addActions.addRelation(type.trim());
                    addActions.refresh();
                });
            });
            relAddRow.getChildren().add(addRelBtn);
            relationsBox.getChildren().add(relAddRow);
        }

        for (LiftPronunciation p : v.getPronunciations()) {
            PronunciationEditor pe = new PronunciationEditor();
            pe.setPronunciation(p, objLangs);
            pronunciationsBox.getChildren().add(pe);
        }

        for (LiftRelation r : v.getRelations()) {
            RelationEditor re = new RelationEditor();
            re.setRelation(r, metaLangs, relationTypes);
            relationsBox.getChildren().add(re);
        }

        extensibleEditor.setModel(v, metaLangs, addActions);
    }
}
