package com.example.ui.controls;

import fr.cnrs.lacito.liftapi.model.AbstractNotable;
import fr.cnrs.lacito.liftapi.model.LiftNote;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;

import java.util.Collection;

/**
 * Editor for {@link AbstractNotable} objects.
 *
 * Extends {@link ExtensibleWithFieldEditor} by adding a "Notes" section.
 * Used by LiftEntry, LiftSense, and LiftExample which all inherit from AbstractNotable.
 */
public class NotableEditor extends ExtensibleWithFieldEditor {

    private final VBox notesBox = new VBox(6);

    public NotableEditor() {
        super();

        TitledPane notesPane = new TitledPane("Notes", notesBox);
        notesPane.setExpanded(false);
        notesPane.setAnimated(false);

        getChildren().add(notesPane);
    }

    public void setModel(AbstractNotable model, Collection<String> availableLangs) {
        // Delegate fields + traits + annotations + dates to the parent editor
        super.setModel(model, availableLangs);

        notesBox.getChildren().clear();
        if (model == null) return;

        for (LiftNote note : model.getNotes().values()) {
            NoteEditor ne = new NoteEditor();
            ne.setNote(note, availableLangs);
            notesBox.getChildren().add(ne);
        }
    }
}
