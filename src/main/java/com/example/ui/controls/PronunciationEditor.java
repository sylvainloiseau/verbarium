package com.example.ui.controls;

import fr.cnrs.lacito.liftapi.model.LiftPronunciation;
import javafx.geometry.Insets;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;

import java.util.Collection;

/**
 * Editor for a single {@link LiftPronunciation}.
 *
 * Displays: pronunciation MultiText + ExtensibleWithFieldEditor
 * (since LiftPronunciation extends AbstractExtensibleWithField).
 */
public final class PronunciationEditor extends VBox {

    private final MultiTextEditor pronTextEditor = new MultiTextEditor();
    private final ExtensibleWithFieldEditor extensibleEditor = new ExtensibleWithFieldEditor();

    public PronunciationEditor() {
        super(6);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #ccb; -fx-border-radius: 4; -fx-background-color: #fafaf0; -fx-background-radius: 4;");

        TitledPane textPane = new TitledPane("Prononciation (MultiText)", pronTextEditor);
        textPane.setExpanded(true);
        textPane.setAnimated(false);

        TitledPane extPane = new TitledPane("Propriétés (dates, traits, annotations, champs)", extensibleEditor);
        extPane.setExpanded(false);
        extPane.setAnimated(false);

        getChildren().addAll(textPane, extPane);
    }

    public void setPronunciation(LiftPronunciation p, Collection<String> langs) {
        if (p == null) {
            pronTextEditor.setMultiText(null);
            extensibleEditor.setModel(null, langs);
            return;
        }
        pronTextEditor.setAvailableLanguages(langs);
        pronTextEditor.setMultiText(p.getProunciation());
        extensibleEditor.setModel(p, langs);
    }
}
