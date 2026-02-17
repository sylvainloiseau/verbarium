package com.example.ui.controls;

import fr.cnrs.lacito.liftapi.model.LiftExample;
import fr.cnrs.lacito.liftapi.model.MultiText;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Collection;
import java.util.Map;

/**
 * Editor for a single {@link LiftExample}.
 *
 * Displays: source, example MultiText, translations (one MultiTextEditor per type),
 * + NotableEditor for inherited properties (notes, fields, traits, annotations, dates).
 */
public final class ExampleEditor extends VBox {

    private final TextField sourceField = new TextField();
    private final MultiTextEditor exampleTextEditor = new MultiTextEditor();
    private final VBox translationsBox = new VBox(6);
    private final NotableEditor notableEditor = new NotableEditor();

    public ExampleEditor() {
        super(6);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #cba; -fx-border-radius: 4; -fx-background-color: #faf6f2; -fx-background-radius: 4;");

        sourceField.setPromptText("source");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label("Source"), 0, 0);
        grid.add(sourceField, 1, 0);
        GridPane.setHgrow(sourceField, Priority.ALWAYS);

        TitledPane exPane = new TitledPane("Exemple (MultiText)", exampleTextEditor);
        exPane.setExpanded(true);
        exPane.setAnimated(false);

        TitledPane trPane = new TitledPane("Traductions", translationsBox);
        trPane.setExpanded(true);
        trPane.setAnimated(false);

        TitledPane extPane = new TitledPane("Propriétés héritées (notes, champs, traits, annotations, dates)", notableEditor);
        extPane.setExpanded(false);
        extPane.setAnimated(false);

        getChildren().addAll(grid, exPane, trPane, extPane);
    }

    /**
     * @param ex        the example
     * @param objLangs  object-languages for the example text
     * @param metaLangs meta-languages for translations, notes, etc.
     */
    public void setExample(LiftExample ex, Collection<String> objLangs, Collection<String> metaLangs) {
        translationsBox.getChildren().clear();

        if (ex == null) {
            sourceField.setText("");
            exampleTextEditor.setMultiText(null);
            notableEditor.setModel(null, metaLangs);
            return;
        }
        sourceField.setText(ex.getSource().orElse(""));
        exampleTextEditor.setAvailableLanguages(objLangs);
        exampleTextEditor.setMultiText(ex.getExample());

        // Translations: one MultiTextEditor per translation type — meta-languages
        for (Map.Entry<String, MultiText> kv : ex.getTranslations().entrySet()) {
            String type = kv.getKey();
            MultiText mt = kv.getValue();

            String label = type.isBlank() ? "Traduction (par défaut)" : "Traduction [" + type + "]";
            MultiTextEditor mte = new MultiTextEditor();
            mte.setAvailableLanguages(metaLangs);
            mte.setMultiText(mt);

            TitledPane tp = new TitledPane(label, mte);
            tp.setExpanded(true);
            tp.setAnimated(false);
            translationsBox.getChildren().add(tp);
        }

        notableEditor.setModel(ex, metaLangs);
    }
}
