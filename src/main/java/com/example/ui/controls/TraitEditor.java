package com.example.ui.controls;

import fr.cnrs.lacito.liftapi.model.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.StringJoiner;

/**
 * Editor for a single {@link LiftTrait}.
 * <p>
 * The value widget adapts to the {@link FieldDefinitionType} of the associated
 * {@link LiftFieldAndTraitDefinition}:
 * <ul>
 *   <li>{@code datetime} → {@link DatePicker}</li>
 *   <li>{@code integer} → integer-only {@link TextField}</li>
 *   <li>{@code option / option-collection / option-sequence} → tree/list picker
 *       from the resolved {@link LiftHeaderRange}</li>
 *   <li>otherwise → editable {@link ComboBox} (default)</li>
 * </ul>
 */
public final class TraitEditor extends VBox {

    private final ComboBox<String> nameCombo = new ComboBox<>();
    private final VBox valueBox = new VBox(4);
    private final VBox annotationsBox = new VBox(6);

    private LiftTrait trait;

    public TraitEditor() {
        super(6);
        setPadding(new Insets(4));
        setStyle("-fx-border-color: #cde; -fx-border-radius: 4; -fx-background-color: #f5f8fc; -fx-background-radius: 4;");

        nameCombo.setEditable(false);
        nameCombo.setMaxWidth(Double.MAX_VALUE);
        nameCombo.setPromptText("nom du trait");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label("Nom"), 0, 0);
        grid.add(nameCombo, 1, 0);
        grid.add(new Label("Valeur"), 0, 1);
        grid.add(valueBox, 1, 1);
        GridPane.setHgrow(nameCombo, Priority.ALWAYS);
        GridPane.setHgrow(valueBox, Priority.ALWAYS);

        TitledPane annoPane = new TitledPane("Annotations", annotationsBox);
        annoPane.setExpanded(false);
        annoPane.setAnimated(false);

