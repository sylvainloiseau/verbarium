package com.example.ui;

import com.example.core.DictionaryService;
import com.example.core.LiftOpenException;
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
import javafx.beans.binding.Bindings;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class MainController {

    private final DictionaryService dictionaryService = new DictionaryService();
    private LiftDictionary currentDictionary;
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
    private TableView<Form> formsTable;

    @FXML
    private TableColumn<Form, String> formLangColumn;

    @FXML
    private TableColumn<Form, String> formValueColumn;

    @FXML
    private TableView<LiftSense> sensesTable;

    @FXML
    private TableColumn<LiftSense, String> senseLangColumn;

    @FXML
    private TableColumn<LiftSense, String> senseValueColumn;

    @FXML
    private TableView<LiftExample> examplesTable;

    @FXML
    private TableColumn<LiftExample, String> exTypeColumn;

    @FXML
    private TableColumn<LiftExample, String> exPhraseColumn;

    @FXML
    private TableColumn<LiftExample, String> exGlossColumn;

    @FXML
    private void initialize() {
        // Right panel tables: bound to lift-api objects directly (no row adapters).
        formLangColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue() == null ? "" : safeTrim(cd.getValue().getLang())));
        formValueColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue() == null ? "" : cd.getValue().toString()));

        // Senses table columns are configured dynamically per entry (language columns).
        // Keep fx:id columns unused for now; we still configure the TableView in populateEditor.

        exTypeColumn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue() == null ? "" : cd.getValue().getSource().orElse("")));
        // Example phrase / gloss columns are configured dynamically per entry (language columns).

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
        baseEntries.clear();

        if (dictionary == null) {
            applyFilters();
            updateCountLabel();
            return;
        }

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
        codeCol.setCellValueFactory(cd -> {
            LiftEntry e = cd.getValue();
            if (e == null) return new ReadOnlyStringWrapper("");
            return Bindings.createStringBinding(() -> getTraitValue(e, "code"), e.traitsProperty());
        });

        // Determine object languages used in lexical-unit/forms across the whole dictionary
        var formLangs = dictionary.getLiftDictionaryComponents().getAllEntries().stream()
                .flatMap(e -> e.getForms().getLangs().stream())
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .sorted()
                .toList();

        // Grouped columns for forms (one column per language)
        TableColumn<LiftEntry, String> formGroup = new TableColumn<>("Form");
        formGroup.setPrefWidth(Math.max(220, formLangs.size() * 160));
        for (String lang : formLangs) {
            TableColumn<LiftEntry, String> langCol = new TableColumn<>(lang);
            langCol.setPrefWidth(160);
            langCol.setCellValueFactory(cd -> {
                LiftEntry e = cd.getValue();
                if (e == null) return new ReadOnlyStringWrapper("");
                return e.getForms().formTextProperty(lang);
            });
            formGroup.getColumns().add(langCol);
        }

        // Pronunciation: one column per language used in pronunciations across the dictionary (no fallbacks).
        var pronLangs = dictionary.getLiftDictionaryComponents().getAllEntries().stream()
                .flatMap(e -> e.getPronunciations().stream())
                .flatMap(p -> p.getProunciation().getLangs().stream())
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .sorted()
                .toList();

        TableColumn<LiftEntry, String> pronGroup = new TableColumn<>("Pron");
        pronGroup.setPrefWidth(Math.max(160, pronLangs.size() * 160));
        for (String lang : pronLangs) {
            TableColumn<LiftEntry, String> pCol = new TableColumn<>(lang);
            pCol.setPrefWidth(160);
            pCol.setCellValueFactory(cd -> {
                LiftEntry e = cd.getValue();
                if (e == null) return new ReadOnlyStringWrapper("");
                // Bind to first pronunciation if present; if pronunciations list changes, binding updates
                return Bindings.createStringBinding(() -> e.getPronunciations().stream()
                                .findFirst()
                                .flatMap(p -> p.getProunciation().getForm(lang))
                                .map(Form::toString)
                                .orElse(""),
                        e.pronunciationsProperty()
                );
            });
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

        // Formes tab: all lexical-unit forms (direct Form objects)
        formsTable.setItems(FXCollections.observableArrayList(forms.getForms()));

        // Sens tab: one row per sense; dynamic columns per language for definition + gloss.
        configureSensesTableColumns(entry);
        sensesTable.setItems(FXCollections.observableArrayList(entry.getSenses()));

        // Exemples: all examples from all senses, direct LiftExample rows
        ObservableList<LiftExample> exampleRows = FXCollections.observableArrayList();
        for (LiftSense s : entry.getSenses()) {
            for (LiftExample ex : s.getExamples()) {
                exampleRows.add(ex);
            }
        }
        configureExamplesTableColumns(entry);
        examplesTable.setItems(exampleRows);
    }

    private void configureSensesTableColumns(LiftEntry entry) {
        if (sensesTable == null || entry == null) return;
        sensesTable.getColumns().clear();

        // Meta-languages used in definition/gloss across senses
        var langs = entry.getSenses().stream()
                .flatMap(s -> {
                    var d = s.getDefinition() == null ? List.<String>of() : List.copyOf(s.getDefinition().getLangs());
                    var g = s.getGloss() == null ? List.<String>of() : List.copyOf(s.getGloss().getLangs());
                    return java.util.stream.Stream.concat(d.stream(), g.stream());
                })
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .sorted()
                .toList();

        TableColumn<LiftSense, String> defGroup = new TableColumn<>("Definition");
        for (String lang : langs) {
            TableColumn<LiftSense, String> c = new TableColumn<>(lang);
            c.setPrefWidth(180);
            c.setCellValueFactory(cd -> {
                LiftSense s = cd.getValue();
                if (s == null) return new ReadOnlyStringWrapper("");
                return s.getDefinition().formTextProperty(lang);
            });
            defGroup.getColumns().add(c);
        }

        TableColumn<LiftSense, String> glossGroup = new TableColumn<>("Gloss");
        for (String lang : langs) {
            TableColumn<LiftSense, String> c = new TableColumn<>(lang);
            c.setPrefWidth(180);
            c.setCellValueFactory(cd -> {
                LiftSense s = cd.getValue();
                if (s == null) return new ReadOnlyStringWrapper("");
                return s.getGloss().formTextProperty(lang);
            });
            glossGroup.getColumns().add(c);
        }

        sensesTable.getColumns().addAll(defGroup, glossGroup);
    }

    private void configureExamplesTableColumns(LiftEntry entry) {
        if (examplesTable == null || entry == null) return;
        examplesTable.getColumns().clear();

        TableColumn<LiftExample, String> typeCol = new TableColumn<>("Type");
        typeCol.setPrefWidth(100);
        typeCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue() == null ? "" : cd.getValue().getSource().orElse("")));

        var exLangs = entry.getSenses().stream()
                .flatMap(s -> s.getExamples().stream())
                .flatMap(ex -> ex.getExample() == null ? java.util.stream.Stream.<String>empty() : ex.getExample().getLangs().stream())
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .sorted()
                .toList();

        var trLangs = entry.getSenses().stream()
                .flatMap(s -> s.getExamples().stream())
                .flatMap(ex -> ex.getTranslations() == null ? java.util.stream.Stream.<MultiText>empty() : ex.getTranslations().values().stream())
                .flatMap(mt -> mt == null ? java.util.stream.Stream.<String>empty() : mt.getLangs().stream())
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .sorted()
                .toList();

        TableColumn<LiftExample, String> exGroup = new TableColumn<>("Exemple");
        for (String lang : exLangs) {
            TableColumn<LiftExample, String> c = new TableColumn<>(lang);
            c.setPrefWidth(220);
            c.setCellValueFactory(cd -> {
                LiftExample ex = cd.getValue();
                if (ex == null) return new ReadOnlyStringWrapper("");
                return ex.getExample().formTextProperty(lang);
            });
            exGroup.getColumns().add(c);
        }

        TableColumn<LiftExample, String> trGroup = new TableColumn<>("Traduction");
        for (String lang : trLangs) {
            TableColumn<LiftExample, String> c = new TableColumn<>(lang);
            c.setPrefWidth(220);
            c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                    cd.getValue() == null
                            ? ""
                            : cd.getValue().getTranslations().values().stream()
                            .filter(mt -> mt != null && mt.getForm(lang).isPresent())
                            .findFirst()
                            .flatMap(mt -> mt.getForm(lang))
                            .map(Form::toString)
                            .orElse("")
            ));
            trGroup.getColumns().add(c);
        }

        examplesTable.getColumns().addAll(typeCol, exGroup, trGroup);
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

        // Inputs
        TextField codeField = new TextField(currentCode);
        codeField.setPrefColumnCount(28);

        int r = 0;
        grid.add(new Label("Code"), 0, r);
        grid.add(codeField, 1, r++);

        // --- Formes (lexical-unit) : one field per language present ---
        grid.add(new Label("Formes"), 0, r++);
        Map<String, TextField> formFields = new HashMap<>();
        for (String lang : entry.getForms().getLangs().stream().sorted().toList()) {
            TextField tf = new TextField(entry.getForms().getForm(lang).map(Form::toString).orElse(""));
            formFields.put(lang, tf);
            grid.add(new Label(lang), 0, r);
            grid.add(tf, 1, r++);
        }

        // --- Prononciations : one field per language present in first pronunciation (if any) ---
        grid.add(new Label("Prononciations"), 0, r++);
        Map<String, TextField> pronFields = new HashMap<>();
        LiftPronunciation firstPron = entry.getPronunciations().stream().findFirst().orElse(null);
        List<String> pronLangs = firstPron == null ? List.of() : firstPron.getProunciation().getLangs().stream().sorted().toList();
        for (String lang : pronLangs) {
            TextField tf = new TextField(firstPron.getProunciation().getForm(lang).map(Form::toString).orElse(""));
            pronFields.put(lang, tf);
            grid.add(new Label(lang), 0, r);
            grid.add(tf, 1, r++);
        }

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != saveType) {
            return false; // cancelled
        }

        String newCode = safeTrim(codeField.getText());

        // Apply changes
        replaceTrait(factory, entry, "code", newCode);

        // Apply form updates (no language preference / no heuristics)
        for (Map.Entry<String, TextField> kv : formFields.entrySet()) {
            String lang = kv.getKey();
            String value = safeTrim(kv.getValue().getText());
            setOrRemoveForm(entry.getForms(), lang, value);
        }

        // Apply pronunciation updates (first pronunciation only; remove all if blank)
        boolean anyPron = pronFields.values().stream().map(tf -> safeTrim(tf.getText())).anyMatch(s -> !s.isBlank());
        if (!anyPron) {
            entry.getPronunciations().clear();
        } else {
            LiftPronunciation p = firstPron == null ? factory.createPronunciation(entry) : firstPron;
            for (Map.Entry<String, TextField> kv : pronFields.entrySet()) {
                String lang = kv.getKey();
                String value = safeTrim(kv.getValue().getText());
                setOrRemoveForm(p.getProunciation(), lang, value);
            }
        }
        return true;
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

    private static void setOrRemoveForm(MultiText mt, String lang, String value) {
        if (mt == null || lang == null) return;
        String l = lang.trim();
        if (l.isEmpty()) return;

        String v = value == null ? "" : value;
        if (v.isBlank()) {
            if (!mt.isEmpty() && mt.getForm(l).isPresent()) {
                try {
                    mt.removeForm(l);
                } catch (Exception ignored) {
                }
            }
            return;
        }

        mt.getForm(l).ifPresentOrElse(existing -> existing.changeText(v), () -> {
            try {
                mt.add(new Form(l, v));
            } catch (Exception ignored) {
            }
        });
    }

    // Intentionally no language-name mapping/hardcoding.
}

