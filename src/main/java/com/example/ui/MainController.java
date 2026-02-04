package com.example.ui;

import com.example.core.DictionaryService;
import com.example.core.LiftOpenException;
import com.example.ui.model.ExampleRow;
import com.example.ui.model.EntryRow;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
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
    private final ObservableList<EntryRow> baseRows = FXCollections.observableArrayList();
    private final FilteredList<EntryRow> filteredRows = new FilteredList<>(baseRows, r -> true);
    private final SortedList<EntryRow> sortedRows = new SortedList<>(filteredRows);

    @FXML
    private TableView<EntryRow> entryTable;

    @FXML
    private TextField searchField;

    @FXML
    private TableColumn<EntryRow, String> entryColumn;

    @FXML
    private TableColumn<EntryRow, String> codeColumn;

    @FXML
    private TableColumn<EntryRow, String> pronColumn;

    @FXML
    private TableColumn<EntryRow, String> langColumn;

    @FXML
    private TableColumn<EntryRow, String> defColumn;

    @FXML
    private TableColumn<EntryRow, String> contentColumn;

    @FXML
    private TableColumn<EntryRow, String> metaLangColumn;

    @FXML
    private ComboBox<String> filterByCombo;

    @FXML
    private ComboBox<String> languageFilterCombo;

    @FXML
    private ComboBox<String> sortByCombo;

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
    private TextField dialectEditField;

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
        entryColumn.setCellValueFactory(new PropertyValueFactory<>("entry"));
        codeColumn.setCellValueFactory(new PropertyValueFactory<>("code"));
        pronColumn.setCellValueFactory(new PropertyValueFactory<>("pron"));
        langColumn.setCellValueFactory(new PropertyValueFactory<>("lang"));
        defColumn.setCellValueFactory(new PropertyValueFactory<>("def"));
        contentColumn.setCellValueFactory(new PropertyValueFactory<>("content"));
        metaLangColumn.setCellValueFactory(new PropertyValueFactory<>("metaLang"));

        formLangColumn.setCellValueFactory(new PropertyValueFactory<>("lang"));
        formValueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));

        senseLangColumn.setCellValueFactory(new PropertyValueFactory<>("lang"));
        senseValueColumn.setCellValueFactory(new PropertyValueFactory<>("value"));

        exTypeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        exPhraseColumn.setCellValueFactory(new PropertyValueFactory<>("fr"));
        exGlossColumn.setCellValueFactory(new PropertyValueFactory<>("gloss"));

        filterByCombo.setItems(FXCollections.observableArrayList(
                "Tous",
                "Entrée",
                "Code",
                "Prononciation",
                "Langue",
                "Définitions",
                "Métalangue",
                "Contenu linguistique"
        ));
        filterByCombo.getSelectionModel().selectFirst();
        entryTable.setItems(sortedRows);
        refreshLanguageFilter();

        sortByCombo.setItems(FXCollections.observableArrayList(
                "Entrée",
                "Code",
                "Prononciation",
                "Langue",
                "Définitions",
                "Métalangue"
        ));
        sortByCombo.getSelectionModel().selectFirst();

        updateCountLabel();
        filteredRows.addListener((javafx.collections.ListChangeListener<? super EntryRow>) c -> updateCountLabel());

        entryTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV == null || newV.getId() == null) {
                editEntryTitle.setText("(sélectionne une entrée)");
                editEntryCode.setText("");
                formsTable.setItems(FXCollections.observableArrayList());
                sensesTable.setItems(FXCollections.observableArrayList());
                examplesTable.setItems(FXCollections.observableArrayList());
                return;
            }

            LiftEntry selected = entryById.get(newV.getId());
            if (selected == null) {
                editEntryTitle.setText(newV.getEntry());
                editEntryCode.setText(newV.getCode());
                formsTable.setItems(FXCollections.observableArrayList());
                sensesTable.setItems(FXCollections.observableArrayList());
                examplesTable.setItems(FXCollections.observableArrayList());
                return;
            }
            populateEditor(selected);
        });

        // Filters
        searchField.textProperty().addListener((obs, o, n) -> applyFilters());
        languageFilterCombo.valueProperty().addListener((obs, o, n) -> applyFilters());
        filterByCombo.valueProperty().addListener((obs, o, n) -> applyFilters());
        sortByCombo.valueProperty().addListener((obs, o, n) -> applySort(sortedRows));
        applyFilters();
        applySort(sortedRows);

        // Default: load embedded demo file on startup.
        setDictionary(loadDemoDictionary());
    }

    private void applyFilters() {
        if (filteredRows == null) return;
        final String q = Optional.ofNullable(searchField.getText()).orElse("").trim().toLowerCase(Locale.ROOT);
        final String lang = Optional.ofNullable(languageFilterCombo.getValue()).orElse("Toutes");
        final String filterBy = Optional.ofNullable(filterByCombo.getValue()).orElse("Tous");

        filteredRows.setPredicate(row -> {
            if (row == null) return false;
            if (!"Toutes".equals(lang) && (row.getLang() == null || !lang.equals(row.getLang()))) return false;
            if (q.isEmpty()) return true;

            return switch (filterBy) {
                case "Entrée" -> contains(row.getEntry(), q);
                case "Code" -> contains(row.getCode(), q);
                case "Prononciation" -> contains(row.getPron(), q);
                case "Langue" -> contains(row.getLang(), q);
                case "Définitions" -> contains(row.getDef(), q);
                case "Métalangue" -> contains(row.getMetaLang(), q);
                case "Contenu linguistique" -> contains(row.getContent(), q);
                default -> contains(row.getEntry(), q)
                        || contains(row.getCode(), q)
                        || contains(row.getPron(), q)
                        || contains(row.getLang(), q)
                        || contains(row.getDef(), q)
                        || contains(row.getMetaLang(), q)
                        || contains(row.getContent(), q);
            };
        });
        updateCountLabel();
    }

    private void applySort(SortedList<EntryRow> sortedRows) {
        if (sortedRows == null) return;
        final String sortBy = Optional.ofNullable(sortByCombo.getValue()).orElse("Entrée");
        Comparator<EntryRow> cmp = switch (sortBy) {
            case "Code" -> Comparator.comparing(r -> safe(r.getCode()));
            case "Prononciation" -> Comparator.comparing(r -> safe(r.getPron()));
            case "Langue" -> Comparator.comparing(r -> safe(r.getLang()));
            case "Définitions" -> Comparator.comparing(r -> safe(r.getDef()));
            case "Métalangue" -> Comparator.comparing(r -> safe(r.getMetaLang()));
            default -> Comparator.comparing(r -> safe(r.getEntry()));
        };
        sortedRows.setComparator(cmp);
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
        int total = baseRows.size();
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

    private void setDictionary(LiftDictionary dictionary) {
        this.currentDictionary = dictionary;
        entryById.clear();
        baseRows.clear();

        if (dictionary == null) {
            refreshLanguageFilter();
            applyFilters();
            updateCountLabel();
            return;
        }

        dictionary.getLiftDictionaryComponents().getAllEntries()
                .forEach(e -> e.getId().ifPresent(id -> entryById.put(id, e)));

        baseRows.addAll(dictionary.getLiftDictionaryComponents().getAllEntries().stream()
                .map(this::toRow)
                .toList());

        refreshLanguageFilter();
        applyFilters();
        applySort(sortedRows);
        updateCountLabel();

        if (!entryTable.getItems().isEmpty()) {
            entryTable.getSelectionModel().selectFirst();
        }
    }

    private void refreshLanguageFilter() {
        List<String> langs = baseRows.stream()
                .map(EntryRow::getLang)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .sorted()
                .toList();

        ObservableList<String> items = FXCollections.observableArrayList();
        items.add("Toutes");
        items.addAll(langs);
        languageFilterCombo.setItems(items);

        if (languageFilterCombo.getSelectionModel().isEmpty()) {
            languageFilterCombo.getSelectionModel().selectFirst();
        }
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

    private EntryRow toRow(LiftEntry e) {
        String id = e.getId().orElse("");

        // Entrée: prefer fr, else first form.
        MultiText forms = e.getForms();
        Form preferred = forms.getForm("fr").orElseGet(() -> forms.getForms().stream().findFirst().orElse(Form.EMPTY_FORM));
        String entry = preferred == Form.EMPTY_FORM ? "" : preferred.toString();

        // Code + Métalangue (dialecte): from traits
        Map<String, List<LiftTrait>> traitsByName = e.getTraits().stream().collect(Collectors.groupingBy(LiftTrait::getName));
        String code = traitsByName.getOrDefault("code", List.of()).stream().findFirst().map(LiftTrait::getValue).orElse("");
        String metaLang = traitsByName.getOrDefault("dialecte", List.of()).stream().findFirst().map(LiftTrait::getValue).orElse("");

        // Langue: derive from code suffix (e.g. n./fr), fallback to preferred form lang.
        String langCode = extractLangCodeFromCodeTrait(code).orElseGet(() -> preferred == Form.EMPTY_FORM ? "" : preferred.getLang());
        String lang = displayLang(langCode);

        // Pronunciation: first pronunciation form (prefer fr)
        String pron = e.getPronunciations().stream()
                .findFirst()
                .flatMap(p -> p.getMainMultiText().getForm("fr").or(() -> p.getMainMultiText().getForms().stream().findFirst()))
                .map(Form::toString)
                .orElse("");

        // Definitions + content: from first sense
        Optional<LiftSense> firstSense = e.getSenses().stream().findFirst();
        String defs = firstSense
                .map(s -> joinForms(s.getDefinition(), List.of("fr", "en")))
                .orElse("");

        String content = firstSense
                .map(s -> joinForms(s.getGloss(), List.of("en", "fr", "es", "de", "it", "pt", "ru", "ar", "zh", "ja", "tpi")))
                .orElse("");

        return new EntryRow(id, entry, code, pron, lang, defs, content, metaLang);
    }

    private static String joinForms(MultiText mt, List<String> preferredLangOrder) {
        if (mt == null || mt.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String l : preferredLangOrder) {
            mt.getForm(l).ifPresent(f -> appendSep(sb, f.toString()));
        }
        // Append remaining langs
        for (Form f : mt.getForms()) {
            if (!preferredLangOrder.contains(f.getLang())) {
                appendSep(sb, f.toString());
            }
        }
        return sb.toString();
    }

    private static void appendSep(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) return;
        if (!sb.isEmpty()) sb.append("; ");
        sb.append(part);
    }

    private void populateEditor(LiftEntry entry) {
        // Header
        MultiText forms = entry.getForms();
        Form preferred = forms.getForm("fr").orElseGet(() -> forms.getForms().stream().findFirst().orElse(Form.EMPTY_FORM));
        editEntryTitle.setText(preferred == Form.EMPTY_FORM ? "(sans forme)" : preferred.toString());
        String code = entry.getTraits().stream().filter(t -> "code".equals(t.getName())).findFirst().map(LiftTrait::getValue).orElse("");
        String dialecte = entry.getTraits().stream().filter(t -> "dialecte".equals(t.getName())).findFirst().map(LiftTrait::getValue).orElse("");
        editEntryCode.setText(code);

        codeEditField.setText(code);
        dialectEditField.setText(dialecte);

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
            formRows.add(new FormRow(displayLang(f.getLang()), f.toString()));
        }
        formsTable.setItems(formRows);

        // Sens tab: show definitions in meta languages for each sense (flatten)
        ObservableList<SenseValueRow> senseRows = FXCollections.observableArrayList();
        for (LiftSense s : entry.getSenses()) {
            for (Form f : s.getDefinition().getForms()) {
                senseRows.add(new SenseValueRow(displayLang(f.getLang()), f.toString()));
            }
            for (Form f : s.getGloss().getForms()) {
                // also show glosses if present
                senseRows.add(new SenseValueRow(displayLang(f.getLang()), f.toString()));
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
        EntryRow selectedRow = entryTable.getSelectionModel().getSelectedItem();
        if (selectedRow == null || selectedRow.getId() == null) {
            showError("Modification", "Aucune entrée sélectionnée.");
            return;
        }
        if (currentDictionary == null) {
            showError("Modification", "Aucun dictionnaire chargé.");
            return;
        }

        LiftEntry entry = entryById.get(selectedRow.getId());
        if (entry == null) {
            showError("Modification", "Entrée introuvable dans le dictionnaire en mémoire.");
            return;
        }

        LiftFactory factory = getFactory(currentDictionary);
        if (factory == null) {
            showError("Modification", "Impossible d'accéder à la factory interne (édition non supportée).");
            return;
        }

        boolean saved = showEditEntryDialog(factory, entry);
        if (!saved) return;

        // Persist changes
        try {
            currentDictionary.save(); // overwrite file it was read from
        } catch (Exception e) {
            showError("Modification", "Impossible d'enregistrer le fichier LIFT: " + e.getMessage());
            return;
        }

        // Refresh only the modified row (keeps filters/sort and selection)
        String keepId = selectedRow.getId();
        EntryRow updatedRow = toRow(entry);
        for (int i = 0; i < baseRows.size(); i++) {
            if (keepId.equals(baseRows.get(i).getId())) {
                baseRows.set(i, updatedRow);
                break;
            }
        }
        refreshLanguageFilter();
        applyFilters();
        applySort(sortedRows);
        selectRowById(keepId);
        entryTable.refresh();
        populateEditor(entry);
    }

    private boolean showEditEntryDialog(LiftFactory factory, LiftEntry entry) {
        if (entryTable == null || entryTable.getScene() == null) return false;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Modifier l’entrée");
        dialog.initOwner(entryTable.getScene().getWindow());

        ButtonType saveType = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        // Current values
        String currentCode = entry.getTraits().stream()
                .filter(t -> "code".equals(t.getName()))
                .findFirst()
                .map(LiftTrait::getValue)
                .orElse("");
        String currentDialecte = entry.getTraits().stream()
                .filter(t -> "dialecte".equals(t.getName()))
                .findFirst()
                .map(LiftTrait::getValue)
                .orElse("");
        String currentPron = entry.getPronunciations().stream()
                .findFirst()
                .flatMap(p -> p.getProunciation().getForms().stream().findFirst())
                .map(Form::toString)
                .orElse("");
        MultiText forms = entry.getForms();
        Form preferred = forms.getForm("fr").orElseGet(() -> forms.getForms().stream().findFirst().orElse(Form.EMPTY_FORM));
        String derivedLangCode = extractLangCodeFromCodeTrait(currentCode)
                .orElseGet(() -> preferred == Form.EMPTY_FORM ? "" : preferred.getLang());

        // Inputs
        TextField codeField = new TextField(currentCode);
        TextField pronField = new TextField(currentPron);
        TextField dialectField = new TextField(currentDialecte);
        TextField langCodeField = new TextField(derivedLangCode);

        Label langNamePreview = new Label(displayLang(derivedLangCode));
        langNamePreview.getStyleClass().add("muted");
        langCodeField.textProperty().addListener((obs, o, n) -> langNamePreview.setText(displayLang(safeTrim(n))));

        int r = 0;
        grid.add(new Label("Code"), 0, r);
        grid.add(codeField, 1, r++);
        grid.add(new Label("Prononciation"), 0, r);
        grid.add(pronField, 1, r++);
        grid.add(new Label("Dialecte"), 0, r);
        grid.add(dialectField, 1, r++);
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
        String newDialect = safeTrim(dialectField.getText());
        String newPronValue = safeTrim(pronField.getText());
        String newLangCode = safeTrim(langCodeField.getText());

        // Apply changes
        replaceTrait(factory, entry, "code", newCode);
        replaceTrait(factory, entry, "dialecte", newDialect);

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

    private void selectRowById(String id) {
        if (id == null) return;
        for (EntryRow row : entryTable.getItems()) {
            if (id.equals(row.getId())) {
                entryTable.getSelectionModel().select(row);
                entryTable.scrollTo(row);
                return;
            }
        }
    }

    private static Optional<String> extractLangCodeFromCodeTrait(String code) {
        if (code == null) return Optional.empty();
        int idx = code.lastIndexOf('/');
        if (idx < 0 || idx == code.length() - 1) return Optional.empty();
        String lang = code.substring(idx + 1).trim();
        return lang.isEmpty() ? Optional.empty() : Optional.of(lang);
    }

    private static String displayLang(String code) {
        if (code == null) return "";
        String normalized = code.trim();
        if (normalized.isEmpty()) return "";
        // keep base language if tags like fr-FR / pt_BR
        String base = normalized.split("[-_]", 2)[0].toLowerCase(Locale.ROOT);
        return switch (base) {
            case "fr" -> "Français";
            case "en" -> "Anglais";
            case "es" -> "Espagnol";
            case "de" -> "Allemand";
            case "it" -> "Italien";
            case "pt" -> "Portugais";
            case "ru" -> "Russe";
            case "ar" -> "Arabe";
            case "zh" -> "Chinois";
            case "ja" -> "Japonais";
            case "tpi" -> "Tok Pisin";
            default -> normalized;
        };
    }
}