        getChildren().addAll(grid, annoPane);
    }

    /**
     * @param t               the trait to edit
     * @param availableLangs  languages for annotation sub-editors
     * @param traitNames      all known trait names in the dictionary
     * @param valuesForName   known values for each trait name (fallback when no definition)
     * @param definition      optional field/trait definition carrying @type and resolved range
     */
    public void setTrait(LiftTrait t, Collection<String> availableLangs,
                         Collection<String> traitNames, Map<String, Set<String>> valuesForName,
                         Optional<LiftFieldAndTraitDefinition> definition) {
        this.trait = t;
        if (t == null) {
            nameCombo.getItems().clear();
            valueBox.getChildren().clear();
            annotationsBox.getChildren().clear();
            return;
        }

        nameCombo.setItems(FXCollections.observableArrayList(
            traitNames instanceof List ? (List<String>) traitNames : new ArrayList<>(traitNames)));
        nameCombo.setValue(t.getName());

        valueBox.getChildren().setAll(buildValueWidget(t, definition, valuesForName));

        annotationsBox.getChildren().clear();
        List<LiftAnnotation> annos = t.getAnnotations();
        if (annos != null) {
            for (LiftAnnotation a : annos) {
                AnnotationEditor ae = new AnnotationEditor();
                ae.setAnnotation(a, availableLangs, Set.of());
                annotationsBox.getChildren().add(ae);
            }
        }
    }

    private Node buildValueWidget(LiftTrait t, Optional<LiftFieldAndTraitDefinition> defOpt,
                                   Map<String, Set<String>> valuesForName) {
        if (defOpt.isPresent()) {
            LiftFieldAndTraitDefinition def = defOpt.get();
            Optional<FieldDefinitionType> typeOpt = def.getDefinitionType();
            if (typeOpt.isPresent()) {
                return switch (typeOpt.get()) {
                    case DATETIME -> buildDatePicker(t);
                    case INTEGER  -> buildIntegerField(t);
                    case OPTION, OPTION_COLLECTION, OPTION_SEQUENCE -> {
                        Optional<LiftHeaderRange> rangeOpt = def.getResolvedRange();
                        yield rangeOpt.isPresent()
                            ? buildRangePicker(t, rangeOpt.get(), typeOpt.get())
                            : buildDefaultCombo(t, valuesForName);
                    }
                    default -> buildDefaultCombo(t, valuesForName);
                };
            }
        }
        return buildDefaultCombo(t, valuesForName);
    }

    private DatePicker buildDatePicker(LiftTrait t) {
        DatePicker dp = new DatePicker();
        dp.setMaxWidth(Double.MAX_VALUE);
        try {
            if (t.getValue() != null && !t.getValue().isBlank())
                dp.setValue(LocalDate.parse(t.getValue().substring(0, 10)));
        } catch (DateTimeParseException ignored) {}
        dp.valueProperty().addListener((obs, o, n) -> {
            if (n != null) t.valueProperty().set(n.toString());
        });
        return dp;
    }

    private TextField buildIntegerField(LiftTrait t) {
        TextField tf = new TextField(t.getValue());
        tf.setMaxWidth(Double.MAX_VALUE);
        tf.textProperty().addListener((obs, o, n) -> {
            if (n.matches("-?\\d*")) t.valueProperty().set(n);
            else tf.setText(o);
        });
        return tf;
    }

    /**
     * For option/option-collection/option-sequence: show a tree popup where
     * range-elements are organised by their @parent attribute.
     * The selected abbreviation(s) are shown in a read-only label; clicking opens the picker.
     */
    private Node buildRangePicker(LiftTrait t, LiftHeaderRange range, FieldDefinitionType type) {
        boolean multiSelect = type == FieldDefinitionType.OPTION_COLLECTION
                           || type == FieldDefinitionType.OPTION_SEQUENCE;

        Label displayLabel = new Label(t.getValue());
        displayLabel.setMaxWidth(Double.MAX_VALUE);
        displayLabel.setStyle("-fx-border-color: #aab; -fx-padding: 3 6 3 6; -fx-background-color: white; -fx-background-radius: 3;");

        Button pickBtn = new Button("…");
        pickBtn.setOnAction(e -> {
            String chosen = showRangePickerDialog(range, t.getValue(), multiSelect);
            if (chosen != null) {
                t.valueProperty().set(chosen);
                displayLabel.setText(chosen);
            }
        });

        VBox box = new VBox(4, displayLabel, pickBtn);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private ComboBox<String> buildDefaultCombo(LiftTrait t, Map<String, Set<String>> valuesForName) {
        ComboBox<String> combo = new ComboBox<>();
        combo.setEditable(true);
        combo.setMaxWidth(Double.MAX_VALUE);
        Set<String> knownValues = valuesForName != null
            ? valuesForName.getOrDefault(t.getName(), Set.of()) : Set.of();
        combo.setItems(FXCollections.observableArrayList(new TreeSet<>(knownValues)));
        combo.setValue(t.getValue());
        combo.getEditor().textProperty().bindBidirectional(t.valueProperty());
        return combo;
    }

    /**
     * Shows a Dialog with a TreeView of range-elements (using @parent hierarchy).
     * Returns the selected element's abbreviation, or null if cancelled.
     */
    private String showRangePickerDialog(LiftHeaderRange range, String currentValue, boolean multiSelect) {
        Dialog<String> dlg = new Dialog<>();
        dlg.setTitle(range.getId());
        dlg.setHeaderText("Sélectionner une valeur");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.setResizable(true);
        dlg.getDialogPane().setPrefSize(360, 400);

        TreeItem<LiftHeaderRangeElement> root = new TreeItem<>(null);
        root.setExpanded(true);
        Map<String, TreeItem<LiftHeaderRangeElement>> itemMap = new LinkedHashMap<>();

        for (LiftHeaderRangeElement re : range.getRangeElements()) {
            TreeItem<LiftHeaderRangeElement> item = new TreeItem<>(re);
            item.setExpanded(true);
            itemMap.put(re.getId(), item);
        }
        for (LiftHeaderRangeElement re : range.getRangeElements()) {
            TreeItem<LiftHeaderRangeElement> item = itemMap.get(re.getId());
            String pid = re.getParentId().orElse(null);
            if (pid != null && itemMap.containsKey(pid)) itemMap.get(pid).getChildren().add(item);
            else root.getChildren().add(item);
        }

        TreeView<LiftHeaderRangeElement> tree = new TreeView<>(root);
        tree.setShowRoot(false);
        tree.getSelectionModel().setSelectionMode(
            multiSelect ? SelectionMode.MULTIPLE : SelectionMode.SINGLE);
        tree.setCellFactory(tv -> new TreeCell<>() {
            @Override protected void updateItem(LiftHeaderRangeElement item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                String abbrev = item.getAbbrev().getForms().stream().findFirst()
                    .map(f -> f.toPlainText()).orElse("");
                String label = item.getLabel().getForms().stream().findFirst()
                    .map(f -> f.toPlainText()).orElse(item.getId());
                setText(abbrev.isBlank() ? label : abbrev + " – " + label);
            }
        });

        // Pre-select current value
        String[] current = currentValue == null ? new String[0] : currentValue.split("\\s+");
        Set<String> currentSet = new HashSet<>(Arrays.asList(current));
        for (TreeItem<LiftHeaderRangeElement> ti : itemMap.values()) {
            if (ti.getValue() != null) {
                String abbrev = ti.getValue().getAbbrev().getForms().stream().findFirst()
                    .map(f -> f.toPlainText()).orElse(ti.getValue().getId());
                if (currentSet.contains(abbrev) || currentSet.contains(ti.getValue().getId()))
                    tree.getSelectionModel().select(ti);
            }
        }

        dlg.getDialogPane().setContent(new ScrollPane(tree) {{ setFitToWidth(true); setFitToHeight(true); }});

        dlg.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            var selected = tree.getSelectionModel().getSelectedItems();
            if (selected.isEmpty()) return null;
            StringJoiner sj = new StringJoiner(" ");
            for (var ti : selected) {
                if (ti.getValue() != null) {
                    String abbrev = ti.getValue().getAbbrev().getForms().stream().findFirst()
                        .map(f -> f.toPlainText()).orElse(ti.getValue().getId());
                    sj.add(abbrev.isBlank() ? ti.getValue().getId() : abbrev);
                }
            }
            return sj.toString();
        });

        return dlg.showAndWait().orElse(null);
    }

    /** Backward-compatible overload — no definition data. */
    public void setTrait(LiftTrait t, Collection<String> availableLangs,
                         Collection<String> traitNames, Map<String, Set<String>> valuesForName) {
        setTrait(t, availableLangs, traitNames, valuesForName, Optional.empty());
    }

    /** Backward-compatible overload (no dropdown data). */
    public void setTrait(LiftTrait t, Collection<String> availableLangs) {
        setTrait(t, availableLangs, List.of(), Map.of(), Optional.empty());
    }
}
