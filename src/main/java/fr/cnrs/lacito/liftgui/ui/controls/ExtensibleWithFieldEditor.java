
/**
 
* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui.controls;

import fr.cnrs.lacito.liftapi.model.AbstractExtensibleWithField;
import fr.cnrs.lacito.liftapi.model.LiftField;
import fr.cnrs.lacito.liftgui.ui.I18n;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.Collection;
import java.util.List;

/**
 * Programmatic editor for {@link AbstractExtensibleWithField}.
 *
 * Extends {@link ExtensibleWithoutFieldEditor} by also displaying
 * the list of {@link LiftField}s (each via a {@link FieldEditor}).
 */
public class ExtensibleWithFieldEditor extends ExtensibleWithoutFieldEditor {

    private final VBox fieldsBox = new VBox(6);

    public ExtensibleWithFieldEditor() {
        super();

        TitledPane fieldsPane = new TitledPane("Champs (Field)", fieldsBox);
        fieldsPane.setExpanded(false);
        fieldsPane.setAnimated(false);

        getChildren().add(fieldsPane);
    }

    public void setModel(AbstractExtensibleWithField model, Collection<String> availableLangs) {
        setModel(model, availableLangs, null);
    }

    public void setModel(AbstractExtensibleWithField model, Collection<String> availableLangs, ExtensibleAddActions addActions) {
        super.setModel(model, availableLangs, addActions);

        fieldsBox.getChildren().clear();
        if (model == null) return;

        if (addActions != null) {
            FlowPane fieldAddRow = new FlowPane(6, 4);
            Button addFieldBtn = new Button(I18n.get("btn.addField"));
            addFieldBtn.getStyleClass().add("example-add-button");
            addFieldBtn.setOnAction(e -> {
                List<String> types = addActions.getKnownFieldTypes();
                ChoiceDialog<String> dlg = new ChoiceDialog<>(types.isEmpty() ? null : types.get(0), types);
                dlg.setTitle(I18n.get("btn.addField"));
                dlg.setHeaderText(I18n.get("col.type"));
                dlg.showAndWait().ifPresent(type -> {
                    addActions.addField(type);
                    addActions.refresh();
                });
            });
            fieldAddRow.getChildren().add(addFieldBtn);
            fieldsBox.getChildren().add(fieldAddRow);
        }

        List<LiftField> fields = model.getFields();
        if (fields != null) {
            for (LiftField f : fields) {
                FieldEditor fe = new FieldEditor();
                fe.setField(f, availableLangs);
                fieldsBox.getChildren().add(fe);
            }
        }
    }
}
