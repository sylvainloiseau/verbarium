package com.example.ui.controls;

import fr.cnrs.lacito.liftapi.model.LiftAnnotation;
import fr.cnrs.lacito.liftapi.model.MultiText;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.*;

/**
 * Reusable editor for a {@link MultiText} (section 5.6 of the specification).
 *
 * Layout per language row:
 * <pre>
 *   lg : |_text_field_________| [-] [edit]
 * </pre>
 *
 * Below all rows, a ComboBox dropdown lets the user pick an unused language to add a new row.
 * The dropdown is disabled when the field is populated for every available language.
 */
public final class MultiTextEditor extends VBox {

    private final VBox rowsBox = new VBox(6);
    private final VBox annotationsBox = new VBox(6);
    private final ComboBox<String> addLangCombo = new ComboBox<>();
    private final TitledPane annotationsPane = new TitledPane("Annotations", annotationsBox);
    private final ObservableList<String> allLanguages = FXCollections.observableArrayList();

    private MultiText multiText;

    public MultiTextEditor() {
        super(8);
        setPadding(new Insets(4, 0, 4, 0));

        addLangCombo.setPromptText("Ajouter une langue…");
        addLangCombo.setPrefWidth(200);
        addLangCombo.setEditable(true);
        addLangCombo.setOnAction(e -> {
            String sel = safeTrim(addLangCombo.getValue());
            if (!sel.isBlank()) {
                addRowForLang(sel);
                addLangCombo.setValue(null);
                refreshAddLangChoices();
            }
        });

        annotationsPane.setExpanded(false);
        annotationsPane.setAnimated(false);

        getChildren().addAll(rowsBox, addLangCombo, annotationsPane);
    }

    public void setAvailableLanguages(Collection<String> langs) {
        allLanguages.setAll(normalizeLangs(langs));
        if (multiText != null) {
            for (String l : sorted(multiText.getLangs())) {
                if (!allLanguages.contains(l)) allLanguages.add(l);
            }
        }
        allLanguages.sort(Comparator.naturalOrder());
        refreshAddLangChoices();
        for (var node : rowsBox.getChildren()) {
            if (node instanceof Row r) r.langLabel.setText(r.boundLang);
        }
        rebuildAnnotations();
    }

    public void setMultiText(MultiText mt) {
        this.multiText = mt;
        rebuild();
    }

    private void rebuild() {
        rowsBox.getChildren().clear();
        if (multiText == null) { refreshAddLangChoices(); rebuildAnnotations(); return; }

        for (String l : sorted(multiText.getLangs())) {
            if (!allLanguages.contains(l)) allLanguages.add(l);
        }
        allLanguages.sort(Comparator.naturalOrder());

        for (String lang : sorted(multiText.getLangs())) {
            rowsBox.getChildren().add(new Row(lang));
        }
        refreshAddLangChoices();
        rebuildAnnotations();
    }

    private void addRowForLang(String lang) {
        if (multiText == null) return;
        String l = safeTrim(lang);
        if (l.isBlank()) return;
        if (!allLanguages.contains(l)) {
            allLanguages.add(l);
            allLanguages.sort(Comparator.naturalOrder());
        }
        multiText.formTextProperty(l).set("");
        rowsBox.getChildren().add(new Row(l));
    }

    private void refreshAddLangChoices() {
        Set<String> used = getUsedLanguages();
        List<String> available = new ArrayList<>();
        for (String l : allLanguages) {
            if (!used.contains(l)) available.add(l);
        }
        addLangCombo.setItems(FXCollections.observableArrayList(available));
        addLangCombo.setDisable(available.isEmpty());
    }

    private void rebuildAnnotations() {
        annotationsBox.getChildren().clear();
        List<LiftAnnotation> annotations = getAnnotationsSafe();
        if (annotations.isEmpty()) {
            annotationsBox.getChildren().add(new Label("(aucune annotation)"));
            return;
        }

        List<String> knownNames = annotations.stream()
            .map(LiftAnnotation::getName)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList();

        int i = 1;
        for (LiftAnnotation a : annotations) {
            AnnotationEditor ae = new AnnotationEditor();
            ae.setAnnotation(a, allLanguages, knownNames);

            String title = a.getName() == null || a.getName().isBlank() ? "#" + i : "#" + i + " - " + a.getName();
            TitledPane tp = new TitledPane(title, ae);
            tp.setExpanded(false);
            tp.setAnimated(false);
            annotationsBox.getChildren().add(tp);
            i++;
        }
    }

