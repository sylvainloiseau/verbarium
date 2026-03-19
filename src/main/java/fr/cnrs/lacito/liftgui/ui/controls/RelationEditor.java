/**
 
* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.model.LiftRelation;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
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
import java.util.TreeSet;

/**
 * Editor for a single {@link LiftRelation}.
 *
 * Displays: type, refID, order, usage MultiText + ExtensibleWithFieldEditor.
 */
public final class RelationEditor extends VBox {

    private final ComboBox<String> typeCombo = new ComboBox<>();
    private final TextField refIdField = new TextField();
    private final TextField orderField = new TextField();
    private ChangeListener<String> refIdListener;
    private ChangeListener<String> orderListener;
    private final MultiTextEditor usageEditor = new MultiTextEditor();
    private final ExtensibleWithFieldEditor extensibleEditor = new ExtensibleWithFieldEditor();

    public RelationEditor() {
        super(6);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #bca; -fx-border-radius: 4; -fx-background-color: #f8faf4; -fx-background-radius: 4;");

        typeCombo.setEditable(false);
        typeCombo.setPromptText("type");
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        refIdField.setPromptText("ref ID");
        refIdField.setVisible(false);
        Label refIdLabel = new Label("Ref ID");
        refIdLabel.setVisible(false);
        orderField.setPromptText("ordre");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        int r = 0;
        grid.add(new Label("Type"), 0, r);
        grid.add(typeCombo, 1, r++);
        grid.add(refIdLabel, 0, r);
        grid.add(refIdField, 1, r++);
        grid.add(new Label("Ordre"), 0, r);
        grid.add(orderField, 1, r++);
        GridPane.setHgrow(typeCombo, Priority.ALWAYS);
        GridPane.setHgrow(refIdField, Priority.ALWAYS);
        GridPane.setHgrow(orderField, Priority.ALWAYS);

        TitledPane usagePane = new TitledPane("Usage (MultiText)", usageEditor);
        usagePane.setExpanded(false);
        usagePane.setAnimated(false);

        TitledPane extPane = new TitledPane("Propriétés (dates, traits, annotations, champs)", extensibleEditor);
        extPane.setExpanded(false);
        extPane.setAnimated(false);

        getChildren().addAll(grid, usagePane, extPane);
    }

    private ChangeListener<String> typeListener;

    public void setRelation(LiftRelation rel, Collection<String> langs) {
        setRelation(rel, langs, List.of());
    }

    /**
     * @param relationTypes allowed types from header range {@code lexical-relation} (non-editable combo only).
     */
    public void setRelation(LiftRelation rel, Collection<String> langs, List<String> relationTypes) {
        if (typeListener != null) {
            typeCombo.valueProperty().removeListener(typeListener);
            typeListener = null;
        }
        if (rel == null) {
            typeCombo.setItems(FXCollections.observableArrayList());
            typeCombo.setValue(null);
            refIdField.setText("");
            orderField.setText("");
            if (refIdListener != null) refIdField.textProperty().removeListener(refIdListener);
            if (orderListener != null) orderField.textProperty().removeListener(orderListener);
            refIdListener = null;
            orderListener = null;
            usageEditor.setMultiText(null);
            extensibleEditor.setModel(null, langs);
            return;
        }
        TreeSet<String> items = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (relationTypes != null) items.addAll(relationTypes);
        String current = rel.getType() != null ? rel.getType() : "";
        if (!current.isBlank()) items.add(current);
        typeCombo.setItems(FXCollections.observableArrayList(items));
        typeCombo.setValue(current.isBlank() ? null : current);
        LiftRelation relRef = rel;
        typeListener = (obs, o, n) -> {
            if (n != null && !n.isBlank()) relRef.setType(n);
        };
        typeCombo.valueProperty().addListener(typeListener);
        refIdField.setText(rel.getRefID().orElse(""));
        orderField.setText(rel.getOrder().map(String::valueOf).orElse(""));
        if (refIdListener != null) refIdField.textProperty().removeListener(refIdListener);
        if (orderListener != null) orderField.textProperty().removeListener(orderListener);
        LiftRelation relationRef = rel;
        refIdListener = (obs, o, n) -> relationRef.setRefId(n != null ? n : "");
        orderListener = (obs, o, n) -> {
            if (n != null && !n.isBlank()) {
                try { relationRef.setOrder(Integer.parseInt(n.trim())); } catch (NumberFormatException ignored) {}
            }
        };
        refIdField.textProperty().addListener(refIdListener);
        orderField.textProperty().addListener(orderListener);
        usageEditor.setAvailableLanguages(langs);
        usageEditor.setMultiText(rel.getUsage());
        extensibleEditor.setModel(rel, langs);
    }
}
