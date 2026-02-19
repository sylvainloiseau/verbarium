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
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.stage.FileChooser;
import javafx.application.Platform;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.io.*;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main controller implementing specifications 5.7.2 through 5.12.
 *
 * Architecture:
 *  - Left:  TreeView for navigation (Objects, Languages, Categories, Configuration)
 *  - Center: dynamic TableView switching per navigation selection
 *  - Right:  detail editor form for selected table row
 */
public final class MainController {

    /* ─── Nav view identifiers (i18n keys) ─── */
    private static final String NAV_ENTRIES     = "nav.entries";
    private static final String NAV_SENSES      = "nav.senses";
    private static final String NAV_EXAMPLES    = "nav.examples";
    private static final String NAV_NOTES       = "nav.notes";
    private static final String NAV_VARIANTS    = "nav.variants";
    private static final String NAV_ETYMOLOGIES = "nav.etymologies";
    private static final String NAV_OBJ_LANGS   = "nav.objectLangs";
    private static final String NAV_META_LANGS  = "nav.metaLangs";
    private static final String NAV_TRAITS      = "nav.traits";
    private static final String NAV_ANNOTATIONS = "nav.annotations";
    private static final String NAV_FIELDS      = "nav.fields";
    private static final String NAV_GRAM_INFO   = "nav.gramInfo";
    private static final String NAV_POS         = "nav.pos";
    private static final String NAV_TRANS_TYPES = "nav.transTypes";
    private static final String NAV_NOTE_TYPES  = "nav.noteTypes";
    private static final String NAV_QUICK_ENTRY = "nav.quickEntry";

    /* ─── State ─── */
    private final DictionaryService dictionaryService = new DictionaryService();
    private LiftDictionary currentDictionary;
    private String currentView = NAV_ENTRIES;

    /* ─── FXML nodes ─── */
    @FXML private TreeView<String> navTree;
    @FXML private StackPane tableContainer;
    @FXML private TextField searchField;
    @FXML private Label viewTitle;
    @FXML private Label tableCountLabel;
    @FXML private Label editEntryTitle;
    @FXML private Label editEntryCode;
    @FXML private VBox rightContent;
    @FXML private VBox editorContainer;
    @FXML private Menu recentMenu;
    @FXML private SplitPane mainSplit;
    @FXML private Button addButton;

    /* ─── Entry table (main view) ─── */
    private final TableView<LiftEntry> entryTable = new TableView<>();
    private final ObservableList<LiftEntry> baseEntries = FXCollections.observableArrayList();
    private final FilteredList<LiftEntry> filteredEntries = new FilteredList<>(baseEntries, e -> true);

    /* ─── Generic object tables ─── */
    private final TableView<LiftSense> senseTable = new TableView<>();
    private final TableView<LiftExample> exampleTable = new TableView<>();
    private final TableView<LiftVariant> variantTable = new TableView<>();
    private final TableView<LiftTrait> traitTable = new TableView<>();
    private final TableView<LiftAnnotation> annotationTable = new TableView<>();
    private final TableView<LiftField> fieldTable = new TableView<>();
    private final TableView<MultiTextField> langFieldTable = new TableView<>();
    private final TableView<QuickEntryRow> quickEntryTable = new TableView<>();

    /* ─── Wrapper for language field view ─── */
    public record MultiTextField(String parentType, String parentId, String lang, String text) {}
    public static class QuickEntryRow {
        private final Map<String, javafx.beans.property.StringProperty> forms = new HashMap<>();
        private final Map<String, javafx.beans.property.StringProperty> glosses = new HashMap<>();
        private final javafx.beans.property.StringProperty gramInfo = new javafx.beans.property.SimpleStringProperty("");
        public javafx.beans.property.StringProperty formProperty(String lang) { return forms.computeIfAbsent(lang, k -> new javafx.beans.property.SimpleStringProperty("")); }
        public javafx.beans.property.StringProperty glossProperty(String lang) { return glosses.computeIfAbsent(lang, k -> new javafx.beans.property.SimpleStringProperty("")); }
        public javafx.beans.property.StringProperty gramInfoProperty() { return gramInfo; }
    }

    /* ─────────────────────── INITIALIZATION ─────────────────────── */

    @FXML
    private void initialize() {
        buildNavTree();
        setupEntryTable();
        setupGenericTables();
        searchField.textProperty().addListener((obs, o, n) -> applyCurrentFilter());
        setDictionary(loadDemoDictionary());
        switchView(NAV_ENTRIES);
    }

    /* ─── Navigation tree (5.7.2) ─── */