    private List<LiftAnnotation> getAnnotationsSafe() {
        if (multiText == null) return List.of();
        List<LiftAnnotation> annotations = multiText.getAnnotations();
        if (annotations == null || annotations.isEmpty()) return List.of();
        return annotations.stream().filter(Objects::nonNull).toList();
    }

    private Set<String> getUsedLanguages() {
        Set<String> used = new HashSet<>();
        for (var node : rowsBox.getChildren()) {
            if (node instanceof Row r && !r.boundLang.isBlank()) {
                used.add(r.boundLang);
            }
        }
        return used;
    }

    /* ─── Row: one language ─── */

    private final class Row extends HBox {
        private final Label langLabel = new Label();
        private final TextField textField = new TextField();
        private final Button removeButton = new Button("-");
        private final Button editButton = new Button("edit");
        private String boundLang = "";

        private Row(String lang) {
            super(6);
            setPadding(new Insets(2, 0, 2, 0));
            setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            langLabel.setPrefWidth(60);
            langLabel.setMinWidth(60);
            langLabel.setStyle("-fx-font-weight: bold;");
            langLabel.setText(safeTrim(lang));

            textField.setPromptText("texte…");
            HBox.setHgrow(textField, Priority.ALWAYS);

            removeButton.setPrefWidth(28);
            removeButton.setTooltip(new Tooltip("Supprimer cette langue"));
            removeButton.getStyleClass().add("page-button");
            removeButton.setOnAction(e -> removeRow());

            editButton.setPrefWidth(40);
            editButton.setTooltip(new Tooltip("Éditer le texte"));
            editButton.getStyleClass().add("page-button");
            editButton.setOnAction(e -> openEditDialog());

            getChildren().addAll(langLabel, textField, removeButton, editButton);

            bindToLang(safeTrim(lang));
        }

        private void removeRow() {
            if (multiText != null && !boundLang.isBlank()) {
                multiText.formTextProperty(boundLang).set("");
            }
            unbind();
            rowsBox.getChildren().remove(this);
            refreshAddLangChoices();
        }

        private void openEditDialog() {
            TextInputDialog dlg = new TextInputDialog(textField.getText());
            dlg.setTitle("Éditer le champ [" + boundLang + "]");
            dlg.setHeaderText("Contenu pour la langue « " + boundLang + " »");
            dlg.setContentText("Texte :");
            dlg.getEditor().setPrefColumnCount(40);
            dlg.showAndWait().ifPresent(val -> textField.setText(val));
        }

        private void bindToLang(String lang) {
            if (multiText == null) return;
            String l = safeTrim(lang);
            boundLang = l;
            if (l.isBlank()) return;
            textField.textProperty().bindBidirectional(multiText.formTextProperty(l));
        }

        private void unbind() {
            if (multiText == null) return;
            if (!boundLang.isBlank()) {
                try {
                    textField.textProperty().unbindBidirectional(multiText.formTextProperty(boundLang));
                } catch (Exception ignored) {}
            }
            boundLang = "";
        }
    }

    /* ─── Utilities ─── */

    private static List<String> normalizeLangs(Collection<String> langs) {
        if (langs == null) return List.of();
        Set<String> set = new LinkedHashSet<>();
        for (String l : langs) {
            String t = safeTrim(l);
            if (!t.isBlank()) set.add(t);
        }
        List<String> out = new ArrayList<>(set);
        out.sort(Comparator.naturalOrder());
        return out;
    }

    private static List<String> sorted(Collection<String> langs) {
        if (langs == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String s : langs) {
            String t = safeTrim(s);
            if (!t.isBlank()) out.add(t);
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
