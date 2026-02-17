package com.example.ui.controls;

import fr.cnrs.lacito.liftapi.model.LiftReversal;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Collection;

/**
 * Editor for a single {@link LiftReversal}.
 *
 * Displays: type, forms MultiText, and recursive main (if present).
 */
public final class ReversalEditor extends VBox {

    private final TextField typeField = new TextField();
    private final MultiTextEditor formsEditor = new MultiTextEditor();
    private final VBox mainBox = new VBox(6);

    public ReversalEditor() {
        super(6);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #b9c; -fx-border-radius: 4; -fx-background-color: #f8f4fa; -fx-background-radius: 4;");

        typeField.setEditable(false);
        typeField.setPromptText("type");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label("Type"), 0, 0);
        grid.add(typeField, 1, 0);
        GridPane.setHgrow(typeField, Priority.ALWAYS);

        TitledPane formsPane = new TitledPane("Formes (MultiText)", formsEditor);
        formsPane.setExpanded(true);
        formsPane.setAnimated(false);

        TitledPane mainPane = new TitledPane("Main (récursif)", mainBox);
        mainPane.setExpanded(false);
        mainPane.setAnimated(false);

        getChildren().addAll(grid, formsPane, mainPane);
    }

    public void setReversal(LiftReversal rev, Collection<String> langs) {
        mainBox.getChildren().clear();

        if (rev == null) {
            typeField.setText("");
            formsEditor.setMultiText(null);
            return;
        }
        typeField.setText(rev.getType().orElse(""));
        formsEditor.setAvailableLanguages(langs);
        formsEditor.setMultiText(rev.getForms());

        if (rev.getMain() != null) {
            ReversalEditor mainEditor = new ReversalEditor();
            mainEditor.setReversal(rev.getMain(), langs);
            mainBox.getChildren().add(mainEditor);
        }
    }
}
