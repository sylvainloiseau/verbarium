package com.example.ui;

import com.example.core.DictionaryService;
import com.example.core.LiftOpenException;
import com.example.ui.model.ExampleRow;
import com.example.ui.model.FormRow;
import com.example.ui.model.SenseValueRow;
import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftExample;
import fr.cnrs.lacito.liftapi.model.LiftFactory;
import fr.cnrs.lacito.liftapi.model.LiftPronunciation;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftapi.model.LiftTrait;
import fr.cnrs.lacito.liftapi.model.MultiText;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.geometry.Insets;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class MainController {

    private final DictionaryService dictionaryService = new DictionaryService();
    private LiftDictionary currentDictionary;
    private final Map<String, LiftEntry> entryById = new HashMap<>();
    private final ObservableList<LiftEntry> baseEntries = FXCollections.observableArrayList();
    private final FilteredList<LiftEntry> filteredEntries = new FilteredList<>(baseEntries, e -> true);
    private final SortedList<LiftEntry> sortedEntries = new SortedList<>(filteredEntries);

    @FXML
    private TableView<LiftEntry> entryTable;

    @FXML
    private TextField searchField;

    @FXML
    private Label tableCountLabel;

    @FXML
    private Label editEntryTitle;

    @FXML
    private Label editEntryCode;

    @FXML
    private TextField codeEditField;

    @FXML
    private TextField pronEditField;

    @FXML
    private TableView<FormRow> formsTable;

    @FXML
    private TableColumn<FormRow, String> formLangColumn;

    @FXML
    private TableColumn<FormRow, String> formValueColumn;

    @FXML
    private TableView<SenseValueRow> sensesTable;

    @FXML
    private TableColumn<SenseValueRow, String> senseLangColumn;

    @FXML
    private TableColumn<SenseValueRow, String> senseValueColumn;

    @FXML
    private TableView<ExampleRow> examplesTable;

    @FXML
    private TableColumn<ExampleRow, String> exTypeColumn;

    @FXML
    private TableColumn<ExampleRow, String> exPhraseColumn;

    @FXML
    private TableColumn<ExampleRow, String> exGlossColumn;

    @FXML
    private void initialize() {
        formLangColumn.setCellValueFactory(new PropertyValueFactory<>("lang"));
        formValueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));

        senseLangColumn.setCellValueFactory(new PropertyValueFactory<>("lang"));
        senseValueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));

        exTypeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        exPhraseColumn.setCellValueFactory(new PropertyValueFactory<>("fr"));
        exGlossColumn.setCellValueFactory(new PropertyValueFactory<>("gloss"));

        entryTable.setItems(sortedEntries);

        updateCountLabel();
        filteredEntries.addListener((javafx.collections.ListChangeListener<? super LiftEntry>) c -> updateCountLabel());

        entryTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV == null || newV.getId().isEmpty()) {
                editEntryTitle.setText("(sélectionne une entrée)");
                editEntryCode.setText("");
                formsTable.setItems(FXCollections.observableArrayList());
                sensesTable.setItems(FXCollections.observableArrayList());
                examplesTable.setItems(FXCollections.observableArrayList());
                return;
            }

            populateEditor(newV);
        });

        // Filters
        searchField.textProperty().addListener((obs, o, n) -> applyFilters());
        applyFilters();

        // Default: load embedded demo file on startup.
        setDictionary(loadDemoDictionary());
    }

    private void applyFilters() {
        if (filteredEntries == null) return;
        final String q = Optional.ofNullable(searchField.getText()).orElse("").trim().toLowerCase(Locale.ROOT);

        filteredEntries.setPredicate(entry -> {
            if (entry == null) return false;
            if (q.isEmpty()) return true;

            String haystack = buildSearchText(entry);
            return contains(haystack, q);
        });
        updateCountLabel();
    }

    private static boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }

    private static String safe(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private void updateCountLabel() {
        if (tableCountLabel == null || entryTable == null) return;
        int shown = entryTable.getItems() == null ? 0 : entryTable.getItems().size();
        int total = baseEntries.size();
        tableCountLabel.setText(shown + " entrées affichées sur " + total);
    }

    @FXML
    private void onImportLift() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importer un fichier LIFT (.lift)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichier LIFT (*.lift)", "*.lift"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Tous les fichiers (*.*)", "*.*"));

        File selected = chooser.showOpenDialog(entryTable.getScene().getWindow());
        if (selected == null) return;

        try {
            LiftDictionary dictionary = dictionaryService.loadFromFile(selected);
            setDictionary(dictionary);
        } catch (LiftOpenException e) {
            showError("Import LIFT", e.getMessage());
        } catch (IOException e) {
            showError("Import LIFT", "Erreur d’accès au fichier: " + e.getMessage());
        }
    }

    @FXML
    private void onSave() {
        if (currentDictionary == null) {
            showError("Enregistrer", "Aucun dictionnaire chargé.");
            return;
        }
        try {
            currentDictionary.save();
        } catch (Exception e) {
            showError("Enregistrer", "Impossible d'enregistrer le fichier LIFT: " + e.getMessage());
        }
    }

    private void setDictionary(LiftDictionary dictionary) {
        this.currentDictionary = dictionary;
        entryById.clear();
        baseEntries.clear();

        if (dictionary == null) {
            applyFilters();
            updateCountLabel();
            return;
        }

        dictionary.getLiftDictionaryComponents().getAllEntries()
                .forEach(e -> e.getId().ifPresent(id -> entryById.put(id, e)));

        baseEntries.addAll(dictionary.getLiftDictionaryComponents().getAllEntries());

        configureEntryTableColumns(dictionary);

        applyFilters();
        updateCountLabel();

        if (!entryTable.getItems().isEmpty()) {
            entryTable.getSelectionModel().selectFirst();
        }
    }

    private void configureEntryTableColumns(LiftDictionary dictionary) {
        if (entryTable == null) return;
        entryTable.getColumns().clear();

        // Code trait
        TableColumn<LiftEntry, String> codeCol = new TableColumn<>("Code");
        codeCol.setPrefWidth(140);
        codeCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(getTraitValue(cd.getValue(), "code")));

        // Determine object languages used in lexical-unit/forms across the whole dictionary
        var langs = dictionary.getLiftDictionaryComponents().getAllEntries().stream()
                .flatMap(e -> e.getForms().getLangs().stream())
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .sorted()
                .toList();

        // Grouped columns for forms (one column per language)
        TableColumn<LiftEntry, String> formGroup = new TableColumn<>("Form");
        formGroup.setPrefWidth(Math.max(220, langs.size() * 160));
        for (String lang : langs) {
            TableColumn<LiftEntry, String> langCol = new TableColumn<>(lang);
            langCol.setPrefWidth(160);
            langCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                    cd.getValue() == null
                            ? ""
                            : cd.getValue().getForms().getForm(lang).map(Form::toString).orElse("")
            ));
            formGroup.getColumns().add(langCol);
        }

        // Pronunciation (best-effort): first pronunciation, shown in its available language form(s)
        TableColumn<LiftEntry, String> pronGroup = new TableColumn<>("Pron");
        pronGroup.setPrefWidth(Math.max(160, langs.size() * 160));
        for (String lang : langs) {
            TableColumn<LiftEntry, String> pCol = new TableColumn<>(lang);
            pCol.setPrefWidth(160);
            pCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                    cd.getValue() == null
                            ? ""
                            : cd.getValue().getPronunciations().stream()
                            .findFirst()
                            .flatMap(p -> p.getProunciation().getForm(lang))
                            .or(() -> cd.getValue().getPronunciations().stream()
                                    .findFirst()
                                    .flatMap(p -> p.getProunciation().getForms().stream().findFirst()))
                            .map(Form::toString)
                            .orElse("")
            ));
            pronGroup.getColumns().add(pCol);
        }

        entryTable.getColumns().addAll(codeCol, formGroup, pronGroup);
    }

    private static String getTraitValue(LiftEntry entry, String traitName) {
        if (entry == null || traitName == null) return "";
        return entry.getTraits().stream()
                .filter(t -> traitName.equals(t.getName()))
                .findFirst()
                .map(LiftTrait::getValue)
                .orElse("");
    }

    private static String buildSearchText(LiftEntry entry) {
        if (entry == null) return "";
        StringBuilder sb = new StringBuilder();
        appendSep(sb, getTraitValue(entry, "code"));
        for (Form f : entry.getForms().getForms()) appendSep(sb, f.toString());
        for (LiftPronunciation p : entry.getPronunciations()) {
            for (Form f : p.getProunciation().getForms()) appendSep(sb, f.toString());
        }
        for (LiftSense s : entry.getSenses()) {
            for (Form f : s.getDefinition().getForms()) appendSep(sb, f.toString());
            for (Form f : s.getGloss().getForms()) appendSep(sb, f.toString());
        }
        return sb.toString();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private LiftDictionary loadDemoDictionary() {
        try (InputStream in = MainController.class.getResourceAsStream("/lift/demo.lift")) {
            if (in == null) return null;
            File tmp = Files.createTempFile("dictionary-demo-", ".lift").toFile();
            tmp.deleteOnExit();
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                in.transferTo(out);
            }
            return LiftDictionary.loadDictionaryWithFile(tmp);
        } catch (Exception e) {
            // Keep UI alive even if demo file fails; show empty tables.
            return null;
        }
    }

    private static void appendSep(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) return;
        if (!sb.isEmpty()) sb.append("; ");
        sb.append(part);
    }

    private void populateEditor(LiftEntry entry) {
        // Header
        MultiText forms = entry.getForms();
        Form preferred = forms.getForms().stream().findFirst().orElse(Form.EMPTY_FORM);
        editEntryTitle.setText(preferred == Form.EMPTY_FORM ? "(sans forme)" : preferred.toString());
        String code = entry.getTraits().stream().filter(t -> "code".equals(t.getName())).findFirst().map(LiftTrait::getValue).orElse("");
        editEntryCode.setText(code);

        codeEditField.setText(code);

        // Pronunciation: display first available pronunciation form
        String pron = entry.getPronunciations().stream()
                .findFirst()
                .flatMap(p -> p.getProunciation().getForms().stream().findFirst())
                .map(Form::toString)
                .orElse("");
        pronEditField.setText(pron);

        // Formes tab: all lexical-unit forms
        ObservableList<FormRow> formRows = FXCollections.observableArrayList();
        for (Form f : forms.getForms()) {
            formRows.add(new FormRow(f.getLang(), f.toString()));
        }
        formsTable.setItems(formRows);

        // Sens tab: show definitions in meta languages for each sense (flatten)
        ObservableList<SenseValueRow> senseRows = FXCollections.observableArrayList();
        for (LiftSense s : entry.getSenses()) {
            for (Form f : s.getDefinition().getForms()) {
                senseRows.add(new SenseValueRow(f.getLang(), f.toString()));
            }
            for (Form f : s.getGloss().getForms()) {
                // also show glosses if present
                senseRows.add(new SenseValueRow(f.getLang(), f.toString()));
            }
        }
        sensesTable.setItems(senseRows);

        // Exemples: all examples from all senses
        ObservableList<ExampleRow> exampleRows = FXCollections.observableArrayList();
        for (LiftSense s : entry.getSenses()) {
            for (LiftExample ex : s.getExamples()) {
                String phrase = ex.getExample().getForm("fr").orElseGet(() -> ex.getExample().getForms().stream().findFirst().orElse(Form.EMPTY_FORM)).toString();
                String gloss = "";
                if (!ex.getTranslations().isEmpty()) {
                    MultiText tr = ex.getTranslations().getOrDefault("", ex.getTranslations().values().stream().findFirst().orElse(null));
                    if (tr != null) {
                        gloss = tr.getForm("en").orElseGet(() -> tr.getForms().stream().findFirst().orElse(Form.EMPTY_FORM)).toString();
                    }
                }
                String type = ex.getSource().orElse("phrase");
                exampleRows.add(new ExampleRow(type, phrase, gloss));
            }
        }
        examplesTable.setItems(exampleRows);
    }

    @FXML
    private void onModifyEntry() {
        LiftEntry entry = entryTable.getSelectionModel().getSelectedItem();
        if (entry == null || entry.getId().isEmpty()) {
            showError("Modification", "Aucune entrée sélectionnée.");
            return;
        }
        if (currentDictionary == null) {
            showError("Modification", "Aucun dictionnaire chargé.");
            return;
        }

        LiftFactory factory = getFactory(currentDictionary);
        if (factory == null) {
            showError("Modification", "Impossible d'accéder à la factory interne (édition non supportée).");
            return;
        }

        boolean saved = showEditEntryDialog(factory, entry);
        if (!saved) return;

        // Persist changes (explicit save action)
        onSave();

        // Refresh view
        applyFilters();
        entryTable.refresh();
        populateEditor(entry);
    }

    private boolean showEditEntryDialog(LiftFactory factory, LiftEntry entry) {
        if (entryTable == null || entryTable.getScene() == null) return false;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Modifier l’entrée");
        dialog.initOwner(entryTable.getScene().getWindow());
        dialog.setResizable(true);

        ButtonType saveType = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefSize(640, 420);
        dialog.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.setPrefWidth(600);

        // Current values
        String currentCode = entry.getTraits().stream()
                .filter(t -> "code".equals(t.getName()))
                .findFirst()
                .map(LiftTrait::getValue)
                .orElse("");
        String currentPron = entry.getPronunciations().stream()
                .findFirst()
                .flatMap(p -> p.getProunciation().getForms().stream().findFirst())
                .map(Form::toString)
                .orElse("");
        MultiText forms = entry.getForms();
        Form preferred = forms.getForms().stream().findFirst().orElse(Form.EMPTY_FORM);
        String derivedLangCode = extractLangCodeFromCodeTrait(currentCode)
                .orElseGet(() -> preferred == Form.EMPTY_FORM ? "" : preferred.getLang());

        // Inputs
        TextField codeField = new TextField(currentCode);
        TextField pronField = new TextField(currentPron);
        TextField langCodeField = new TextField(derivedLangCode);

        Label langNamePreview = new Label(derivedLangCode);
        langNamePreview.getStyleClass().add("muted");
        langCodeField.textProperty().addListener((obs, o, n) -> langNamePreview.setText(safeTrim(n)));

        int r = 0;
        grid.add(new Label("Code"), 0, r);
        grid.add(codeField, 1, r++);
        grid.add(new Label("Prononciation"), 0, r);
        grid.add(pronField, 1, r++);
        grid.add(new Label("Langue (code)"), 0, r);
        grid.add(langCodeField, 1, r++);
        grid.add(new Label("Langue (nom)"), 0, r);
        grid.add(langNamePreview, 1, r);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != saveType) {
            return false; // cancelled
        }

        String newCode = safeTrim(codeField.getText());
        String newPronValue = safeTrim(pronField.getText());
        String newLangCode = safeTrim(langCodeField.getText());

        // Apply changes
        replaceTrait(factory, entry, "code", newCode);

        // Change preferred form language when requested (best-effort)
        if (!newLangCode.isBlank()) {
            try {
                replacePreferredFormLang(entry, newLangCode);
            } catch (Exception e) {
                showError("Modification", "Impossible de modifier la langue de l’entrée: " + e.getMessage());
                return false;
            }
        }

        // Update pronunciation (use explicit lang if provided)
        replacePronunciation(factory, entry, newCode, newLangCode, newPronValue);
        return true;
    }

    private static void replacePreferredFormLang(LiftEntry entry, String newLangCode) {
        if (entry == null || newLangCode == null) return;
        String lang = newLangCode.trim();
        if (lang.isEmpty()) return;

        MultiText mt = entry.getForms();
        if (mt == null || mt.isEmpty()) return;

        Form preferred = mt.getForm("fr").orElseGet(() -> mt.getForms().stream().findFirst().orElse(Form.EMPTY_FORM));
        if (preferred == Form.EMPTY_FORM) return;
        String oldLang = preferred.getLang();
        if (lang.equals(oldLang)) return;

        String text = preferred.toString();

        // If a form already exists for the new language, overwrite its text and remove the old one
        mt.getForm(lang).ifPresentOrElse(existing -> {
            existing.changeText(text);
            try {
                mt.removeForm(oldLang);
            } catch (Exception ignored) {
            }
        }, () -> {
            // Otherwise, move preferred text to new language
            try {
                mt.removeForm(oldLang);
            } catch (Exception ignored) {
            }
            mt.add(new Form(lang, text));
        });
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private static LiftFactory getFactory(LiftDictionary d) {
        if (d == null) return null;
        if (d.getLiftDictionaryComponents() instanceof LiftFactory lf) return lf;
        return null;
    }

    private static void replaceTrait(LiftFactory factory, LiftEntry target, String name, String value) {
        // Remove existing traits with same name
        target.getTraits().removeIf(t -> name.equals(t.getName()));
        if (value != null && !value.isBlank()) {
            factory.createTrait(name, value, target);
        }
    }

    private static void replacePronunciation(LiftFactory factory, LiftEntry entry, String codeTrait, String langOverride, String pronunciationValue) {
        // Blank => remove pronunciations
        if (pronunciationValue == null || pronunciationValue.isBlank()) {
            entry.getPronunciations().clear();
            return;
        }

        String langCode = Optional.ofNullable(langOverride)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElseGet(() -> extractLangCodeFromCodeTrait(codeTrait).orElseGet(() -> {
                    Form preferred = entry.getForms().getForm("fr").orElseGet(() -> entry.getForms().getForms().stream().findFirst().orElse(Form.EMPTY_FORM));
                    return preferred == Form.EMPTY_FORM ? "fr" : preferred.getLang();
                }));

        LiftPronunciation p;
        if (entry.getPronunciations().isEmpty()) {
            p = factory.createPronunciation(entry);
        } else {
            p = entry.getPronunciations().getFirst();
        }

        MultiText mt = p.getProunciation();
        // Clear existing forms
        for (String l : List.copyOf(mt.getLangs())) {
            try {
                mt.removeForm(l);
            } catch (Exception ignored) {
                // ignore
            }
        }
        mt.add(new Form(langCode, pronunciationValue));
    }

    private static Optional<String> extractLangCodeFromCodeTrait(String code) {
        if (code == null) return Optional.empty();
        int idx = code.lastIndexOf('/');
        if (idx < 0 || idx == code.length() - 1) return Optional.empty();
        String lang = code.substring(idx + 1).trim();
        return lang.isEmpty() ? Optional.empty() : Optional.of(lang);
    }

    // Intentionally no language-name mapping/hardcoding.
}