    private void buildNavTree() {
        navKeyMap.clear();

        TreeItem<String> root = new TreeItem<>(I18n.get("nav.dictionary"));
        root.setExpanded(true);

        TreeItem<String> objects = new TreeItem<>(I18n.get("nav.objects"));
        objects.setExpanded(true);
        objects.getChildren().addAll(
            navItem(NAV_ENTRIES), navItem(NAV_SENSES),
            navItem(NAV_EXAMPLES), navItem(NAV_NOTES),
            navItem(NAV_VARIANTS), navItem(NAV_ETYMOLOGIES)
        );

        TreeItem<String> langs = new TreeItem<>(I18n.get("nav.languages"));
        langs.setExpanded(true);
        langs.getChildren().addAll(navItem(NAV_OBJ_LANGS), navItem(NAV_META_LANGS));

        TreeItem<String> cats = new TreeItem<>(I18n.get("nav.categories"));
        cats.setExpanded(true);
        cats.getChildren().addAll(
            navItem(NAV_GRAM_INFO), navItem(NAV_POS),
            navItem(NAV_TRAITS), navItem(NAV_ANNOTATIONS),
            navItem(NAV_TRANS_TYPES), navItem(NAV_NOTE_TYPES),
            navItem(NAV_FIELDS)
        );

        TreeItem<String> quick = navItem(NAV_QUICK_ENTRY);

        root.getChildren().addAll(objects, langs, cats, quick);
        navTree.setRoot(root);
        navTree.setShowRoot(false);

        navTree.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.isLeaf()) {
                String key = navKeyMap.get(newV);
                if (key != null) switchView(key);
            }
        });

        navTree.setCellFactory(tv -> {
            TreeCell<String> cell = new TreeCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };
            ContextMenu ctx = new ContextMenu();
            MenuItem createItem = new MenuItem(I18n.get("btn.createObject"));
            createItem.setOnAction(e -> onCreateNewObject());
            ctx.getItems().add(createItem);
            cell.setContextMenu(ctx);
            return cell;
        });
    }

    private final Map<TreeItem<String>, String> navKeyMap = new HashMap<>();

    private TreeItem<String> navItem(String i18nKey) {
        TreeItem<String> item = new TreeItem<>(I18n.get(i18nKey));
        navKeyMap.put(item, i18nKey);
        return item;
    }


    /* ─── View switching ─── */

    private void switchView(String viewName) {
        currentView = viewName;
        viewTitle.setText(I18n.get(viewName));
        editorContainer.getChildren().clear();
        editEntryTitle.setText(I18n.get("panel.selectElement"));
        editEntryCode.setText("");
        tableContainer.getChildren().clear();
        addButton.setText(I18n.get("btn.new"));

        switch (viewName) {
            case NAV_ENTRIES     -> showEntryView();
            case NAV_SENSES      -> showSenseView();
            case NAV_EXAMPLES    -> showExampleView();
            case NAV_NOTES       -> showNoteView();
            case NAV_VARIANTS    -> showVariantView();
            case NAV_ETYMOLOGIES -> showEtymologyView();
            case NAV_OBJ_LANGS   -> showLangFieldView(true);
            case NAV_META_LANGS  -> showLangFieldView(false);
            case NAV_TRAITS      -> showTraitView();
            case NAV_ANNOTATIONS -> showAnnotationView();
            case NAV_FIELDS      -> showFieldView();
            case NAV_GRAM_INFO   -> showGramInfoView();
            case NAV_POS         -> showPosView();
            case NAV_TRANS_TYPES -> showTranslationTypesView();
            case NAV_NOTE_TYPES  -> showNoteTypesView();
            case NAV_QUICK_ENTRY -> showQuickEntryView();
            default -> showEntryView();
        }
    }

    /* ════════════════════ ENTRY VIEW ════════════════════ */

    private void setupEntryTable() {
        SortedList<LiftEntry> sorted = new SortedList<>(filteredEntries);
        sorted.comparatorProperty().bind(entryTable.comparatorProperty());
        entryTable.setItems(sorted);
        entryTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null && currentView.equals(NAV_ENTRIES)) populateEntryEditor(n);
        });
    }

    private void showEntryView() {
        addButton.setText(I18n.get("btn.newEntry"));
        HBox filterRow = buildEntryFilterRow();

        Button clearBtn = new Button("Réinitialiser les filtres");
        clearBtn.setOnAction(e -> {
            entryColumnFilters.forEach(tf -> tf.setText(""));
            applyCurrentFilter();
            entryTable.getSelectionModel().clearSelection();
        });
        HBox header = new HBox();
        header.setPadding(new Insets(0,6,4,6));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(spacer, clearBtn);

        VBox wrapper = new VBox(header, filterRow, entryTable);
        VBox.setVgrow(entryTable, Priority.ALWAYS);
        tableContainer.getChildren().setAll(wrapper);
        applyCurrentFilter();
        updateCountLabel(filteredEntries.size(), baseEntries.size());
    }

    private final List<TextField> entryColumnFilters = new ArrayList<>();

    private HBox buildEntryFilterRow() {
        entryColumnFilters.clear();
        HBox row = new HBox(2);
        row.setPadding(new Insets(2, 0, 2, 0));
        row.setStyle("-fx-background-color: #eef2f3;");
        for (TableColumn<LiftEntry, ?> col : collectLeafColumns(entryTable)) {
            TextField tf = new TextField();
            tf.setPromptText(I18n.get("filter.prompt"));
            tf.setPrefWidth(col.getPrefWidth());
            tf.setStyle("-fx-font-size: 11px; -fx-padding: 2 4 2 4;");
            tf.textProperty().addListener((obs, o, n) -> applyCurrentFilter());
            entryColumnFilters.add(tf);
            row.getChildren().add(tf);
        }
        return row;
    }

    private void configureEntryTableColumns() {
        entryTable.getColumns().clear();
        if (currentDictionary == null) return;

        TableColumn<LiftEntry, String> codeCol = new TableColumn<>(I18n.get("col.code"));
        codeCol.setPrefWidth(120);
        codeCol.setCellValueFactory(cd -> {
            LiftEntry e = cd.getValue();
            return e == null ? new ReadOnlyStringWrapper("") :
                Bindings.createStringBinding(() -> getTraitValue(e, "code"), e.traitsProperty());
        });

        var formLangs = currentDictionary.getLiftDictionaryComponents().getAllEntries().stream()
                .flatMap(e -> e.getForms().getLangs().stream()).filter(s -> s != null && !s.isBlank()).distinct().sorted().toList();
        TableColumn<LiftEntry, String> formGroup = new TableColumn<>(I18n.get("col.form"));
        for (String lang : formLangs) {
            TableColumn<LiftEntry, String> c = new TableColumn<>(lang);
            c.setPrefWidth(140);
            c.setCellValueFactory(cd -> cd.getValue() == null ? new ReadOnlyStringWrapper("") : cd.getValue().getForms().formTextProperty(lang));
            formGroup.getColumns().add(c);
        }

        TableColumn<LiftEntry, String> morphCol = new TableColumn<>(I18n.get("col.morphType"));
        morphCol.setPrefWidth(110);
        morphCol.setCellValueFactory(cd -> {
            LiftEntry e = cd.getValue();
            return e == null ? new ReadOnlyStringWrapper("") :
                Bindings.createStringBinding(() -> getTraitValue(e, "morph-type"), e.traitsProperty());
        });

        TableColumn<LiftEntry, String> dateCol = new TableColumn<>(I18n.get("col.dateCreated"));
        dateCol.setPrefWidth(130);
        dateCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue() == null ? "" : cd.getValue().getDateCreated().orElse("")));

        entryTable.getColumns().addAll(codeCol, formGroup, morphCol, dateCol);
    }

    /* ════════════════════ SENSE VIEW ════════════════════ */

    private void showSenseView() {
        senseTable.setItems(FXCollections.observableArrayList());
        senseTable.getColumns().clear();
        if (currentDictionary == null) { tableContainer.getChildren().setAll(senseTable); return; }

        List<String> metaLangs = getMetaLanguages();
        TableColumn<LiftSense, String> idCol = col(I18n.get("col.id"), s -> s.getId().orElse(""));
        TableColumn<LiftSense, String> giCol = col(I18n.get("col.gramInfo"), s -> s.getGrammaticalInfo().map(GrammaticalInfo::getValue).orElse(""));
        TableColumn<LiftSense, String> glossGroup = new TableColumn<>(I18n.get("col.gloss"));
        for (String l : metaLangs) {
            glossGroup.getColumns().add(col(l, s -> s.getGloss().getForm(l).map(Form::toPlainText).orElse("")));
        }
        TableColumn<LiftSense, String> defGroup = new TableColumn<>(I18n.get("col.definition"));
        for (String l : metaLangs) {
            defGroup.getColumns().add(col(l, s -> s.getDefinition().getForm(l).map(Form::toPlainText).orElse("")));
        }
        senseTable.getColumns().addAll(idCol, giCol, glossGroup, defGroup);
        senseTable.getItems().addAll(currentDictionary.getLiftDictionaryComponents().getAllSenses());
        senseTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> { if (n != null) populateSenseEditor(n); });
        tableContainer.getChildren().setAll(wrapTableWithFilters(senseTable));
        updateCountLabel(senseTable.getItems().size(), senseTable.getItems().size());
    }

    /* ════════════════════ EXAMPLE VIEW ════════════════════ */

    private void showExampleView() {
        exampleTable.setItems(FXCollections.observableArrayList());
        exampleTable.getColumns().clear();
        if (currentDictionary == null) { tableContainer.getChildren().setAll(exampleTable); return; }

        List<String> objLangs = getObjectLanguages();
        TableColumn<LiftExample, String> srcCol = col(I18n.get("col.source"), ex -> ex.getSource().orElse(""));
        TableColumn<LiftExample, String> exGroup = new TableColumn<>(I18n.get("col.example"));
        for (String l : objLangs) {
            exGroup.getColumns().add(col(l, ex -> ex.getExample().getForm(l).map(Form::toPlainText).orElse("")));
        }
        exampleTable.getColumns().addAll(srcCol, exGroup);
        exampleTable.getItems().addAll(currentDictionary.getLiftDictionaryComponents().getAllExamples());
        exampleTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> { if (n != null) populateExampleEditor(n); });
        tableContainer.getChildren().setAll(wrapTableWithFilters(exampleTable));
        updateCountLabel(exampleTable.getItems().size(), exampleTable.getItems().size());
    }

    /* ════════════════════ NOTE VIEW ════════════════════ */

    private void showNoteView() {
        TableView<LiftNote> noteTable = new TableView<>();
        if (currentDictionary == null) { tableContainer.getChildren().setAll(noteTable); return; }
        List<String> metaLangs = getMetaLanguages();
        TableColumn<LiftNote, String> typeCol = col(I18n.get("col.type"), n -> n.getType().orElse(""));
        TableColumn<LiftNote, String> textGroup = new TableColumn<>(I18n.get("col.text"));
        for (String l : metaLangs) textGroup.getColumns().add(col(l, n -> n.getText().getForm(l).map(Form::toPlainText).orElse("")));
        noteTable.getColumns().addAll(typeCol, textGroup);
        noteTable.getItems().addAll(currentDictionary.getLiftDictionaryComponents().getAllNotes());
        noteTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> { if (n != null) populateNoteEditor(n); });
        tableContainer.getChildren().setAll(wrapTableWithFilters(noteTable));
        updateCountLabel(noteTable.getItems().size(), noteTable.getItems().size());
    }

    /* ════════════════════ VARIANT VIEW ════════════════════ */

    private void showVariantView() {
        variantTable.setItems(FXCollections.observableArrayList());
        variantTable.getColumns().clear();
        if (currentDictionary == null) { tableContainer.getChildren().setAll(variantTable); return; }
        List<String> objLangs = getObjectLanguages();
        TableColumn<LiftVariant, String> refCol = col(I18n.get("col.ref"), v -> v.getRefId().orElse(""));
        TableColumn<LiftVariant, String> formGroup = new TableColumn<>(I18n.get("col.forms"));
        for (String l : objLangs) formGroup.getColumns().add(col(l, v -> v.getForms().getForm(l).map(Form::toPlainText).orElse("")));
        variantTable.getColumns().addAll(refCol, formGroup);
        variantTable.getItems().addAll(currentDictionary.getLiftDictionaryComponents().getAllVariants());
        variantTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> { if (n != null) populateVariantEditor(n); });
        tableContainer.getChildren().setAll(wrapTableWithFilters(variantTable));
        updateCountLabel(variantTable.getItems().size(), variantTable.getItems().size());
    }

    /* ════════════════════ ETYMOLOGY VIEW ════════════════════ */

    private void showEtymologyView() {
        TableView<LiftEtymology> etyTable = new TableView<>();
        if (currentDictionary == null) { tableContainer.getChildren().setAll(etyTable); return; }
        etyTable.getColumns().addAll(
            col(I18n.get("col.type"), (LiftEtymology e) -> e.getType() != null ? e.getType() : ""),
            col(I18n.get("col.source"), (LiftEtymology e) -> e.getSource() != null ? e.getSource() : "")
        );
        currentDictionary.getLiftDictionaryComponents().getAllEntries().stream()
            .flatMap(e -> e.getEtymologies().stream()).forEach(etyTable.getItems()::add);
        tableContainer.getChildren().setAll(wrapTableWithFilters(etyTable));
        updateCountLabel(etyTable.getItems().size(), etyTable.getItems().size());
    }

    /* ════════════════════ LANGUAGE FIELD VIEW (5.9) ════════════════════ */

    private void showLangFieldView(boolean objectLangs) {
        langFieldTable.setItems(FXCollections.observableArrayList());
        langFieldTable.getColumns().clear();
        if (currentDictionary == null) { tableContainer.getChildren().setAll(langFieldTable); return; }

        List<String> langs = objectLangs ? getObjectLanguages() : getMetaLanguages();

        // Collect all multitext entries with parent info
        List<MultiTextField> rows = new ArrayList<>();
        for (LiftEntry entry : currentDictionary.getLiftDictionaryComponents().getAllEntries()) {
            if (objectLangs) {
                collectMtRows(rows, "form", entry.getId().orElse("?"), entry.getForms(), langs);
                for (LiftVariant v : entry.getVariants()) collectMtRows(rows, "variante", v.getRefId().orElse("?"), v.getForms(), langs);
                for (LiftPronunciation p : entry.getPronunciations()) collectMtRows(rows, "pron", entry.getId().orElse("?"), p.getProunciation(), langs);
                for (LiftSense s : entry.getSenses()) {
                    for (LiftExample ex : s.getExamples()) collectMtRows(rows, "exemple", s.getId().orElse("?"), ex.getExample(), langs);
                }
            } else {
                for (LiftSense s : entry.getSenses()) {
                    collectMtRows(rows, "définition", s.getId().orElse("?"), s.getDefinition(), langs);
                    collectMtRows(rows, "gloss", s.getId().orElse("?"), s.getGloss(), langs);
                    for (LiftExample ex : s.getExamples()) {
                        for (MultiText tr : ex.getTranslations().values()) collectMtRows(rows, "traduction", s.getId().orElse("?"), tr, langs);
                    }
                }
                for (LiftNote n : entry.getNotes().values()) collectMtRows(rows, "note", entry.getId().orElse("?"), n.getText(), langs);
            }
        }

        TableColumn<MultiTextField, String> parentTypeCol = new TableColumn<>(I18n.get("col.parentType"));
        parentTypeCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().parentType()));
        parentTypeCol.setPrefWidth(100);
        TableColumn<MultiTextField, String> parentIdCol = new TableColumn<>(I18n.get("col.parent"));
        parentIdCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().parentId()));
        parentIdCol.setPrefWidth(120);

        TableColumn<MultiTextField, String> langGroup = new TableColumn<>(I18n.get("nav.languages"));
        for (String l : langs) {
            TableColumn<MultiTextField, String> c = new TableColumn<>(l);
            c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(l.equals(cd.getValue().lang()) ? cd.getValue().text() : ""));
            c.setPrefWidth(160);
            langGroup.getColumns().add(c);
        }

        langFieldTable.getColumns().addAll(parentTypeCol, parentIdCol, langGroup);
        langFieldTable.getItems().addAll(rows);
        tableContainer.getChildren().setAll(wrapTableWithFilters(langFieldTable));
        updateCountLabel(rows.size(), rows.size());
    }

    private static void collectMtRows(List<MultiTextField> rows, String type, String parentId, MultiText mt, List<String> langs) {
        for (Form f : mt.getForms()) {
            if (langs.contains(f.getLang())) {
                rows.add(new MultiTextField(type, parentId, f.getLang(), f.toPlainText()));
            }
        }
    }

    /* ════════════════════ TRAIT VIEW (5.10 – split: names top, values bottom) ════════════════════ */

    private void showTraitView() {
        traitTable.setItems(FXCollections.observableArrayList());
        traitTable.getColumns().clear();
        if (currentDictionary == null) { tableContainer.getChildren().setAll(traitTable); return; }

        List<LiftTrait> allTraits = currentDictionary.getLiftDictionaryComponents().getAllTraits();

        Map<String, Long> nameCounts = allTraits.stream()
            .collect(Collectors.groupingBy(LiftTrait::getName, Collectors.counting()));
        TableView<CategoryRow> nameTable = new TableView<>();
        nameTable.getColumns().addAll(
            colCat(I18n.get("col.name"), CategoryRow::value),
            colCat(I18n.get("col.frequency"), r -> String.valueOf(r.frequency()))
        );
        nameCounts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(e -> nameTable.getItems().add(new CategoryRow(e.getKey(), e.getValue())));
        nameTable.setPrefHeight(180);

        traitTable.getColumns().addAll(
            col(I18n.get("col.parent"), (LiftTrait t) -> describeParent(t.getParent())),
            col(I18n.get("col.name"), LiftTrait::getName),
            col(I18n.get("col.value"), LiftTrait::getValue),
            col(I18n.get("col.frequency"), t -> {
                long c = allTraits.stream().filter(x -> x.getName().equals(t.getName()) && x.getValue().equals(t.getValue())).count();
                return String.valueOf(c);
            })
        );
        traitTable.getItems().addAll(allTraits);

        Label bottomLabel = new Label(I18n.get("category.traitValues"));
        nameTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n == null) { traitTable.getItems().setAll(allTraits); bottomLabel.setText(I18n.get("category.traitValues")); }
            else {
                traitTable.getItems().setAll(allTraits.stream().filter(t -> n.value().equals(t.getName())).toList());
                bottomLabel.setText(I18n.get("category.traitValuesOf", n.value()));
            }
            updateCountLabel(traitTable.getItems().size(), allTraits.size());
        });

        Button clearBtn = new Button(I18n.get("btn.showAll"));
        clearBtn.setOnAction(e -> {
            nameTable.getSelectionModel().clearSelection();
            traitTable.getItems().setAll(allTraits);
            bottomLabel.setText(I18n.get("category.traitValues"));
            updateCountLabel(allTraits.size(), allTraits.size());
        });

        VBox topPane = new VBox(4, new Label(I18n.get("category.traitNames")), wrapTableWithFilters(nameTable));
        VBox bottomPane = new VBox(4, new HBox(8, bottomLabel, clearBtn), wrapTableWithFilters(traitTable));
        VBox.setVgrow(bottomPane, Priority.ALWAYS);
        SplitPane split = new SplitPane(topPane, bottomPane);
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.35);
        tableContainer.getChildren().setAll(split);
        updateCountLabel(allTraits.size(), allTraits.size());
    }

    /* ════════════════════ ANNOTATION VIEW (5.10 – split: names top, values bottom) ════════════════════ */

    private void showAnnotationView() {
        annotationTable.setItems(FXCollections.observableArrayList());
        annotationTable.getColumns().clear();
        if (currentDictionary == null) { tableContainer.getChildren().setAll(annotationTable); return; }

        List<LiftAnnotation> all = currentDictionary.getLiftDictionaryComponents().getAllAnnotations();

        Map<String, Long> nameCounts = all.stream()
            .collect(Collectors.groupingBy(LiftAnnotation::getName, Collectors.counting()));
        TableView<CategoryRow> nameTable = new TableView<>();
        nameTable.getColumns().addAll(
            colCat(I18n.get("col.name"), CategoryRow::value),
            colCat(I18n.get("col.frequency"), r -> String.valueOf(r.frequency()))
        );
        nameCounts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(e -> nameTable.getItems().add(new CategoryRow(e.getKey(), e.getValue())));
        nameTable.setPrefHeight(180);

        annotationTable.getColumns().addAll(
            col(I18n.get("col.parent"), (LiftAnnotation a) -> describeParent(a.getParent())),
            col(I18n.get("col.name"), LiftAnnotation::getName),
            col(I18n.get("col.value"), a -> a.getValue().orElse("")),
            col(I18n.get("col.who"), a -> a.getWho().orElse("")),
            col(I18n.get("col.when"), a -> a.getWhen().orElse("")),
            col(I18n.get("col.frequency"), a -> {
                long c = all.stream().filter(x -> x.getName().equals(a.getName()) && x.getValue().orElse("").equals(a.getValue().orElse(""))).count();
                return String.valueOf(c);
            })
        );
        annotationTable.getItems().addAll(all);

        Label bottomLabel = new Label(I18n.get("category.annotationValues"));
        nameTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n == null) { annotationTable.getItems().setAll(all); bottomLabel.setText(I18n.get("category.annotationValues")); }
            else {
                annotationTable.getItems().setAll(all.stream().filter(a -> n.value().equals(a.getName())).toList());
                bottomLabel.setText(I18n.get("category.annotationValuesOf", n.value()));
            }
            updateCountLabel(annotationTable.getItems().size(), all.size());
        });

        Button clearBtn = new Button(I18n.get("btn.showAll"));
        clearBtn.setOnAction(e -> {
            nameTable.getSelectionModel().clearSelection();
            annotationTable.getItems().setAll(all);
            bottomLabel.setText(I18n.get("category.annotationValues"));
            updateCountLabel(all.size(), all.size());
        });

        VBox topPane = new VBox(4, new Label(I18n.get("category.annotationNames")), wrapTableWithFilters(nameTable));
        VBox bottomPane = new VBox(4, new HBox(8, bottomLabel, clearBtn), wrapTableWithFilters(annotationTable));
        VBox.setVgrow(bottomPane, Priority.ALWAYS);
        SplitPane split = new SplitPane(topPane, bottomPane);
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.35);
        tableContainer.getChildren().setAll(split);
        updateCountLabel(all.size(), all.size());
    }

    /* ════════════════════ FIELD VIEW (5.10) ════════════════════ */

    private void showFieldView() {
        fieldTable.setItems(FXCollections.observableArrayList());
        fieldTable.getColumns().clear();
        if (currentDictionary == null) { tableContainer.getChildren().setAll(fieldTable); return; }
        fieldTable.getColumns().addAll(
            col(I18n.get("col.type"), LiftField::getName),
            col(I18n.get("col.text"), f -> f.getText().getForms().stream().findFirst().map(Form::toPlainText).orElse(""))
        );
        fieldTable.getItems().addAll(currentDictionary.getLiftDictionaryComponents().getAllFields());
        tableContainer.getChildren().setAll(wrapTableWithFilters(fieldTable));
        updateCountLabel(fieldTable.getItems().size(), fieldTable.getItems().size());
    }

    /* ════════════════════ QUICK ENTRY VIEW (5.12) ════════════════════ */

    private void showQuickEntryView() {
        quickEntryTable.setItems(FXCollections.observableArrayList());
        quickEntryTable.getColumns().clear();
        if (currentDictionary == null) { tableContainer.getChildren().setAll(quickEntryTable); return; }
        quickEntryTable.setEditable(true);
        List<String> objLangs = getObjectLanguages();
        List<String> metaLangs = getMetaLanguages();

        for (String l : objLangs) {
            TableColumn<QuickEntryRow, String> c = new TableColumn<>("form [" + l + "]");
            c.setCellValueFactory(cd -> cd.getValue().formProperty(l));
            c.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
            c.setOnEditCommit(ev -> ev.getRowValue().formProperty(l).set(ev.getNewValue()));
            c.setPrefWidth(130);
            c.setEditable(true);
            quickEntryTable.getColumns().add(c);
        }
        for (String l : metaLangs) {
            TableColumn<QuickEntryRow, String> c = new TableColumn<>("sens [" + l + "]");
            c.setCellValueFactory(cd -> cd.getValue().glossProperty(l));
            c.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
            c.setOnEditCommit(ev -> ev.getRowValue().glossProperty(l).set(ev.getNewValue()));
            c.setPrefWidth(130);
            c.setEditable(true);
            quickEntryTable.getColumns().add(c);
        }
        List<String> knownGramCodes = currentDictionary.getLiftDictionaryComponents().getAllSenses().stream()
            .map(s -> s.getGrammaticalInfo().map(GrammaticalInfo::getValue).orElse(null))
            .filter(Objects::nonNull).distinct().sorted().toList();
        ObservableList<String> gramItems = FXCollections.observableArrayList(knownGramCodes);

        TableColumn<QuickEntryRow, String> giCol = new TableColumn<>(I18n.get("col.gramCode"));
        giCol.setCellValueFactory(cd -> cd.getValue().gramInfoProperty());
        giCol.setCellFactory(tc -> {
            ComboBox<String> combo = new ComboBox<>(gramItems);
            combo.setEditable(true);
            TableCell<QuickEntryRow, String> cell = new TableCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) { setGraphic(null); }
                    else { combo.setValue(item); setGraphic(combo); }
                }
            };
            combo.valueProperty().addListener((obs, o, n) -> {
                if (cell.getTableRow() != null && cell.getTableRow().getItem() != null) {
                    cell.getTableRow().getItem().gramInfoProperty().set(n != null ? n : "");
                }
            });
            return cell;
        });
        giCol.setPrefWidth(140);
        giCol.setEditable(true);
        quickEntryTable.getColumns().add(giCol);

        // Seed with empty rows; auto-add new row when last row is edited
        for (int i = 0; i < 5; i++) quickEntryTable.getItems().add(new QuickEntryRow());

        quickEntryTable.setOnMouseClicked(e -> {
            int lastIdx = quickEntryTable.getItems().size() - 1;
            if (lastIdx >= 0 && quickEntryTable.getSelectionModel().getSelectedIndex() == lastIdx) {
                quickEntryTable.getItems().add(new QuickEntryRow());
            }
        });

        tableContainer.getChildren().setAll(quickEntryTable);
        updateCountLabel(0, 0);
        addButton.setText(I18n.get("btn.createEntries"));
    }

    /* ─── Create new object (5.8 context menu) ─── */

    @FXML
    private void onCreateNewObject() {
        if (currentDictionary == null) { showError(I18n.get("error.creation"), I18n.get("error.noDictionary")); return; }
        LiftFactory factory = getFactory(currentDictionary);
        if (factory == null) return;

        switch (currentView) {
            case NAV_QUICK_ENTRY -> createEntriesFromQuickTable(factory);
            case NAV_ENTRIES -> createNewEntry(factory);
            case NAV_SENSES -> {
                LiftEntry selEntry = entryTable.getSelectionModel().getSelectedItem();
                if (selEntry == null) { showError(I18n.get("error.creation"), I18n.get("info.selectEntryForSense")); return; }
                factory.createSense(new org.xml.sax.helpers.AttributesImpl(), selEntry);
                switchView(NAV_SENSES);
            }
            default -> showInfo(I18n.get("error.creation"), I18n.get("info.useQuickEntry"));
        }
    }

    private void createNewEntry(LiftFactory factory) {
        org.xml.sax.helpers.AttributesImpl attrs = new org.xml.sax.helpers.AttributesImpl();
        attrs.addAttribute("", "id", "id", "CDATA", UUID.randomUUID().toString());
        LiftEntry entry = factory.createEntry(attrs);
        baseEntries.add(entry);
        entryTable.getSelectionModel().select(entry);
        entryTable.scrollTo(entry);
        applyCurrentFilter();
    }

    private void createEntriesFromQuickTable(LiftFactory factory) {
        List<String> objLangs = getObjectLanguages();
        List<String> metaLangs = getMetaLanguages();
        int created = 0;
        for (QuickEntryRow row : quickEntryTable.getItems()) {
            boolean hasContent = objLangs.stream().anyMatch(l -> !row.formProperty(l).get().isBlank())
                || metaLangs.stream().anyMatch(l -> !row.glossProperty(l).get().isBlank());
            if (!hasContent) continue;
            org.xml.sax.helpers.AttributesImpl attrs = new org.xml.sax.helpers.AttributesImpl();
            attrs.addAttribute("", "id", "id", "CDATA", UUID.randomUUID().toString());
            LiftEntry entry = factory.createEntry(attrs);
            for (String l : objLangs) {
                String v = row.formProperty(l).get();
                if (!v.isBlank()) entry.getForms().add(new Form(l, v));
            }
            LiftSense sense = factory.createSense(new org.xml.sax.helpers.AttributesImpl(), entry);
            for (String l : metaLangs) {
                String v = row.glossProperty(l).get();
                if (!v.isBlank()) sense.addGloss(new Form(l, v));
            }
            String gi = row.gramInfoProperty().get();
            if (!gi.isBlank()) sense.setGrammaticalInfo(gi);
            baseEntries.add(entry);
            created++;
        }
        if (created > 0) {
            showInfo(I18n.get("nav.quickEntry"), I18n.get("info.quickEntryCreated", created));
            quickEntryTable.getItems().clear();
            for (int i = 0; i < 5; i++) quickEntryTable.getItems().add(new QuickEntryRow());
        }
    }

    /* ─── Editor population helpers ─── */

    private void populateEntryEditor(LiftEntry entry) {
        List<String> objLangs = getObjectLanguages();
        List<String> metaLangs = getMetaLanguages();

        // Collect known dropdown values from the dictionary
        List<String> traitNames = getKnownTraitNames();
        Map<String, Set<String>> traitValues = getKnownTraitValues();
        List<String> annotationNames = getKnownAnnotationNames();
        List<String> fieldTypes = getKnownFieldTypes();

        Form preferred = entry.getForms().getForms().stream().findFirst().orElse(Form.EMPTY_FORM);
        editEntryTitle.setText(preferred == Form.EMPTY_FORM ? "(sans forme)" : preferred.toPlainText());
        editEntryCode.setText(getTraitValue(entry, "code"));
        editorContainer.getChildren().clear();

        addSection(editorContainer, I18n.get("editor.identity"), () -> {
            GridPane g = new GridPane(); g.setHgap(8); g.setVgap(6);
            addReadOnlyRow(g, 0, I18n.get("field.id"), entry.getId().orElse(""));

            g.add(new Label(I18n.get("field.dateCreated")), 0, 1);
            DatePicker dpCreated = buildDatePicker(entry.getDateCreated().orElse(""));
            dpCreated.valueProperty().addListener((obs, o, n) -> entry.setDateCreated(n != null ? n.toString() : null));
            GridPane.setHgrow(dpCreated, Priority.ALWAYS);
            g.add(dpCreated, 1, 1);

            g.add(new Label(I18n.get("field.dateModified")), 0, 2);
            DatePicker dpModified = buildDatePicker(entry.getDateModified().orElse(""));
            dpModified.valueProperty().addListener((obs, o, n) -> entry.setDateModified(n != null ? n.toString() : null));
            GridPane.setHgrow(dpModified, Priority.ALWAYS);
            g.add(dpModified, 1, 2);
            return g;
        }, true);
        addListSection(editorContainer, I18n.get("editor.traits"), entry.getTraits(), t -> {
            TraitEditor te = new TraitEditor(); te.setTrait(t, objLangs, traitNames, traitValues); return te;
        }, true);
        addSection(editorContainer, I18n.get("editor.forms"), () -> { MultiTextEditor m = new MultiTextEditor(); m.setAvailableLanguages(objLangs); m.setMultiText(entry.getForms()); return m; }, true);
        addListSection(editorContainer, I18n.get("editor.pronunciations"), entry.getPronunciations(), p -> { PronunciationEditor pe = new PronunciationEditor(); pe.setPronunciation(p, objLangs); return pe; }, false);

        if (!entry.getSenses().isEmpty()) {
            addSection(editorContainer, I18n.get("editor.senses") + " (" + entry.getSenses().size() + ")", () -> {
                VBox box = new VBox(4);
                for (LiftSense s : entry.getSenses()) {
                    String label = s.getId().orElse("?") + " – " + s.getGloss().getForms().stream().findFirst().map(Form::toPlainText).orElse("");
                    Hyperlink link = new Hyperlink(label);
                    link.setOnAction(e -> { switchView(NAV_SENSES); senseTable.getSelectionModel().select(s); senseTable.scrollTo(s); });
                    box.getChildren().add(link);
                }
                return box;
            }, true);
        }
        addListSection(editorContainer, I18n.get("editor.variants"), entry.getVariants(), v -> { VariantEditor ve = new VariantEditor(); ve.setVariant(v, objLangs, metaLangs); return ve; }, false);
        addListSection(editorContainer, I18n.get("editor.relations"), entry.getRelations(), r -> { RelationEditor re = new RelationEditor(); re.setRelation(r, metaLangs); return re; }, false);
        addListSection(editorContainer, I18n.get("editor.etymologies"), entry.getEtymologies(), et -> { EtymologyEditor ee = new EtymologyEditor(); ee.setEtymology(et, objLangs, metaLangs); return ee; }, false);
        addListSection(editorContainer, I18n.get("editor.annotations"), entry.getAnnotations(), a -> {
            AnnotationEditor ae = new AnnotationEditor(); ae.setAnnotation(a, metaLangs, annotationNames); return ae;
        }, false);
        addListSection(editorContainer, I18n.get("editor.notes"), new ArrayList<>(entry.getNotes().values()), n -> { NoteEditor ne = new NoteEditor(); ne.setNote(n, metaLangs); return ne; }, false);
        addListSection(editorContainer, I18n.get("editor.fields"), entry.getFields(), f -> {
            FieldEditor fe = new FieldEditor(); fe.setField(f, metaLangs, fieldTypes); return fe;
        }, false);
    }

    private void populateSenseEditor(LiftSense sense) {
        List<String> metaLangs = getMetaLanguages();
        List<String> objLangs = getObjectLanguages();
        editEntryTitle.setText(I18n.get("sense.title", sense.getId().orElse("?")));
        editEntryCode.setText(sense.getGrammaticalInfo().map(GrammaticalInfo::getValue).orElse(""));
        editorContainer.getChildren().clear();

        // Parent link: navigate back to entry view filtered to this sense's parent
        findParentEntry(sense).ifPresent(parentEntry -> {
            Hyperlink backLink = new Hyperlink(I18n.get("sense.backToEntry", parentEntry.getId().orElse("?")));
            backLink.setOnAction(e -> { switchView(NAV_ENTRIES); entryTable.getSelectionModel().select(parentEntry); entryTable.scrollTo(parentEntry); });
            editorContainer.getChildren().add(backLink);
        });

        // Link to examples
        if (!sense.getExamples().isEmpty()) {
            Hyperlink exLink = new Hyperlink(I18n.get("sense.viewExamples", sense.getExamples().size()));
            exLink.setOnAction(e -> switchView(NAV_EXAMPLES));
            editorContainer.getChildren().add(exLink);
        }

        SenseEditor se = new SenseEditor();
        se.setSense(sense, metaLangs, objLangs);
        editorContainer.getChildren().add(se);
    }

    private Optional<LiftEntry> findParentEntry(LiftSense sense) {
        if (currentDictionary == null) return Optional.empty();
        return currentDictionary.getLiftDictionaryComponents().getAllEntries().stream()
            .filter(e -> e.getSenses().contains(sense))
            .findFirst();
    }

    private void populateExampleEditor(LiftSense parentSense, LiftExample ex) {
        editEntryTitle.setText(I18n.get("nav.examples"));
        editEntryCode.setText(ex.getSource().orElse(""));
        editorContainer.getChildren().clear();

        /*Création lien retour*/
        if (parentSense != null) {
            Hyperlink backLink = new Hyperlink("<- Retour au sens");
            backLink.setOnAction(e -> populateSenseEditor(parentSense));
            editorContainer.getChildren().add(backLink);
        }
        /*Création éditeur*/
        ExampleEditor ee = new ExampleEditor();
        ee.setExample(ex, getObjectLanguages(), getMetaLanguages());
        editorContainer.getChildren().add(ee);
    }

    private void populateExampleEditor(LiftExample ex) {
        Optional<LiftSense> parent = findParentSense(ex);
        populateExampleEditor(parent.orElse(null), ex);
    }

    private Optional<LiftSense> findParentSense(LiftExample ex) {
        if (currentDictionary == null) return Optional.empty();
        return currentDictionary.getLiftDictionaryComponents().getAllSenses().stream()
            .filter(s -> containsExample(s, ex))
            .findFirst();
    }

    private boolean containsExample(LiftSense sense, LiftExample ex) {
        if (sense.getExamples().contains(ex)) return true;
        for (LiftSense sub : sense.getSubSenses()) if (containsExample(sub, ex)) return true;
        return false;
    }
    private void populateNoteEditor(LiftNote note) {
        editEntryTitle.setText(I18n.get("nav.notes") + " : " + note.getType().orElse("?"));
        editEntryCode.setText("");
        editorContainer.getChildren().clear();
        NoteEditor ne = new NoteEditor();
        ne.setNote(note, getMetaLanguages());
        editorContainer.getChildren().add(ne);
    }

    private void populateVariantEditor(LiftVariant v) {
        editEntryTitle.setText(I18n.get("nav.variants") + " : " + v.getRefId().orElse("?"));
        editEntryCode.setText("");
        editorContainer.getChildren().clear();
        VariantEditor ve = new VariantEditor();
        ve.setVariant(v, getObjectLanguages(), getMetaLanguages());
        editorContainer.getChildren().add(ve);
    }

    /* ─── Setup generic tables ─── */

    private void setupGenericTables() {
        entryTable.setPlaceholder(new Label(I18n.get("placeholder.noData")));
        senseTable.setPlaceholder(new Label(I18n.get("placeholder.noSense")));
        exampleTable.setPlaceholder(new Label(I18n.get("placeholder.noExample")));
        variantTable.setPlaceholder(new Label(I18n.get("placeholder.noVariant")));
        traitTable.setPlaceholder(new Label(I18n.get("placeholder.noTrait")));
        annotationTable.setPlaceholder(new Label(I18n.get("placeholder.noAnnotation")));
        fieldTable.setPlaceholder(new Label(I18n.get("placeholder.noField")));
        langFieldTable.setPlaceholder(new Label(I18n.get("placeholder.noMultiField")));
        quickEntryTable.setPlaceholder(new Label(I18n.get("placeholder.quickEntryHint")));
    }

    /* ────────────────── FILTER / SEARCH ────────────────── */

    private void applyCurrentFilter() {
        String q = Optional.ofNullable(searchField.getText()).orElse("").trim().toLowerCase(Locale.ROOT);
        if (currentView.equals(NAV_ENTRIES)) {
            List<TableColumn<LiftEntry, ?>> leaves = collectLeafColumns(entryTable);
            filteredEntries.setPredicate(entry -> {
                if (entry == null) return false;
                if (!q.isEmpty() && !buildSearchText(entry).toLowerCase(Locale.ROOT).contains(q)) return false;
                // Per-column filters
                for (int i = 0; i < entryColumnFilters.size() && i < leaves.size(); i++) {
                    String ft = entryColumnFilters.get(i).getText();
                    if (ft == null || ft.isBlank()) continue;
                    Object val = leaves.get(i).getCellObservableValue(entry) != null ? leaves.get(i).getCellObservableValue(entry).getValue() : null;
                    String cellText = val != null ? val.toString() : "";
                    if (!cellText.toLowerCase(Locale.ROOT).contains(ft.toLowerCase(Locale.ROOT))) return false;
                }
                return true;
            });
            updateCountLabel(filteredEntries.size(), baseEntries.size());
        }
    }

    /* ────────────────── DICTIONARY MANAGEMENT ────────────────── */

    private void setDictionary(LiftDictionary dictionary) {
        this.currentDictionary = dictionary;
        baseEntries.clear();
        if (dictionary == null) { updateCountLabel(0, 0); return; }
        baseEntries.addAll(dictionary.getLiftDictionaryComponents().getAllEntries());
        configureEntryTableColumns();
        if (currentView.equals(NAV_ENTRIES)) {
            applyCurrentFilter();
            if (!filteredEntries.isEmpty()) entryTable.getSelectionModel().selectFirst();
        }
    }

    /* ────────────────── MENU HANDLERS ────────────────── */

    @FXML private void onImportLift() {
        FileChooser ch = new FileChooser();
        ch.setTitle(I18n.get("dialog.openLift"));
        ch.getExtensionFilters().addAll(new FileChooser.ExtensionFilter(I18n.get("dialog.liftFilter"), "*.lift"), new FileChooser.ExtensionFilter(I18n.get("dialog.allFilter"), "*.*"));
        File f = ch.showOpenDialog(navTree.getScene().getWindow());
        if (f == null) return;
        try { setDictionary(dictionaryService.loadFromFile(f)); switchView(NAV_ENTRIES); }
        catch (Exception e) { showError(I18n.get("error.open"), e.getMessage()); }
    }

    @FXML private void onSave() {
        if (currentDictionary == null) { showError(I18n.get("error.save"), I18n.get("error.noDictionary")); return; }
        try { currentDictionary.save(); } catch (Exception e) { showError(I18n.get("error.save"), e.getMessage()); }
    }

    @FXML private void onNewDictionary() { setDictionary(null); switchView(NAV_ENTRIES); }

    @FXML private void onSaveAs() {
        if (currentDictionary == null) { showError(I18n.get("error.saveAs"), I18n.get("error.noDictionaryShort")); return; }
        FileChooser ch = new FileChooser(); ch.setTitle(I18n.get("dialog.saveLift"));
        ch.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.get("dialog.liftFilter"), "*.lift"));
        File f = ch.showSaveDialog(navTree.getScene().getWindow());
        if (f != null) { try { currentDictionary.save(f); } catch (Exception e) { showError(I18n.get("error.saveAs"), e.getMessage()); } }
    }

    @FXML private void onPreferences() { showPreferencesDialog(); }
    @FXML private void onQuit() { Platform.exit(); }

    @FXML private void onCopy() { copySelectedToClipboard(); }
    @FXML private void onPaste() { showInfo(I18n.get("menu.edit.paste"), I18n.get("info.paste")); }
    @FXML private void onCut() { copySelectedToClipboard(); }

    private void copySelectedToClipboard() {
        LiftEntry e = entryTable.getSelectionModel().getSelectedItem();
        if (e == null) return;
        ClipboardContent cc = new ClipboardContent();
        StringBuilder sb = new StringBuilder(); sb.append(e.getId().orElse(""));
        for (Form f : e.getForms().getForms()) sb.append("\t").append(f.toPlainText());
        cc.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(cc);
    }

    /* ─── Vue menu items: delegate to switchView ─── */
    @FXML private void onViewEntries() { switchView(NAV_ENTRIES); }
    @FXML private void onViewSenses() { switchView(NAV_SENSES); }
    @FXML private void onViewExamples() { switchView(NAV_EXAMPLES); }
    @FXML private void onViewNotes() { switchView(NAV_NOTES); }
    @FXML private void onViewVariants() { switchView(NAV_VARIANTS); }
    @FXML private void onViewEtymologies() { switchView(NAV_ETYMOLOGIES); }
    @FXML private void onViewObjectLangs() { switchView(NAV_OBJ_LANGS); }
    @FXML private void onViewMetaLangs() { switchView(NAV_META_LANGS); }
    @FXML private void onViewTraits() { switchView(NAV_TRAITS); }
    @FXML private void onViewAnnotations() { switchView(NAV_ANNOTATIONS); }
    @FXML private void onViewFields() { switchView(NAV_FIELDS); }
    @FXML private void onViewGramInfo() { switchView(NAV_GRAM_INFO); }
    @FXML private void onViewPos() { switchView(NAV_POS); }
    @FXML private void onViewTransTypes() { switchView(NAV_TRANS_TYPES); }
    @FXML private void onViewNoteTypes() { switchView(NAV_NOTE_TYPES); }

    /* ─── Configuration menu ─── */
    @FXML private void onConfigNoteTypes() { showConfigDialog(I18n.get("menu.config.noteTypes"), () -> currentDictionary == null ? List.of() : currentDictionary.getLiftDictionaryComponents().getAllNotes().stream().map(n -> n.getType().orElse("?")).distinct().sorted().toList()); }
    @FXML private void onConfigTranslationTypes() { showConfigDialog(I18n.get("menu.config.translationTypes"), () -> currentDictionary == null ? List.of() : currentDictionary.getLiftDictionaryComponents().getAllExamples().stream().flatMap(ex -> ex.getTranslations().keySet().stream()).distinct().sorted().toList()); }
    @FXML private void onConfigLanguages() { showConfigDialog(I18n.get("menu.config.languages"), this::getAllLanguages); }
    @FXML private void onConfigTraitTypes() { showConfigDialog(I18n.get("menu.config.traitTypes"), () -> currentDictionary == null ? List.of() : currentDictionary.getTraitName().stream().sorted().toList()); }
    @FXML private void onConfigAnnotationTypes() { showConfigDialog(I18n.get("menu.config.annotationTypes"), () -> currentDictionary == null ? List.of() : currentDictionary.getLiftDictionaryComponents().getAllAnnotations().stream().map(LiftAnnotation::getName).distinct().sorted().toList()); }
    @FXML private void onConfigFieldTypes() { showConfigDialog(I18n.get("menu.config.fieldTypes"), () -> currentDictionary == null ? List.of() : currentDictionary.getFieldType().stream().sorted().toList()); }

    /* ─── Outil menu ─── */
    @FXML private void onValidateDictionary() {
        if (currentDictionary == null) { showError(I18n.get("error.validation"), I18n.get("error.noDictionaryShort")); return; }
        var c = currentDictionary.getLiftDictionaryComponents();
        showInfo(I18n.get("error.validation"), I18n.get("info.validationResult",
                c.getAllEntries().size(), c.getAllSenses().size(), c.getAllExamples().size(),
                String.join(", ", getObjectLanguages()), String.join(", ", getMetaLanguages())));
    }

    @FXML private void onExportCsv() {
        if (currentDictionary == null) { showError(I18n.get("error.export"), I18n.get("error.noDictionaryShort")); return; }
        FileChooser ch = new FileChooser(); ch.setTitle(I18n.get("dialog.exportCsv"));
        ch.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.get("dialog.csvFilter"), "*.csv"));
        File f = ch.showSaveDialog(navTree.getScene().getWindow());
        if (f == null) return;
        try (PrintWriter pw = new PrintWriter(f, "UTF-8")) {
            List<String> ol = getObjectLanguages();
            pw.print("id"); for (String l : ol) pw.print("\tform[" + l + "]"); pw.println();
            for (LiftEntry e : currentDictionary.getLiftDictionaryComponents().getAllEntries()) {
                pw.print(e.getId().orElse("")); for (String l : ol) pw.print("\t" + e.getForms().getForm(l).map(Form::toPlainText).orElse("")); pw.println();
            }
            showInfo(I18n.get("error.export"), I18n.get("info.exportSuccess", f.getAbsolutePath()));
        } catch (Exception e) { showError(I18n.get("error.export"), e.getMessage()); }
    }

    @FXML private void onModifyEntry() {
        if (!currentView.equals(NAV_ENTRIES)) return;
        LiftEntry entry = entryTable.getSelectionModel().getSelectedItem();
        if (entry == null) { showError(I18n.get("error.modification"), I18n.get("error.noEntrySelected")); return; }
        populateEntryEditor(entry);
    }

    /* ════════════════════ GRAMMATICAL INFO VIEW ════════════════════ */

    private void showGramInfoView() {
        if (currentDictionary == null) { tableContainer.getChildren().setAll(new Label(I18n.get("placeholder.noDictionary"))); return; }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LiftSense s : currentDictionary.getLiftDictionaryComponents().getAllSenses()) {
            s.getGrammaticalInfo().ifPresent(gi -> counts.merge(gi.getValue(), 1L, Long::sum));
        }
        showCategoryTable(I18n.get("nav.gramInfo"), I18n.get("col.value"), counts);
    }

    /* ════════════════════ POS VIEW ════════════════════ */

    private void showPosView() {
        if (currentDictionary == null) { tableContainer.getChildren().setAll(new Label(I18n.get("placeholder.noDictionary"))); return; }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LiftTrait t : currentDictionary.getLiftDictionaryComponents().getAllTraits()) {
            if ("from-part-of-speech".equals(t.getName()) || "POS".equalsIgnoreCase(t.getName())) {
                counts.merge(t.getValue(), 1L, Long::sum);
            }
        }
        // Also count grammatical-info values as POS
        for (LiftSense s : currentDictionary.getLiftDictionaryComponents().getAllSenses()) {
            s.getGrammaticalInfo().ifPresent(gi -> counts.merge(gi.getValue(), 1L, Long::sum));
        }
        showCategoryTable(I18n.get("nav.pos"), I18n.get("col.category"), counts);
    }

    /* ════════════════════ TRANSLATION TYPES VIEW ════════════════════ */

    private void showTranslationTypesView() {
        if (currentDictionary == null) { tableContainer.getChildren().setAll(new Label(I18n.get("placeholder.noDictionary"))); return; }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LiftExample ex : currentDictionary.getLiftDictionaryComponents().getAllExamples()) {
            for (String type : ex.getTranslations().keySet()) counts.merge(type, 1L, Long::sum);
        }
        showCategoryTable(I18n.get("nav.transTypes"), I18n.get("col.type"), counts);
    }

    /* ════════════════════ NOTE TYPES VIEW ════════════════════ */

    private void showNoteTypesView() {
        if (currentDictionary == null) { tableContainer.getChildren().setAll(new Label(I18n.get("placeholder.noDictionary"))); return; }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LiftNote n : currentDictionary.getLiftDictionaryComponents().getAllNotes()) {
            counts.merge(n.getType().orElse(I18n.get("placeholder.noType")), 1L, Long::sum);
        }
        showCategoryTable(I18n.get("nav.noteTypes"), I18n.get("col.type"), counts);
    }

    /** Shared helper: show a simple value + frequency table for category views. */
    private record CategoryRow(String value, long frequency) {}

    private void showCategoryTable(String title, String colLabel, Map<String, Long> counts) {
        TableView<CategoryRow> table = new TableView<>();
        TableColumn<CategoryRow, String> valCol = new TableColumn<>(colLabel);
        valCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().value()));
        valCol.setPrefWidth(250);
        TableColumn<CategoryRow, String> freqCol = new TableColumn<>(I18n.get("col.frequency"));
        freqCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(String.valueOf(cd.getValue().frequency())));
        freqCol.setPrefWidth(100);
        table.getColumns().addAll(valCol, freqCol);
        counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(e -> table.getItems().add(new CategoryRow(e.getKey(), e.getValue())));
        VBox wrapper = wrapTableWithFilters(table);
        tableContainer.getChildren().setAll(wrapper);
        updateCountLabel(table.getItems().size(), table.getItems().size());
    }

    /* ════════════════════ COLUMN FILTERS (5.8) ════════════════════ */

    /**
     * Wraps a TableView in a VBox with a row of TextFields below the headers.
     * Each TextField filters its column; only rows matching ALL column filters are shown.
     */
    @SuppressWarnings("unchecked")
    private static <T> VBox wrapTableWithFilters(TableView<T> table) {
        ObservableList<T> sourceItems = FXCollections.observableArrayList(table.getItems());
        FilteredList<T> filtered = new FilteredList<>(sourceItems, t -> true);
        table.setItems(filtered);

        HBox filterRow = new HBox(6);
        filterRow.setPadding(new Insets(4, 6, 4, 6));
        filterRow.setStyle("-fx-background-color: #eef2f3;");
        List<TextField> filterFields = new ArrayList<>();

        Button clearBtn = new Button("Réinitialiser les filtres");
        clearBtn.setOnAction(e -> {
            // Clearing text will trigger listeners that update the predicate to show all
            filterFields.forEach(tf -> tf.setText(""));
            filtered.setPredicate(t -> true);
            table.getSelectionModel().clearSelection();
        });

        HBox header = new HBox();
        header.setPadding(new Insets(0, 6, 4, 6));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(spacer, clearBtn);

        for (TableColumn<T, ?> column : collectLeafColumns(table)) {
            TextField tf = new TextField();
            tf.setPromptText(I18n.get("filter.prompt"));
            tf.setPrefWidth(column.getPrefWidth());
            tf.setStyle("-fx-font-size: 11px; -fx-padding: 2 4 2 4;");
            filterFields.add(tf);
            filterRow.getChildren().add(tf);

            tf.textProperty().addListener((obs, o, n) -> {
                filtered.setPredicate(row -> {
                    for (int i = 0; i < filterFields.size(); i++) {
                        String filterText = filterFields.get(i).getText();
                        if (filterText == null || filterText.isBlank()) continue;
                        List<TableColumn<T, ?>> leaves = collectLeafColumns(table);
                        if (i >= leaves.size()) continue;
                        TableColumn<T, ?> col = leaves.get(i);
                        Object cellVal = col.getCellObservableValue(row) != null ? col.getCellObservableValue(row).getValue() : null;
                        String cellText = cellVal != null ? cellVal.toString() : "";
                        if (!cellText.toLowerCase(Locale.ROOT).contains(filterText.toLowerCase(Locale.ROOT))) return false;
                    }
                    return true;
                });
            });
        }

        VBox wrapper = new VBox(header, filterRow, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        return wrapper;
    }

    /** Collect leaf (non-grouped) columns in display order. */
    private static <T> List<TableColumn<T, ?>> collectLeafColumns(TableView<T> table) {
        List<TableColumn<T, ?>> leaves = new ArrayList<>();
        for (TableColumn<T, ?> c : table.getColumns()) collectLeaves(c, leaves);
        return leaves;
    }
    private static <T> void collectLeaves(TableColumn<T, ?> col, List<TableColumn<T, ?>> leaves) {
        if (col.getColumns().isEmpty()) { leaves.add(col); }
        else { for (TableColumn<T, ?> child : col.getColumns()) collectLeaves(child, leaves); }
    }

    /* ────────────────── UTILITIES ────────────────── */

    @FunctionalInterface private interface NodeFactory { javafx.scene.Node create(); }
    @FunctionalInterface private interface ItemRenderer<T> { javafx.scene.Node render(T item); }
    @FunctionalInterface private interface ListSupplier { List<String> get(); }

    private static TableColumn<CategoryRow, String> colCat(String title, java.util.function.Function<CategoryRow, String> extractor) {
        TableColumn<CategoryRow, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue() == null ? "" : extractor.apply(cd.getValue())));
        c.setPrefWidth(title.equals("Fréquence") ? 100 : 250);
        return c;
    }

    private static <T> TableColumn<T, String> col(String title, java.util.function.Function<T, String> extractor) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue() == null ? "" : extractor.apply(cd.getValue())));
        c.setPrefWidth(140);
        return c;
    }

    private static void addSection(VBox container, String title, NodeFactory factory, boolean expanded) {
        TitledPane tp = new TitledPane(title, factory.create()); tp.setExpanded(expanded); tp.setAnimated(false); container.getChildren().add(tp);
    }

    private static <T> void addListSection(VBox container, String title, List<T> items, ItemRenderer<T> renderer, boolean expanded) {
        if (items == null || items.isEmpty()) { TitledPane tp = new TitledPane(title + " (0)", new Label(I18n.get("editor.none"))); tp.setExpanded(false); tp.setAnimated(false); container.getChildren().add(tp); return; }
        VBox box = new VBox(4); int i = 1;
        for (T item : items) { TitledPane ip = new TitledPane("#" + i++, renderer.render(item)); ip.setExpanded(false); ip.setAnimated(false); box.getChildren().add(ip); }
        TitledPane tp = new TitledPane(title + " (" + items.size() + ")", box); tp.setExpanded(expanded); tp.setAnimated(false); container.getChildren().add(tp);
    }

    private static void addReadOnlyRow(GridPane grid, int row, String label, String value) {
        grid.add(new Label(label), 0, row);
        TextField tf = new TextField(value); tf.setEditable(false); GridPane.setHgrow(tf, Priority.ALWAYS); grid.add(tf, 1, row);
    }

    private void updateCountLabel(int shown, int total) { if (tableCountLabel != null) tableCountLabel.setText(shown + " / " + total); }

    private static DatePicker buildDatePicker(String isoDate) {
        DatePicker dp = new DatePicker();
        dp.setEditable(true);
        dp.setPromptText("yyyy-MM-dd");
        if (isoDate != null && !isoDate.isBlank()) {
            try {
                String datePart = isoDate.length() > 10 ? isoDate.substring(0, 10) : isoDate;
                dp.setValue(LocalDate.parse(datePart));
            } catch (DateTimeParseException ignored) {}
        }
        return dp;
    }

    private static String getTraitValue(LiftEntry e, String name) {
        return e == null ? "" : e.getTraits().stream().filter(t -> name.equals(t.getName())).findFirst().map(LiftTrait::getValue).orElse("");
    }

    private static String buildSearchText(LiftEntry entry) {
        if (entry == null) return "";
        StringBuilder sb = new StringBuilder(); appendSep(sb, getTraitValue(entry, "code"));
        for (Form f : entry.getForms().getForms()) appendSep(sb, f.toPlainText());
        for (LiftPronunciation p : entry.getPronunciations()) for (Form f : p.getProunciation().getForms()) appendSep(sb, f.toPlainText());
        for (LiftSense s : entry.getSenses()) { for (Form f : s.getDefinition().getForms()) appendSep(sb, f.toPlainText()); for (Form f : s.getGloss().getForms()) appendSep(sb, f.toPlainText()); }
        return sb.toString();
    }

    private static String describeParent(Object parent) {
        if (parent == null) return "";
        if (parent instanceof LiftEntry e) return "entry:" + e.getId().orElse("?");
        if (parent instanceof LiftSense s) return "sense:" + s.getId().orElse("?");
        if (parent instanceof GrammaticalInfo) return "gram-info";
        return parent.getClass().getSimpleName();
    }

    private List<String> getObjectLanguages() {
        return currentDictionary == null ? List.of() : currentDictionary.getObjectLanguagesOfAllText().stream().filter(s -> s != null && !s.isBlank()).sorted().toList();
    }
    private List<String> getMetaLanguages() {
        return currentDictionary == null ? List.of() : currentDictionary.getMetaLanguagesOfAllText().stream().filter(s -> s != null && !s.isBlank()).sorted().toList();
    }
    private List<String> getAllLanguages() {
        if (currentDictionary == null) return List.of();
        Set<String> all = new HashSet<>(); all.addAll(currentDictionary.getObjectLanguagesOfAllText()); all.addAll(currentDictionary.getMetaLanguagesOfAllText());
        return all.stream().filter(s -> s != null && !s.isBlank()).sorted().toList();
    }

    /* ─── Known dropdown values from dictionary ─── */

    private List<String> getKnownTraitNames() {
        return currentDictionary == null ? List.of() : currentDictionary.getTraitName().stream().sorted().toList();
    }
    private Map<String, Set<String>> getKnownTraitValues() {
        if (currentDictionary == null) return Map.of();
        Map<String, Set<String>> result = new HashMap<>();
        for (LiftTrait t : currentDictionary.getLiftDictionaryComponents().getAllTraits()) {
            result.computeIfAbsent(t.getName(), k -> new TreeSet<>()).add(t.getValue());
        }
        return result;
    }
    private List<String> getKnownAnnotationNames() {
        return currentDictionary == null ? List.of() : currentDictionary.getLiftDictionaryComponents().getAllAnnotations().stream()
            .map(LiftAnnotation::getName).filter(Objects::nonNull).distinct().sorted().toList();
    }
    private List<String> getKnownFieldTypes() {
        return currentDictionary == null ? List.of() : currentDictionary.getFieldType().stream().sorted().toList();
    }

    private void showError(String title, String msg) { Alert a = new Alert(Alert.AlertType.ERROR); a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait(); }
    private void showInfo(String title, String msg) { Alert a = new Alert(Alert.AlertType.INFORMATION); a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait(); }

    private void showPreferencesDialog() {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(I18n.get("prefs.title"));
        dlg.setHeaderText(I18n.get("prefs.title"));
        dlg.setResizable(false);
        dlg.getDialogPane().setPrefSize(400, 200);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        Label langLabel = new Label(I18n.get("prefs.language"));
        ComboBox<String> langCombo = new ComboBox<>(FXCollections.observableArrayList("Français", "English"));
        langCombo.setValue("fr".equals(I18n.getLocale().getLanguage()) ? "Français" : "English");

        langCombo.setOnAction(e -> {
            String sel = langCombo.getValue();
            Locale newLocale = "Français".equals(sel) ? Locale.FRENCH : Locale.ENGLISH;
            if (!newLocale.getLanguage().equals(I18n.getLocale().getLanguage())) {
                I18n.setLocale(newLocale);
                dlg.close();
                Platform.runLater(com.example.MainApp::reloadScene);
            }
        });

        VBox content = new VBox(12, new HBox(12, langLabel, langCombo));
        content.setPadding(new Insets(16));
        dlg.getDialogPane().setContent(content);
        dlg.showAndWait();
    }

    private record ConfigRow(javafx.beans.property.StringProperty abbrev, javafx.beans.property.StringProperty description) {
        ConfigRow(String a, String d) {
            this(new javafx.beans.property.SimpleStringProperty(a), new javafx.beans.property.SimpleStringProperty(d));
        }
    }

    private void showConfigDialog(String title, ListSupplier supplier) {
        List<String> items = supplier.get();
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(I18n.get("config.title", title));
        dlg.setHeaderText(I18n.get("config.header", title, items.size()));
        dlg.setResizable(true);
        dlg.getDialogPane().setPrefSize(600, 480);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TableView<ConfigRow> configTable = new TableView<>();
        configTable.setEditable(true);
        configTable.setPrefHeight(320);

        TableColumn<ConfigRow, String> abbrCol = new TableColumn<>(I18n.get("col.abbreviation"));
        abbrCol.setCellValueFactory(cd -> cd.getValue().abbrev());
        abbrCol.setCellFactory(TextFieldTableCell.forTableColumn());
        abbrCol.setOnEditCommit(e -> e.getRowValue().abbrev().set(e.getNewValue()));
        abbrCol.setPrefWidth(180);

        TableColumn<ConfigRow, String> descCol = new TableColumn<>(I18n.get("col.description"));
        descCol.setCellValueFactory(cd -> cd.getValue().description());
        descCol.setCellFactory(TextFieldTableCell.forTableColumn());
        descCol.setOnEditCommit(e -> e.getRowValue().description().set(e.getNewValue()));
        descCol.setPrefWidth(350);

        configTable.getColumns().addAll(abbrCol, descCol);
        for (String item : items) configTable.getItems().add(new ConfigRow(item, ""));

        TextField addAbbrField = new TextField();
        addAbbrField.setPromptText(I18n.get("config.addAbbr"));
        TextField addDescField = new TextField();
        addDescField.setPromptText(I18n.get("config.addDesc"));
        Button addBtn = new Button(I18n.get("btn.add"));
        addBtn.setOnAction(e -> {
            String a = addAbbrField.getText().trim();
            if (!a.isEmpty()) {
                configTable.getItems().add(new ConfigRow(a, addDescField.getText().trim()));
                addAbbrField.clear(); addDescField.clear();
            }
        });
        Button removeBtn = new Button(I18n.get("btn.delete"));
        removeBtn.setOnAction(e -> {
            ConfigRow sel = configTable.getSelectionModel().getSelectedItem();
            if (sel != null) configTable.getItems().remove(sel);
        });
        HBox controls = new HBox(8, addAbbrField, addDescField, addBtn, removeBtn);
        controls.setPadding(new Insets(6, 0, 0, 0));
        HBox.setHgrow(addAbbrField, Priority.ALWAYS);
        HBox.setHgrow(addDescField, Priority.ALWAYS);

        VBox content = new VBox(6, configTable, controls);
        dlg.getDialogPane().setContent(content);
        dlg.showAndWait();
    }

    private LiftDictionary loadDemoDictionary() {
        try (InputStream in = MainController.class.getResourceAsStream("/lift/demo.lift")) {
            if (in == null) return null;
            File tmp = Files.createTempFile("dict-demo-", ".lift").toFile(); tmp.deleteOnExit();
            try (FileOutputStream out = new FileOutputStream(tmp)) { in.transferTo(out); }
            return LiftDictionary.loadDictionaryWithFile(tmp);
        } catch (Exception e) { return null; }
    }

    private static LiftFactory getFactory(LiftDictionary d) { return d != null && d.getLiftDictionaryComponents() instanceof LiftFactory lf ? lf : null; }
    private static String safeTrim(String s) { return s == null ? "" : s.trim(); }
    private static void appendSep(StringBuilder sb, String part) { if (part != null && !part.isBlank()) { if (!sb.isEmpty()) sb.append("; "); sb.append(part); } }
}
