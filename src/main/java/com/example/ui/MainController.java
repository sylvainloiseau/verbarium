package com.example.ui;

import com.example.core.DictionaryService;
import com.example.core.LiftOpenException;
import com.example.ui.controls.*;
import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.beans.binding.Bindings;
import javafx.scene.control.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.stage.FileChooser;

import javafx.application.Platform;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public final class MainController {

    private final DictionaryService dictionaryService = new DictionaryService();
    private LiftDictionary currentDictionary;
    private final ObservableList<LiftEntry> baseEntries = FXCollections.observableArrayList();
    private final FilteredList<LiftEntry> filteredEntries = new FilteredList<>(baseEntries, e -> true);
    private final SortedList<LiftEntry> sortedEntries = new SortedList<>(filteredEntries);

    @FXML private TableView<LiftEntry> entryTable;
    @FXML private TextField searchField;
    @FXML private Label tableCountLabel;
    @FXML private Label editEntryTitle;
    @FXML private Label editEntryCode;
    @FXML private VBox rightContent;
    @FXML private VBox editorContainer;
    @FXML private Menu recentMenu;

    @FXML
    private void initialize() {
        entryTable.setItems(sortedEntries);
        updateCountLabel();
        filteredEntries.addListener((javafx.collections.ListChangeListener<? super LiftEntry>) c -> updateCountLabel());

        entryTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV == null || newV.getId().isEmpty()) {
                editEntryTitle.setText("(sélectionne une entrée)");
                editEntryCode.setText("");
                editorContainer.getChildren().clear();
                return;
            }
            populateEditor(newV);
        });

        searchField.textProperty().addListener((obs, o, n) -> applyFilters());
        applyFilters();

        setDictionary(loadDemoDictionary());
    }

    /* ─── Filters & search ─── */

    private void applyFilters() {
        if (filteredEntries == null) return;
        final String q = Optional.ofNullable(searchField.getText()).orElse("").trim().toLowerCase(Locale.ROOT);
        filteredEntries.setPredicate(entry -> {
            if (entry == null) return false;
            if (q.isEmpty()) return true;
            return buildSearchText(entry).toLowerCase(Locale.ROOT).contains(q);
        });
        updateCountLabel();
    }

    private void updateCountLabel() {
        if (tableCountLabel == null || entryTable == null) return;
        int shown = entryTable.getItems() == null ? 0 : entryTable.getItems().size();
        int total = baseEntries.size();
        tableCountLabel.setText(shown + " entrées affichées sur " + total);
    }

    /* ─── Import / Save ─── */

    @FXML
    private void onImportLift() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importer un fichier LIFT (.lift)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichier LIFT (*.lift)", "*.lift"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Tous les fichiers (*.*)", "*.*"));

        File selected = chooser.showOpenDialog(entryTable.getScene().getWindow());
        if (selected == null) return;

        try {
            setDictionary(dictionaryService.loadFromFile(selected));
        } catch (LiftOpenException e) {
            showError("Import LIFT", e.getMessage());
        } catch (IOException e) {
            showError("Import LIFT", "Erreur d'accès au fichier: " + e.getMessage());
        }
    }

    @FXML
    private void onSave() {
        if (currentDictionary == null) { showError("Enregistrer", "Aucun dictionnaire chargé."); return; }
        try { currentDictionary.save(); }
        catch (Exception e) { showError("Enregistrer", "Impossible d'enregistrer: " + e.getMessage()); }
    }

    @FXML
    private void onNewDictionary() {
        setDictionary(null);
        editEntryTitle.setText("(sélectionne une entrée)");
        editEntryCode.setText("");
        editorContainer.getChildren().clear();
    }

    @FXML
    private void onSaveAs() {
        if (currentDictionary == null) { showError("Enregistrer sous", "Aucun dictionnaire chargé."); return; }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Enregistrer sous…");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichier LIFT (*.lift)", "*.lift"));
        File selected = chooser.showSaveDialog(entryTable.getScene().getWindow());
        if (selected == null) return;
        try { currentDictionary.save(selected); }
        catch (Exception e) { showError("Enregistrer sous", "Impossible d'enregistrer: " + e.getMessage()); }
    }

    @FXML
    private void onPreferences() {
        showInfo("Paramètres", "La fenêtre de paramètres sera disponible dans une prochaine version.");
    }

    @FXML
    private void onQuit() {
        Platform.exit();
    }

    /* ─── Édition ─── */

    @FXML
    private void onCopy() {
        copySelectedEntryToClipboard();
    }

    @FXML
    private void onPaste() {
        showInfo("Coller", "Le collage d'entrées sera disponible dans une prochaine version.");
    }

    @FXML
    private void onCut() {
        copySelectedEntryToClipboard();
    }

    private void copySelectedEntryToClipboard() {
        LiftEntry entry = entryTable.getSelectionModel().getSelectedItem();
        if (entry == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append(entry.getId().orElse(""));
        for (Form f : entry.getForms().getForms()) {
            sb.append("\t").append(f.toPlainText());
        }
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(sb.toString());
        clipboard.setContent(content);
    }

    /* ─── Vue ─── */

    @FXML private void onViewEntries() { showInfo("Vue", "Affichage des entrées (vue actuelle)."); }
    @FXML private void onViewSenses() { showViewList("Sens", () -> currentDictionary == null ? List.of() : currentDictionary.getLiftDictionaryComponents().getAllSenses().stream().map(s -> s.getId().orElse("?") + " – " + s.getGloss().getForms().stream().findFirst().map(Form::toPlainText).orElse("")).toList()); }
    @FXML private void onViewExamples() { showViewList("Exemples", () -> currentDictionary == null ? List.of() : currentDictionary.getLiftDictionaryComponents().getAllExamples().stream().map(ex -> ex.getExample().getForms().stream().findFirst().map(Form::toPlainText).orElse("(vide)")).toList()); }
    @FXML private void onViewNotes() { showViewList("Notes", () -> currentDictionary == null ? List.of() : currentDictionary.getLiftDictionaryComponents().getAllEntries().stream().flatMap(e -> e.getNotes().values().stream()).map(n -> n.getType().orElse("?") + " – " + n.getText().getForms().stream().findFirst().map(Form::toPlainText).orElse("")).toList()); }
    @FXML private void onViewVariants() { showViewList("Variantes", () -> currentDictionary == null ? List.of() : currentDictionary.getLiftDictionaryComponents().getAllVariants().stream().map(v -> v.getRefId().orElse("?") + " – " + v.getForms().getForms().stream().findFirst().map(Form::toPlainText).orElse("")).toList()); }
    @FXML private void onViewEtymologies() { showViewList("Étymologies", () -> currentDictionary == null ? List.of() : currentDictionary.getLiftDictionaryComponents().getAllEntries().stream().flatMap(e -> e.getEtymologies().stream()).map(et -> et.getType() + " ← " + et.getSource()).toList()); }

    @FXML private void onViewObjectLangs() { showViewList("Langues objet", this::getObjectLanguages); }
    @FXML private void onViewMetaLangs() { showViewList("Méta-langues", this::getMetaLanguages); }
    @FXML private void onViewTraits() { showViewList("Types de trait", () -> currentDictionary == null ? List.of() : currentDictionary.getTraitName().stream().sorted().toList()); }
    @FXML private void onViewAnnotations() { showViewList("Types d'annotation", () -> currentDictionary == null ? List.of() : currentDictionary.getLiftDictionaryComponents().getAllAnnotations().stream().map(LiftAnnotation::getName).distinct().sorted().toList()); }
    @FXML private void onViewFields() { showViewList("Types de field", () -> currentDictionary == null ? List.of() : currentDictionary.getFieldType().stream().sorted().toList()); }

    /* ─── Configuration des champs ─── */

    @FXML private void onConfigNoteTypes() { showConfigList("Types de notes", () -> currentDictionary == null ? List.of() : currentDictionary.getLiftDictionaryComponents().getAllEntries().stream().flatMap(e -> e.getNotes().values().stream()).map(n -> n.getType().orElse("(sans type)")).distinct().sorted().toList()); }
    @FXML private void onConfigTranslationTypes() { showConfigList("Types de traductions", () -> currentDictionary == null ? List.of() : currentDictionary.getLiftDictionaryComponents().getAllExamples().stream().flatMap(ex -> ex.getTranslations().keySet().stream()).distinct().sorted().toList()); }
    @FXML private void onConfigLanguages() { showConfigList("Langues", this::getAllLanguages); }
    @FXML private void onConfigTraitTypes() { showConfigList("Types de trait", () -> currentDictionary == null ? List.of() : currentDictionary.getTraitName().stream().sorted().toList()); }
    @FXML private void onConfigAnnotationTypes() { showConfigList("Types d'annotation", () -> currentDictionary == null ? List.of() : currentDictionary.getLiftDictionaryComponents().getAllAnnotations().stream().map(LiftAnnotation::getName).distinct().sorted().toList()); }
    @FXML private void onConfigFieldTypes() { showConfigList("Types de field", () -> currentDictionary == null ? List.of() : currentDictionary.getFieldType().stream().sorted().toList()); }

    /* ─── Outil ─── */

    @FXML
    private void onValidateDictionary() {
        if (currentDictionary == null) { showError("Validation", "Aucun dictionnaire chargé."); return; }
        int entries = currentDictionary.getLiftDictionaryComponents().getAllEntries().size();
        int senses = currentDictionary.getLiftDictionaryComponents().getAllSenses().size();
        int examples = currentDictionary.getLiftDictionaryComponents().getAllExamples().size();
        showInfo("Validation du dictionnaire",
                "Le dictionnaire contient :\n" +
                "  • " + entries + " entrées\n" +
                "  • " + senses + " sens\n" +
                "  • " + examples + " exemples\n" +
                "  • Langues objet : " + String.join(", ", getObjectLanguages()) + "\n" +
                "  • Méta-langues : " + String.join(", ", getMetaLanguages()));
    }

    @FXML
    private void onExportCsv() {
        if (currentDictionary == null) { showError("Export CSV", "Aucun dictionnaire chargé."); return; }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exporter en CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV (*.csv)", "*.csv"));
        File selected = chooser.showSaveDialog(entryTable.getScene().getWindow());
        if (selected == null) return;
        try (PrintWriter pw = new PrintWriter(selected, "UTF-8")) {
            List<String> objLangs = getObjectLanguages();
            pw.print("id");
            for (String l : objLangs) pw.print("\tform[" + l + "]");
            pw.println();
            for (LiftEntry e : currentDictionary.getLiftDictionaryComponents().getAllEntries()) {
                pw.print(e.getId().orElse(""));
                for (String l : objLangs) pw.print("\t" + e.getForms().getForm(l).map(Form::toPlainText).orElse(""));
                pw.println();
            }
            showInfo("Export CSV", "Export réussi vers " + selected.getAbsolutePath());
        } catch (Exception e) {
            showError("Export CSV", "Impossible d'exporter: " + e.getMessage());
        }
    }

    /* ─── Helpers pour dialogues Vue / Config ─── */

    @FunctionalInterface
    private interface ListSupplier { List<String> get(); }

    private void showViewList(String title, ListSupplier supplier) {
        List<String> items = supplier.get();
        Alert dlg = new Alert(Alert.AlertType.INFORMATION);
        dlg.setTitle("Vue – " + title);
        dlg.setHeaderText(title + " (" + items.size() + ")");
        ListView<String> listView = new ListView<>(FXCollections.observableArrayList(items));
        listView.setPrefHeight(300);
        dlg.getDialogPane().setContent(listView);
        dlg.setResizable(true);
        dlg.showAndWait();
    }

    private void showConfigList(String title, ListSupplier supplier) {
        List<String> items = supplier.get();
        Alert dlg = new Alert(Alert.AlertType.INFORMATION);
        dlg.setTitle("Configuration – " + title);
        dlg.setHeaderText(title + " (" + items.size() + " valeurs trouvées dans le dictionnaire)");
        ListView<String> listView = new ListView<>(FXCollections.observableArrayList(items));
        listView.setPrefHeight(300);
        dlg.getDialogPane().setContent(listView);
        dlg.setResizable(true);
        dlg.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /* ─── Dictionary / Entry table ─── */

    private void setDictionary(LiftDictionary dictionary) {
        this.currentDictionary = dictionary;
        baseEntries.clear();

        if (dictionary == null) { applyFilters(); updateCountLabel(); return; }

        baseEntries.addAll(dictionary.getLiftDictionaryComponents().getAllEntries());
        configureEntryTableColumns(dictionary);
        applyFilters();
        updateCountLabel();
        if (!entryTable.getItems().isEmpty()) entryTable.getSelectionModel().selectFirst();
    }

    private void configureEntryTableColumns(LiftDictionary dictionary) {
        if (entryTable == null) return;
        entryTable.getColumns().clear();

        // Code trait column
        TableColumn<LiftEntry, String> codeCol = new TableColumn<>("Code");
        codeCol.setPrefWidth(140);
        codeCol.setCellValueFactory(cd -> {
            LiftEntry e = cd.getValue();
            if (e == null) return new ReadOnlyStringWrapper("");
            return Bindings.createStringBinding(() -> getTraitValue(e, "code"), e.traitsProperty());
        });

        // Form columns grouped by language
        var formLangs = dictionary.getLiftDictionaryComponents().getAllEntries().stream()
                .flatMap(e -> e.getForms().getLangs().stream())
                .filter(s -> s != null && !s.isBlank())
                .distinct().sorted().toList();

        TableColumn<LiftEntry, String> formGroup = new TableColumn<>("Form");
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

        // Pronunciation columns grouped by language
        var pronLangs = dictionary.getLiftDictionaryComponents().getAllEntries().stream()
                .flatMap(e -> e.getPronunciations().stream())
                .flatMap(p -> p.getProunciation().getLangs().stream())
                .filter(s -> s != null && !s.isBlank())
                .distinct().sorted().toList();

        TableColumn<LiftEntry, String> pronGroup = new TableColumn<>("Pron");
        for (String lang : pronLangs) {
            TableColumn<LiftEntry, String> pCol = new TableColumn<>(lang);
            pCol.setPrefWidth(160);
            pCol.setCellValueFactory(cd -> {
                LiftEntry e = cd.getValue();
                if (e == null) return new ReadOnlyStringWrapper("");
                return Bindings.createStringBinding(() -> e.getPronunciations().stream()
                        .findFirst()
                        .flatMap(p -> p.getProunciation().getForm(lang))
                        .map(Form::toPlainText)
                        .orElse(""), e.pronunciationsProperty());
            });
            pronGroup.getColumns().add(pCol);
        }

        entryTable.getColumns().addAll(codeCol, formGroup, pronGroup);
    }

    /* ─── Right panel: exhaustive entry editor ─── */

    private void populateEditor(LiftEntry entry) {
        List<String> objLangs = getObjectLanguages();
        List<String> metaLangs = getMetaLanguages();
        List<String> allLangs = getAllLanguages();
        // For backward compatibility, keep a local alias
        List<String> langs = objLangs;

        // Header
        Form preferred = entry.getForms().getForms().stream().findFirst().orElse(Form.EMPTY_FORM);
        editEntryTitle.setText(preferred == Form.EMPTY_FORM ? "(sans forme)" : preferred.toPlainText());
        String code = getTraitValue(entry, "code");
        editEntryCode.setText(code);

        editorContainer.getChildren().clear();

        // 1. ID / order / dateDeleted
        addSection(editorContainer, "Identifiant", () -> {
            GridPane grid = new GridPane();
            grid.setHgap(8); grid.setVgap(6);
            addReadOnlyRow(grid, 0, "ID", entry.getId().orElse(""));
            addReadOnlyRow(grid, 1, "GUID", entry.getGuid().orElse(""));
            addReadOnlyRow(grid, 2, "Ordre", entry.getOrder().orElse(""));
            addReadOnlyRow(grid, 3, "Date suppression", entry.getDateDeleted().orElse(""));
            return grid;
        }, true);

        // 2. Traits (using TraitEditor)
        addListSection(editorContainer, "Traits", entry.getTraits(), t -> {
            TraitEditor te = new TraitEditor();
            te.setTrait(t, langs);
            return te;
        }, true);

        // 3. Formes (lexical-unit) — MultiTextEditor
        addSection(editorContainer, "Formes (lexical-unit)", () -> {
            MultiTextEditor mte = new MultiTextEditor();
            mte.setAvailableLanguages(langs);
            mte.setMultiText(entry.getForms());
            return mte;
        }, true);

        // 4. Citations — méta-langues (descriptif)
        if (!entry.getCitations().isEmpty()) {
            addSection(editorContainer, "Citations", () -> {
                MultiTextEditor mte = new MultiTextEditor();
                mte.setAvailableLanguages(metaLangs);
                mte.setMultiText(entry.getCitations());
                return mte;
            }, false);
        }

        // 5. Prononciations — langues-objet
        addListSection(editorContainer, "Prononciations", entry.getPronunciations(), p -> {
            PronunciationEditor pe = new PronunciationEditor();
            pe.setPronunciation(p, objLangs);
            return pe;
        }, true);

        // 6. Sens — méta-langues pour définition/gloss, objet pour exemples
        addListSection(editorContainer, "Sens", entry.getSenses(), s -> {
            SenseEditor se = new SenseEditor();
            se.setSense(s, metaLangs, objLangs);
            return se;
        }, true);

        // 7. Variantes — langues-objet pour formes
        addListSection(editorContainer, "Variantes", entry.getVariants(), v -> {
            VariantEditor ve = new VariantEditor();
            ve.setVariant(v, objLangs, metaLangs);
            return ve;
        }, false);

        // 8. Relations — méta-langues pour usage
        addListSection(editorContainer, "Relations", entry.getRelations(), r -> {
            RelationEditor re = new RelationEditor();
            re.setRelation(r, metaLangs);
            return re;
        }, false);

        // 9. Étymologies — langues-objet pour formes, méta-langues pour glosses
        addListSection(editorContainer, "Étymologies", entry.getEtymologies(), e -> {
            EtymologyEditor ee = new EtymologyEditor();
            ee.setEtymology(e, objLangs, metaLangs);
            return ee;
        }, false);

        // 10. Notes (entry-level, via AbstractNotable) — méta-langues
        if (!entry.getNotes().isEmpty()) {
            addListSection(editorContainer, "Notes", new ArrayList<>(entry.getNotes().values()), n -> {
                NoteEditor ne = new NoteEditor();
                ne.setNote(n, metaLangs);
                return ne;
            }, false);
        }

        // 11. Annotations (entry-level) — méta-langues
        addListSection(editorContainer, "Annotations", entry.getAnnotations(), a -> {
            AnnotationEditor ae = new AnnotationEditor();
            ae.setAnnotation(a, metaLangs);
            return ae;
        }, false);

        // 12. Champs / Fields (entry-level) — méta-langues
        addListSection(editorContainer, "Champs (Field)", entry.getFields(), f -> {
            FieldEditor fe = new FieldEditor();
            fe.setField(f, metaLangs);
            return fe;
        }, false);

        // 13. Dates (entry-level)
        addSection(editorContainer, "Dates", () -> {
            GridPane grid = new GridPane();
            grid.setHgap(8); grid.setVgap(6);
            addReadOnlyRow(grid, 0, "Date création", entry.getDateCreated().orElse(""));
            addReadOnlyRow(grid, 1, "Date modification", entry.getDateModified().orElse(""));
            return grid;
        }, false);
    }

    /* ─── Helper: build TitledPane sections ─── */

    @FunctionalInterface
    private interface NodeFactory { javafx.scene.Node create(); }

    private static void addSection(VBox container, String title, NodeFactory factory, boolean expanded) {
        TitledPane tp = new TitledPane(title, factory.create());
        tp.setExpanded(expanded);
        tp.setAnimated(false);
        container.getChildren().add(tp);
    }

    @FunctionalInterface
    private interface ItemRenderer<T> { javafx.scene.Node render(T item); }

    private static <T> void addListSection(VBox container, String title, List<T> items, ItemRenderer<T> renderer, boolean expanded) {
        if (items == null || items.isEmpty()) {
            TitledPane tp = new TitledPane(title + " (0)", new Label("Aucun élément"));
            tp.setExpanded(false);
            tp.setAnimated(false);
            container.getChildren().add(tp);
            return;
        }
        VBox box = new VBox(6);
        int i = 1;
        for (T item : items) {
            TitledPane itemPane = new TitledPane("#" + i++, renderer.render(item));
            itemPane.setExpanded(expanded);
            itemPane.setAnimated(false);
            box.getChildren().add(itemPane);
        }
        TitledPane tp = new TitledPane(title + " (" + items.size() + ")", box);
        tp.setExpanded(expanded);
        tp.setAnimated(false);
        container.getChildren().add(tp);
    }

    private static void addReadOnlyRow(GridPane grid, int row, String label, String value) {
        grid.add(new Label(label), 0, row);
        TextField tf = new TextField(value);
        tf.setEditable(false);
        GridPane.setHgrow(tf, Priority.ALWAYS);
        grid.add(tf, 1, row);
    }

    /* ─── Modify entry (popup dialog) ─── */

    @FXML
    private void onModifyEntry() {
        LiftEntry entry = entryTable.getSelectionModel().getSelectedItem();
        if (entry == null || entry.getId().isEmpty()) { showError("Modification", "Aucune entrée sélectionnée."); return; }
        if (currentDictionary == null) { showError("Modification", "Aucun dictionnaire chargé."); return; }

        LiftFactory factory = getFactory(currentDictionary);
        if (factory == null) { showError("Modification", "Impossible d'accéder à la factory interne."); return; }

        if (showEditEntryDialog(factory, entry)) {
            onSave();
            applyFilters();
            entryTable.refresh();
            populateEditor(entry);
        }
    }

    private boolean showEditEntryDialog(LiftFactory factory, LiftEntry entry) {
        if (entryTable == null || entryTable.getScene() == null) return false;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Modifier l'entrée");
        dialog.initOwner(entryTable.getScene().getWindow());
        dialog.setResizable(true);

        ButtonType saveType = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefSize(640, 420);
        dialog.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.setPrefWidth(600);

        String currentCode = getTraitValue(entry, "code");
        TextField codeField = new TextField(currentCode);
        codeField.setPrefColumnCount(28);

        int r = 0;
        grid.add(new Label("Code"), 0, r);
        grid.add(codeField, 1, r++);

        // Forms: one field per language present
        grid.add(new Label("Formes"), 0, r++);
        Map<String, TextField> formFields = new HashMap<>();
        for (String lang : entry.getForms().getLangs().stream().sorted().toList()) {
            TextField tf = new TextField(entry.getForms().getForm(lang).map(Form::toPlainText).orElse(""));
            formFields.put(lang, tf);
            grid.add(new Label(lang), 0, r);
            grid.add(tf, 1, r++);
        }

        // Pronunciations: one field per language in first pronunciation
        grid.add(new Label("Prononciations"), 0, r++);
        Map<String, TextField> pronFields = new HashMap<>();
        LiftPronunciation firstPron = entry.getPronunciations().stream().findFirst().orElse(null);
        List<String> pronLangs = firstPron == null ? List.of() : firstPron.getProunciation().getLangs().stream().sorted().toList();
        for (String lang : pronLangs) {
            TextField tf = new TextField(firstPron.getProunciation().getForm(lang).map(Form::toPlainText).orElse(""));
            pronFields.put(lang, tf);
            grid.add(new Label(lang), 0, r);
            grid.add(tf, 1, r++);
        }

        dialog.getDialogPane().setContent(grid);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != saveType) return false;

        // Apply code
        replaceTrait(factory, entry, "code", safeTrim(codeField.getText()));

        // Apply forms
        for (Map.Entry<String, TextField> kv : formFields.entrySet()) {
            setOrRemoveForm(entry.getForms(), kv.getKey(), safeTrim(kv.getValue().getText()));
        }

        // Apply pronunciations
        boolean anyPron = pronFields.values().stream().map(tf -> safeTrim(tf.getText())).anyMatch(s -> !s.isBlank());
        if (!anyPron) {
            entry.getPronunciations().clear();
        } else {
            LiftPronunciation p = firstPron == null ? factory.createPronunciation(entry) : firstPron;
            for (Map.Entry<String, TextField> kv : pronFields.entrySet()) {
                setOrRemoveForm(p.getProunciation(), kv.getKey(), safeTrim(kv.getValue().getText()));
            }
        }
        return true;
    }

    /* ─── Utilities ─── */

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
        for (Form f : entry.getForms().getForms()) appendSep(sb, f.toPlainText());
        for (LiftPronunciation p : entry.getPronunciations()) {
            for (Form f : p.getProunciation().getForms()) appendSep(sb, f.toPlainText());
        }
        for (LiftSense s : entry.getSenses()) {
            for (Form f : s.getDefinition().getForms()) appendSep(sb, f.toPlainText());
            for (Form f : s.getGloss().getForms()) appendSep(sb, f.toPlainText());
        }
        return sb.toString();
    }

    private List<String> getObjectLanguages() {
        if (currentDictionary == null) return List.of();
        return currentDictionary.getObjectLanguagesOfAllText().stream()
                .filter(s -> s != null && !s.isBlank())
                .sorted().toList();
    }

    private List<String> getMetaLanguages() {
        if (currentDictionary == null) return List.of();
        return currentDictionary.getMetaLanguagesOfAllText().stream()
                .filter(s -> s != null && !s.isBlank())
                .sorted().toList();
    }

    private List<String> getAllLanguages() {
        if (currentDictionary == null) return List.of();
        Set<String> all = new HashSet<>();
        all.addAll(currentDictionary.getObjectLanguagesOfAllText());
        all.addAll(currentDictionary.getMetaLanguagesOfAllText());
        return all.stream().filter(s -> s != null && !s.isBlank()).sorted().toList();
    }

    private static void replaceTrait(LiftFactory factory, LiftEntry target, String name, String value) {
        target.getTraits().removeIf(t -> name.equals(t.getName()));
        if (value != null && !value.isBlank()) factory.createTrait(name, value, target);
    }

    private static void setOrRemoveForm(MultiText mt, String lang, String value) {
        if (mt == null || lang == null) return;
        String l = lang.trim();
        if (l.isEmpty()) return;
        String v = value == null ? "" : value;
        if (v.isBlank()) {
            if (!mt.isEmpty() && mt.getForm(l).isPresent()) {
                try { mt.removeForm(l); } catch (Exception ignored) {}
            }
            return;
        }
        mt.getForm(l).ifPresentOrElse(existing -> existing.changeText(v), () -> {
            try { mt.add(new Form(l, v)); } catch (Exception ignored) {}
        });
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
            try (FileOutputStream out = new FileOutputStream(tmp)) { in.transferTo(out); }
            return LiftDictionary.loadDictionaryWithFile(tmp);
        } catch (Exception e) { return null; }
    }

    private static String safeTrim(String s) { return s == null ? "" : s.trim(); }

    private static void appendSep(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) return;
        if (!sb.isEmpty()) sb.append("; ");
        sb.append(part);
    }

    private static LiftFactory getFactory(LiftDictionary d) {
        if (d == null) return null;
        if (d.getLiftDictionaryComponents() instanceof LiftFactory lf) return lf;
        return null;
    }
}
