package com.example.ui.controls;

import fr.cnrs.lacito.liftapi.model.MultiText;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Programmatic editor for a {@link MultiText}.
 *
 * One row per language: language dropdown + text field (bound to MultiText.formTextProperty(lang)).
 */
public final class MultiTextEditor extends VBox {

    private final VBox rowsBox = new VBox(8);
    private final ObservableList<String> languageChoices = FXCollections.observableArrayList();

    private MultiText multiText;

    public MultiTextEditor() {
        super(10);
        setPadding(new Insets(6, 0, 6, 0));

        HBox header = new HBox(10);
        Label title = new Label("MultiText");
        title.getStyleClass().add("section-header-title");
        Button add = new Button("+ Langue");
        add.setOnAction(e -> addEmptyRow());
        header.getChildren().addAll(title, spacer(), add);

        getChildren().addAll(header, rowsBox);
    }

    public void setAvailableLanguages(Collection<String> langs) {
        languageChoices.setAll(normalizeLangs(langs));
        // keep current langs even if not in list
        if (multiText != null) {
            for (String l : sorted(multiText.getLangs())) {
                if (!languageChoices.contains(l)) languageChoices.add(l);
            }
        }
        languageChoices.sort(Comparator.naturalOrder());
        // update existing combos
        for (var node : rowsBox.getChildren()) {
            if (node instanceof Row r) {
                r.langCombo.setItems(languageChoices);
            }
        }
    }

    public void setMultiText(MultiText mt) {
        this.multiText = mt;
        rebuild();
    }

    private void rebuild() {
        rowsBox.getChildren().clear();
        if (multiText == null) return;

        // ensure choices include current langs
        for (String l : sorted(multiText.getLangs())) {
            if (!languageChoices.contains(l)) languageChoices.add(l);
        }
        languageChoices.sort(Comparator.naturalOrder());

        for (String lang : sorted(multiText.getLangs())) {
            rowsBox.getChildren().add(new Row(lang));
        }
        if (rowsBox.getChildren().isEmpty()) {
            addEmptyRow();
        }
    }

    private void addEmptyRow() {
        rowsBox.getChildren().add(new Row(""));
    }

    private HBox spacer() {
        HBox s = new HBox();
        HBox.setHgrow(s, Priority.ALWAYS);
        return s;
    }

    private final class Row extends HBox {
        private final ComboBox<String> langCombo = new ComboBox<>();
        private final TextField textField = new TextField();
        private final Button removeButton = new Button("✕");

        private String boundLang = "";

        private Row(String initialLang) {
            super(10);
            setPadding(new Insets(2, 0, 2, 0));

            langCombo.setEditable(true);
            langCombo.setPrefWidth(140);
            langCombo.setItems(languageChoices);
            langCombo.setPromptText("lang");
            langCombo.setValue(safeTrim(initialLang));

            textField.setPromptText("texte…");
            HBox.setHgrow(textField, Priority.ALWAYS);

            removeButton.setOnAction(e -> removeRow());
            removeButton.getStyleClass().add("page-button");

            getChildren().addAll(langCombo, textField, removeButton);

            // bind to initial language if present
            bindToLang(langCombo.getValue());

            langCombo.valueProperty().addListener((obs, oldV, newV) -> onLangChanged(oldV, newV));
        }

        private void removeRow() {
            // clear value for current lang (removes form)
            if (multiText != null && boundLang != null && !boundLang.isBlank()) {
                multiText.formTextProperty(boundLang).set("");
            }
            unbind();
            rowsBox.getChildren().remove(this);
            if (rowsBox.getChildren().isEmpty()) {
                addEmptyRow();
            }
        }

        private void onLangChanged(String oldLang, String newLang) {
            String oldL = safeTrim(oldLang);
            String newL = safeTrim(newLang);
            if (Objects.equals(oldL, newL)) return;

            String currentText = safeTrim(textField.getText());
            unbind();

            // remove old lang value
            if (multiText != null && !oldL.isBlank()) {
                multiText.formTextProperty(oldL).set("");
            }

            // set new lang value (best-effort)
            if (multiText != null && !newL.isBlank()) {
                if (!languageChoices.contains(newL)) {
                    languageChoices.add(newL);
                    languageChoices.sort(Comparator.naturalOrder());
                }
                multiText.formTextProperty(newL).set(currentText);
            }

            bindToLang(newL);
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
            if (boundLang != null && !boundLang.isBlank()) {
                try {
                    textField.textProperty().unbindBidirectional(multiText.formTextProperty(boundLang));
                } catch (Exception ignored) {
                }
            }
            boundLang = "";
        }
    }

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

