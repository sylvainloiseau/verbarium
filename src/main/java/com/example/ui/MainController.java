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
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.stage.FileChooser;
import javafx.application.Platform;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.control.TextInputDialog;

import java.io.*;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
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

    /* ─── Header configuration nav keys ─── */
    private static final String NAV_CFG_NOTE_TYPES  = "nav.cfgNoteTypes";
    private static final String NAV_CFG_TRANS_TYPES = "nav.cfgTransTypes";
    private static final String NAV_CFG_GRAM_INFO   = "nav.cfgGramInfo";
    private static final String NAV_CFG_TRAIT_RANGES = "nav.cfgTraitRanges";
    private static final String NAV_CFG_FIELD_DEFS  = "nav.cfgFieldDefs";

    /* ─── State ─── */
    private final DictionaryService dictionaryService = new DictionaryService();
    private LiftDictionary currentDictionary;
    private String currentView = NAV_ENTRIES;
    private boolean ignoreNavSelectionEvents = false;

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
        ensureRightPanelVisible();
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

        TreeItem<String> headerCfg = new TreeItem<>(I18n.get("nav.headerConfig"));
        headerCfg.setExpanded(true);
        headerCfg.getChildren().addAll(
            navItem(NAV_CFG_NOTE_TYPES), navItem(NAV_CFG_TRANS_TYPES),
            navItem(NAV_CFG_GRAM_INFO), navItem(NAV_CFG_TRAIT_RANGES),
            navItem(NAV_CFG_FIELD_DEFS)
        );

        TreeItem<String> quick = navItem(NAV_QUICK_ENTRY);

        root.getChildren().addAll(objects, langs, cats, headerCfg, quick);
        navTree.setRoot(root);
        navTree.setShowRoot(false);

        navTree.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (ignoreNavSelectionEvents) return;
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
        ensureRightPanelVisible();
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
            case NAV_CFG_NOTE_TYPES  -> showHeaderRangeView("note-type");
            case NAV_CFG_TRANS_TYPES -> showHeaderRangeView("translation-type");
            case NAV_CFG_GRAM_INFO   -> showHeaderRangeView("grammatical-info");
            case NAV_CFG_TRAIT_RANGES -> showHeaderAllRangesView();
            case NAV_CFG_FIELD_DEFS  -> showHeaderFieldDefsView();
            default -> showEntryView();
        }
    }

    private boolean splitConstraintInstalled = false;

    private void ensureRightPanelVisible() {
        if (rightContent != null) {
            rightContent.setManaged(true);
            rightContent.setVisible(true);
        }
        if (mainSplit != null && mainSplit.getItems().size() >= 2 && !splitConstraintInstalled) {
            splitConstraintInstalled = true;
            // Enforce min widths so neither pane can completely disappear.
            javafx.scene.Node leftPane  = mainSplit.getItems().get(0);
            javafx.scene.Node rightPane = mainSplit.getItems().get(1);
            if (leftPane  instanceof Region r) r.setMinWidth(250);
            if (rightPane instanceof Region r) r.setMinWidth(300);

            // Clamp the divider so the right panel always stays visible.
            SplitPane.Divider divider = mainSplit.getDividers().get(0);
            divider.positionProperty().addListener((obs, oldPos, newPos) -> {
                double total = mainSplit.getWidth();
                if (total <= 0) return;
                double minRight = 300.0;
                double maxPosition = 1.0 - (minRight / total);
                double minLeft = 250.0;
                double minPosition = minLeft / total;
                double clamped = Math.max(minPosition, Math.min(maxPosition, newPos.doubleValue()));
                if (Math.abs(clamped - newPos.doubleValue()) > 0.001) {
                    Platform.runLater(() -> divider.setPosition(clamped));
                }
            });
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
        GridPane filterRow = buildEntryFilterRow();
        String clearOption = I18n.get("filter.clear");

        Button clearBtn = new Button(I18n.get("filter.resetAll"));
        clearBtn.setOnAction(e -> {
            entryFilterInternalUpdate = true;
            try {
                entryColumnFilters.forEach(cb -> cb.setValue(clearOption));
            } finally {
                entryFilterInternalUpdate = false;
            }
            applyCurrentFilter();
            entryTable.getSelectionModel().clearSelection();
        });
        HBox header = new HBox();
        header.setPadding(new Insets(0,6,4,6));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(spacer, clearBtn);

        VBox wrapper = new VBox(header, filterRow, entryTable);
        wrapper.setMinWidth(0);
        filterRow.setMinWidth(0);
        entryTable.setMinWidth(0);
        VBox.setVgrow(entryTable, Priority.ALWAYS);
        tableContainer.getChildren().setAll(wrapper);
        applyCurrentFilter();
        if (!filteredEntries.isEmpty()) {
            entryTable.getSelectionModel().selectFirst();
            LiftEntry selected = entryTable.getSelectionModel().getSelectedItem();
            if (selected != null) populateEntryEditor(selected);
        }
        updateCountLabel(filteredEntries.size(), baseEntries.size());
    }

    private final List<ComboBox<String>> entryColumnFilters = new ArrayList<>();
    private boolean entryFilterInternalUpdate = false;

    private GridPane buildEntryFilterRow() {
        entryColumnFilters.clear();
        GridPane row = new GridPane();
        row.setHgap(0);
        row.setPadding(new Insets(2, 0, 2, 0));
        row.setStyle("-fx-background-color: #eef2f3;");
        String clearOption = I18n.get("filter.clear");
        List<TableColumn<LiftEntry, ?>> leaves = collectLeafColumns(entryTable);

        for (int i = 0; i < leaves.size(); i++) {
            TableColumn<LiftEntry, ?> col = leaves.get(i);
            ComboBox<String> cb = new ComboBox<>();
            cb.setPromptText(I18n.get("filter.prompt"));
            cb.setEditable(false);
            cb.setMaxWidth(Double.MAX_VALUE);
            cb.setStyle("-fx-font-size: 11px; -fx-padding: 0 2 0 2;");
            cb.setCellFactory(list -> new ListCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setDisable(false);
                        setStyle("");
                        return;
                    }
                    setText(item);
                    boolean isClearItem = clearOption.equals(item);
                    boolean activeFilter = isActiveFilter(cb.getValue(), clearOption);
                    boolean disableClear = isClearItem && !activeFilter;
                    setDisable(disableClear);
                    setStyle(disableClear ? "-fx-opacity: 0.45;" : "");
                }
            });
            cb.setButtonCell(new ListCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item);
                }
            });
            cb.valueProperty().addListener((obs, o, n) -> {
                if (clearOption.equals(n) && !isActiveFilter(o, clearOption)) {
                    cb.setValue(o);
                    return;
                }
                if (entryFilterInternalUpdate) return;
                applyCurrentFilter();
            });
            entryColumnFilters.add(cb);

            ColumnConstraints cc = new ColumnConstraints();
            cc.prefWidthProperty().bind(col.widthProperty());
            row.getColumnConstraints().add(cc);
            GridPane.setHgrow(cb, Priority.ALWAYS);
            row.add(cb, i, 0);
        }

        entryFilterInternalUpdate = true;
        try {
            entryColumnFilters.forEach(cb -> cb.setValue(clearOption));
        } finally {
            entryFilterInternalUpdate = false;
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
        langFieldTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) populateLangFieldEditor(n);
        });
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

        traitTable.getColumns().addAll(
            col(I18n.get("col.parentType"), (LiftTrait t) -> describeParentType(t.getParent())),
            col(I18n.get("col.parent"), (LiftTrait t) -> describeParent(t.getParent())),
            col(I18n.get("col.name"), LiftTrait::getName),
            col(I18n.get("col.value"), LiftTrait::getValue),
            col(I18n.get("col.frequency"), t -> {
                long c = allTraits.stream().filter(x -> x.getName().equals(t.getName()) && x.getValue().equals(t.getValue())).count();
                return String.valueOf(c);
            })
        );
        traitTable.getItems().addAll(allTraits);
        traitTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) populateTraitSummaryEditor(n);
        });

        tableContainer.getChildren().setAll(wrapTableWithFilters(traitTable));
        updateCountLabel(allTraits.size(), allTraits.size());
    }

    private void showAnnotationView() {
        annotationTable.setItems(FXCollections.observableArrayList());
        annotationTable.getColumns().clear();
        if (currentDictionary == null) { tableContainer.getChildren().setAll(annotationTable); return; }

        List<LiftAnnotation> all = currentDictionary.getLiftDictionaryComponents().getAllAnnotations();

        annotationTable.getColumns().addAll(
            col(I18n.get("col.parentType"), (LiftAnnotation a) -> describeParentType(a.getParent())),
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
        annotationTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) populateAnnotationSummaryEditor(n);
        });

        tableContainer.getChildren().setAll(wrapTableWithFilters(annotationTable));
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
        fieldTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) populateFieldSummaryEditor(n);
        });
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
        try {
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

        addSection(editorContainer, I18n.get("editor.forms"), () -> { MultiTextEditor m = new MultiTextEditor(); m.setAvailableLanguages(objLangs); m.setMultiText(entry.getForms()); return m; }, true);
        addListSection(editorContainer, I18n.get("editor.traits"), safeList(entry.getTraits()), t -> {
            TraitEditor te = new TraitEditor(); te.setTrait(t, objLangs, traitNames, traitValues); return te;
        }, true);
        addListSection(editorContainer, I18n.get("editor.pronunciations"), safeList(entry.getPronunciations()), p -> { PronunciationEditor pe = new PronunciationEditor(); pe.setPronunciation(p, objLangs); return pe; }, false);

        List<LiftSense> senses = safeList(entry.getSenses());
        if (!senses.isEmpty()) {
            addSection(editorContainer, I18n.get("editor.senses") + " (" + senses.size() + ")", () -> {
                VBox box = new VBox(4);
                for (LiftSense s : senses) {
                    String label = s.getId().orElse("?") + " – " + s.getGloss().getForms().stream().findFirst().map(Form::toPlainText).orElse("");
                    Hyperlink link = new Hyperlink(label);
                    link.setOnAction(e -> navigateToSenseKeepingEntriesFocus(s));
                    box.getChildren().add(link);
                }
                return box;
            }, true);
        }
        addListSection(editorContainer, I18n.get("editor.variants"), safeList(entry.getVariants()), v -> { VariantEditor ve = new VariantEditor(); ve.setVariant(v, objLangs, metaLangs); return ve; }, false);
        addListSection(editorContainer, I18n.get("editor.relations"), safeList(entry.getRelations()), r -> { RelationEditor re = new RelationEditor(); re.setRelation(r, metaLangs); return re; }, false);
        addListSection(editorContainer, I18n.get("editor.etymologies"), safeList(entry.getEtymologies()), et -> { EtymologyEditor ee = new EtymologyEditor(); ee.setEtymology(et, objLangs, metaLangs); return ee; }, false);
        addListSection(editorContainer, I18n.get("editor.annotations"), safeList(entry.getAnnotations()), a -> {
            AnnotationEditor ae = new AnnotationEditor(); ae.setAnnotation(a, metaLangs, annotationNames); return ae;
        }, false);
        addListSection(editorContainer, I18n.get("editor.notes"), new ArrayList<>(safeMapValues(entry.getNotes())), n -> { NoteEditor ne = new NoteEditor(); ne.setNote(n, metaLangs); return ne; }, false);
        addListSection(editorContainer, I18n.get("editor.fields"), safeList(entry.getFields()), f -> {
            FieldEditor fe = new FieldEditor(); fe.setField(f, metaLangs, fieldTypes); return fe;
        }, false);
        addSection(editorContainer, I18n.get("editor.identity"), () -> {
            GridPane g = new GridPane(); g.setHgap(8); g.setVgap(6);
            addReadOnlyRow(g, 0, I18n.get("field.id"), entry.getId().orElse(""));

            g.add(new Label(I18n.get("field.dateCreated")), 0, 1);
            DatePicker dpCreated = buildDatePicker(entry.getDateCreated().orElse(""));
            styleReadOnlyDatePicker(dpCreated);
            GridPane.setHgrow(dpCreated, Priority.ALWAYS);
            g.add(dpCreated, 1, 1);

            g.add(new Label(I18n.get("field.dateModified")), 0, 2);
            DatePicker dpModified = buildDatePicker(entry.getDateModified().orElse(""));
            styleReadOnlyDatePicker(dpModified);
            GridPane.setHgrow(dpModified, Priority.ALWAYS);
            g.add(dpModified, 1, 2);
            return g;
        }, true);

        LiftFactory factory = getFactory(currentDictionary);
        if (factory != null) {
            FlowPane addButtons = new FlowPane(8, 6);
            addButtons.setPadding(new Insets(8, 0, 0, 0));

            Button addSenseBtn = new Button(I18n.get("btn.addSense"));
            addSenseBtn.setOnAction(e -> {
                factory.createSense(new org.xml.sax.helpers.AttributesImpl(), entry);
                populateEntryEditor(entry);
            });

            Button addVariantBtn = new Button(I18n.get("btn.addVariant"));
            addVariantBtn.setOnAction(e -> {
                factory.createVariant(new org.xml.sax.helpers.AttributesImpl(), entry);
                populateEntryEditor(entry);
            });

            Button addPronBtn = new Button(I18n.get("btn.addPronunciation"));
            addPronBtn.setOnAction(e -> {
                factory.createPronunciation(entry);
                populateEntryEditor(entry);
            });

            Button addTraitBtn = new Button(I18n.get("btn.addTrait"));
            addTraitBtn.setOnAction(e -> {
                List<String> names = getKnownTraitNames();
                ChoiceDialog<String> dlg = new ChoiceDialog<>(names.isEmpty() ? null : names.get(0), names);
                dlg.setTitle(I18n.get("btn.addTrait"));
                dlg.setHeaderText(I18n.get("col.name"));
                dlg.showAndWait().ifPresent(name -> {
                    factory.createTrait(name, "", entry);
                    populateEntryEditor(entry);
                });
            });

            Button addNoteBtn = new Button(I18n.get("btn.addNote"));
            addNoteBtn.setOnAction(e -> {
                List<String> types = getKnownNoteTypes();
                ChoiceDialog<String> dlg = new ChoiceDialog<>(types.isEmpty() ? null : types.get(0), types);
                dlg.setTitle(I18n.get("btn.addNote"));
                dlg.setHeaderText(I18n.get("col.type"));
                dlg.showAndWait().ifPresent(type -> {
                    factory.createNote(type, entry);
                    populateEntryEditor(entry);
                });
            });

            Button addFieldBtn = new Button(I18n.get("btn.addField"));
            addFieldBtn.setOnAction(e -> {
                List<String> types = getKnownFieldTypes();
                ChoiceDialog<String> dlg = new ChoiceDialog<>(types.isEmpty() ? null : types.get(0), types);
                dlg.setTitle(I18n.get("btn.addField"));
                dlg.setHeaderText(I18n.get("col.type"));
                dlg.showAndWait().ifPresent(type -> {
                    factory.createField(type, entry);
                    populateEntryEditor(entry);
                });
            });

            Button addAnnotBtn = new Button(I18n.get("btn.addAnnotation"));
            addAnnotBtn.setOnAction(e -> {
                List<String> names = getKnownAnnotationNames();
                ChoiceDialog<String> dlg = new ChoiceDialog<>(names.isEmpty() ? null : names.get(0), names);
                dlg.setTitle(I18n.get("btn.addAnnotation"));
                dlg.setHeaderText(I18n.get("col.name"));
                dlg.showAndWait().ifPresent(name -> {
                    factory.createAnnotation(name, entry);
                    populateEntryEditor(entry);
                });
            });

            addButtons.getChildren().addAll(addSenseBtn, addVariantBtn, addPronBtn, addTraitBtn, addNoteBtn, addFieldBtn, addAnnotBtn);
            editorContainer.getChildren().add(addButtons);
        }
        } catch (Exception ex) {
            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            values.put(I18n.get("field.id"), entry == null ? "" : entry.getId().orElse(""));
            values.put("Erreur", Optional.ofNullable(ex.getMessage()).orElse(ex.getClass().getSimpleName()));
            populateSummaryEditor("Entrée (erreur d'affichage)", "", values);
        }
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static <K, V> Collection<V> safeMapValues(Map<K, V> map) {
        return map == null ? List.of() : map.values();
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
            backLink.setOnAction(e -> navigateToEntryFromSense(parentEntry));
            editorContainer.getChildren().add(backLink);
        });

        // Link to examples
        if (!sense.getExamples().isEmpty()) {
            Hyperlink exLink = new Hyperlink(I18n.get("sense.viewExamples", sense.getExamples().size()));
            LiftExample firstExample = sense.getExamples().getFirst();
            exLink.setOnAction(e -> navigateToExampleKeepingEntriesFocus(firstExample));
            editorContainer.getChildren().add(exLink);
        }

        SenseEditor se = new SenseEditor();
        se.setSense(sense, metaLangs, objLangs);
        editorContainer.getChildren().add(se);

        LiftFactory factory = getFactory(currentDictionary);
        if (factory != null) {
            FlowPane addButtons = new FlowPane(8, 6);
            addButtons.setPadding(new Insets(8, 0, 0, 0));

            Button addExBtn = new Button(I18n.get("btn.addExample"));
            addExBtn.setOnAction(e -> {
                factory.createExample(new org.xml.sax.helpers.AttributesImpl(), sense);
                populateSenseEditor(sense);
            });

            Button addNoteBtn = new Button(I18n.get("btn.addNote"));
            addNoteBtn.setOnAction(e -> {
                List<String> types = getKnownNoteTypes();
                ChoiceDialog<String> dlg = new ChoiceDialog<>(types.isEmpty() ? null : types.get(0), types);
                dlg.setTitle(I18n.get("btn.addNote"));
                dlg.setHeaderText(I18n.get("col.type"));
                dlg.showAndWait().ifPresent(type -> {
                    factory.createNote(type, sense);
                    populateSenseEditor(sense);
                });
            });

            addButtons.getChildren().addAll(addExBtn, addNoteBtn);
            editorContainer.getChildren().add(addButtons);
        }
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

    private void navigateToSenseKeepingEntriesFocus(LiftSense sense) {
        if (sense == null) return;
        switchView(NAV_SENSES);
        clearSearchAndVisibleColumnFilters();
        if (senseTable.getItems().contains(sense)) {
            senseTable.getSelectionModel().select(sense);
            senseTable.scrollTo(sense);
        }
        pinLeftNavigationOnEntries();
    }

    private void navigateToEntryFromSense(LiftEntry entry) {
        if (entry == null) return;
        switchView(NAV_ENTRIES);
        clearSearchAndVisibleColumnFilters();
        applyCurrentFilter();
        if (filteredEntries.contains(entry)) {
            entryTable.getSelectionModel().select(entry);
            entryTable.scrollTo(entry);
            populateEntryEditor(entry);
        } else if (!filteredEntries.isEmpty()) {
            LiftEntry first = filteredEntries.getFirst();
            entryTable.getSelectionModel().select(first);
            entryTable.scrollTo(first);
            populateEntryEditor(first);
        }
    }

    private void navigateToExampleKeepingEntriesFocus(LiftExample example) {
        if (example == null) return;
        switchView(NAV_EXAMPLES);
        clearSearchAndVisibleColumnFilters();
        if (exampleTable.getItems().contains(example)) {
            exampleTable.getSelectionModel().select(example);
            exampleTable.scrollTo(example);
        }
        pinLeftNavigationOnEntries();
    }

    private void clearSearchAndVisibleColumnFilters() {
        if (searchField != null && !searchField.getText().isBlank()) {
            searchField.clear();
        }
        if (tableContainer == null || tableContainer.getChildren().isEmpty()) return;
        javafx.scene.Node root = tableContainer.getChildren().get(0);
        if (!(root instanceof VBox wrapper) || wrapper.getChildren().size() < 2) return;
        javafx.scene.Node filterNode = wrapper.getChildren().get(1);
        if (!(filterNode instanceof Pane filterPane)) return;

        String clearOption = I18n.get("filter.clear");
        for (javafx.scene.Node child : filterPane.getChildren()) {
            if (!(child instanceof ComboBox<?> rawCombo)) continue;
            @SuppressWarnings("unchecked")
            ComboBox<String> combo = (ComboBox<String>) rawCombo;
            combo.setValue(clearOption);
        }
    }

    private void pinLeftNavigationOnEntries() {
        if (navTree == null || navTree.getSelectionModel() == null) return;
        TreeItem<String> entriesItem = findNavItemByKey(NAV_ENTRIES);
        if (entriesItem == null) return;

        ignoreNavSelectionEvents = true;
        try {
            navTree.getSelectionModel().select(entriesItem);
        } finally {
            ignoreNavSelectionEvents = false;
        }
    }

    private TreeItem<String> findNavItemByKey(String navKey) {
        for (Map.Entry<TreeItem<String>, String> entry : navKeyMap.entrySet()) {
            if (Objects.equals(entry.getValue(), navKey)) return entry.getKey();
        }
        return null;
    }

    private void populateLangFieldEditor(MultiTextField row) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put(I18n.get("col.parentType"), row.parentType());
        values.put(I18n.get("col.parent"), row.parentId());
        values.put(I18n.get("nav.languages"), row.lang());
        values.put(I18n.get("col.text"), row.text());
        populateSummaryEditor(I18n.get(currentView), "", values);
    }

    private void populateTraitSummaryEditor(LiftTrait trait) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put(I18n.get("col.parentType"), describeParentType(trait.getParent()));
        values.put(I18n.get("col.parent"), describeParent(trait.getParent()));
        values.put(I18n.get("col.name"), trait.getName());
        values.put(I18n.get("col.value"), trait.getValue());
        populateSummaryEditor(I18n.get("nav.traits"), "", values);
    }

    private void populateAnnotationSummaryEditor(LiftAnnotation annotation) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put(I18n.get("col.parentType"), describeParentType(annotation.getParent()));
        values.put(I18n.get("col.parent"), describeParent(annotation.getParent()));
        values.put(I18n.get("col.name"), annotation.getName());
        values.put(I18n.get("col.value"), annotation.getValue().orElse(""));
        values.put(I18n.get("col.who"), annotation.getWho().orElse(""));
        values.put(I18n.get("col.when"), annotation.getWhen().orElse(""));
        populateSummaryEditor(I18n.get("nav.annotations"), "", values);
    }

    private void populateFieldSummaryEditor(LiftField field) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put(I18n.get("col.type"), field.getName());
        values.put(I18n.get("col.text"), field.getText().getForms().stream().findFirst().map(Form::toPlainText).orElse(""));
        populateSummaryEditor(I18n.get("nav.fields"), "", values);
    }

    private void populateCategorySummaryEditor(String title, CategoryRow row) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put(I18n.get("col.value"), row.value());
        values.put(I18n.get("col.frequency"), String.valueOf(row.frequency()));
        populateSummaryEditor(title, "", values);
    }

    private void populateSummaryEditor(String title, String code, LinkedHashMap<String, String> values) {
        editEntryTitle.setText(title);
        editEntryCode.setText(code == null ? "" : code);
        editorContainer.getChildren().clear();

        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(6);
        int row = 0;
        for (Map.Entry<String, String> e : values.entrySet()) {
            addReadOnlyRow(g, row++, e.getKey(), e.getValue() == null ? "" : e.getValue());
        }
        editorContainer.getChildren().add(g);
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
            String clearOption = I18n.get("filter.clear");
            filteredEntries.setPredicate(entry -> {
                if (entry == null) return false;
                if (!q.isEmpty() && !buildSearchText(entry).toLowerCase(Locale.ROOT).contains(q)) return false;
                for (int i = 0; i < entryColumnFilters.size() && i < leaves.size(); i++) {
                    String selected = entryColumnFilters.get(i).getValue();
                    if (!isActiveFilter(selected, clearOption)) continue;
                    Object val = leaves.get(i).getCellObservableValue(entry) != null ? leaves.get(i).getCellObservableValue(entry).getValue() : null;
                    String cellText = val != null ? val.toString() : "";
                    if (!selected.equals(cellText)) return false;
                }
                return true;
            });
            refreshEntryFacetChoices(q, leaves, clearOption);
            updateCountLabel(filteredEntries.size(), baseEntries.size());
        }
    }

    private void refreshEntryFacetChoices(String q, List<TableColumn<LiftEntry, ?>> leaves, String clearOption) {
        if (entryColumnFilters.isEmpty()) return;
        entryFilterInternalUpdate = true;
        try {
            for (int i = 0; i < entryColumnFilters.size() && i < leaves.size(); i++) {
                ComboBox<String> combo = entryColumnFilters.get(i);
                String currentValue = combo.getValue();
                final int colIndex = i;

                List<String> values = baseEntries.stream()
                    .filter(e -> e != null && (q.isEmpty() || buildSearchText(e).toLowerCase(Locale.ROOT).contains(q)))
                    .filter(e -> rowMatchesEntryFiltersExcluding(e, leaves, clearOption, colIndex))
                    .map(e -> {
                        Object v = leaves.get(colIndex).getCellObservableValue(e) != null ? leaves.get(colIndex).getCellObservableValue(e).getValue() : null;
                        return v == null ? "" : v.toString();
                    })
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();

                ObservableList<String> items = FXCollections.observableArrayList();
                items.add(clearOption);
                items.addAll(values);
                combo.setItems(items);

                if (currentValue != null && items.contains(currentValue)) combo.setValue(currentValue);
                else combo.setValue(clearOption);
            }
        } finally {
            entryFilterInternalUpdate = false;
        }
    }

    private boolean rowMatchesEntryFiltersExcluding(
        LiftEntry entry,
        List<TableColumn<LiftEntry, ?>> leaves,
        String clearOption,
        int ignoredColumn
    ) {
        for (int i = 0; i < entryColumnFilters.size() && i < leaves.size(); i++) {
            if (i == ignoredColumn) continue;
            String selected = entryColumnFilters.get(i).getValue();
            if (!isActiveFilter(selected, clearOption)) continue;
            Object val = leaves.get(i).getCellObservableValue(entry) != null ? leaves.get(i).getCellObservableValue(entry).getValue() : null;
            String cellText = val != null ? val.toString() : "";
            if (!selected.equals(cellText)) return false;
        }
        return true;
    }

    /* ────────────────── DICTIONARY MANAGEMENT ────────────────── */

    private void setDictionary(LiftDictionary dictionary) {
        this.currentDictionary = dictionary;
        baseEntries.clear();
        if (dictionary == null) { updateCountLabel(0, 0); return; }
        ensureHeaderComplete();
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
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) populateCategorySummaryEditor(title, n);
        });
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

        List<TableColumn<T, ?>> leaves = collectLeafColumns(table);
        List<ComboBox<String>> filterBoxes = new ArrayList<>();
        String clearOption = I18n.get("filter.clear");
        AtomicBoolean internalUpdate = new AtomicBoolean(false);

        HBox filterRow = new HBox(6);
        filterRow.setPadding(new Insets(4, 6, 4, 6));
        filterRow.setStyle("-fx-background-color: #eef2f3;");

        Runnable refreshPredicate = () -> filtered.setPredicate(row ->
            rowMatchesAllFilters(row, leaves, filterBoxes, clearOption, -1)
        );

        Runnable refreshFacetChoices = () -> {
            if (internalUpdate.get()) return;
            internalUpdate.set(true);
            try {
                for (int i = 0; i < leaves.size(); i++) {
                    ComboBox<String> combo = filterBoxes.get(i);
                    String currentValue = combo.getValue();
                    final int colIndex = i;

                    List<String> values = sourceItems.stream()
                        .filter(row -> rowMatchesAllFilters(row, leaves, filterBoxes, clearOption, colIndex))
                        .map(row -> cellText(row, leaves.get(colIndex)))
                        .filter(s -> s != null && !s.isBlank())
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();

                    ObservableList<String> items = FXCollections.observableArrayList();
                    items.add(clearOption);
                    items.addAll(values);
                    combo.setItems(items);

                    if (currentValue != null && items.contains(currentValue)) combo.setValue(currentValue);
                    else combo.setValue(clearOption);
                }
            } finally {
                internalUpdate.set(false);
            }
        };

        Button clearBtn = new Button(I18n.get("filter.resetAll"));
        clearBtn.setOnAction(e -> {
            internalUpdate.set(true);
            try {
                filterBoxes.forEach(cb -> cb.setValue(clearOption));
            } finally {
                internalUpdate.set(false);
            }
            refreshPredicate.run();
            refreshFacetChoices.run();
            table.getSelectionModel().clearSelection();
        });

        HBox header = new HBox();
        header.setPadding(new Insets(0, 6, 4, 6));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(spacer, clearBtn);

        for (TableColumn<T, ?> column : leaves) {
            ComboBox<String> cb = new ComboBox<>();
            cb.setPromptText(I18n.get("filter.prompt"));
            cb.setEditable(false);
            cb.setPrefWidth(column.getPrefWidth());
            cb.setMaxWidth(Double.MAX_VALUE);
            cb.setStyle("-fx-font-size: 11px; -fx-padding: 0 2 0 2;");
            cb.setCellFactory(list -> new ListCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setDisable(false);
                        setStyle("");
                        return;
                    }
                    setText(item);
                    boolean isClearItem = clearOption.equals(item);
                    boolean activeFilter = isActiveFilter(cb.getValue(), clearOption);
                    boolean disableClear = isClearItem && !activeFilter;
                    setDisable(disableClear);
                    setStyle(disableClear ? "-fx-opacity: 0.45;" : "");
                }
            });
            cb.setButtonCell(new ListCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item);
                }
            });
            filterBoxes.add(cb);
            filterRow.getChildren().add(cb);

            cb.valueProperty().addListener((obs, o, n) -> {
                if (clearOption.equals(n) && !isActiveFilter(o, clearOption)) {
                    cb.setValue(o);
                    return;
                }
                if (internalUpdate.get()) return;
                refreshPredicate.run();
                refreshFacetChoices.run();
            });
        }

        internalUpdate.set(true);
        try {
            filterBoxes.forEach(cb -> cb.setValue(clearOption));
        } finally {
            internalUpdate.set(false);
        }
        refreshPredicate.run();
        refreshFacetChoices.run();

        VBox wrapper = new VBox(header, filterRow, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        return wrapper;
    }

    private static <T> boolean rowMatchesAllFilters(
        T row,
        List<TableColumn<T, ?>> leaves,
        List<ComboBox<String>> filterBoxes,
        String clearOption,
        int ignoredColumn
    ) {
        for (int i = 0; i < filterBoxes.size(); i++) {
            if (i == ignoredColumn) continue;
            String selected = filterBoxes.get(i).getValue();
            if (selected == null || selected.isBlank() || clearOption.equals(selected)) continue;
            if (!selected.equals(cellText(row, leaves.get(i)))) return false;
        }
        return true;
    }

    private static boolean isActiveFilter(String selected, String clearOption) {
        return selected != null && !selected.isBlank() && !clearOption.equals(selected);
    }

    private static <T> String cellText(T row, TableColumn<T, ?> col) {
        Object cellVal = col.getCellObservableValue(row) != null ? col.getCellObservableValue(row).getValue() : null;
        return cellVal != null ? cellVal.toString() : "";
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

    /* ════════════════════ HEADER CONFIGURATION VIEWS ════════════════════ */

    private void showHeaderRangeView(String rangeId) {
        if (currentDictionary == null) { tableContainer.getChildren().setAll(new Label(I18n.get("cfg.noHeader"))); return; }
        LiftHeader header = currentDictionary.getLiftDictionaryComponents().getHeader();
        LiftFactory factory = getFactory(currentDictionary);
        if (header == null || factory == null) { tableContainer.getChildren().setAll(new Label(I18n.get("cfg.noHeader"))); return; }

        LiftHeaderRange range = header.getRanges().stream()
            .filter(r -> rangeId.equals(r.getId())).findFirst()
            .orElseGet(() -> factory.createRange(rangeId, header));

        TableView<LiftHeaderRangeElement> table = new TableView<>();
        table.setEditable(true);
        table.getColumns().addAll(
            col(I18n.get("cfg.rangeId"), LiftHeaderRangeElement::getId),
            col(I18n.get("cfg.label"), re -> re.getLabel().getForms().stream().findFirst().map(Form::toPlainText).orElse("")),
            col(I18n.get("cfg.abbrev"), re -> re.getAbbrev().getForms().stream().findFirst().map(Form::toPlainText).orElse("")),
            col(I18n.get("cfg.description"), re -> re.getDescription().getForms().stream().findFirst().map(Form::toPlainText).orElse("")),
            col(I18n.get("cfg.usageCount"), re -> String.valueOf(countRangeElementUsage(rangeId, re.getId())))
        );
        table.getItems().addAll(range.getRangeElements());

        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) populateRangeElementEditor(range, n);
        });

        TextField newIdField = new TextField();
        newIdField.setPromptText(I18n.get("cfg.rangeId"));
        Button addBtn = new Button(I18n.get("cfg.addElement"));
        addBtn.setOnAction(e -> {
            String id = newIdField.getText().trim();
            if (!id.isEmpty() && range.getRangeElements().stream().noneMatch(re -> re.getId().equals(id))) {
                LiftHeaderRangeElement newElem = factory.createRangeElement(id, range);
                List<String> metaLangs = getMetaLanguages();
                if (!metaLangs.isEmpty()) {
                    newElem.getDescription().add(new Form(metaLangs.get(0), I18n.get("cfg.autoAdded")));
                }
                table.getItems().add(newElem);
                newIdField.clear();
            }
        });

        Button deleteBtn = new Button(I18n.get("btn.delete"));
        deleteBtn.setOnAction(e -> {
            LiftHeaderRangeElement sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            long usage = countRangeElementUsage(rangeId, sel.getId());
            if (usage > 0) {
                showError(I18n.get("btn.delete"), I18n.get("cfg.deleteNotAllowed", usage));
            } else {
                range.getRangeElements().remove(sel);
                table.getItems().remove(sel);
            }
        });

        Button renameBtn = new Button(I18n.get("cfg.rename"));
        renameBtn.setOnAction(e -> {
            LiftHeaderRangeElement sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            TextInputDialog dlg = new TextInputDialog(sel.getId());
            dlg.setTitle(I18n.get("cfg.rename"));
            dlg.setHeaderText(I18n.get("cfg.renamePrompt", sel.getId()));
            dlg.showAndWait().ifPresent(newName -> {
                if (!newName.isBlank()) {
                    renameRangeElementInData(rangeId, sel.getId(), newName);
                    showHeaderRangeView(rangeId);
                }
            });
        });

        HBox controls = new HBox(8, newIdField, addBtn, deleteBtn, renameBtn);
        controls.setPadding(new Insets(6, 0, 0, 0));
        HBox.setHgrow(newIdField, Priority.ALWAYS);

        VBox wrapper = new VBox(6, wrapTableWithFilters(table), controls);
        VBox.setVgrow(wrapper.getChildren().get(0), Priority.ALWAYS);
        tableContainer.getChildren().setAll(wrapper);
        updateCountLabel(table.getItems().size(), table.getItems().size());
    }

    private void showHeaderAllRangesView() {
        if (currentDictionary == null) { tableContainer.getChildren().setAll(new Label(I18n.get("cfg.noHeader"))); return; }
        LiftHeader header = currentDictionary.getLiftDictionaryComponents().getHeader();
        if (header == null) { tableContainer.getChildren().setAll(new Label(I18n.get("cfg.noHeader"))); return; }

        TableView<LiftHeaderRange> rangeTable = new TableView<>();
        rangeTable.getColumns().addAll(
            col(I18n.get("cfg.rangeId"), LiftHeaderRange::getId),
            col(I18n.get("cfg.usageCount"), r -> String.valueOf(r.getRangeElements().size())),
            col(I18n.get("cfg.description"), r -> r.getDescription().getForms().stream().findFirst().map(Form::toPlainText).orElse(""))
        );
        rangeTable.getItems().addAll(header.getRanges());

        rangeTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) {
                editorContainer.getChildren().clear();
                editEntryTitle.setText(I18n.get("cfg.rangeElements", n.getId()));
                editEntryCode.setText(n.getId());

                VBox elemBox = new VBox(4);
                for (LiftHeaderRangeElement re : n.getRangeElements()) {
                    String label = re.getId() + " – " + re.getDescription().getForms().stream().findFirst().map(Form::toPlainText).orElse("");
                    elemBox.getChildren().add(new Label(label));
                }
                Hyperlink editLink = new Hyperlink(I18n.get("cfg.rangeElements", n.getId()));
                editLink.setOnAction(e -> showHeaderRangeView(n.getId()));
                editorContainer.getChildren().addAll(editLink, elemBox);
            }
        });

        LiftFactory factory = getFactory(currentDictionary);
        TextField newRangeField = new TextField();
        newRangeField.setPromptText(I18n.get("cfg.rangeId"));
        Button addBtn = new Button(I18n.get("cfg.addElement"));
        addBtn.setOnAction(e -> {
            String id = newRangeField.getText().trim();
            if (!id.isEmpty() && factory != null && header.getRanges().stream().noneMatch(r -> r.getId().equals(id))) {
                LiftHeaderRange newRange = factory.createRange(id, header);
                rangeTable.getItems().add(newRange);
                newRangeField.clear();
            }
        });

        HBox controls = new HBox(8, newRangeField, addBtn);
        controls.setPadding(new Insets(6, 0, 0, 0));
        HBox.setHgrow(newRangeField, Priority.ALWAYS);

        VBox wrapper = new VBox(6, wrapTableWithFilters(rangeTable), controls);
        VBox.setVgrow(wrapper.getChildren().get(0), Priority.ALWAYS);
        tableContainer.getChildren().setAll(wrapper);
        updateCountLabel(rangeTable.getItems().size(), rangeTable.getItems().size());
    }

    private void showHeaderFieldDefsView() {
        if (currentDictionary == null) { tableContainer.getChildren().setAll(new Label(I18n.get("cfg.noHeader"))); return; }
        LiftHeader header = currentDictionary.getLiftDictionaryComponents().getHeader();
        LiftFactory factory = getFactory(currentDictionary);
        if (header == null || factory == null) { tableContainer.getChildren().setAll(new Label(I18n.get("cfg.noHeader"))); return; }

        TableView<LiftHeaderFieldDefinition> table = new TableView<>();
        table.getColumns().addAll(
            col(I18n.get("cfg.fieldDefName"), LiftHeaderFieldDefinition::getName),
            col(I18n.get("cfg.kind"), fd -> fieldDefKindLabel(fd)),
            col(I18n.get("cfg.fieldDefType"), fd -> fd.getType().orElse("")),
            col(I18n.get("cfg.targets"), fd -> fd.getFClass().orElse("")),
            col(I18n.get("cfg.description"), fd -> fd.getDescription().getForms().stream().findFirst().map(Form::toPlainText).orElse("")),
            col(I18n.get("cfg.usageCount"), fd -> String.valueOf(countFieldOrTraitUsage(fd)))
        );
        table.getItems().addAll(header.getFields());

        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) populateFieldDefEditor(n);
        });

        Button addBtn = new Button(I18n.get("cfg.newFieldOrTrait"));
        addBtn.setOnAction(e -> showNewFieldDefDialog(header, factory, table));

        Button deleteBtn = new Button(I18n.get("btn.delete"));
        deleteBtn.setOnAction(e -> {
            LiftHeaderFieldDefinition sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            long usage = countFieldOrTraitUsage(sel);
            if (usage > 0) {
                showError(I18n.get("btn.delete"), I18n.get("cfg.deleteNotAllowed", usage));
            } else {
                header.getFields().remove(sel);
                table.getItems().remove(sel);
            }
        });

        HBox controls = new HBox(8, addBtn, deleteBtn);
        controls.setPadding(new Insets(6, 0, 0, 0));

        VBox wrapper = new VBox(6, wrapTableWithFilters(table), controls);
        VBox.setVgrow(wrapper.getChildren().get(0), Priority.ALWAYS);
        tableContainer.getChildren().setAll(wrapper);
        updateCountLabel(table.getItems().size(), table.getItems().size());
    }

    private void showNewFieldDefDialog(LiftHeader header, LiftFactory factory, TableView<LiftHeaderFieldDefinition> table) {
        Dialog<LiftHeaderFieldDefinition> dlg = new Dialog<>();
        dlg.setTitle(I18n.get("cfg.newFieldOrTrait"));
        dlg.setHeaderText(I18n.get("cfg.chooseKind"));
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.setResizable(true);
        dlg.getDialogPane().setPrefWidth(450);

        TextField nameField = new TextField();
        nameField.setPromptText(I18n.get("cfg.fieldDefName"));

        ToggleGroup kindGroup = new ToggleGroup();
        RadioButton rbField = new RadioButton(I18n.get("cfg.kindField"));
        rbField.setToggleGroup(kindGroup);
        rbField.setSelected(true);
        RadioButton rbTrait = new RadioButton(I18n.get("cfg.kindTrait"));
        rbTrait.setToggleGroup(kindGroup);

        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.setEditable(true);
        typeCombo.setPromptText(I18n.get("cfg.fieldDefType"));
        Runnable updateTypes = () -> {
            typeCombo.getItems().clear();
            if (rbField.isSelected()) {
                typeCombo.getItems().addAll("multistring", "multitext");
                typeCombo.setValue("multitext");
            } else {
                typeCombo.getItems().addAll("datetime", "integer", "option", "option-collection", "option-sequence");
                typeCombo.setValue("option");
            }
        };
        updateTypes.run();
        rbField.setOnAction(e -> updateTypes.run());
        rbTrait.setOnAction(e -> updateTypes.run());

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.add(new Label(I18n.get("cfg.fieldDefName")), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label(I18n.get("cfg.kind")), 0, 1);
        grid.add(new HBox(12, rbField, rbTrait), 1, 1);
        grid.add(new Label(I18n.get("cfg.fieldDefType")), 0, 2);
        grid.add(typeCombo, 1, 2);
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(typeCombo, Priority.ALWAYS);
        dlg.getDialogPane().setContent(grid);

        dlg.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            String name = nameField.getText().trim();
            if (name.isEmpty() || header.getFields().stream().anyMatch(fd -> fd.getName().equals(name))) return null;
            LiftHeaderFieldDefinition fd = factory.createFieldDefinition(name, header);
            String typeVal = typeCombo.getValue();
            if (typeVal != null && !typeVal.isBlank()) fd.setType(Optional.of(typeVal));
            List<String> metaLangs = getMetaLanguages();
            if (!metaLangs.isEmpty()) fd.getDescription().add(new Form(metaLangs.get(0), I18n.get("cfg.autoAdded")));
            return fd;
        });

        dlg.showAndWait().ifPresent(fd -> table.getItems().add(fd));
    }

    private void populateRangeElementEditor(LiftHeaderRange range, LiftHeaderRangeElement elem) {
        editEntryTitle.setText(elem.getId());
        editEntryCode.setText(range.getId());
        editorContainer.getChildren().clear();
        List<String> metaLangs = getMetaLanguages();

        addSection(editorContainer, I18n.get("cfg.label"), () -> {
            MultiTextEditor m = new MultiTextEditor(); m.setAvailableLanguages(metaLangs); m.setMultiText(elem.getLabel()); return m;
        }, true);
        addSection(editorContainer, I18n.get("cfg.abbrev"), () -> {
            MultiTextEditor m = new MultiTextEditor(); m.setAvailableLanguages(metaLangs); m.setMultiText(elem.getAbbrev()); return m;
        }, true);
        addSection(editorContainer, I18n.get("cfg.description"), () -> {
            MultiTextEditor m = new MultiTextEditor(); m.setAvailableLanguages(metaLangs); m.setMultiText(elem.getDescription()); return m;
        }, true);
    }

    private void populateFieldDefEditor(LiftHeaderFieldDefinition fd) {
        editEntryTitle.setText(fd.getName());
        String kindLabel = fieldDefKindLabel(fd);
        editEntryCode.setText(kindLabel + " – " + fd.getType().orElse(""));
        editorContainer.getChildren().clear();
        List<String> metaLangs = getMetaLanguages();

        addSection(editorContainer, I18n.get("editor.identity"), () -> {
            GridPane g = new GridPane(); g.setHgap(8); g.setVgap(6);
            addReadOnlyRow(g, 0, I18n.get("cfg.fieldDefName"), fd.getName());
            addReadOnlyRow(g, 1, I18n.get("cfg.kind"), kindLabel);

            g.add(new Label(I18n.get("cfg.fieldDefType")), 0, 2);
            ComboBox<String> typeCombo = new ComboBox<>();
            typeCombo.setEditable(true);
            typeCombo.getItems().addAll("multistring", "multitext", "datetime", "integer", "option", "option-collection", "option-sequence");
            typeCombo.setValue(fd.getType().orElse(""));
            typeCombo.valueProperty().addListener((obs, o, n) -> {
                fd.setType(n == null || n.isBlank() ? Optional.empty() : Optional.of(n));
            });
            GridPane.setHgrow(typeCombo, Priority.ALWAYS);
            g.add(typeCombo, 1, 2);

            g.add(new Label(I18n.get("cfg.targets")), 0, 3);
            TextField classTf = new TextField(fd.getFClass().orElse(""));
            classTf.setPromptText("entry sense variant ...");
            classTf.textProperty().addListener((obs, o, n) -> fd.setFClass(n.isBlank() ? Optional.empty() : Optional.of(n)));
            GridPane.setHgrow(classTf, Priority.ALWAYS);
            g.add(classTf, 1, 3);

            g.add(new Label(I18n.get("cfg.optionRange")), 0, 4);
            TextField orTf = new TextField(fd.getOptionRange().orElse(""));
            orTf.setPromptText("range id...");
            orTf.textProperty().addListener((obs, o, n) -> fd.setOptionRange(n.isBlank() ? Optional.empty() : Optional.of(n)));
            GridPane.setHgrow(orTf, Priority.ALWAYS);
            g.add(orTf, 1, 4);
            return g;
        }, true);

        addSection(editorContainer, I18n.get("cfg.label"), () -> {
            MultiTextEditor m = new MultiTextEditor(); m.setAvailableLanguages(metaLangs); m.setMultiText(fd.getLabel()); return m;
        }, true);
        addSection(editorContainer, I18n.get("cfg.description"), () -> {
            MultiTextEditor m = new MultiTextEditor(); m.setAvailableLanguages(metaLangs); m.setMultiText(fd.getDescription()); return m;
        }, true);
    }

    private long countFieldOrTraitUsage(LiftHeaderFieldDefinition fd) {
        if (currentDictionary == null) return 0;
        var comps = currentDictionary.getLiftDictionaryComponents();
        long fieldCount = comps.getAllFields().stream().filter(f -> fd.getName().equals(f.getName())).count();
        long traitCount = comps.getAllTraits().stream().filter(t -> fd.getName().equals(t.getName())).count();
        return fieldCount + traitCount;
    }

    /* ─── Header usage counting & renaming ─── */

    private long countRangeElementUsage(String rangeId, String elementId) {
        if (currentDictionary == null) return 0;
        var comps = currentDictionary.getLiftDictionaryComponents();
        if ("note-type".equals(rangeId))
            return comps.getAllNotes().stream().filter(n -> elementId.equals(n.getType().orElse(null))).count();
        if ("translation-type".equals(rangeId))
            return comps.getAllExamples().stream().filter(ex -> ex.getTranslations().containsKey(elementId)).count();
        if ("grammatical-info".equals(rangeId))
            return comps.getAllSenses().stream().filter(s -> s.getGrammaticalInfo().map(g -> elementId.equals(g.getValue())).orElse(false)).count();
        return comps.getAllTraits().stream().filter(t -> rangeId.equals(t.getName()) && elementId.equals(t.getValue())).count();
    }

    private long countFieldUsage(String fieldName) {
        if (currentDictionary == null) return 0;
        return currentDictionary.getLiftDictionaryComponents().getAllFields().stream()
            .filter(f -> fieldName.equals(f.getName())).count();
    }

    private void renameRangeElementInData(String rangeId, String oldId, String newId) {
        if (currentDictionary == null) return;
        var comps = currentDictionary.getLiftDictionaryComponents();
        LiftHeader header = comps.getHeader();
        if (header != null) {
            header.getRanges().stream().filter(r -> rangeId.equals(r.getId())).findFirst().ifPresent(r -> {
                r.getRangeElements().stream().filter(re -> oldId.equals(re.getId())).findFirst().ifPresent(re -> {
                    try {
                        var idField = LiftHeaderRangeElement.class.getDeclaredField("id");
                        idField.setAccessible(true);
                        idField.set(re, newId);
                    } catch (Exception ignored) {}
                });
            });
        }
        if ("note-type".equals(rangeId)) {
            comps.getAllNotes().stream().filter(n -> oldId.equals(n.getType().orElse(null))).forEach(n -> n.setType(newId));
        } else if ("grammatical-info".equals(rangeId)) {
            comps.getAllSenses().stream()
                .filter(s -> s.getGrammaticalInfo().map(g -> oldId.equals(g.getValue())).orElse(false))
                .forEach(s -> s.setGrammaticalInfo(newId));
        } else {
            comps.getAllTraits().stream().filter(t -> rangeId.equals(t.getName()) && oldId.equals(t.getValue()))
                .forEach(t -> t.setValue(newId));
        }
    }

    /* ════════════════════ AUTO-POPULATE HEADER ════════════════════ */

    private void ensureHeaderComplete() {
        if (currentDictionary == null) return;
        LiftFactory factory = getFactory(currentDictionary);
        if (factory == null) return;
        var comps = currentDictionary.getLiftDictionaryComponents();
        LiftHeader header = comps.getHeader();
        if (header == null) header = factory.createHeader();

        String autoDesc = I18n.get("cfg.autoAdded");
        List<String> metaLangs = getMetaLanguages();
        String descLang = metaLangs.isEmpty() ? "en" : metaLangs.get(0);

        ensureRange(factory, header, "note-type",
            comps.getAllNotes().stream().map(n -> n.getType().orElse(null)).filter(Objects::nonNull).collect(Collectors.toSet()),
            descLang, autoDesc);

        ensureRange(factory, header, "translation-type",
            comps.getAllExamples().stream().flatMap(ex -> ex.getTranslations().keySet().stream()).collect(Collectors.toSet()),
            descLang, autoDesc);

        ensureRange(factory, header, "grammatical-info",
            comps.getAllSenses().stream().map(s -> s.getGrammaticalInfo().map(GrammaticalInfo::getValue).orElse(null)).filter(Objects::nonNull).collect(Collectors.toSet()),
            descLang, autoDesc);

        Map<String, Set<String>> traitsByName = new HashMap<>();
        for (LiftTrait t : comps.getAllTraits()) {
            traitsByName.computeIfAbsent(t.getName(), k -> new TreeSet<>()).add(t.getValue());
        }
        for (var entry : traitsByName.entrySet()) {
            ensureRange(factory, header, entry.getKey(), entry.getValue(), descLang, autoDesc);
        }

        Set<String> definedFieldDefs = header.getFields().stream().map(LiftHeaderFieldDefinition::getName).collect(Collectors.toSet());

        Set<String> fieldNames = comps.getAllFields().stream().map(LiftField::getName).collect(Collectors.toSet());
        for (String fn : fieldNames) {
            if (!definedFieldDefs.contains(fn)) {
                LiftHeaderFieldDefinition fd = factory.createFieldDefinition(fn, header);
                fd.setType(Optional.of("multitext"));
                fd.getDescription().add(new Form(descLang, autoDesc));
                definedFieldDefs.add(fn);
            }
        }

        Set<String> traitNames = comps.getAllTraits().stream().map(LiftTrait::getName).collect(Collectors.toSet());
        for (String tn : traitNames) {
            if (!definedFieldDefs.contains(tn)) {
                LiftHeaderFieldDefinition fd = factory.createFieldDefinition(tn, header);
                fd.setType(Optional.of("option"));
                fd.getDescription().add(new Form(descLang, autoDesc));
            }
        }
    }

    private static String fieldDefKindLabel(LiftHeaderFieldDefinition fd) {
        if (fd == null) return "";
        if (fd.isFieldDefinition()) return I18n.get("cfg.kindField");
        if (fd.isTraitDefinition()) return I18n.get("cfg.kindTrait");
        return I18n.get("cfg.kindUnknown");
    }

    private static void ensureRange(LiftFactory factory, LiftHeader header, String rangeId, Set<String> values, String descLang, String autoDesc) {
        LiftHeaderRange range = header.getRanges().stream()
            .filter(r -> rangeId.equals(r.getId())).findFirst()
            .orElseGet(() -> factory.createRange(rangeId, header));
        Set<String> existing = range.getRangeElements().stream().map(LiftHeaderRangeElement::getId).collect(Collectors.toSet());
        for (String val : values) {
            if (!existing.contains(val)) {
                LiftHeaderRangeElement newElem = factory.createRangeElement(val, range);
                newElem.getDescription().add(new Form(descLang, autoDesc));
            }
        }
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
        TextField tf = new TextField(value);
        styleReadOnlyTextField(tf);
        GridPane.setHgrow(tf, Priority.ALWAYS);
        grid.add(tf, 1, row);
    }

    private static void styleReadOnlyTextField(TextField tf) {
        tf.setEditable(false);
        tf.setFocusTraversable(false);
        tf.getStyleClass().add("read-only-meta-field");
    }

    private static void styleReadOnlyDatePicker(DatePicker dp) {
        dp.setEditable(false);
        dp.setDisable(true);
        dp.setFocusTraversable(false);
        dp.getStyleClass().add("read-only-meta-picker");
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

    private static String describeParentType(Object parent) {
        if (parent == null) return "";
        if (parent instanceof LiftEntry) return I18n.get("nav.entries");
        if (parent instanceof LiftSense) return I18n.get("nav.senses");
        if (parent instanceof GrammaticalInfo) return I18n.get("nav.gramInfo");
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

    /* ─── Known dropdown values from header ranges ─── */

    private List<String> getKnownTraitNames() {
        if (currentDictionary == null) return List.of();
        LiftHeader h = currentDictionary.getLiftDictionaryComponents().getHeader();
        if (h != null) {
            Set<String> standardRanges = Set.of("note-type", "translation-type", "grammatical-info");
            return h.getRanges().stream().map(LiftHeaderRange::getId)
                .filter(id -> !standardRanges.contains(id)).sorted().toList();
        }
        return currentDictionary.getTraitName().stream().sorted().toList();
    }
    private Map<String, Set<String>> getKnownTraitValues() {
        if (currentDictionary == null) return Map.of();
        Map<String, Set<String>> result = new HashMap<>();
        LiftHeader h = currentDictionary.getLiftDictionaryComponents().getHeader();
        if (h != null) {
            Set<String> standardRanges = Set.of("note-type", "translation-type", "grammatical-info");
            for (LiftHeaderRange r : h.getRanges()) {
                if (standardRanges.contains(r.getId())) continue;
                Set<String> vals = r.getRangeElements().stream().map(LiftHeaderRangeElement::getId).collect(Collectors.toCollection(TreeSet::new));
                result.put(r.getId(), vals);
            }
        }
        if (result.isEmpty()) {
            for (LiftTrait t : currentDictionary.getLiftDictionaryComponents().getAllTraits()) {
                result.computeIfAbsent(t.getName(), k -> new TreeSet<>()).add(t.getValue());
            }
        }
        return result;
    }
    private List<String> getKnownAnnotationNames() {
        return currentDictionary == null ? List.of() : currentDictionary.getLiftDictionaryComponents().getAllAnnotations().stream()
            .map(LiftAnnotation::getName).filter(Objects::nonNull).distinct().sorted().toList();
    }
    private List<String> getKnownFieldTypes() {
        if (currentDictionary == null) return List.of();
        LiftHeader h = currentDictionary.getLiftDictionaryComponents().getHeader();
        if (h != null && !h.getFields().isEmpty()) {
            return h.getFields().stream().map(LiftHeaderFieldDefinition::getName).sorted().toList();
        }
        return currentDictionary.getFieldType().stream().sorted().toList();
    }
    private List<String> getKnownNoteTypes() {
        return getHeaderRangeValues("note-type");
    }
    private List<String> getKnownGramInfoValues() {
        return getHeaderRangeValues("grammatical-info");
    }
    private List<String> getHeaderRangeValues(String rangeId) {
        if (currentDictionary == null) return List.of();
        LiftHeader h = currentDictionary.getLiftDictionaryComponents().getHeader();
        if (h != null) {
            return h.getRanges().stream().filter(r -> rangeId.equals(r.getId())).findFirst()
                .map(r -> r.getRangeElements().stream().map(LiftHeaderRangeElement::getId).sorted().toList())
                .orElse(List.of());
        }
        return List.of();
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
