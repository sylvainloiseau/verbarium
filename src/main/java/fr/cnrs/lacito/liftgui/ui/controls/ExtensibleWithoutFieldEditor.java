
/**
 
* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.model.AbstractExtensibleWithoutField;
import fr.cnrs.lacito.liftapi.model.LiftAnnotation;
import fr.cnrs.lacito.liftapi.model.LiftTrait;
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
 * Programmatic editor for any {@link AbstractExtensibleWithoutField}.
 *
 * Displays: dateCreation, dateModification, list of Traits (TraitEditor), list of Annotations (AnnotationEditor).
 * This component is reused inside FieldEditor, NoteEditor, etc.
 */
public class ExtensibleWithoutFieldEditor extends VBox {

    private final TextField dateCreatedField = new TextField();
    private final TextField dateModifiedField = new TextField();
    private final VBox traitsBox = new VBox(6);
    private final VBox annotationsBox = new VBox(6);

    public ExtensibleWithoutFieldEditor() {
        super(8);
        setPadding(new Insets(4));

        dateCreatedField.setEditable(false);
        dateCreatedField.setPromptText("(non définie)");
        dateModifiedField.setEditable(false);
        dateModifiedField.setPromptText("(non définie)");

        GridPane datesGrid = new GridPane();
        datesGrid.setHgap(8);
        datesGrid.setVgap(6);
        datesGrid.add(new Label("Date création"), 0, 0);
        datesGrid.add(dateCreatedField, 1, 0);
        datesGrid.add(new Label("Date modification"), 0, 1);
        datesGrid.add(dateModifiedField, 1, 1);
        GridPane.setHgrow(dateCreatedField, Priority.ALWAYS);
        GridPane.setHgrow(dateModifiedField, Priority.ALWAYS);

        traitsPane = new TitledPane("Traits", traitsBox);
        traitsPane.setExpanded(false);
        traitsPane.setAnimated(false);

        annotationsPane = new TitledPane("Annotations", annotationsBox);
        annotationsPane.setExpanded(false);
        annotationsPane.setAnimated(false);

        getChildren().addAll(datesGrid, traitsPane, annotationsPane);
    }

    private TitledPane traitsPane;
    private TitledPane annotationsPane;

    public void setModel(AbstractExtensibleWithoutField model, Collection<String> availableLangs) {
        setModel(model, availableLangs, null);
    }

    public void setModel(AbstractExtensibleWithoutField model, Collection<String> availableLangs, ExtensibleAddActions addActions) {
        traitsBox.getChildren().clear();
        annotationsBox.getChildren().clear();

        if (model == null) {
            dateCreatedField.setText("");
            dateModifiedField.setText("");
            return;
        }

        dateCreatedField.setText(model.getDateCreated().orElse(""));
        dateModifiedField.setText(model.getDateModified().orElse(""));

        // Traits
        List<LiftTrait> traits = model.getTraits();
        if (traits != null) {
            for (LiftTrait t : traits) {
                TraitEditor te = new TraitEditor();
                te.setTrait(t, availableLangs);
                traitsBox.getChildren().add(te);
            }
        }

        // Annotations
        List<LiftAnnotation> annos = model.getAnnotations();
        if (annos != null) {
            for (LiftAnnotation a : annos) {
                AnnotationEditor ae = new AnnotationEditor();
                ae.setAnnotation(a, availableLangs);
                annotationsBox.getChildren().add(ae);
            }
        }

        // Add buttons in applet
        updateAddButtons(addActions);
    }

    private void updateAddButtons(ExtensibleAddActions addActions) {
        if (addActions == null) return;
        FlowPane traitAddRow = new FlowPane(6, 4);
        Button addTraitBtn = new Button(I18n.get("btn.addTrait"));
        addTraitBtn.getStyleClass().add("example-add-button");
        addTraitBtn.setOnAction(e -> {
            List<String> names = addActions.getKnownTraitNames();
            ChoiceDialog<String> dlg = new ChoiceDialog<>(names.isEmpty() ? null : names.get(0), names);
            dlg.setTitle(I18n.get("btn.addTrait"));
            dlg.setHeaderText(I18n.get("col.name"));
            dlg.showAndWait().ifPresent(name -> {
                addActions.addTrait(name, "");
                addActions.refresh();
            });
        });
        traitAddRow.getChildren().add(addTraitBtn);
        traitsBox.getChildren().add(0, traitAddRow);

        FlowPane annotAddRow = new FlowPane(6, 4);
        Button addAnnotBtn = new Button(I18n.get("btn.addAnnotation"));
        addAnnotBtn.getStyleClass().add("example-add-button");
        addAnnotBtn.setOnAction(e -> {
            List<String> names = addActions.getKnownAnnotationNames();
            Optional<String> nameOpt;
            if (names.isEmpty()) {
                TextInputDialog tid = new TextInputDialog();
                tid.setTitle(I18n.get("btn.addAnnotation"));
                tid.setHeaderText(I18n.get("col.name"));
                nameOpt = tid.showAndWait();
            } else {
                ChoiceDialog<String> dlg = new ChoiceDialog<>(names.get(0), names);
                dlg.setTitle(I18n.get("btn.addAnnotation"));
                dlg.setHeaderText(I18n.get("col.name"));
                nameOpt = dlg.showAndWait();
            }
            nameOpt.filter(n -> n != null && !n.isBlank()).ifPresent(name -> {
                addActions.addAnnotation(name.trim());
                addActions.refresh();
            });
        });
        annotAddRow.getChildren().add(addAnnotBtn);
        annotationsBox.getChildren().add(0, annotAddRow);
    }
}
