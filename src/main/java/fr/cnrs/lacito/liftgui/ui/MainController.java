/**
 
* @author Inès GBADAMASSI
* @author Maryse GOEH-AKUE
* @author Ermeline BRESSON
* @author Ayman JARI
* @author Erij MAZOUZ

**/
package fr.cnrs.lacito.liftgui.ui;

import fr.cnrs.lacito.liftgui.core.DictionaryService;
import fr.cnrs.lacito.liftgui.core.LiftOpenException;
import fr.cnrs.lacito.liftgui.ui.controls.*;
import fr.cnrs.lacito.liftgui.undo.*;
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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.control.TextInputDialog;
import javafx.util.Pair;

import java.io.*;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
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
    private static final Logger LOGGER = Logger.getLogger(MainController.class.getName());
    private static final String FILTER_MODE_TEXT = "text";
    private static final int MAX_RECENT_FILES = 5;
    private static final String PREF_RECENT_PREFIX = "recent.file.";
    private static final Preferences PREFS = Preferences.userNodeForPackage(MainController.class);
    private void saveRecentFile(File f) {
        // Décale les fichiers récents et ajoute le nouveau en premier
        List<String> recents = loadRecentFiles();
        recents.remove(f.getAbsolutePath());
        recents.add(0, f.getAbsolutePath());
        if (recents.size() > MAX_RECENT_FILES) recents = recents.subList(0, MAX_RECENT_FILES);
        for (int i = 0; i < MAX_RECENT_FILES; i++) {
            if (i < recents.size()) PREFS.put(PREF_RECENT_PREFIX + i, recents.get(i));
            else PREFS.remove(PREF_RECENT_PREFIX + i);
        }
        refreshRecentMenu();
    }

    private List<String> loadRecentFiles() {
        List<String> recents = new ArrayList<>();
        for (int i = 0; i < MAX_RECENT_FILES; i++) {
            String path = PREFS.get(PREF_RECENT_PREFIX + i, null);
            if (path != null) recents.add(path);
        }
        return recents;
    }

    private void refreshRecentMenu() {
        if (recentMenu == null) return;
        recentMenu.getItems().clear();
        List<String> recents = loadRecentFiles();
        if (recents.isEmpty()) {
            MenuItem empty = new MenuItem(I18n.get("menu.file.noRecent"));
            empty.setDisable(true);
            recentMenu.getItems().add(empty);
            return;
        }
        for (String path : recents) {
            File f = new File(path);
            MenuItem item = new MenuItem(f.getName() + "  (" + f.getParent() + ")");
            item.setOnAction(e -> {
                try { setDictionary(dictionaryService.loadFromFile(f)); switchView(NAV_ENTRIES); }
                catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Ouverture du fichier LIFT (fichier récent)", ex);
                    showError(I18n.get("error.open"), I18n.formatErrorMessage("error.open.detail", ex));
                }
            });
            item.setDisable(!f.exists());
            recentMenu.getItems().add(item);
        }
    }
    /* ─── Nav view identifiers (i18n keys) ─── */
    private static final String NAV_ENTRIES     = "nav.entries";
    private static final String NAV_SENSES      = "nav.senses";
    private static final String NAV_EXAMPLES    = "nav.examples";
    private static final String NAV_NOTES       = "nav.notes";
    private static final String NAV_VARIANTS    = "nav.variants";
    private static final String NAV_ETYMOLOGIES = "nav.etymologies";
    private static final String NAV_RELATIONS   = "nav.relations";
    private static final String NAV_OBJ_LANGS   = "nav.objectLangs";
    private static final String NAV_META_LANGS  = "nav.metaLangs";
    private static final String NAV_TRAITS      = "nav.traits";
    private static final String NAV_ANNOTATIONS = "nav.annotations";
    private static final String NAV_FIELDS      = "nav.fields";
    private static final String NAV_GRAM_INFO   = "nav.gramInfo";
    private static final String NAV_TRANS_TYPES = "nav.transTypes";
    private static final String NAV_NOTE_TYPES    = "nav.noteTypes";
    private static final String NAV_RELATION_TYPES = "nav.relationTypes";
    private static final String NAV_QUICK_ENTRY  = "nav.quickEntry";

    /* ─── Header configuration nav keys ─── */
    private static final String NAV_CFG_DESC        = "nav.cfgDesc";
    private static final String NAV_CFG_FIELD_DEFS  = "nav.cfgFieldDefs";
    private static final String NAV_CFG_MANAGE_LANGS = "nav.cfgManageLangs";
    private static final String NAV_CFG_MANAGE_NOTE_TYPES = "nav.cfgManageNoteTypes";
    private static final String NAV_CFG_MANAGE_TRANS_TYPES = "nav.cfgManageTransTypes";
    private static final String NAV_CFG_MANAGE_ANNOTATION_TYPES = "nav.cfgManageAnnotationTypes";
    private static final String NAV_CFG_MANAGE_RELATION_TYPES = "nav.cfgManageRelationTypes";
    private static final String NAV_CFG_RANGE_PREFIX = "cfg.range.";  // + rangeId for dynamic ranges
    private TreeItem<String> headerCfgNode;  // kept for dynamic rebuild

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
    @FXML private MenuBar menuBar;
    @FXML private SplitPane mainSplit;
    @FXML private Button addButton;
    @FXML private HBox modifyButtonRow;
    @FXML private Button modifyButton;
    @FXML private Button undoButton;
    @FXML private Button redoButton;
    @FXML private MenuItem undoMenuItem;
    @FXML private MenuItem redoMenuItem;

    /* ─── Undo/Redo ─── */
    private final UndoManager undoManager = new UndoManager();

    /* ─── Entry table (main view) ─── */
    private final TableView<LiftEntry> entryTable = new TableView<>();
    private final ObservableList<LiftEntry> baseEntries = FXCollections.observableArrayList();
    private final FilteredList<LiftEntry> filteredEntries = new FilteredList<>(baseEntries, e -> true);

    /* ─── Generic object tables ─── */
    private final TableView<LiftSense> senseTable = new TableView<>();
    private final TableView<LiftExample> exampleTable = new TableView<>();
    private final TableView<LiftVariant> variantTable = new TableView<>();
    private final TableView<LiftRelation> relationTable = new TableView<>();
    private final TableView<TraitRow> traitTable = new TableView<>();

    private record TraitRow(String parentType, String name, String value, long frequency) {}
    private final TableView<LiftAnnotation> annotationTable = new TableView<>();
    private final TableView<LiftField> fieldTable = new TableView<>();
    private final TableView<MultiTextField> langFieldTable = new TableView<>();
    private final TableView<QuickEntryRow> quickEntryTable = new TableView<>();

    /* ─── Wrapper for language field view ─── */
    public record MultiTextField(String parentType, String parentId, String lang, String text, Object parentObject, MultiText multiText) {
        public MultiTextField(String parentType, String parentId, String lang, String text) {
            this(parentType, parentId, lang, text, null, null);
        }
    }
    public static class QuickEntryRow {
        private final Map<String, javafx.beans.property.StringProperty> forms = new HashMap<>();
        private final Map<String, javafx.beans.property.StringProperty> glosses = new HashMap<>();
        private final javafx.beans.property.StringProperty gramInfo = new javafx.beans.property.SimpleStringProperty("");
        /** True once this row has been auto-committed as a new entry, to avoid double-creation. */
        private final javafx.beans.property.BooleanProperty created = new javafx.beans.property.SimpleBooleanProperty(false);
        public javafx.beans.property.StringProperty formProperty(String lang) { return forms.computeIfAbsent(lang, k -> new javafx.beans.property.SimpleStringProperty("")); }
        public javafx.beans.property.StringProperty glossProperty(String lang) { return glosses.computeIfAbsent(lang, k -> new javafx.beans.property.SimpleStringProperty("")); }
        public javafx.beans.property.StringProperty gramInfoProperty() { return gramInfo; }
        public javafx.beans.property.BooleanProperty createdProperty() { return created; }
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
        setupMenuHover();
        switchView(NAV_ENTRIES);
        refreshRecentMenu();
        setupUndoRedo();
    }

    private void setupUndoRedo() {
        if (undoButton != null) {
            undoButton.setGraphic(Icons.undoIcon());
            undoButton.setTooltip(new Tooltip(I18n.get("menu.edit.undo") + " (Ctrl+Z)"));
        }
        if (redoButton != null) {
            redoButton.setGraphic(Icons.redoIcon());
            redoButton.setTooltip(new Tooltip(I18n.get("menu.edit.redo") + " (Ctrl+Y)"));
        }
        if (undoButton != null) undoButton.disableProperty().bind(undoManager.canUndoProperty().not());
        if (redoButton != null) redoButton.disableProperty().bind(undoManager.canRedoProperty().not());
        if (undoMenuItem != null) undoMenuItem.disableProperty().bind(undoManager.canUndoProperty().not());
        if (redoMenuItem != null) redoMenuItem.disableProperty().bind(undoManager.canRedoProperty().not());
        if (menuBar != null) {
            menuBar.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) installUndoRedoAccelerators(newScene);
            });
            if (menuBar.getScene() != null) installUndoRedoAccelerators(menuBar.getScene());
        }
    }

    private void installUndoRedoAccelerators(javafx.scene.Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN), this::onUndo);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN), this::onRedo);
    }

    private void setupMenuHover() {
        if (menuBar == null) return;
        // Nodes are not yet in the scene graph at initialize() time; install after layout.
        menuBar.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            Platform.runLater(() -> {
                for (Menu menu : menuBar.getMenus()) {
                    javafx.scene.Node btn = menu.getStyleableNode();
                    if (btn == null) continue;
                    btn.setOnMouseEntered(e -> {
                        boolean anyOpen = menuBar.getMenus().stream().anyMatch(Menu::isShowing);
                        if (anyOpen) {
                            menuBar.getMenus().forEach(Menu::hide);
                            menu.show();
                        }
                    });
                }
            });
        });
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
            navItem(NAV_VARIANTS), navItem(NAV_ETYMOLOGIES),
            navItem(NAV_RELATIONS)
        );

        TreeItem<String> langs = new TreeItem<>(I18n.get("nav.languages"));
        langs.setExpanded(true);
        langs.getChildren().addAll(navItem(NAV_OBJ_LANGS), navItem(NAV_META_LANGS));

        TreeItem<String> cats = new TreeItem<>(I18n.get("nav.categories"));
        cats.setExpanded(true);
        cats.getChildren().addAll(
            navItem(NAV_GRAM_INFO),
            navItem(NAV_TRAITS), navItem(NAV_ANNOTATIONS),
            navItem(NAV_TRANS_TYPES), navItem(NAV_NOTE_TYPES),
            navItem(NAV_RELATION_TYPES),
            navItem(NAV_FIELDS)
        );

        headerCfgNode = new TreeItem<>(I18n.get("nav.headerConfig"));
        headerCfgNode.setExpanded(true);
        rebuildHeaderCfgChildren();

        TreeItem<String> quick = navItem(NAV_QUICK_ENTRY);

        root.getChildren().addAll(objects, langs, cats, headerCfgNode, quick);
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

    /** Dynamic nav item with explicit label (for ranges whose id is the label). */
    private TreeItem<String> navItemDynamic(String key, String label) {
        TreeItem<String> item = new TreeItem<>(label);
        navKeyMap.put(item, key);
        return item;
    }

    /**
     * (Re-)builds the children of the "Configuration du dictionnaire" nav node.
     * Structure: Dictionary description, Field and traits definitions (separated),
     * then a "Ranges" section grouping all dynamic ranges.
     */
    private void rebuildHeaderCfgChildren() {
        if (headerCfgNode == null) return;
        // Remove old items from navKeyMap (including nested range children)
        headerCfgNode.getChildren().forEach(this::removeFromNavKeyMapRecursive);
        headerCfgNode.getChildren().clear();

        // First group: Dictionary description and Field definitions (separated)
        headerCfgNode.getChildren().add(navItem(NAV_CFG_DESC));
        headerCfgNode.getChildren().add(navItem(NAV_CFG_FIELD_DEFS));

        // Manage X entries (open config dialogs)
        headerCfgNode.getChildren().add(navItem(NAV_CFG_MANAGE_LANGS));
        headerCfgNode.getChildren().add(navItem(NAV_CFG_MANAGE_NOTE_TYPES));
        headerCfgNode.getChildren().add(navItem(NAV_CFG_MANAGE_TRANS_TYPES));
        headerCfgNode.getChildren().add(navItem(NAV_CFG_MANAGE_ANNOTATION_TYPES));
        headerCfgNode.getChildren().add(navItem(NAV_CFG_MANAGE_RELATION_TYPES));

        // Second group: Ranges (Taxinomies) - parent node with dynamic range entries as children
        TreeItem<String> rangesNode = new TreeItem<>(I18n.get("nav.cfgRanges"));
        rangesNode.setExpanded(false);
        if (currentDictionary != null) {
            LiftHeader header = currentDictionary.getLiftDictionaryComponents().getHeader();
            if (header != null) {
                for (LiftHeaderRange range : header.getRanges()) {
                    String key = NAV_CFG_RANGE_PREFIX + range.getId();
                    String label = range.getLabel().getForms().stream()
                        .findFirst().map(Form::toPlainText).orElse(range.getId());
                    rangesNode.getChildren().add(navItemDynamic(key, label));
                }
            }
        }
        headerCfgNode.getChildren().add(rangesNode);
        headerCfgNode.setExpanded(true);
    }

    private void removeFromNavKeyMapRecursive(TreeItem<String> item) {
        navKeyMap.remove(item);
        item.getChildren().forEach(this::removeFromNavKeyMapRecursive);
    }

    /* ─── View switching ─── */

    private void switchView(String viewName) {
        currentView = viewName;
        ensureRightPanelVisible();
        String title = viewName.startsWith(NAV_CFG_RANGE_PREFIX)
            ? viewName.substring(NAV_CFG_RANGE_PREFIX.length())
            : I18n.get(viewName);
        viewTitle.setText(title);
        editorContainer.getChildren().clear();
        editEntryTitle.setText(I18n.get("panel.selectElement"));
        editEntryCode.setText("");
        tableContainer.getChildren().clear();
        addButton.setText(I18n.get("btn.new"));

        if (viewName.startsWith(NAV_CFG_RANGE_PREFIX)) {
            setRightPanelVisible(true);
            showHeaderRangeView(viewName.substring(NAV_CFG_RANGE_PREFIX.length()));
            selectNavItem(viewName);
            return;
        }
        switch (viewName) {
            case NAV_ENTRIES     -> { setModifyButtonVisible(true); showEntryView(); }
            case NAV_SENSES      -> { setModifyButtonVisible(false); showSenseView(); }
            case NAV_EXAMPLES    -> { setModifyButtonVisible(false); showExampleView(); }
            case NAV_NOTES       -> { setModifyButtonVisible(false); showNoteView(); }
            case NAV_VARIANTS    -> { setModifyButtonVisible(false); showVariantView(); }
            case NAV_ETYMOLOGIES -> { setModifyButtonVisible(false); showEtymologyView(); }
            case NAV_RELATIONS   -> { setModifyButtonVisible(false); showRelationView(); }
            case NAV_OBJ_LANGS   -> { setModifyButtonVisible(false); showLangFieldView(true); }
            case NAV_META_LANGS  -> { setModifyButtonVisible(false); showLangFieldView(false); }
            case NAV_TRAITS      -> { setModifyButtonVisible(false); showTraitView(); }
            case NAV_ANNOTATIONS -> { setModifyButtonVisible(false); showAnnotationView(); }
            case NAV_FIELDS      -> { setModifyButtonVisible(false); showFieldView(); }
            case NAV_GRAM_INFO   -> { setModifyButtonVisible(false); showGramInfoView(); }
            case NAV_TRANS_TYPES -> { setModifyButtonVisible(false); showTranslationTypesView(); }
            case NAV_NOTE_TYPES    -> { setModifyButtonVisible(false); showNoteTypesView(); }
            case NAV_RELATION_TYPES -> { setModifyButtonVisible(false); showRelationTypesView(); }
            case NAV_QUICK_ENTRY  -> showQuickEntryView();
            case NAV_CFG_DESC        -> { showHeaderDescView(); setRightPanelVisible(false); }
            case NAV_CFG_FIELD_DEFS  -> showHeaderFieldDefsView();
            case NAV_CFG_MANAGE_LANGS -> { openManageLanguagesDialog(); showHeaderDescView(); setRightPanelVisible(false); }
            case NAV_CFG_MANAGE_NOTE_TYPES -> { onConfigNoteTypes(); showHeaderDescView(); setRightPanelVisible(false); }
            case NAV_CFG_MANAGE_TRANS_TYPES -> { onConfigTranslationTypes(); showHeaderDescView(); setRightPanelVisible(false); }
            case NAV_CFG_MANAGE_ANNOTATION_TYPES -> { onConfigAnnotationTypes(); showHeaderDescView(); setRightPanelVisible(false); }
            case NAV_CFG_MANAGE_RELATION_TYPES -> { onConfigRelationTypes(); showHeaderDescView(); setRightPanelVisible(false); }
            default -> showEntryView();
        }
        if (!NAV_CFG_DESC.equals(viewName)) setRightPanelVisible(true);
        // Hide search field in quick entry view (not needed there)
        if (searchField != null) {
            boolean showSearch = !NAV_QUICK_ENTRY.equals(viewName);
            searchField.setVisible(showSearch);
            searchField.setManaged(showSearch);
        }
        selectNavItem(viewName);
    }

    @FXML private void onUndo() {
        undoManager.undo();
    }

    @FXML private void onRedo() {
        undoManager.redo();
    }

    private boolean splitConstraintInstalled = false;

    private void setRightPanelVisible(boolean visible) {
        if (mainSplit != null && mainSplit.getItems().size() >= 2) {
            javafx.scene.Node rightPane = mainSplit.getItems().get(1);
            rightPane.setManaged(visible);
            rightPane.setVisible(visible);
        }
    }

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
        row.setMinWidth(0);
        String clearOption = I18n.get("filter.clear");
        List<TableColumn<LiftEntry, ?>> leaves = collectLeafColumns(entryTable);

        for (int i = 0; i < leaves.size(); i++) {
            TableColumn<LiftEntry, ?> col = leaves.get(i);
            ComboBox<String> cb = new ComboBox<>();
            cb.getStyleClass().add("filter-combo");
            cb.setPromptText(I18n.get("filter.prompt"));
            cb.setEditable(false);
            cb.setMaxWidth(Double.MAX_VALUE);
            cb.setMinWidth(0);
            cb.setMinHeight(26);
            cb.setPrefHeight(26);
            cb.setStyle("-fx-font-size: 11px;");
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

        // Aligner la largeur des filtres sur la zone des colonnes (prend en compte la scrollbar verticale)
        row.maxWidthProperty().bind(Bindings.createDoubleBinding(
            () -> leaves.stream().mapToDouble(c -> c.getWidth()).sum(),
            leaves.stream().<javafx.beans.Observable>map(col -> col.widthProperty()).toArray(javafx.beans.Observable[]::new)
        ));
        return row;
    }

    private void configureEntryTableColumns() {
        entryTable.getColumns().clear();
        entryTable.setEditable(true); // ← active l'édition sur la table
        if (currentDictionary == null) return;

        // ── Colonnes Formes par langue ──
        var formLangs = currentDictionary.getLiftDictionaryComponents().getAllEntries().stream()
                .flatMap(e -> e.getForms().getLangs().stream())
                .filter(s -> s != null && !s.isBlank()).distinct().sorted().toList();

        TableColumn<LiftEntry, String> formGroup = new TableColumn<>(I18n.get("col.form"));
        for (String lang : formLangs) {
            TableColumn<LiftEntry, String> c = new TableColumn<>(lang);
            c.setMinWidth(85);
            c.setPrefWidth(140);
            c.setCellValueFactory(cd -> cd.getValue() == null
                    ? new ReadOnlyStringWrapper("")
                    : cd.getValue().getForms().formTextProperty(lang));
            c.setCellFactory(TextFieldTableCell.forTableColumn());
            c.setOnEditCommit(ev -> {
                LiftEntry e = ev.getRowValue();
                if (e == null) return;
                e.getForms().getForms().stream()
                        .filter(f -> lang.equals(f.getLang())).findFirst()
                        .ifPresentOrElse(
                                f -> f.changeText(ev.getNewValue()),  // ← changeText au lieu de setText
                                () -> e.getForms().add(new Form(lang, ev.getNewValue()))
                        );
            });
            formGroup.getColumns().add(c);
        }

        // ── Colonne morph-type (trait "morph-type") ──
        TableColumn<LiftEntry, String> morphCol = new TableColumn<>(I18n.get("col.morphType"));
        morphCol.setMinWidth(85);
        morphCol.setPrefWidth(110);
        morphCol.setCellValueFactory(cd -> {
            LiftEntry e = cd.getValue();
            return e == null ? new ReadOnlyStringWrapper("") :
                    Bindings.createStringBinding(() -> getTraitValue(e, "morph-type"), e.traitsProperty());
        });
        morphCol.setCellFactory(TextFieldTableCell.forTableColumn());
        morphCol.setOnEditCommit(ev -> {
            LiftEntry e = ev.getRowValue();
            if (e == null) return;
            e.getTraits().stream().filter(t -> "morph-type".equals(t.getName())).findFirst()
                    .ifPresentOrElse(
                            t -> t.setValue(ev.getNewValue()),
                            () -> {
                                LiftFactory factory = getFactory(currentDictionary);
                                if (factory != null) factory.createTrait("morph-type", ev.getNewValue(), e);
                            }
                    );
        });

        // ── Colonne Date (lecture seule) ──
        TableColumn<LiftEntry, String> dateCol = new TableColumn<>(I18n.get("col.dateCreated"));
        dateCol.setMinWidth(85);
        dateCol.setPrefWidth(130);
        dateCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                cd.getValue() == null ? "" : cd.getValue().getDateCreated().orElse("")));

        entryTable.getColumns().addAll(formGroup, morphCol, dateCol);
    }
    /* ════════════════════ SENSE VIEW ════════════════════ */

    private void showSenseView() {
        senseTable.setItems(FXCollections.observableArrayList());
        senseTable.getColumns().clear();
        if (currentDictionary == null) { tableContainer.getChildren().setAll(senseTable); return; }

        List<String> objLangs = getObjectLanguages();
        List<String> metaLangs = getMetaLanguages();
        Map<LiftSense, LiftEntry> senseToEntry = buildSenseToEntryMap();
        // Colonne "Entrée parente" : forme(s) de l'entrée dont c'est le sens
        TableColumn<LiftSense, String> parentEntryGroup = new TableColumn<>(I18n.get("col.parentEntry"));
        for (String l : objLangs) {
            final String lang = l;
            TableColumn<LiftSense, String> c = col(l, s -> {
                LiftEntry parent = senseToEntry.get(s);
                return parent != null ? parent.getForms().getForm(lang).map(Form::toPlainText).orElse("") : "";
            });
            c.setPrefWidth(120);
            parentEntryGroup.getColumns().add(c);
        }
        TableColumn<LiftSense, String> giCol = col(I18n.get("col.gramInfo"), s -> s.getGrammaticalInfo().map(GrammaticalInfo::getValue).orElse(""));
        TableColumn<LiftSense, String> glossGroup = new TableColumn<>(I18n.get("col.gloss"));
        for (String l : metaLangs) {
            glossGroup.getColumns().add(col(l, s -> s.getGloss().getForm(l).map(Form::toPlainText).orElse("")));
        }
        TableColumn<LiftSense, String> defGroup = new TableColumn<>(I18n.get("col.definition"));
        for (String l : metaLangs) {
            defGroup.getColumns().add(col(l, s -> s.getDefinition().getForm(l).map(Form::toPlainText).orElse("")));
        }
        senseTable.getColumns().addAll(parentEntryGroup, giCol, glossGroup, defGroup);
        senseTable.getItems().addAll(currentDictionary.getLiftDictionaryComponents().getAllSenses());
        senseTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> { if (n != null) populateSenseEditor(n); });
        tableContainer.getChildren().setAll(wrapTableWithFilters(senseTable, (f,t) -> updateCountLabel(f,t), searchField != null ? searchField.textProperty() : null));
        updateCountLabel(senseTable.getItems().size(), senseTable.getItems().size());
    }

    /* ════════════════════ EXAMPLE VIEW ════════════════════ */

    private void showExampleView() {
        exampleTable.setItems(FXCollections.observableArrayList());
        exampleTable.getColumns().clear();
        if (currentDictionary == null) { tableContainer.getChildren().setAll(exampleTable); return; }

        List<String> objLangs = getObjectLanguages();
        List<String> metaLangs = getMetaLanguages();
        Set<String> transTypes = currentDictionary.getTranslationType();

        // 1. Sens parent (multitexte : glose du sens parent)
        TableColumn<LiftExample, String> parentSenseGroup = new TableColumn<>(I18n.get("col.parentSense"));
        for (String l : metaLangs) {
            final String lang = l;
            parentSenseGroup.getColumns().add(col(l, ex -> {
                LiftSense parent = ex.getParent() != null ? ex.getParent() : findParentSense(ex).orElse(null);
                if (parent == null) return "";
                // Gloss = forme principale du sens ; sinon première forme disponible
                MultiText gloss = parent.getGloss();
                return gloss.getForm(lang).map(Form::toPlainText)
                    .or(() -> gloss.getForms().stream().findFirst().map(Form::toPlainText))
                    .orElse("");
            }));
        }
        exampleTable.getColumns().add(parentSenseGroup);

        // 2. Source
        TableColumn<LiftExample, String> srcCol = col(I18n.get("col.source"), ex -> ex.getSource().orElse(""));
        exampleTable.getColumns().add(srcCol);

        // 3. Exemple (langues objet)
        TableColumn<LiftExample, String> exGroup = new TableColumn<>(I18n.get("col.example"));
        for (String l : objLangs) {
            exGroup.getColumns().add(col(l, ex -> ex.getExample().getForm(l).map(Form::toPlainText).orElse("")));
        }
        exampleTable.getColumns().add(exGroup);

        // 4. Traductions : un groupe par type de traduction, sous-colonnes par langue méta
        for (String transType : transTypes.stream().sorted().toList()) {
            TableColumn<LiftExample, String> transGroup = new TableColumn<>(
                transType.isEmpty() ? I18n.get("col.translation") : transType);
            for (String l : metaLangs) {
                final String lang = l;
                final String type = transType;
                transGroup.getColumns().add(col(l, ex -> {
                    MultiText mt = ex.getTranslations().get(type);
                    return mt != null ? mt.getForm(lang).map(Form::toPlainText).orElse("") : "";
                }));
            }
            exampleTable.getColumns().add(transGroup);
        }

        exampleTable.getItems().addAll(currentDictionary.getLiftDictionaryComponents().getAllExamples());
        exampleTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> { if (n != null) populateExampleEditor(n); });
        tableContainer.getChildren().setAll(wrapTableWithFilters(exampleTable, (f,t2) -> updateCountLabel(f,t2), searchField != null ? searchField.textProperty() : null));
        updateCountLabel(exampleTable.getItems().size(), exampleTable.getItems().size());
    }

    /* ════════════════════ NOTE VIEW ════════════════════ */

    private void showNoteView() {
        TableView<LiftNote> noteTable = new TableView<>();
        if (currentDictionary == null) { tableContainer.getChildren().setAll(noteTable); return; }
        List<String> metaLangs = getMetaLanguages();
        TableColumn<LiftNote, String> parentTypeCol = col(I18n.get("col.parentType"), n -> describeParentType(n.getParent()));
        TableColumn<LiftNote, String> typeCol = col(I18n.get("col.type"), n -> n.getType().orElse(""));
        TableColumn<LiftNote, String> textGroup = new TableColumn<>(I18n.get("col.text"));
        for (String l : metaLangs) {
            TableColumn<LiftNote, String> c = col(l, n -> n.getText().getForm(l).map(Form::toPlainText).orElse(""));
            c.getProperties().put("filterMode", FILTER_MODE_TEXT);
            textGroup.getColumns().add(c);
        }
        noteTable.getColumns().addAll(parentTypeCol, typeCol, textGroup);
        noteTable.getItems().addAll(currentDictionary.getLiftDictionaryComponents().getAllNotes());
        noteTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> { if (n != null) populateNoteEditor(n); });
        tableContainer.getChildren().setAll(wrapTableWithFilters(noteTable, (f,t2) -> updateCountLabel(f,t2), searchField != null ? searchField.textProperty() : null));
        updateCountLabel(noteTable.getItems().size(), noteTable.getItems().size());
    }

    /* ════════════════════ VARIANT VIEW ════════════════════ */

    private void showVariantView() {
        variantTable.setItems(FXCollections.observableArrayList());
        variantTable.getColumns().clear();
        if (currentDictionary == null) { tableContainer.getChildren().setAll(variantTable); return; }
        List<String> objLangs = getObjectLanguages();
        TableColumn<LiftVariant, String> parentFormGroup = new TableColumn<>(I18n.get("col.parentEntry"));
        for (String l : objLangs) {
            parentFormGroup.getColumns().add(col(l, v -> v.getParent() != null ? v.getParent().getForms().getForm(l).map(Form::toPlainText).orElse("") : ""));
        }
        TableColumn<LiftVariant, String> variantTypeCol = col("variant-type", v -> getTraitValueFor(v, "variant-type"));
        TableColumn<LiftVariant, String> isPrimaryCol = col("is-primary", v -> getTraitValueFor(v, "is-primary"));
        TableColumn<LiftVariant, String> refCol = col(I18n.get("col.ref"), v -> v.getRefId().orElse(""));
        TableColumn<LiftVariant, String> formGroup = new TableColumn<>(I18n.get("col.forms"));
        for (String l : objLangs) formGroup.getColumns().add(col(l, v -> v.getForms().getForm(l).map(Form::toPlainText).orElse("")));
        variantTable.getColumns().addAll(parentFormGroup, variantTypeCol, isPrimaryCol, refCol, formGroup);
        variantTable.getItems().addAll(currentDictionary.getLiftDictionaryComponents().getAllVariants());
        variantTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> { if (n != null) populateVariantEditor(n); });
        tableContainer.getChildren().setAll(wrapTableWithFilters(variantTable, (f,t2) -> updateCountLabel(f,t2), searchField != null ? searchField.textProperty() : null));
        updateCountLabel(variantTable.getItems().size(), variantTable.getItems().size());
    }

    /* ════════════════════ RELATION VIEW ════════════════════ */

    private void showRelationView() {
        relationTable.setItems(FXCollections.observableArrayList());
        relationTable.getColumns().clear();
        if (currentDictionary == null) { tableContainer.getChildren().setAll(relationTable); return; }
        List<String> objLangs = getObjectLanguages();
        List<String> metaLangs = getMetaLanguages();
        var entryById = currentDictionary.getLiftDictionaryComponents().getEntryById();
        TableColumn<LiftRelation, String> parentFormGroup = new TableColumn<>(I18n.get("col.parentEntry"));
        for (String l : objLangs) {
            parentFormGroup.getColumns().add(col(l, r -> {
                MultiText forms = getParentEntryForms(r);
                return forms != null ? forms.getForm(l).map(Form::toPlainText).orElse("") : "";
            }));
        }
        TableColumn<LiftRelation, String> typeCol = col(I18n.get("col.type"), (LiftRelation r) -> r.getType() != null ? r.getType() : "");
        TableColumn<LiftRelation, String> refFormGroup = new TableColumn<>(I18n.get("col.ref") + " (form)");
        for (String l : objLangs) {
            refFormGroup.getColumns().add(col(l, r -> {
                String refId = r.getRefID().orElse("");
                if (refId.isBlank()) return "";
                LiftEntry pointed = entryById != null ? entryById.get(refId) : null;
                return pointed != null ? pointed.getForms().getForm(l).map(Form::toPlainText).orElse("") : "";
            }));
        }
        TableColumn<LiftRelation, String> usageCol = new TableColumn<>(I18n.get("col.usage"));
        for (String l : metaLangs) {
            usageCol.getColumns().add(col(l, r -> r.getUsage().getForm(l).map(Form::toPlainText).orElse("")));
        }
        relationTable.getColumns().addAll(parentFormGroup, typeCol, refFormGroup, usageCol);
        relationTable.getItems().addAll(currentDictionary.getLiftDictionaryComponents().getAllRelations());
        relationTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> { if (n != null) populateRelationEditor(n); });
        tableContainer.getChildren().setAll(wrapTableWithFilters(relationTable, (f,t2) -> updateCountLabel(f,t2), searchField != null ? searchField.textProperty() : null));
        updateCountLabel(relationTable.getItems().size(), relationTable.getItems().size());
    }

    private void populateRelationEditor(LiftRelation relation) {
        List<String> metaLangs = getMetaLanguages();
        editEntryTitle.setText(I18n.get("nav.relations") + " : " + (relation.getType() != null ? relation.getType() : "?"));
        editEntryCode.setText(relation.getRefID().orElse(""));
        editorContainer.getChildren().clear();
        AbstractExtensibleWithoutField parent = relation.getParent();
        if (parent instanceof LiftEntry entry) {
            String entryForm = entry.getForms().getForms().stream().findFirst().map(Form::toPlainText).orElse("");
            if (entryForm.isEmpty()) entryForm = "?";
            Button backBtn = new Button(I18n.get("sense.backToEntry", entryForm));
            backBtn.getStyleClass().addAll("example-add-button", "back-btn");
            backBtn.setOnAction(e -> navigateToEntryFromSense(entry));
            editorContainer.getChildren().add(backBtn);
        } else if (parent instanceof LiftSense sense) {
            Button backBtn = new Button(I18n.get("sense.backToSense", senseDisplayText(sense)));
            backBtn.getStyleClass().addAll("example-add-button", "back-btn");
            backBtn.setOnAction(e -> navigateToSenseFromParent(sense));
            editorContainer.getChildren().add(backBtn);
        } else if (parent instanceof LiftVariant variant) {
            LiftEntry entry = variant.getParent();
            if (entry != null) {
                String entryForm = entry.getForms().getForms().stream().findFirst().map(Form::toPlainText).orElse("");
                if (entryForm.isEmpty()) entryForm = "?";
                Button backBtn = new Button(I18n.get("sense.backToEntry", entryForm));
                backBtn.getStyleClass().addAll("example-add-button", "back-btn");
                backBtn.setOnAction(e -> navigateToEntryFromSense(entry));
                editorContainer.getChildren().add(backBtn);
            }
        }
        RelationEditor re = new RelationEditor();
        re.setRelation(relation, metaLangs);
        editorContainer.getChildren().add(re);
    }

    /* ════════════════════ ETYMOLOGY VIEW ════════════════════ */

    private void showEtymologyView() {
        TableView<LiftEtymology> etyTable = new TableView<>();
        if (currentDictionary == null) { tableContainer.getChildren().setAll(etyTable); return; }
        List<String> objLangs = getObjectLanguages();
        TableColumn<LiftEtymology, String> typeCol = col(I18n.get("col.type"), (LiftEtymology e) -> e.getType() != null ? e.getType() : "");
        TableColumn<LiftEtymology, String> sourceCol = col(I18n.get("col.source"), (LiftEtymology e) -> e.getSource() != null ? e.getSource() : "");
        TableColumn<LiftEtymology, String> formGroup = new TableColumn<>(I18n.get("col.forms"));
        for (String l : objLangs) formGroup.getColumns().add(col(l, e -> e.getForms().getForm(l).map(Form::toPlainText).orElse("")));
        etyTable.getColumns().addAll(typeCol, sourceCol, formGroup);
        currentDictionary.getLiftDictionaryComponents().getAllEntries().stream()
            .flatMap(e -> e.getEtymologies().stream()).forEach(etyTable.getItems()::add);
        tableContainer.getChildren().setAll(wrapTableWithFilters(etyTable, (f,t2) -> updateCountLabel(f,t2)));
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
                collectMtRows(rows, I18n.get("nav.entries"), entry.getId().orElse("?"), entry, entry.getForms(), langs);
                for (LiftVariant v : entry.getVariants()) collectMtRows(rows, "variante", v.getRefId().orElse("?"), v, v.getForms(), langs);
                for (LiftPronunciation p : entry.getPronunciations()) collectMtRows(rows, "pron", entry.getId().orElse("?"), p, p.getProunciation(), langs);
                for (LiftSense s : entry.getSenses()) {
                    for (LiftExample ex : s.getExamples()) collectMtRows(rows, "exemple", s.getId().orElse("?"), ex, ex.getExample(), langs);
                }
            } else {
                for (LiftSense s : entry.getSenses()) {
                    collectMtRows(rows, "définition", s.getId().orElse("?"), s, s.getDefinition(), langs);
                    collectMtRows(rows, "gloss", s.getId().orElse("?"), s, s.getGloss(), langs);
                    for (LiftExample ex : s.getExamples()) {
                        for (MultiText tr : ex.getTranslations().values()) collectMtRows(rows, "traduction", s.getId().orElse("?"), ex, tr, langs);
                    }
                }
                for (LiftNote n : entry.getNotes().values()) collectMtRows(rows, "note", entry.getId().orElse("?"), n, n.getText(), langs);
            }
        }

        TableColumn<MultiTextField, String> parentTypeCol = new TableColumn<>(I18n.get("col.parentType"));
        parentTypeCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().parentType()));
        parentTypeCol.setPrefWidth(100);

        TableColumn<MultiTextField, String> langGroup = new TableColumn<>(I18n.get("nav.languages"));
        for (String l : langs) {
            TableColumn<MultiTextField, String> c = new TableColumn<>(l);
            c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(l.equals(cd.getValue().lang()) ? cd.getValue().text() : ""));
            c.setPrefWidth(160);
            c.getProperties().put("filterMode", FILTER_MODE_TEXT);
            langGroup.getColumns().add(c);
        }

        langFieldTable.getColumns().addAll(parentTypeCol, langGroup);
        langFieldTable.getItems().addAll(rows);
        langFieldTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) populateLangFieldEditor(n);
        });
        tableContainer.getChildren().setAll(wrapTableWithFilters(langFieldTable, (f,t2) -> updateCountLabel(f,t2), searchField != null ? searchField.textProperty() : null));
        updateCountLabel(rows.size(), rows.size());
    }

    private static void collectMtRows(List<MultiTextField> rows, String parentType, String parentId, Object parentObject, MultiText mt, List<String> langs) {
        for (Form f : mt.getForms()) {
            if (langs.contains(f.getLang())) {
                rows.add(new MultiTextField(parentType, parentId, f.getLang(), f.toPlainText(), parentObject, mt));
            }
        }
    }

    /* ════════════════════ TRAIT VIEW (5.10 – split: names top, values bottom) ════════════════════ */

    private void showTraitView() {
        traitTable.setItems(FXCollections.observableArrayList());
        traitTable.getColumns().clear();
        if (currentDictionary == null) { tableContainer.getChildren().setAll(wrapTableWithFilters(traitTable,
                (filtered, total) -> updateCountLabel(filtered, total), searchField != null ? searchField.textProperty() : null)); return; }

        Map<String, TraitRow> counts = new LinkedHashMap<>();
        for (LiftTrait t : currentDictionary.getLiftDictionaryComponents().getAllTraits()) {
            String key = t.getName() + "|" + t.getValue();
            counts.compute(key, (k, row) -> {
                if (row == null) return new TraitRow("", t.getName(), t.getValue(), 1);
                return new TraitRow("", row.name, row.value, row.frequency + 1);
            });
        }

        traitTable.getColumns().addAll(
            col(I18n.get("col.name"), (TraitRow r) -> r.name()),
            col(I18n.get("col.value"), (TraitRow r) -> r.value()),
            col(I18n.get("col.frequency"), r -> String.valueOf(r.frequency()))
        );
        traitTable.getItems().addAll(counts.values().stream().sorted(Comparator.comparingLong(TraitRow::frequency).reversed()).toList());
        traitTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) populateTraitSummaryEditor(n);
        });

        tableContainer.getChildren().setAll(wrapTableWithFilters(traitTable, (f,t2) -> updateCountLabel(f,t2), searchField != null ? searchField.textProperty() : null));
        updateCountLabel(traitTable.getItems().size(), traitTable.getItems().size());
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

        tableContainer.getChildren().setAll(wrapTableWithFilters(annotationTable, (f,t2) -> updateCountLabel(f,t2), searchField != null ? searchField.textProperty() : null));
        updateCountLabel(all.size(), all.size());
    }

    /* ════════════════════ FIELD VIEW (5.10) ════════════════════ */

    private void showFieldView() {
        fieldTable.setItems(FXCollections.observableArrayList());
        fieldTable.getColumns().clear();
        if (currentDictionary == null) { tableContainer.getChildren().setAll(fieldTable); return; }
        fieldTable.getColumns().addAll(
            col(I18n.get("col.parentType"), f -> describeParentType(f.getParent())),
            col(I18n.get("col.type"), LiftField::getName),
            col(I18n.get("col.text"), f -> f.getText().getForms().stream().findFirst().map(Form::toPlainText).orElse(""))
        );
        fieldTable.getItems().addAll(currentDictionary.getLiftDictionaryComponents().getAllFields());
        fieldTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) populateFieldSummaryEditor(n);
        });
        tableContainer.getChildren().setAll(wrapTableWithFilters(fieldTable, (f,t2) -> updateCountLabel(f,t2), searchField != null ? searchField.textProperty() : null));
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

        // Seed with empty rows
        for (int i = 0; i < 5; i++) quickEntryTable.getItems().add(new QuickEntryRow());

        // Auto-create entry when user leaves a filled row (selection changes away from it)
        LiftFactory factory = getFactory(currentDictionary);
        quickEntryTable.getSelectionModel().selectedIndexProperty().addListener((obs, oldIdx, newIdx) -> {
            int prev = oldIdx.intValue();
            if (prev < 0 || prev >= quickEntryTable.getItems().size()) return;
            QuickEntryRow row = quickEntryTable.getItems().get(prev);
            boolean hasContent = objLangs.stream().anyMatch(l -> !row.formProperty(l).get().isBlank())
                || metaLangs.stream().anyMatch(l -> !row.glossProperty(l).get().isBlank());
            if (!hasContent || factory == null) return;

            // Check if entry was already auto-created for this row
            if (Boolean.TRUE.equals(row.createdProperty().get())) return;
            row.createdProperty().set(true);

            org.xml.sax.helpers.AttributesImpl attrs = new org.xml.sax.helpers.AttributesImpl();
            attrs.addAttribute("", "id", "id", "CDATA", UUID.randomUUID().toString());
            LiftEntry entry = factory.createEntry(attrs);
            for (String l : objLangs) {
                String v = row.formProperty(l).get();
                if (!v.isBlank()) entry.getForms().add(new Form(l, v));
            }
            org.xml.sax.helpers.AttributesImpl senseAttrs = new org.xml.sax.helpers.AttributesImpl();
            senseAttrs.addAttribute("", "id", "id", "CDATA", UUID.randomUUID().toString());
            LiftSense sense = factory.createSense(senseAttrs, entry);
            for (String l : metaLangs) {
                String v = row.glossProperty(l).get();
                if (!v.isBlank()) sense.addGloss(new Form(l, v));
            }
            String gi = row.gramInfoProperty().get();
            if (!gi.isBlank()) sense.setGrammaticalInfo(gi);
            baseEntries.add(entry);
            updateCountLabel(baseEntries.size(), baseEntries.size());

            // Ensure there's always an empty row at the end
            int lastIdx = quickEntryTable.getItems().size() - 1;
            QuickEntryRow lastRow = quickEntryTable.getItems().get(lastIdx);
            boolean lastHasContent = objLangs.stream().anyMatch(l -> !lastRow.formProperty(l).get().isBlank())
                || metaLangs.stream().anyMatch(l -> !lastRow.glossProperty(l).get().isBlank());
            if (lastHasContent) quickEntryTable.getItems().add(new QuickEntryRow());
        });

        // Also append a new row when user clicks on the last row
        quickEntryTable.setOnMouseClicked(e -> {
            int lastIdx = quickEntryTable.getItems().size() - 1;
            if (lastIdx >= 0 && quickEntryTable.getSelectionModel().getSelectedIndex() == lastIdx) {
                QuickEntryRow lastRow = quickEntryTable.getItems().get(lastIdx);
                boolean lastHasContent = objLangs.stream().anyMatch(l -> !lastRow.formProperty(l).get().isBlank())
                    || metaLangs.stream().anyMatch(l -> !lastRow.glossProperty(l).get().isBlank());
                if (lastHasContent) quickEntryTable.getItems().add(new QuickEntryRow());
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
                org.xml.sax.helpers.AttributesImpl senseAttrs = new org.xml.sax.helpers.AttributesImpl();
                senseAttrs.addAttribute("", "id", "id", "CDATA", UUID.randomUUID().toString());
                factory.createSense(senseAttrs, selEntry);
                switchView(NAV_SENSES);
            }
            case NAV_RELATIONS, NAV_EXAMPLES, NAV_NOTES, NAV_VARIANTS, NAV_ETYMOLOGIES ->
                showInfo(I18n.get("error.creation"), I18n.get("info.addFromParent"));
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
            org.xml.sax.helpers.AttributesImpl senseAttrs = new org.xml.sax.helpers.AttributesImpl();
            senseAttrs.addAttribute("", "id", "id", "CDATA", UUID.randomUUID().toString());
            LiftSense sense = factory.createSense(senseAttrs, entry);
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
        setModifyButtonVisible(true);
        try {
        List<String> objLangs = getObjectLanguages();
        List<String> metaLangs = getMetaLanguages();

        // Collect known dropdown values filtered by element type (entry)
        List<String> traitNames = getKnownTraitNamesFor(FieldDefinitionTarget.ENTRY);
        Map<String, Set<String>> traitValues = getKnownTraitValues();
        List<String> annotationNames = getKnownAnnotationNames();
        List<String> fieldTypes = getKnownFieldTypesFor(FieldDefinitionTarget.ENTRY);

        Form preferred = entry.getForms().getForms().stream().findFirst().orElse(Form.EMPTY_FORM);
        editEntryTitle.setText(preferred == Form.EMPTY_FORM ? "(sans forme)" : preferred.toPlainText());
        editEntryCode.setText(getTraitValue(entry, "code"));
        editorContainer.getChildren().clear();

            Button deleteBtn = new Button(I18n.get("btn.delete"));
            deleteBtn.getStyleClass().add("delete-btn");
            deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 7 12 7 12;");
            deleteBtn.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle(I18n.get("confirm.delete.title"));
                confirm.setHeaderText(null);
                confirm.setContentText(I18n.get("confirm.delete.entry",
                        entry.getForms().getForms().stream().findFirst()
                                .map(Form::toPlainText).filter(t -> t != null && !t.isBlank()).orElse("(sans forme)")));
                confirm.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
                    int idx = baseEntries.indexOf(entry);
                    Runnable refresh = () -> {
                        editorContainer.getChildren().clear();
                        editEntryTitle.setText(I18n.get("panel.selectElement"));
                        editEntryCode.setText("");
                        applyCurrentFilter();
                    };
                    DeleteEntryCommand cmd = new DeleteEntryCommand(entry, idx,
                        () -> getFactory(currentDictionary), baseEntries, refresh, refresh);
                    cmd.redo();
                    undoManager.execute(cmd);
                });
            });
            editorContainer.getChildren().add(deleteBtn);

        addSection(editorContainer, I18n.get("editor.forms"), () -> { MultiTextEditor m = new MultiTextEditor(); m.setAvailableLanguages(objLangs); m.setMultiText(entry.getForms()); return m; }, true);
        addListSection(editorContainer, I18n.get("editor.traits"), safeList(entry.getTraits()), t -> {
            TraitEditor te = new TraitEditor();
            te.setTrait(t, objLangs, traitNames, traitValues, findFieldDef(t.getName()));
            return te;
        }, true);
        addListSection(editorContainer, I18n.get("editor.pronunciations"), safeList(entry.getPronunciations()), p -> { PronunciationEditor pe = new PronunciationEditor(); pe.setPronunciation(p, objLangs); return pe; }, false);

        List<LiftSense> senses = safeList(entry.getSenses());
        if (!senses.isEmpty()) {
            addSection(editorContainer, I18n.get("editor.senses") + " (" + senses.size() + ")", () -> {
                VBox box = new VBox(4);
                for (LiftSense s : senses) {
                    String label = s.getGloss().getForms().stream().findFirst().map(Form::toPlainText).orElse("");
                    if (label.isEmpty()) label = "?";
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
                org.xml.sax.helpers.AttributesImpl senseAttrs = new org.xml.sax.helpers.AttributesImpl();
                senseAttrs.addAttribute("", "id", "id", "CDATA", UUID.randomUUID().toString());
                factory.createSense(senseAttrs, entry);
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
                Optional<String> nameOpt;
                if (names.isEmpty()) {
                    TextInputDialog tid = new TextInputDialog();
                    tid.setTitle(I18n.get("btn.addAnnotation"));
                    tid.setHeaderText(I18n.get("col.name"));
                    nameOpt = tid.showAndWait();
                } else {
                    ChoiceDialog<String> dlg = new ChoiceDialog<>(names.get(0), names);
                    dlg.setTitle(I18n.get("btn.addAnnotation"));
                    dlg.setHeaderText(I18n.get("col.name"));
                    nameOpt = dlg.showAndWait();
                }
                nameOpt.filter(n -> n != null && !n.isBlank()).ifPresent(name -> {
                    factory.createAnnotation(name.trim(), entry);
                    populateEntryEditor(entry);
                });
            });

            Button addRelationBtn = new Button(I18n.get("btn.addRelation"));
            addRelationBtn.setOnAction(e -> {
                List<String> types = getKnownRelationTypes();
                Optional<String> typeOpt;
                if (types.isEmpty()) {
                    TextInputDialog tid = new TextInputDialog();
                    tid.setTitle(I18n.get("btn.addRelation"));
                    tid.setHeaderText(I18n.get("col.type"));
                    typeOpt = tid.showAndWait();
                } else {
                    ChoiceDialog<String> dlg = new ChoiceDialog<>(types.get(0), types);
                    dlg.setTitle(I18n.get("btn.addRelation"));
                    dlg.setHeaderText(I18n.get("col.type"));
                    typeOpt = dlg.showAndWait();
                }
                typeOpt.filter(t -> t != null && !t.isBlank()).ifPresent(type -> {
                    org.xml.sax.helpers.AttributesImpl attrs = new org.xml.sax.helpers.AttributesImpl();
                    attrs.addAttribute("", "type", "type", "CDATA", type.trim());
                    factory.createRelation(attrs, entry);
                    populateEntryEditor(entry);
                });
            });

            Button addEtymologyBtn = new Button(I18n.get("btn.addEtymology"));
            addEtymologyBtn.setOnAction(e -> {
                showAddEtymologyDialog(entry, factory);
            });

            addButtons.getChildren().addAll(addSenseBtn, addVariantBtn, addPronBtn, addTraitBtn, addNoteBtn, addFieldBtn, addAnnotBtn, addRelationBtn, addEtymologyBtn);
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

    /** Texte d'affichage d'un sens : glose(s) séparées par " / ", sinon définition, sinon "?". */
    private static String senseDisplayText(LiftSense sense) {
        if (sense == null) return "?";
        String gloss = sense.getGloss().getForms().stream()
                .map(Form::toPlainText)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining(" / "));
        if (!gloss.isEmpty()) return gloss;
        String def = sense.getDefinition().getForms().stream()
                .map(Form::toPlainText)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining(" / "));
        if (!def.isEmpty()) return def;
        return "?";
    }

    private void populateSenseEditor(LiftSense sense) {
        List<String> metaLangs = getMetaLanguages();
        List<String> objLangs = getObjectLanguages();
        editEntryTitle.setText(senseDisplayText(sense));
        editEntryCode.setText(sense.getGrammaticalInfo().map(GrammaticalInfo::getValue).orElse(""));
        editorContainer.getChildren().clear();

        Button deleteBtn = new Button(I18n.get("btn.delete"));
        deleteBtn.getStyleClass().add("delete-btn");
        deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 7 12 7 12;");
        deleteBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle(I18n.get("confirm.delete.title"));
            confirm.setHeaderText(null);
            confirm.setContentText(I18n.get("confirm.delete.sense", senseDisplayText(sense)));
            confirm.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
                findParentSenseListAndIndex(sense).ifPresent(pair -> {
                    java.util.List<LiftSense> parentList = pair.getKey();
                    int idx = pair.getValue();
                    Runnable refresh = () -> {
                        editorContainer.getChildren().clear();
                        editEntryTitle.setText(I18n.get("panel.selectElement"));
                        editEntryCode.setText("");
                        showSenseView();
                    };
                    DeleteSenseCommand cmd = new DeleteSenseCommand(sense, parentList, idx,
                        () -> getFactory(currentDictionary), refresh, refresh);
                    cmd.redo();
                    undoManager.execute(cmd);
                });
            });
        });
        editorContainer.getChildren().add(deleteBtn);
        // Parent button: navigate back to entry view filtered to this sense's parent
        findParentEntry(sense).ifPresent(parentEntry -> {
            String entryForm = parentEntry.getForms().getForms().stream().findFirst().map(Form::toPlainText).orElse("");
            if (entryForm.isEmpty()) entryForm = "?";
            Button backBtn = new Button(I18n.get("sense.backToEntry", entryForm));
            backBtn.getStyleClass().addAll("example-add-button", "back-btn");
            backBtn.setOnAction(e -> navigateToEntryFromSense(parentEntry));
            editorContainer.getChildren().add(backBtn);
        });

        // Links to examples (one per example, showing example number)
        List<LiftExample> examples = sense.getExamples();
        for (int i = 0; i < examples.size(); i++) {
            final int idx = i + 1;
            final LiftExample ex = examples.get(i);
            Hyperlink exLink = new Hyperlink(I18n.get("sense.exampleN", idx));
            exLink.setOnAction(e -> navigateToExampleKeepingEntriesFocus(ex));
            editorContainer.getChildren().add(exLink);
        }

        SenseEditor se = new SenseEditor();
        LiftFactory factory = getFactory(currentDictionary);
        BiConsumer<String, MultiText> onAddAnnotation = (factory != null)
            ? (name, mt) -> factory.createAnnotation(name, mt)
            : null;
        se.setSense(sense, metaLangs, objLangs, onAddAnnotation, getKnownAnnotationNames());
        editorContainer.getChildren().add(se);
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

            Button addRelationBtn = new Button(I18n.get("btn.addRelation"));
            addRelationBtn.setOnAction(e -> {
                List<String> types = getKnownRelationTypes();
                Optional<String> typeOpt;
                if (types.isEmpty()) {
                    TextInputDialog tid = new TextInputDialog();
                    tid.setTitle(I18n.get("btn.addRelation"));
                    tid.setHeaderText(I18n.get("col.type"));
                    typeOpt = tid.showAndWait();
                } else {
                    ChoiceDialog<String> dlg = new ChoiceDialog<>(types.get(0), types);
                    dlg.setTitle(I18n.get("btn.addRelation"));
                    dlg.setHeaderText(I18n.get("col.type"));
                    typeOpt = dlg.showAndWait();
                }
                typeOpt.filter(t -> t != null && !t.isBlank()).ifPresent(type -> {
                    org.xml.sax.helpers.AttributesImpl attrs = new org.xml.sax.helpers.AttributesImpl();
                    attrs.addAttribute("", "type", "type", "CDATA", type.trim());
                    factory.createRelation(attrs, sense);
                    populateSenseEditor(sense);
                });
            });

            Button addReversalBtn = new Button(I18n.get("btn.addReversal"));
            addReversalBtn.setOnAction(e -> {
                org.xml.sax.helpers.AttributesImpl attrs = new org.xml.sax.helpers.AttributesImpl();
                factory.createReversal(attrs, sense);
                populateSenseEditor(sense);
            });

            Button addSubSenseBtn = new Button(I18n.get("btn.addSubSense"));
            addSubSenseBtn.setOnAction(e -> {
                org.xml.sax.helpers.AttributesImpl senseAttrs = new org.xml.sax.helpers.AttributesImpl();
                senseAttrs.addAttribute("", "id", "id", "CDATA", UUID.randomUUID().toString());
                factory.createSense(senseAttrs, sense);
                populateSenseEditor(sense);
            });

            Button addTraitBtn = new Button(I18n.get("btn.addTrait"));
            addTraitBtn.setOnAction(e -> {
                List<String> names = getKnownTraitNames();
                ChoiceDialog<String> dlg = new ChoiceDialog<>(names.isEmpty() ? null : names.get(0), names);
                dlg.setTitle(I18n.get("btn.addTrait"));
                dlg.setHeaderText(I18n.get("col.name"));
                dlg.showAndWait().ifPresent(name -> {
                    factory.createTrait(name, "", sense);
                    populateSenseEditor(sense);
                });
            });

            Button addAnnotBtn = new Button(I18n.get("btn.addAnnotation"));
            addAnnotBtn.setOnAction(e -> {
                List<String> names = getKnownAnnotationNames();
                Optional<String> nameOpt;
                if (names.isEmpty()) {
                    TextInputDialog tid = new TextInputDialog();
                    tid.setTitle(I18n.get("btn.addAnnotation"));
                    tid.setHeaderText(I18n.get("col.name"));
                    nameOpt = tid.showAndWait();
                } else {
                    ChoiceDialog<String> dlg = new ChoiceDialog<>(names.get(0), names);
                    dlg.setTitle(I18n.get("btn.addAnnotation"));
                    dlg.setHeaderText(I18n.get("col.name"));
                    nameOpt = dlg.showAndWait();
                }
                nameOpt.filter(n -> n != null && !n.isBlank()).ifPresent(name -> {
                    factory.createAnnotation(name.trim(), sense);
                    populateSenseEditor(sense);
                });
            });

            Button addFieldBtn = new Button(I18n.get("btn.addField"));
            addFieldBtn.setOnAction(e -> {
                List<String> types = getKnownFieldTypesFor(FieldDefinitionTarget.SENSE);
                ChoiceDialog<String> dlg = new ChoiceDialog<>(types.isEmpty() ? null : types.get(0), types);
                dlg.setTitle(I18n.get("btn.addField"));
                dlg.setHeaderText(I18n.get("col.type"));
                dlg.showAndWait().ifPresent(type -> {
                    factory.createField(type, sense);
                    populateSenseEditor(sense);
                });
            });

            addButtons.getChildren().addAll(addExBtn, addNoteBtn, addRelationBtn, addReversalBtn, addSubSenseBtn, addTraitBtn, addAnnotBtn, addFieldBtn);
            editorContainer.getChildren().add(addButtons);
        }
    }

    private Optional<LiftEntry> findParentEntry(LiftSense sense) {
        if (currentDictionary == null) return Optional.empty();
        return currentDictionary.getLiftDictionaryComponents().getAllEntries().stream()
            .filter(e -> containsSense(e.getSenses(), sense))
            .findFirst();
    }

    /** Precomputed map sense -> entry for fast lookup in sense table (avoids O(n) per cell). */
    private Map<LiftSense, LiftEntry> buildSenseToEntryMap() {
        Map<LiftSense, LiftEntry> map = new HashMap<>();
        if (currentDictionary == null) return map;
        for (LiftEntry e : currentDictionary.getLiftDictionaryComponents().getAllEntries()) {
            fillSenseToEntry(map, e.getSenses(), e);
        }
        return map;
    }

    private void fillSenseToEntry(Map<LiftSense, LiftEntry> map, List<LiftSense> senses, LiftEntry entry) {
        for (LiftSense s : senses) {
            map.put(s, entry);
            fillSenseToEntry(map, s.getSubSenses(), entry);
        }
    }

    private boolean containsSense(List<LiftSense> list, LiftSense target) {
        if (list.contains(target)) return true;
        for (LiftSense s : list) {
            if (containsSense(s.getSubSenses(), target)) return true;
        }
        return false;
    }

    private Optional<Pair<List<LiftSense>, Integer>> findParentSenseListAndIndex(LiftSense sense) {
        if (currentDictionary == null) return Optional.empty();
        for (LiftEntry e : currentDictionary.getLiftDictionaryComponents().getAllEntries()) {
            var found = findInList(e.getSenses(), sense);
            if (found != null) return Optional.of(found);
        }
        return Optional.empty();
    }

    private Pair<List<LiftSense>, Integer> findInList(List<LiftSense> list, LiftSense target) {
        int idx = list.indexOf(target);
        if (idx >= 0) return new Pair<>(list, idx);
        for (LiftSense s : list) {
            var sub = findInList(s.getSubSenses(), target);
            if (sub != null) return sub;
        }
        return null;
    }

    private void populateExampleEditor(LiftSense parentSense, LiftExample ex) {
        editEntryTitle.setText(I18n.get("nav.examples"));
        editEntryCode.setText(ex.getSource().orElse(""));
        editorContainer.getChildren().clear();
        Button deleteBtn = new Button(I18n.get("btn.delete"));
        deleteBtn.getStyleClass().add("delete-btn");
        deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 7 12 7 12;");
        deleteBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle(I18n.get("confirm.delete.title"));
            confirm.setHeaderText(null);
            confirm.setContentText(I18n.get("confirm.delete.example"));
            confirm.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
                findParentSense(ex).ifPresent(parent -> {
                    java.util.List<LiftExample> parentList = parent.getExamples();
                    int idx = parentList.indexOf(ex);
                    Runnable refresh = () -> {
                        editorContainer.getChildren().clear();
                        editEntryTitle.setText(I18n.get("panel.selectElement"));
                        editEntryCode.setText("");
                        showExampleView();
                    };
                    DeleteExampleCommand cmd = new DeleteExampleCommand(ex, parent, idx,
                        () -> getFactory(currentDictionary), refresh, refresh);
                    cmd.redo();
                    undoManager.execute(cmd);
                });
            });
        });
        editorContainer.getChildren().add(deleteBtn);

        LiftSense resolvedParent = parentSense != null
                ? parentSense
                : findParentSense(ex).orElse(null);

        if (resolvedParent != null) {
            final LiftSense finalParent = resolvedParent;
            String senseGloss = finalParent.getGloss().getForms().stream().findFirst().map(Form::toPlainText).orElse("");
            if (senseGloss.isEmpty()) senseGloss = "?";
            Button backBtn = new Button(I18n.get("sense.backToSense", senseGloss));
            backBtn.getStyleClass().addAll("example-add-button", "back-btn");
            backBtn.setOnAction(e -> {
                switchView(NAV_SENSES);
                if (senseTable.getItems().contains(finalParent)) {
                    senseTable.getSelectionModel().select(finalParent);
                    senseTable.scrollTo(finalParent);
                }
                selectNavItem(NAV_SENSES);
                populateSenseEditor(finalParent);
            });
            editorContainer.getChildren().add(backBtn);
        }

        ExampleEditor ee = new ExampleEditor();
        LiftFactory factory = getFactory(currentDictionary);
        BiConsumer<String, MultiText> onAddAnnotation = (factory != null)
            ? (name, mt) -> factory.createAnnotation(name, mt)
            : null;
        ee.setExample(ex, getObjectLanguages(), getMetaLanguages(), onAddAnnotation, getKnownAnnotationNames());
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
        // ✅ Mettre le focus sur "Sens" dans le menu gauche
        selectNavItem(NAV_SENSES);
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

    /** Navigue vers la fiche du sens (depuis une note ou autre objet enfant). */
    private void navigateToSenseFromParent(LiftSense sense) {
        if (sense == null) return;
        switchView(NAV_SENSES);
        if (senseTable.getItems().contains(sense)) {
            senseTable.getSelectionModel().select(sense);
            senseTable.scrollTo(sense);
            populateSenseEditor(sense);
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
        selectNavItem(NAV_EXAMPLES);
    }
    private void selectNavItem(String navKey) {
        if (navTree == null || navTree.getSelectionModel() == null) return;
        TreeItem<String> item = findNavItemByKey(navKey);
        if (item == null) return;
        ignoreNavSelectionEvents = true;
        try {
            navTree.getSelectionModel().select(item);
        } finally {
            ignoreNavSelectionEvents = false;
        }
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
            if (child instanceof ComboBox<?> rawCombo) {
                @SuppressWarnings("unchecked")
                ComboBox<String> combo = (ComboBox<String>) rawCombo;
                combo.setValue(clearOption);
            } else if (child instanceof TextField textField) {
                textField.clear();
            }
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
        setModifyButtonVisible(false);
        editEntryTitle.setText(I18n.get(currentView));
        editEntryCode.setText("");
        editorContainer.getChildren().clear();

        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(6);
        int r = 0;
        addReadOnlyRow(g, r++, I18n.get("col.parentType"), row.parentType());
        g.add(new Label(I18n.get("col.parent")), 0, r);
        if (row.parentObject() != null) {
            Button parentLink = new Button(row.parentId());
            parentLink.getStyleClass().add("hyperlink");
            parentLink.setOnAction(e -> navigateToObject(row.parentObject()));
            GridPane.setHgrow(parentLink, Priority.ALWAYS);
            g.add(parentLink, 1, r);
        } else {
            TextField tf = new TextField(row.parentId());
            styleReadOnlyTextField(tf);
            GridPane.setHgrow(tf, Priority.ALWAYS);
            g.add(tf, 1, r);
        }
        r++;
        addReadOnlyRow(g, r++, I18n.get("nav.languages"), row.lang());

        if (row.multiText() != null) {
            editorContainer.getChildren().add(g);
            MultiTextEditor mte = new MultiTextEditor();
            List<String> availLangs = NAV_OBJ_LANGS.equals(currentView) ? getObjectLanguages() : getMetaLanguages();
            mte.setAvailableLanguages(availLangs.isEmpty() ? List.of(row.lang()) : availLangs);
            mte.setMultiText(row.multiText());
            VBox textSection = new VBox(6);
            textSection.getChildren().add(new Label(I18n.get("col.text")));
            textSection.getChildren().add(mte);
            VBox.setVgrow(mte, Priority.ALWAYS);
            editorContainer.getChildren().add(textSection);
        } else {
            addReadOnlyRow(g, r, I18n.get("col.text"), row.text());
            editorContainer.getChildren().add(g);
        }
    }

    private void populateTraitSummaryEditor(TraitRow row) {
        setModifyButtonVisible(false);
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put(I18n.get("col.name"), row.name());
        values.put(I18n.get("col.value"), row.value());
        values.put(I18n.get("col.frequency"), String.valueOf(row.frequency()));
        populateSummaryEditor(I18n.get("nav.traits"), "", values);
        Button accessBtn = new Button(I18n.get("btn.accessObjects"));
        accessBtn.getStyleClass().add("example-add-button");
        accessBtn.setMaxWidth(Double.MAX_VALUE);
        accessBtn.setOnAction(e -> showObjectsWithTrait(row.name(), row.value()));
        editorContainer.getChildren().add(accessBtn);
    }

    private void populateNoteTypeSummaryEditor(NoteTypeRow row) {
        setModifyButtonVisible(false);
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put(I18n.get("col.type"), row.type());
        values.put(I18n.get("col.parentType"), row.parentType());
        values.put(I18n.get("col.frequency"), String.valueOf(row.frequency()));
        populateSummaryEditor(I18n.get("nav.noteTypes"), "", values);
        Button accessBtn = new Button(I18n.get("btn.accessObjects"));
        accessBtn.getStyleClass().add("example-add-button");
        accessBtn.setMaxWidth(Double.MAX_VALUE);
        accessBtn.setOnAction(e -> showObjectsWithNoteType(row.type()));
        editorContainer.getChildren().add(accessBtn);
    }

    private void setModifyButtonVisible(boolean visible) {
        if (modifyButtonRow != null) modifyButtonRow.setVisible(visible);
    }

    private void showObjectsWithTrait(String traitName, String traitValue) {
        if (currentDictionary == null) return;
        List<Object> matches = new ArrayList<>();
        var comps = currentDictionary.getLiftDictionaryComponents();
        for (LiftEntry e : comps.getAllEntries()) {
            if (e.getTraits().stream().anyMatch(t -> traitName.equals(t.getName()) && traitValue.equals(t.getValue())))
                matches.add(e);
        }
        for (LiftSense s : comps.getAllSenses()) {
            if (s.getTraits().stream().anyMatch(t -> traitName.equals(t.getName()) && traitValue.equals(t.getValue())))
                matches.add(s);
        }
        for (LiftExample ex : comps.getAllExamples()) {
            if (ex.getTraits().stream().anyMatch(t -> traitName.equals(t.getName()) && traitValue.equals(t.getValue())))
                matches.add(ex);
        }
        for (LiftVariant v : comps.getAllVariants()) {
            if (v.getTraits() != null && v.getTraits().stream().anyMatch(t -> traitName.equals(t.getName()) && traitValue.equals(t.getValue())))
                matches.add(v);
        }
        for (LiftEtymology et : comps.getAllEntries().stream().flatMap(e -> e.getEtymologies().stream()).toList()) {
            if (et.getTraits().stream().anyMatch(t -> traitName.equals(t.getName()) && traitValue.equals(t.getValue())))
                matches.add(et);
        }
        showFilteredObjectsTable(matches, I18n.get("nav.traits") + ": " + traitName + " = " + traitValue);
    }

    private void showObjectsWithNoteType(String noteType) {
        if (currentDictionary == null) return;
        List<Object> matches = new ArrayList<>();
        for (LiftNote n : currentDictionary.getLiftDictionaryComponents().getAllNotes()) {
            if (noteType.equals(n.getType().orElse(""))) matches.add(n);
        }
        showFilteredObjectsTable(matches, I18n.get("nav.noteTypes") + ": " + noteType);
    }

    private void showFilteredObjectsTable(List<Object> matches, String title) {
        TableView<Object> table = new TableView<>(FXCollections.observableArrayList(matches));
        table.getColumns().add(col("Type", o -> o.getClass().getSimpleName()));
        table.getColumns().add(col(I18n.get("col.id"), o -> {
            if (o instanceof LiftEntry e) return e.getId().orElse("");
            if (o instanceof LiftSense s) return s.getId().orElse("");
            if (o instanceof LiftExample ex) return ex.getExample().getForms().stream().findFirst().map(Form::toPlainText).orElse("(example)");
            if (o instanceof LiftNote n) return n.getParent() != null ? describeParent(n.getParent()) : "";
            if (o instanceof LiftVariant v) return v.getRefId().orElse("");
            if (o instanceof LiftEtymology et) return et.getType() != null ? et.getType() : "";
            if (o instanceof LiftRelation r) return (r.getType() != null ? r.getType() : "") + " @ " + describeParent(r.getParent());
            if (o instanceof LiftPronunciation p) return p.getParent() != null ? describeParent(p.getParent()) : "";
            return "";
        }));
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) navigateToObject(n);
        });
        viewTitle.setText(title);
        tableContainer.getChildren().setAll(wrapTableWithFilters(table));
        updateCountLabel(matches.size(), matches.size());
    }

    private void navigateToObject(Object obj) {
        if (obj instanceof LiftEntry e) { switchView(NAV_ENTRIES); selectEntryInTable(e); populateEntryEditor(e); }
        else if (obj instanceof LiftSense s) { findParentEntry(s).ifPresent(entry -> { switchView(NAV_ENTRIES); selectEntryInTable(entry); populateEntryEditor(entry); }); }
        else if (obj instanceof LiftExample ex) { findParentSense(ex).ifPresent(sense -> { findParentEntry(sense).ifPresent(entry -> { switchView(NAV_ENTRIES); selectEntryInTable(entry); populateEntryEditor(entry); }); }); }
        else if (obj instanceof LiftNote n) { AbstractNotable p = n.getParent(); if (p instanceof LiftEntry e) { switchView(NAV_ENTRIES); selectEntryInTable(e); populateEntryEditor(e); } else if (p instanceof LiftSense s) { findParentEntry(s).ifPresent(entry -> { switchView(NAV_ENTRIES); selectEntryInTable(entry); populateEntryEditor(entry); }); } }
        else if (obj instanceof LiftVariant v) { if (v.getParent() != null) { switchView(NAV_ENTRIES); selectEntryInTable(v.getParent()); populateEntryEditor(v.getParent()); } }
        else if (obj instanceof LiftEtymology et) { LiftEntry entry = et.getParent(); if (entry != null) { switchView(NAV_ENTRIES); selectEntryInTable(entry); populateEntryEditor(entry); } }
        else if (obj instanceof LiftRelation r) {
            AbstractExtensibleWithoutField p = r.getParent();
            if (p instanceof LiftEntry e) { switchView(NAV_ENTRIES); selectEntryInTable(e); populateEntryEditor(e); }
            else if (p instanceof LiftSense s) { findParentEntry(s).ifPresent(entry -> { switchView(NAV_ENTRIES); selectEntryInTable(entry); populateEntryEditor(entry); }); }
            else if (p instanceof LiftVariant v) { if (v.getParent() != null) { switchView(NAV_ENTRIES); selectEntryInTable(v.getParent()); populateEntryEditor(v.getParent()); } }
        }
        else if (obj instanceof LiftPronunciation p) {
            if (p.getParent() instanceof LiftEntry e) { switchView(NAV_ENTRIES); selectEntryInTable(e); populateEntryEditor(e); }
        }
    }

    private void selectEntryInTable(LiftEntry entry) {
        entryTable.getSelectionModel().select(entry);
    }

    private void populateAnnotationSummaryEditor(LiftAnnotation annotation) {
        setModifyButtonVisible(false);
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put(I18n.get("col.parentType"), describeParentType(annotation.getParent()));
        values.put(I18n.get("col.name"), annotation.getName());
        values.put(I18n.get("col.value"), annotation.getValue().orElse(""));
        values.put(I18n.get("col.who"), annotation.getWho().orElse(""));
        values.put(I18n.get("col.when"), annotation.getWhen().orElse(""));
        populateSummaryEditor(I18n.get("nav.annotations"), "", values);
        addGoToParentButton(annotation.getParent());
    }

    private void populateFieldSummaryEditor(LiftField field) {
        setModifyButtonVisible(false);
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put(I18n.get("col.parentType"), describeParentType(field.getParent()));
        values.put(I18n.get("col.type"), field.getName());
        values.put(I18n.get("col.text"), field.getText().getForms().stream().findFirst().map(Form::toPlainText).orElse(""));
        populateSummaryEditor(I18n.get("nav.fields"), "", values);
        addGoToParentButton(field.getParent());
        Button accessBtn = new Button(I18n.get("btn.accessObjects"));
        accessBtn.getStyleClass().add("example-add-button");
        accessBtn.setMaxWidth(Double.MAX_VALUE);
        accessBtn.setOnAction(e -> showObjectsWithFieldType(field.getName()));
        editorContainer.getChildren().add(accessBtn);
    }

    private void showObjectsWithFieldType(String fieldType) {
        if (currentDictionary == null) return;
        List<Object> matches = currentDictionary.getLiftDictionaryComponents().getAllFields().stream()
            .filter(f -> fieldType.equals(f.getName()))
            .map(LiftField::getParent)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        showFilteredObjectsTable(matches, I18n.get("nav.fields") + ": " + fieldType);
    }

    private void addGoToParentButton(Object parent) {
        if (parent == null) return;
        Button goBtn = new Button(I18n.get("btn.goToParent"));
        goBtn.getStyleClass().add("example-add-button");
        goBtn.setMaxWidth(Double.MAX_VALUE);
        goBtn.setOnAction(e -> navigateToObject(parent));
        editorContainer.getChildren().add(goBtn);
    }

    private void populateCategorySummaryEditor(String title, CategoryRow row) {
        populateCategorySummaryEditor(title, row, null);
    }

    private void populateCategorySummaryEditor(String title, CategoryRow row, String categoryKind) {
        setModifyButtonVisible(false);
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put(I18n.get("col.value"), row.value());
        values.put(I18n.get("col.frequency"), String.valueOf(row.frequency()));
        populateSummaryEditor(title, "", values);
        if (categoryKind != null) {
            Button accessBtn = new Button(I18n.get("btn.accessObjects"));
            accessBtn.getStyleClass().add("example-add-button");
            accessBtn.setMaxWidth(Double.MAX_VALUE);
            final String val = row.value();
            accessBtn.setOnAction(e -> {
                if ("grammatical-info".equals(categoryKind)) showObjectsWithGramInfo(val);
                else if ("translation-type".equals(categoryKind)) showObjectsWithTranslationType(val);
                else if ("relation-type".equals(categoryKind)) showObjectsWithRelationType(val);
            });
            editorContainer.getChildren().add(accessBtn);
        }
    }

    private void showObjectsWithGramInfo(String gramInfoValue) {
        if (currentDictionary == null) return;
        List<Object> matches = currentDictionary.getLiftDictionaryComponents().getAllSenses().stream()
            .filter(s -> s.getGrammaticalInfo().map(g -> gramInfoValue.equals(g.getValue())).orElse(false))
            .collect(Collectors.toList());
        showFilteredObjectsTable(matches, I18n.get("nav.gramInfo") + ": " + gramInfoValue);
    }

    private void showObjectsWithTranslationType(String transType) {
        if (currentDictionary == null) return;
        List<Object> matches = currentDictionary.getLiftDictionaryComponents().getAllExamples().stream()
            .filter(ex -> ex.getTranslations().containsKey(transType))
            .collect(Collectors.toList());
        showFilteredObjectsTable(matches, I18n.get("nav.transTypes") + ": " + transType);
    }

    private void showObjectsWithRelationType(String relationType) {
        if (currentDictionary == null) return;
        List<Object> matches = currentDictionary.getLiftDictionaryComponents().getAllRelations().stream()
            .filter(r -> relationType.equals(r.getType()))
            .collect(Collectors.toList());
        showFilteredObjectsTable(matches, I18n.get("nav.relationTypes") + ": " + relationType);
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
        AbstractNotable parent = note.getParent();
        if (parent != null) {
            if (parent instanceof LiftEntry entry) {
                String entryForm = entry.getForms().getForms().stream().findFirst().map(Form::toPlainText).orElse("");
                if (entryForm.isEmpty()) entryForm = "?";
                Button backBtn = new Button(I18n.get("sense.backToEntry", entryForm));
                backBtn.getStyleClass().addAll("example-add-button", "back-btn");
                backBtn.setOnAction(e -> navigateToEntryFromSense(entry));
                editorContainer.getChildren().add(backBtn);
            } else if (parent instanceof LiftSense sense) {
                Button backBtn = new Button(I18n.get("sense.backToSense", senseDisplayText(sense)));
                backBtn.getStyleClass().addAll("example-add-button", "back-btn");
                backBtn.setOnAction(e -> navigateToSenseFromParent(sense));
                editorContainer.getChildren().add(backBtn);
            }
        }
        NoteEditor ne = new NoteEditor();
        ne.setNote(note, getMetaLanguages());
        editorContainer.getChildren().add(ne);
    }

    private void populateVariantEditor(LiftVariant v) {
        editEntryTitle.setText(I18n.get("nav.variants") + " : " + v.getRefId().orElse("?"));
        editEntryCode.setText("");
        editorContainer.getChildren().clear();
        LiftEntry parentEntry = v.getParent();
        if (parentEntry != null) {
            String entryForm = parentEntry.getForms().getForms().stream().findFirst().map(Form::toPlainText).orElse("");
            if (entryForm.isEmpty()) entryForm = "?";
            Button backBtn = new Button(I18n.get("sense.backToEntry", entryForm));
            backBtn.getStyleClass().addAll("example-add-button", "back-btn");
            backBtn.setOnAction(e -> navigateToEntryFromSense(parentEntry));
            editorContainer.getChildren().add(backBtn);
        }
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
        relationTable.setPlaceholder(new Label(I18n.get("placeholder.noRelation")));
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
        undoManager.clear();
        baseEntries.clear();
        if (dictionary == null) { updateCountLabel(0, 0); return; }
        ensureHeaderComplete();
        rebuildHeaderCfgChildren();
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
        // ← Utilise le chemin par défaut sauvegardé
        String defaultPath = PREFS.get("ui.defaultPath", System.getProperty("user.home"));
        File defaultDir = new File(defaultPath);
        if (defaultDir.exists()) ch.setInitialDirectory(defaultDir);
        ch.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(I18n.get("dialog.liftFilter"), "*.lift"),
                new FileChooser.ExtensionFilter(I18n.get("dialog.allFilter"), "*.*")
        );
        File f = ch.showOpenDialog(navTree.getScene().getWindow());
        if (f == null) return;
        try {
            setDictionary(dictionaryService.loadFromFile(f));
            switchView(NAV_ENTRIES);
            saveRecentFile(f);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Ouverture du fichier LIFT", e);
            showError(I18n.get("error.open"), I18n.formatErrorMessage("error.open.detail", e));
        }
    }
    @FXML private void onSave() {
        if (currentDictionary == null) { showError(I18n.get("error.save"), I18n.get("error.noDictionary")); return; }
        try { currentDictionary.save(); } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Sauvegarde du dictionnaire", e);
            showError(I18n.get("error.save"), I18n.formatErrorMessage("error.save.detail", e));
        }
    }

    @FXML private void onNewDictionary() { setDictionary(null); switchView(NAV_ENTRIES); }

    @FXML private void onSaveAs() {
        if (currentDictionary == null) { showError(I18n.get("error.saveAs"), I18n.get("error.noDictionaryShort")); return; }
        FileChooser ch = new FileChooser(); ch.setTitle(I18n.get("dialog.saveLift"));
        ch.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.get("dialog.liftFilter"), "*.lift"));
        File f = ch.showSaveDialog(navTree.getScene().getWindow());
        if (f != null) {
            try { currentDictionary.save(f); }
            catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Sauvegarde du dictionnaire sous un autre fichier", e);
                showError(I18n.get("error.saveAs"), I18n.formatErrorMessage("error.saveAs.detail", e));
            }
        }
    }

    @FXML private void onPreferences() { showPreferencesDialog(); }
    @FXML private void onQuit() { Platform.exit(); }

    @FXML private void onCopy() {
        javafx.scene.Node focused = menuBar.getScene().getFocusOwner();
        if (focused instanceof TextField tf) {
            tf.copy();
        } else {
            copySelectedToClipboard();
        }
    }
    @FXML private void onPaste() {
        javafx.scene.Node focused = menuBar.getScene().getFocusOwner();
        if (focused instanceof TextField tf) {
            tf.paste();
        } else {
            // Colle depuis le presse-papiers comme nouvelle entrée
            String text = Clipboard.getSystemClipboard().getString();
            if (text == null || text.isBlank()) return;
            LiftFactory factory = getFactory(currentDictionary);
            if (factory == null) return;
            // Crée une entrée avec le texte collé comme forme
            org.xml.sax.helpers.AttributesImpl attrs = new org.xml.sax.helpers.AttributesImpl();
            attrs.addAttribute("", "id", "id", "CDATA", UUID.randomUUID().toString());
            LiftEntry entry = factory.createEntry(attrs);
            List<String> objLangs = getObjectLanguages();
            if (!objLangs.isEmpty()) entry.getForms().add(new Form(objLangs.get(0), text.trim()));
            baseEntries.add(entry);
            switchView(NAV_ENTRIES);
            entryTable.getSelectionModel().select(entry);
            entryTable.scrollTo(entry);
        }
    }

    @FXML private void onCut() {
        javafx.scene.Node focused = menuBar.getScene().getFocusOwner();
        if (focused instanceof TextField tf) {
            tf.cut();
        } else {
            copySelectedToClipboard();
        }
    }
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
    @FXML private void onViewTransTypes() { switchView(NAV_TRANS_TYPES); }
    @FXML private void onViewNoteTypes() { switchView(NAV_NOTE_TYPES); }
    @FXML private void onViewRelationTypes() { switchView(NAV_RELATION_TYPES); }

    /* ─── Configuration menu ─── */
    @FXML private void onConfigNoteTypes() {
        showConfigDialog(I18n.get("menu.config.noteTypes"),
            () -> currentDictionary == null ? List.of() : currentDictionary.getLiftDictionaryComponents().getAllNotes().stream().map(n -> n.getType().orElse("?")).distinct().sorted().toList(),
            val -> currentDictionary == null ? 0L : currentDictionary.getLiftDictionaryComponents().getAllNotes().stream().filter(n -> val.equals(n.getType().orElse(""))).count());
    }
    @FXML private void onConfigTranslationTypes() {
        showConfigDialog(I18n.get("menu.config.translationTypes"),
            () -> currentDictionary == null ? List.of() : currentDictionary.getLiftDictionaryComponents().getAllExamples().stream().flatMap(ex -> ex.getTranslations().keySet().stream()).distinct().sorted().toList(),
            val -> currentDictionary == null ? 0L : currentDictionary.getLiftDictionaryComponents().getAllExamples().stream().filter(ex -> ex.getTranslations().containsKey(val)).count());
    }
    @FXML private void onConfigLanguages() {
        showConfigDialog(I18n.get("menu.config.languages"), this::getAllLanguages,
            val -> currentDictionary == null ? 0L : currentDictionary.getLiftDictionaryComponents().getAllEntries().stream().filter(e -> e.getForms().getForm(val).isPresent()).count());
    }
    @FXML private void onConfigTraitTypes() {
        showConfigDialog(I18n.get("menu.config.traitTypes"),
            () -> currentDictionary == null ? List.of() : currentDictionary.getTraitName().stream().sorted().toList(),
            val -> currentDictionary == null ? 0L : currentDictionary.getLiftDictionaryComponents().getAllTraits().stream().filter(t -> val.equals(t.getName())).count());
    }
    @FXML private void onConfigAnnotationTypes() {
        showConfigDialog(I18n.get("menu.config.annotationTypes"),
            () -> currentDictionary == null ? List.of() : currentDictionary.getLiftDictionaryComponents().getAllAnnotations().stream().map(LiftAnnotation::getName).distinct().sorted().toList(),
            val -> currentDictionary == null ? 0L : currentDictionary.getLiftDictionaryComponents().getAllAnnotations().stream().filter(a -> val.equals(a.getName())).count());
    }
    @FXML private void onConfigFieldTypes() {
        showConfigDialog(I18n.get("menu.config.fieldTypes"),
            () -> currentDictionary == null ? List.of() : currentDictionary.getFieldType().stream().sorted().toList(),
            val -> currentDictionary == null ? 0L : currentDictionary.getLiftDictionaryComponents().getAllFields().stream().filter(f -> val.equals(f.getName())).count());
    }

    private void onConfigRelationTypes() {
        showConfigDialog(I18n.get("nav.cfgManageRelationTypes"),
            () -> currentDictionary == null ? List.of() : currentDictionary.getLiftDictionaryComponents().getAllRelations().stream().map(LiftRelation::getType).distinct().sorted().toList(),
            val -> currentDictionary == null ? 0L : currentDictionary.getLiftDictionaryComponents().getAllRelations().stream().filter(r -> val.equals(r.getType())).count());
    }

    /** Dialog for managing languages, with object-languages and meta-languages in separate sections.
     *  Supports add/delete. Deleted languages are removed from all multitexts and no longer appear in dropdowns. */
    private void openManageLanguagesDialog() {
        if (currentDictionary == null) return;
        var ldc = currentDictionary.getLiftDictionaryComponents();
        List<String> objLangs = new ArrayList<>(getObjectLanguages());
        List<String> metaLangs = new ArrayList<>(getMetaLanguages());

        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(I18n.get("config.title", I18n.get("nav.cfgManageLangs")));
        dlg.setResizable(true);
        dlg.getDialogPane().setPrefSize(520, 480);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);

        TitledPane objPane = new TitledPane(I18n.get("nav.objectLangs"),
            buildEditableLanguagePanel(objLangs, true, ldc.getAllObjectLanguagesMultiText()));
        objPane.setExpanded(true);
        TitledPane metaPane = new TitledPane(I18n.get("nav.metaLangs"),
            buildEditableLanguagePanel(metaLangs, false, ldc.getAllMetaLanguagesMultiText()));
        metaPane.setExpanded(true);

        VBox content = new VBox(10, objPane, metaPane);
        content.setPadding(new Insets(12));
        dlg.getDialogPane().setContent(content);
        dlg.showAndWait();
    }

    /** Builds an editable panel for a language list (object or meta) with add/delete. */
    private VBox buildEditableLanguagePanel(List<String> langs, boolean isObject, List<MultiText> multiTexts) {
        TableView<String> table = new TableView<>(FXCollections.observableArrayList(langs));
        table.setPrefHeight(140);
        TableColumn<String, String> langCol = new TableColumn<>(I18n.get("col.code"));
        langCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue()));
        langCol.setPrefWidth(120);
        TableColumn<String, String> usageCol = new TableColumn<>(I18n.get("cfg.usageCount"));
        usageCol.setCellValueFactory(cd -> {
            long n = countLanguageUsage(cd.getValue(), multiTexts);
            return new ReadOnlyStringWrapper(String.valueOf(n));
        });
        usageCol.setPrefWidth(80);
        table.getColumns().addAll(langCol, usageCol);

        TextField addField = new TextField();
        addField.setPromptText(I18n.get("config.addAbbr"));
        Button addBtn = new Button(I18n.get("btn.add"));
        addBtn.setOnAction(e -> {
            String code = addField.getText().trim();
            if (code.isEmpty()) return;
            if (langs.contains(code)) return;
            if (addLanguageToDictionary(code, isObject)) {
                langs.add(code);
                langs.sort(Comparator.naturalOrder());
                table.setItems(FXCollections.observableArrayList(langs));
                addField.clear();
            }
        });
        Button removeBtn = new Button(I18n.get("btn.delete"));
        removeBtn.setOnAction(e -> {
            String sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            long usage = countLanguageUsage(sel, multiTexts);
            if (usage > 0) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle(I18n.get("btn.delete"));
                confirm.setHeaderText(I18n.get("config.deleteWarning", sel, usage));
                confirm.setContentText(I18n.get("config.deleteConfirm"));
                if (confirm.showAndWait().filter(r -> r == ButtonType.OK).isEmpty()) return;
            }
            removeLanguageFromMultiTexts(sel, multiTexts);
            langs.remove(sel);
            table.setItems(FXCollections.observableArrayList(langs));
        });

        HBox controls = new HBox(8, addField, addBtn, removeBtn);
        controls.setPadding(new Insets(6, 0, 0, 0));
        HBox.setHgrow(addField, Priority.ALWAYS);
        return new VBox(6, table, controls);
    }

    private long countLanguageUsage(String lang, List<MultiText> multiTexts) {
        if (lang == null || lang.isBlank() || multiTexts == null) return 0;
        return multiTexts.stream().filter(mt -> mt.getForm(lang).isPresent()).count();
    }

    private void removeLanguageFromMultiTexts(String lang, List<MultiText> multiTexts) {
        if (lang == null || lang.isBlank() || multiTexts == null) return;
        for (MultiText mt : multiTexts) {
            if (mt.getForm(lang).isPresent() && !mt.isEmpty()) {
                try { mt.removeForm(lang); } catch (Exception ignored) {}
            }
        }
    }

    /** Adds a language by inserting an empty form in the first available multitext of the appropriate type. */
    private boolean addLanguageToDictionary(String lang, boolean isObject) {
        if (currentDictionary == null || lang == null || lang.isBlank()) return false;
        var ldc = currentDictionary.getLiftDictionaryComponents();
        List<MultiText> targets = isObject ? ldc.getAllObjectLanguagesMultiText() : ldc.getAllMetaLanguagesMultiText();
        for (MultiText mt : targets) {
            if (!mt.getForm(lang).isPresent()) {
                try {
                    mt.add(new Form(lang, ""));
                    return true;
                } catch (Exception ignored) {}
            }
        }
        return false;
    }

    private void showAddEtymologyDialog(LiftEntry entry, LiftFactory factory) {
        Dialog<Pair<String, String>> dlg = new Dialog<>();
        dlg.setTitle(I18n.get("btn.addEtymology"));
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField sourceField = new TextField();
        sourceField.setPromptText(I18n.get("col.source"));
        List<String> knownTypes = currentDictionary == null ? List.of() : currentDictionary.getLiftDictionaryComponents().getAllEntries().stream()
            .flatMap(e -> e.getEtymologies().stream()).map(LiftEtymology::getType).filter(Objects::nonNull).distinct().sorted().toList();
        ComboBox<String> typeCombo = new ComboBox<>(FXCollections.observableArrayList(knownTypes));
        typeCombo.setEditable(true);
        typeCombo.setPromptText(I18n.get("col.type"));
        GridPane grid = new GridPane();
        grid.setHgap(8); grid.setVgap(8);
        grid.add(new Label(I18n.get("col.type")), 0, 0);
        grid.add(typeCombo, 1, 0);
        grid.add(new Label(I18n.get("col.source")), 0, 1);
        grid.add(sourceField, 1, 1);
        dlg.getDialogPane().setContent(grid);
        dlg.setResultConverter(btn -> btn == ButtonType.OK ? new Pair<>(typeCombo.getValue() != null ? typeCombo.getValue() : typeCombo.getEditor().getText(), sourceField.getText()) : null);
        dlg.showAndWait().ifPresent(pair -> {
            String type = pair.getKey() != null ? pair.getKey().trim() : "";
            String source = pair.getValue() != null ? pair.getValue().trim() : "";
            if (type.isEmpty()) type = "unknown";
            org.xml.sax.helpers.AttributesImpl attrs = new org.xml.sax.helpers.AttributesImpl();
            attrs.addAttribute("", "type", "type", "CDATA", type);
            attrs.addAttribute("", "source", "source", "CDATA", source);
            factory.createEtymology(attrs, entry);
            populateEntryEditor(entry);
        });
    }

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
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Export CSV", e);
            showError(I18n.get("error.export"), I18n.formatErrorMessage("error.export.detail", e));
        }
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
        showCategoryTable(I18n.get("nav.gramInfo"), I18n.get("col.value"), counts, "grammatical-info");
    }

    /* ════════════════════ TRANSLATION TYPES VIEW ════════════════════ */

    private void showTranslationTypesView() {
        if (currentDictionary == null) { tableContainer.getChildren().setAll(new Label(I18n.get("placeholder.noDictionary"))); return; }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LiftExample ex : currentDictionary.getLiftDictionaryComponents().getAllExamples()) {
            for (String type : ex.getTranslations().keySet()) counts.merge(type, 1L, Long::sum);
        }
        showCategoryTable(I18n.get("nav.transTypes"), I18n.get("col.type"), counts, "translation-type");
    }

    /* ════════════════════ NOTE TYPES VIEW ════════════════════ */

    private record NoteTypeRow(String type, String parentType, long frequency) {}

    private void showNoteTypesView() {
        if (currentDictionary == null) { tableContainer.getChildren().setAll(new Label(I18n.get("placeholder.noDictionary"))); return; }
        Map<String, NoteTypeRow> counts = new LinkedHashMap<>();
        for (LiftNote n : currentDictionary.getLiftDictionaryComponents().getAllNotes()) {
            String type = n.getType().orElse(I18n.get("placeholder.noType"));
            String pt = describeParentType(n.getParent());
            String key = type + "|" + pt;
            counts.compute(key, (k, row) -> {
                if (row == null) return new NoteTypeRow(type, pt, 1);
                return new NoteTypeRow(type, pt, row.frequency + 1);
            });
        }
        TableView<NoteTypeRow> table = new TableView<>();
        table.getColumns().addAll(
            col(I18n.get("col.type"), NoteTypeRow::type),
            col(I18n.get("col.parentType"), NoteTypeRow::parentType),
            col(I18n.get("col.frequency"), r -> String.valueOf(r.frequency()))
        );
        counts.values().stream().sorted(Comparator.comparingLong(NoteTypeRow::frequency).reversed())
            .forEach(table.getItems()::add);
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) populateNoteTypeSummaryEditor(n);
        });
        VBox wrapper = wrapTableWithFilters(table, (f, t) -> updateCountLabel(f, t), searchField != null ? searchField.textProperty() : null);
        tableContainer.getChildren().setAll(wrapper);
        updateCountLabel(table.getItems().size(), table.getItems().size());
    }

    /* ════════════════════ RELATION TYPES VIEW ════════════════════ */

    private void showRelationTypesView() {
        if (currentDictionary == null) { tableContainer.getChildren().setAll(new Label(I18n.get("placeholder.noDictionary"))); return; }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LiftRelation r : currentDictionary.getLiftDictionaryComponents().getAllRelations()) {
            counts.merge(r.getType() != null ? r.getType() : I18n.get("placeholder.noType"), 1L, Long::sum);
        }
        showCategoryTable(I18n.get("nav.relationTypes"), I18n.get("col.type"), counts, "relation-type");
    }

    /** Shared helper: show a simple value + frequency table for category views. */
    private record CategoryRow(String value, long frequency) {}

    private void showCategoryTable(String title, String colLabel, Map<String, Long> counts) {
        showCategoryTable(title, colLabel, counts, null);
    }

    private void showCategoryTable(String title, String colLabel, Map<String, Long> counts, String categoryKind) {
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
            if (n != null) populateCategorySummaryEditor(title, n, categoryKind);
        });
        VBox wrapper = wrapTableWithFilters(table);
        tableContainer.getChildren().setAll(wrapper);
        updateCountLabel(table.getItems().size(), table.getItems().size());
    }

    /* ════════════════════ COLUMN FILTERS (5.8) ════════════════════ */

    /**
     * Wraps a TableView in a VBox with a row of TextFields below the headers.
     * Each TextField filters its column; only rows matching ALL column filters are shown.
     * If searchTextProperty is non-null, the global search bar also filters rows (any visible column).
     */
    @SuppressWarnings("unchecked")
    private static <T> VBox wrapTableWithFilters(TableView<T> table, java.util.function.BiConsumer<Integer,Integer> onCountChanged,
            javafx.beans.property.StringProperty searchTextProperty) {
        ObservableList<T> sourceItems = FXCollections.observableArrayList(table.getItems());
        FilteredList<T> filtered = new FilteredList<>(sourceItems, t -> true);
        table.setItems(filtered);

        List<TableColumn<T, ?>> leaves = collectLeafColumns(table);
        List<javafx.scene.Node> filterInputs = new ArrayList<>();
        List<Boolean> textFilterColumns = new ArrayList<>();
        String clearOption = I18n.get("filter.clear");
        AtomicBoolean internalUpdate = new AtomicBoolean(false);

        GridPane filterRow = new GridPane();
        filterRow.setHgap(0);
        filterRow.setPadding(new Insets(4, 0, 4, 0));
        filterRow.setStyle("-fx-background-color: #eef2f3;");
        filterRow.setMinWidth(0);

        java.util.function.Supplier<String> searchTextSupplier = () ->
            searchTextProperty != null ? Optional.ofNullable(searchTextProperty.get()).orElse("").trim().toLowerCase(Locale.ROOT) : "";

        Runnable refreshPredicate = () -> {
            String q = searchTextSupplier.get();
            filtered.setPredicate(row ->
                    rowMatchesAllFilters(row, leaves, filterInputs, textFilterColumns, clearOption, -1)
                    && rowMatchesSearch(row, leaves, q)
            );
            if (onCountChanged != null)
                javafx.application.Platform.runLater(() -> onCountChanged.accept(filtered.size(), sourceItems.size()));
        };

        Runnable refreshFacetChoices = () -> {
            if (internalUpdate.get()) return;
            internalUpdate.set(true);
            try {
                String q = searchTextSupplier.get();
                for (int i = 0; i < leaves.size(); i++) {
                    if (textFilterColumns.get(i)) continue;
                    ComboBox<String> combo = (ComboBox<String>) filterInputs.get(i);
                    String currentValue = combo.getValue();
                    final int colIndex = i;

                    List<String> values = sourceItems.stream()
                        .filter(row -> rowMatchesAllFilters(row, leaves, filterInputs, textFilterColumns, clearOption, colIndex)
                                && rowMatchesSearch(row, leaves, q))
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

        if (searchTextProperty != null) {
            searchTextProperty.addListener((obs, o, n) -> {
                refreshPredicate.run();
                refreshFacetChoices.run();
            });
        }

        Button clearBtn = new Button(I18n.get("filter.resetAll"));
        clearBtn.setOnAction(e -> {
            if (searchTextProperty != null && !searchTextProperty.get().isBlank()) {
                searchTextProperty.set("");
            }
            internalUpdate.set(true);
            try {
                for (int i = 0; i < filterInputs.size(); i++) {
                    if (textFilterColumns.get(i)) ((TextField) filterInputs.get(i)).clear();
                    else ((ComboBox<String>) filterInputs.get(i)).setValue(clearOption);
                }
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

        for (int i = 0; i < leaves.size(); i++) {
            TableColumn<T, ?> column = leaves.get(i);
            column.setMinWidth(85);
            boolean forceText = FILTER_MODE_TEXT.equals(column.getProperties().get("filterMode"));
            final int colIdx = i;
            long distinct = sourceItems.stream()
                .map(row -> cellText(row, leaves.get(colIdx)))
                .filter(s -> s != null && !s.isBlank())
                .distinct().count();
            boolean hasRepeatedValues = distinct < sourceItems.size() && distinct > 0;
            boolean textFilter = forceText || !hasRepeatedValues;
            textFilterColumns.add(textFilter);

            ColumnConstraints cc = new ColumnConstraints();
            cc.prefWidthProperty().bind(column.widthProperty());
            filterRow.getColumnConstraints().add(cc);

            if (textFilter) {
                TextField tf = new TextField();
                tf.setPromptText(I18n.get("filter.prompt"));
                tf.setMaxWidth(Double.MAX_VALUE);
                tf.setMinWidth(0);
                tf.setMinHeight(26);
                tf.setPrefHeight(26);
                tf.setStyle("-fx-font-size: 11px; -fx-padding: 4 6 4 6;");
                filterInputs.add(tf);
                GridPane.setHgrow(tf, Priority.ALWAYS);
                filterRow.add(tf, i, 0);
                tf.textProperty().addListener((obs, o, n) -> {
                    if (internalUpdate.get()) return;
                    refreshPredicate.run();
                    refreshFacetChoices.run();
                });
                continue;
            }

            ComboBox<String> cb = new ComboBox<>();
            cb.getStyleClass().add("filter-combo");
            cb.setPromptText(I18n.get("filter.prompt"));
            cb.setEditable(false);
            cb.setMaxWidth(Double.MAX_VALUE);
            cb.setMinWidth(0);
            cb.setMinHeight(26);
            cb.setPrefHeight(26);
            cb.setStyle("-fx-font-size: 11px;");
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
            filterInputs.add(cb);
            GridPane.setHgrow(cb, Priority.ALWAYS);
            filterRow.add(cb, i, 0);

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
            for (int i = 0; i < filterInputs.size(); i++) {
                if (textFilterColumns.get(i)) ((TextField) filterInputs.get(i)).clear();
                else ((ComboBox<String>) filterInputs.get(i)).setValue(clearOption);
            }
        } finally {
            internalUpdate.set(false);
        }
        refreshPredicate.run();
        refreshFacetChoices.run();

        // Aligner la largeur des filtres sur la zone des colonnes (prend en compte la scrollbar verticale)
        filterRow.maxWidthProperty().bind(Bindings.createDoubleBinding(
            () -> leaves.stream().mapToDouble(c -> c.getWidth()).sum(),
            leaves.stream().<javafx.beans.Observable>map(TableColumn::widthProperty).toArray(javafx.beans.Observable[]::new)
        ));

        VBox wrapper = new VBox(header, filterRow, table);
        wrapper.setMinWidth(0);
        filterRow.setMinWidth(0);
        table.setMinWidth(0);
        VBox.setVgrow(table, Priority.ALWAYS);
        return wrapper;
    }
    private static <T> VBox wrapTableWithFilters(TableView<T> table) {
        return wrapTableWithFilters(table, null, null);
    }
    private static <T> VBox wrapTableWithFilters(TableView<T> table, java.util.function.BiConsumer<Integer,Integer> onCountChanged) {
        return wrapTableWithFilters(table, onCountChanged, null);
    }
    private static <T> boolean rowMatchesSearch(T row, List<TableColumn<T, ?>> leaves, String searchText) {
        if (searchText == null || searchText.isEmpty()) return true;
        StringBuilder sb = new StringBuilder();
        for (TableColumn<T, ?> col : leaves) {
            String ct = cellText(row, col);
            if (ct != null) sb.append(" ").append(ct);
        }
        return sb.toString().toLowerCase(Locale.ROOT).contains(searchText);
    }

    private static <T> boolean rowMatchesAllFilters(
        T row,
        List<TableColumn<T, ?>> leaves,
        List<javafx.scene.Node> filterInputs,
        List<Boolean> textFilterColumns,
        String clearOption,
        int ignoredColumn
    ) {
        for (int i = 0; i < filterInputs.size(); i++) {
            if (i == ignoredColumn) continue;
            String cellValue = cellText(row, leaves.get(i));
            if (textFilterColumns.get(i)) {
                String query = ((TextField) filterInputs.get(i)).getText();
                if (query == null || query.isBlank()) continue;
                if (!cellValue.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT))) return false;
                continue;
            }
            String selected = ((ComboBox<String>) filterInputs.get(i)).getValue();
            if (selected == null || selected.isBlank() || clearOption.equals(selected)) continue;
            if (!selected.equals(cellValue)) return false;
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

        List<String> metaLangs = getMetaLanguages();

        // ── Top: Range properties (description, label, abbrev) ─────────────
        TitledPane rangePropsPane = new TitledPane(I18n.get("cfg.rangeProperties"), buildRangePropertiesEditor(range, metaLangs));
        rangePropsPane.setExpanded(true);
        rangePropsPane.setAnimated(false);

        // ── Middle: editable tree of range-elements ─────────────────────────
        TreeView<LiftHeaderRangeElement> tree = buildRangeElementTree(range);
        tree.setShowRoot(false);
        tree.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null && n.getValue() != null) populateRangeElementEditor(range, n.getValue());
        });

        // Add controls
        TextField newIdField = new TextField();
        newIdField.setPromptText(I18n.get("cfg.newElementLabel"));

        ComboBox<String> parentCombo = new ComboBox<>();
        parentCombo.setPromptText(I18n.get("cfg.parentElement"));
        parentCombo.getItems().add("");
        range.getRangeElements().stream().map(LiftHeaderRangeElement::getId).forEach(parentCombo.getItems()::add);

        Button addBtn = new Button(I18n.get("cfg.addElement"));
        addBtn.setOnAction(e -> {
            String newId = newIdField.getText().trim();
            if (!newId.isEmpty() && range.getRangeElements().stream().noneMatch(re -> re.getId().equals(newId))) {
                LiftHeaderRangeElement newElem = factory.createRangeElement(newId, range);
                String parentSel = parentCombo.getValue();
                if (parentSel != null && !parentSel.isBlank()) newElem.setParentId(parentSel);
                if (!metaLangs.isEmpty()) newElem.getDescription().add(new Form(metaLangs.get(0), I18n.get("cfg.autoAdded")));
                newIdField.clear();
                showHeaderRangeView(rangeId);
            }
        });

        Button deleteBtn = new Button(I18n.get("btn.delete"));
        deleteBtn.setOnAction(e -> {
            TreeItem<LiftHeaderRangeElement> selItem = tree.getSelectionModel().getSelectedItem();
            if (selItem == null || selItem.getValue() == null) return;
            LiftHeaderRangeElement sel = selItem.getValue();
            long usage = countRangeElementUsage(rangeId, sel.getId());
            if (usage > 0) showError(I18n.get("btn.delete"), I18n.get("cfg.deleteNotAllowed", usage));
            else { range.getRangeElements().remove(sel); showHeaderRangeView(rangeId); }
        });

        Button renameBtn = new Button(I18n.get("cfg.rename"));
        renameBtn.setOnAction(e -> {
            TreeItem<LiftHeaderRangeElement> selItem = tree.getSelectionModel().getSelectedItem();
            if (selItem == null || selItem.getValue() == null) return;
            TextInputDialog dlg = new TextInputDialog(selItem.getValue().getId());
            dlg.setTitle(I18n.get("cfg.rename"));
            dlg.setHeaderText(I18n.get("cfg.renamePrompt", selItem.getValue().getId()));
            dlg.showAndWait().ifPresent(newName -> { if (!newName.isBlank()) { renameRangeElementInData(rangeId, selItem.getValue().getId(), newName); showHeaderRangeView(rangeId); } });
        });

        Label countLbl = new Label(range.getRangeElements().size() + " " + I18n.get("cfg.elements"));
        countLbl.setStyle("-fx-text-fill: #66767a; -fx-font-size: 12px;");

        HBox addRow = new HBox(8, newIdField, parentCombo, addBtn);
        HBox.setHgrow(newIdField, Priority.ALWAYS);
        HBox actionRow = new HBox(8, deleteBtn, renameBtn, new HBox(), countLbl);
        HBox.setHgrow(actionRow.getChildren().get(2), Priority.ALWAYS);

        Label elemTitle = new Label(I18n.get("cfg.rangeElements2"));
        elemTitle.setStyle("-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:#4c6f76;");

        VBox.setVgrow(tree, Priority.ALWAYS);
        VBox centerBox = new VBox(8, rangePropsPane, elemTitle, tree, addRow, actionRow);
        centerBox.setPadding(new Insets(8));
        VBox.setVgrow(centerBox.getChildren().get(2), Priority.ALWAYS);
        tableContainer.getChildren().setAll(centerBox);
        updateCountLabel(range.getRangeElements().size(), range.getRangeElements().size());
    }

    /** Build a small GridPane of MultiTextEditors for description / label / abbrev of a range. */
    private VBox buildRangePropertiesEditor(LiftHeaderRange range, List<String> metaLangs) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(6));
        addMultiTextRow(box, I18n.get("cfg.description"), range.getDescription(), metaLangs);
        addMultiTextRow(box, I18n.get("cfg.label"),       range.getLabel(),       metaLangs);
        addMultiTextRow(box, I18n.get("cfg.abbrev"),      range.getAbbrev(),      metaLangs);
        return box;
    }

    private void addMultiTextRow(VBox box, String lbl, MultiText mt, List<String> langs) {
        Label l = new Label(lbl);
        l.setStyle("-fx-font-weight:bold; -fx-font-size:12px;");
        MultiTextEditor ed = new MultiTextEditor();
        ed.setAvailableLanguages(langs);
        ed.setMultiText(mt);
        box.getChildren().addAll(l, ed);
    }

    /** Show description editor for the LiftHeader itself. */
    private void showHeaderDescView() {
        if (currentDictionary == null) { tableContainer.getChildren().setAll(new Label(I18n.get("cfg.noHeader"))); return; }
        LiftHeader header = currentDictionary.getLiftDictionaryComponents().getHeader();
        if (header == null) { tableContainer.getChildren().setAll(new Label(I18n.get("cfg.noHeader"))); return; }
        List<String> metaLangs = getMetaLanguages();
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        Label title = new Label(I18n.get("nav.cfgDesc"));
        title.setStyle("-fx-font-size:15px; -fx-font-weight:bold; -fx-text-fill:#4c6f76;");
        box.getChildren().add(title);
        addMultiTextRow(box, I18n.get("cfg.description"), header.getDescription(), metaLangs);
        tableContainer.getChildren().setAll(box);
        editorContainer.getChildren().clear();
        editEntryTitle.setText(I18n.get("nav.cfgDesc"));
        editEntryCode.setText("");
    }

    /** Build a TreeView of LiftHeaderRangeElements respecting the @parent hierarchy. */
    private TreeView<LiftHeaderRangeElement> buildRangeElementTree(LiftHeaderRange range) {
        TreeItem<LiftHeaderRangeElement> root = new TreeItem<>(null);
        root.setExpanded(true);
        Map<String, TreeItem<LiftHeaderRangeElement>> itemMap = new java.util.LinkedHashMap<>();

        // First pass: create all items
        for (LiftHeaderRangeElement re : range.getRangeElements()) {
            TreeItem<LiftHeaderRangeElement> item = new TreeItem<>(re);
            item.setExpanded(true);
            itemMap.put(re.getId(), item);
        }
        // Second pass: wire parent-child relationships
        for (LiftHeaderRangeElement re : range.getRangeElements()) {
            TreeItem<LiftHeaderRangeElement> item = itemMap.get(re.getId());
            String pid = re.getParentId().orElse(null);
            if (pid != null && itemMap.containsKey(pid)) {
                itemMap.get(pid).getChildren().add(item);
            } else {
                root.getChildren().add(item);
            }
        }

        TreeView<LiftHeaderRangeElement> tree = new TreeView<>(root);
        tree.setCellFactory(tv -> new TreeCell<>() {
            @Override protected void updateItem(LiftHeaderRangeElement item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); }
                else {
                    String abbrev = item.getAbbrev().getForms().stream().findFirst().map(Form::toPlainText).orElse("");
                    String label  = item.getLabel().getForms().stream().findFirst().map(Form::toPlainText).orElse("");
                    String display = item.getId();
                    if (!abbrev.isBlank()) display += "  [" + abbrev + "]";
                    if (!label.isBlank())  display += "  – " + label;
                    setText(display);
                }
            }
        });
        return tree;
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

        TableView<LiftFieldAndTraitDefinition> table = new TableView<>();
        table.getColumns().addAll(
            col(I18n.get("cfg.fieldDefName"), LiftFieldAndTraitDefinition::getName),
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
            LiftFieldAndTraitDefinition sel = table.getSelectionModel().getSelectedItem();
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

    private void showNewFieldDefDialog(LiftHeader header, LiftFactory factory, TableView<LiftFieldAndTraitDefinition> table) {
        Dialog<LiftFieldAndTraitDefinition> dlg = new Dialog<>();
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
            LiftFieldAndTraitDefinition fd = factory.createFieldDefinition(name, header);
            String typeVal = typeCombo.getValue();
            if (typeVal != null && !typeVal.isBlank()) fd.setType(Optional.of(typeVal));
            List<String> metaLangs = getMetaLanguages();
            if (!metaLangs.isEmpty()) fd.getDescription().add(new Form(metaLangs.get(0), I18n.get("cfg.autoAdded")));
            return fd;
        });

        dlg.showAndWait().ifPresent(fd -> table.getItems().add(fd));
    }

    private void populateRangeElementEditor(LiftHeaderRange range, LiftHeaderRangeElement elem) {
        // No id/guid shown to the user — only human-readable MultiText fields
        String firstLabel = elem.getLabel().getForms().stream().findFirst().map(Form::toPlainText).orElse("");
        String firstAbbrev = elem.getAbbrev().getForms().stream().findFirst().map(Form::toPlainText).orElse("");
        editEntryTitle.setText(firstLabel.isBlank() ? firstAbbrev.isBlank() ? I18n.get("cfg.rangeElement") : firstAbbrev : firstLabel);
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
        }, false);
    }

    private void populateFieldDefEditor(LiftFieldAndTraitDefinition fd) {
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

    private long countFieldOrTraitUsage(LiftFieldAndTraitDefinition fd) {
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

        Set<String> definedFieldDefs = header.getFields().stream().map(LiftFieldAndTraitDefinition::getName).collect(Collectors.toSet());

        Set<String> fieldNames = comps.getAllFields().stream().map(LiftField::getName).collect(Collectors.toSet());
        for (String fn : fieldNames) {
            if (!definedFieldDefs.contains(fn)) {
                LiftFieldAndTraitDefinition fd = factory.createFieldDefinition(fn, header);
                fd.setType(Optional.of("multitext"));
                fd.getDescription().add(new Form(descLang, autoDesc));
                definedFieldDefs.add(fn);
            }
        }

        Set<String> traitNames = comps.getAllTraits().stream().map(LiftTrait::getName).collect(Collectors.toSet());
        for (String tn : traitNames) {
            if (!definedFieldDefs.contains(tn)) {
                LiftFieldAndTraitDefinition fd = factory.createFieldDefinition(tn, header);
                fd.setType(Optional.of("option"));
                fd.getDescription().add(new Form(descLang, autoDesc));
            }
        }
    }

    private static String fieldDefKindLabel(LiftFieldAndTraitDefinition fd) {
        if (fd == null) return "";
        if (fd.isFieldDefinition()) return I18n.get("cfg.kindField");
        if (fd.isTraitDefinition()) return I18n.get("cfg.kindTrait");
        return I18n.get("cfg.kindUnknown");
    }

    /** Find the LiftFieldAndTraitDefinition for a given trait/field name in the current dictionary header. */
    private Optional<LiftFieldAndTraitDefinition> findFieldDef(String name) {
        if (currentDictionary == null || name == null) return Optional.empty();
        LiftHeader header = currentDictionary.getLiftDictionaryComponents().getHeader();
        if (header == null) return Optional.empty();
        return header.getFields().stream().filter(fd -> name.equals(fd.getName())).findFirst();
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

    private void updateCountLabel(int shown, int total) {
        if (tableCountLabel == null) return;
        if (shown == total) {
            tableCountLabel.setText(I18n.get("table.count.total", shown));
            return;
        }
        tableCountLabel.setText(I18n.get("table.count.filtered", shown, total));
    }

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
    private static String getTraitValueFor(LiftVariant v, String name) {
        return v == null || v.getTraits() == null ? "" : v.getTraits().stream().filter(t -> name.equals(t.getName())).findFirst().map(LiftTrait::getValue).orElse("");
    }
    private MultiText getParentEntryForms(LiftRelation r) {
        if (r == null || r.getParent() == null) return null;
        AbstractExtensibleWithoutField p = r.getParent();
        if (p instanceof LiftEntry e) return e.getForms();
        if (p instanceof LiftSense s) return findParentEntry(s).map(LiftEntry::getForms).orElse(null);
        if (p instanceof LiftVariant v) return v.getParent() != null ? v.getParent().getForms() : null;
        return null;
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
        if (parent instanceof LiftExample) return I18n.get("nav.examples");
        if (parent instanceof LiftNote) return I18n.get("nav.notes");
        if (parent instanceof LiftVariant) return I18n.get("nav.variants");
        if (parent instanceof LiftEtymology) return I18n.get("nav.etymologies");
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
        return getKnownTraitNamesFor(null);
    }

    /**
     * Returns trait names allowed for the given target element type.
     * If {@code target} is null, returns all trait names.
     * Filters via field-definition/@class: only include a trait name if its
     * LiftFieldAndTraitDefinition has no @class restriction, or if it includes {@code target}.
     */
    private List<String> getKnownTraitNamesFor(FieldDefinitionTarget target) {
        if (currentDictionary == null) return List.of();
        LiftHeader h = currentDictionary.getLiftDictionaryComponents().getHeader();
        if (h != null && !h.getFields().isEmpty()) {
            return h.getFields().stream()
                .filter(fd -> fd.getKind() == FieldDefinitionKind.TRAIT || fd.getKind() == FieldDefinitionKind.UNKNOWN)
                .filter(fd -> target == null || fd.getTargets().isEmpty() || fd.getTargets().contains(target))
                .map(LiftFieldAndTraitDefinition::getName)
                .sorted().toList();
        }
        // Fallback: scan data
        Set<String> standardRanges = Set.of("note-type", "translation-type", "grammatical-info");
        if (h != null) {
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
        return getKnownFieldTypesFor(null);
    }

    /** Returns field (not trait) type names allowed for the given target element type. */
    private List<String> getKnownFieldTypesFor(FieldDefinitionTarget target) {
        if (currentDictionary == null) return List.of();
        LiftHeader h = currentDictionary.getLiftDictionaryComponents().getHeader();
        if (h != null && !h.getFields().isEmpty()) {
            return h.getFields().stream()
                .filter(fd -> fd.getKind() == FieldDefinitionKind.FIELD || fd.getKind() == FieldDefinitionKind.UNKNOWN)
                .filter(fd -> target == null || fd.getTargets().isEmpty() || fd.getTargets().contains(target))
                .map(LiftFieldAndTraitDefinition::getName)
                .sorted().toList();
        }
        return currentDictionary.getFieldType().stream().sorted().toList();
    }
    private List<String> getKnownNoteTypes() {
        return getHeaderRangeValues("note-type");
    }
    private List<String> getKnownRelationTypes() {
        return getHeaderRangeValues("lexical-relation");
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
        dlg.setResizable(true);
        dlg.getDialogPane().setPrefSize(480, 380);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // ── Langue ──
        Label langLabel = new Label(I18n.get("prefs.language"));
        ComboBox<String> langCombo = new ComboBox<>(FXCollections.observableArrayList("Français", "English"));
        langCombo.setValue("fr".equals(I18n.getLocale().getLanguage()) ? "Français" : "English");

        // ── Taille de police ──
        Label fontLabel = new Label(I18n.get("prefs.fontSize"));
        Slider fontSlider = new Slider(10, 20, Double.parseDouble(PREFS.get("ui.fontSize", "13")));
        fontSlider.setShowTickLabels(true);
        fontSlider.setShowTickMarks(true);
        fontSlider.setMajorTickUnit(2);
        fontSlider.setBlockIncrement(1);
        fontSlider.setSnapToTicks(true);
        Label fontValueLabel = new Label(String.valueOf((int) fontSlider.getValue()) + "px");
        fontSlider.valueProperty().addListener((obs, o, n) ->
                fontValueLabel.setText((int) n.doubleValue() + "px"));

        // ── Thème ──
        Label themeLabel = new Label(I18n.get("prefs.theme"));
        ToggleGroup themeGroup = new ToggleGroup();
        RadioButton lightBtn = new RadioButton(I18n.get("prefs.themeLight"));
        lightBtn.setToggleGroup(themeGroup);
        RadioButton darkBtn = new RadioButton(I18n.get("prefs.themeDark"));
        darkBtn.setToggleGroup(themeGroup);
        String savedTheme = PREFS.get("ui.theme", "light");
        if ("dark".equals(savedTheme)) darkBtn.setSelected(true);
        else lightBtn.setSelected(true);

        // ── Chemin par défaut ──
        Label pathLabel = new Label(I18n.get("prefs.defaultPath"));
        TextField pathField = new TextField(PREFS.get("ui.defaultPath", System.getProperty("user.home")));
        pathField.setPromptText(System.getProperty("user.home"));
        Button browseBtn = new Button(I18n.get("prefs.browse"));
        browseBtn.setOnAction(e -> {
            javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
            dc.setTitle(I18n.get("prefs.defaultPath"));
            File dir = dc.showDialog(dlg.getDialogPane().getScene().getWindow());
            if (dir != null) pathField.setText(dir.getAbsolutePath());
        });
        HBox pathBox = new HBox(8, pathField, browseBtn);
        HBox.setHgrow(pathField, Priority.ALWAYS);

        // ── Layout ──
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);
        grid.setPadding(new Insets(16));

        grid.add(langLabel, 0, 0);
        grid.add(langCombo, 1, 0);

        grid.add(fontLabel, 0, 1);
        HBox fontBox = new HBox(8, fontSlider, fontValueLabel);
        HBox.setHgrow(fontSlider, Priority.ALWAYS);
        grid.add(fontBox, 1, 1);

        grid.add(themeLabel, 0, 2);
        grid.add(new HBox(12, lightBtn, darkBtn), 1, 2);

        grid.add(pathLabel, 0, 3);
        grid.add(pathBox, 1, 3);

        GridPane.setHgrow(langCombo, Priority.ALWAYS);
        GridPane.setHgrow(fontBox, Priority.ALWAYS);
        GridPane.setHgrow(pathBox, Priority.ALWAYS);

        dlg.getDialogPane().setContent(grid);

        dlg.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                // Sauvegarde taille de police
                int fontSize = (int) fontSlider.getValue();
                PREFS.put("ui.fontSize", String.valueOf(fontSize));
                applyFontSize(fontSize);

                // Sauvegarde thème
                String theme = darkBtn.isSelected() ? "dark" : "light";
                PREFS.put("ui.theme", theme);
                applyTheme(theme);

                // Sauvegarde chemin par défaut
                PREFS.put("ui.defaultPath", pathField.getText().trim());

                // Applique langue si changée
                String sel = langCombo.getValue();
                Locale newLocale = "Français".equals(sel) ? Locale.FRENCH : Locale.ENGLISH;
                if (!newLocale.getLanguage().equals(I18n.getLocale().getLanguage())) {
                    I18n.setLocale(newLocale);
                    Platform.runLater(fr.cnrs.lacito.liftgui.MainApp::reloadScene);
                }
            }
            return null;
        });

        dlg.showAndWait();
    }

    private void applyFontSize(int size) {
        if (menuBar == null || menuBar.getScene() == null) return;
        menuBar.getScene().getRoot().setStyle("-fx-font-size: " + size + "px;");
    }

    private void applyTheme(String theme) {
        if (menuBar == null || menuBar.getScene() == null) return;
        javafx.collections.ObservableList<String> sheets = menuBar.getScene().getStylesheets();
        sheets.removeIf(s -> s.contains("dark") || s.contains("light"));
        if ("dark".equals(theme)) {
            String darkCss = fr.cnrs.lacito.liftgui.MainApp.class.getResource("/fr/cnrs/lacito/liftgui/ui/dark.css") != null
                    ? fr.cnrs.lacito.liftgui.MainApp.class.getResource("/fr/cnrs/lacito/liftgui/ui/dark.css").toExternalForm()
                    : null;
            if (darkCss != null) sheets.add(darkCss);
            else menuBar.getScene().getRoot().setStyle(
                    menuBar.getScene().getRoot().getStyle() +
                            "; -fx-base: #2b2b2b; -fx-background: #3c3f41; -fx-control-inner-background: #45494a;");
        }
    }
    private record ConfigRow(javafx.beans.property.StringProperty abbrev, javafx.beans.property.StringProperty description) {
        ConfigRow(String a, String d) {
            this(new javafx.beans.property.SimpleStringProperty(a), new javafx.beans.property.SimpleStringProperty(d));
        }
    }

    @FunctionalInterface private interface UsageChecker { long count(String value); }

    private void showConfigDialog(String title, ListSupplier supplier) {
        showConfigDialog(title, supplier, val -> 0L);
    }

    private void showConfigDialog(String title, ListSupplier supplier, UsageChecker usageChecker) {
        List<String> items = supplier.get();
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(I18n.get("config.title", title));
        dlg.setHeaderText(I18n.get("config.header", title, items.size()));
        dlg.setResizable(true);
        dlg.getDialogPane().setPrefSize(620, 480);
        // ← Remplace CLOSE par OK + CANCEL
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TableView<ConfigRow> configTable = new TableView<>();
        configTable.setEditable(true);
        configTable.setPrefHeight(320);

        TableColumn<ConfigRow, String> abbrCol = new TableColumn<>(I18n.get("col.abbreviation"));
        abbrCol.setCellValueFactory(cd -> cd.getValue().abbrev());
        abbrCol.setCellFactory(TextFieldTableCell.forTableColumn());
        abbrCol.setOnEditCommit(e -> e.getRowValue().abbrev().set(e.getNewValue()));
        abbrCol.setPrefWidth(180);

        TableColumn<ConfigRow, String> usageCol = new TableColumn<>(I18n.get("cfg.usageCount"));
        usageCol.setCellValueFactory(cd -> {
            long n = usageChecker.count(cd.getValue().abbrev().get());
            return new javafx.beans.property.ReadOnlyStringWrapper(String.valueOf(n));
        });
        usageCol.setPrefWidth(80);

        TableColumn<ConfigRow, String> descCol = new TableColumn<>(I18n.get("col.description"));
        descCol.setCellValueFactory(cd -> cd.getValue().description());
        descCol.setCellFactory(TextFieldTableCell.forTableColumn());
        descCol.setOnEditCommit(e -> e.getRowValue().description().set(e.getNewValue()));
        descCol.setPrefWidth(320);

        configTable.getColumns().addAll(abbrCol, usageCol, descCol);
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
            if (sel == null) return;
            long usage = usageChecker.count(sel.abbrev().get());
            if (usage > 0) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle(I18n.get("btn.delete"));
                confirm.setHeaderText(I18n.get("config.deleteWarning", sel.abbrev().get(), usage));
                confirm.setContentText(I18n.get("config.deleteConfirm"));
                confirm.showAndWait().filter(r -> r == ButtonType.OK)
                        .ifPresent(r -> configTable.getItems().remove(sel));
            } else {
                configTable.getItems().remove(sel);
            }
        });

        HBox controls = new HBox(8, addAbbrField, addDescField, addBtn, removeBtn);
        controls.setPadding(new Insets(6, 0, 0, 0));
        HBox.setHgrow(addAbbrField, Priority.ALWAYS);
        HBox.setHgrow(addDescField, Priority.ALWAYS);

        VBox content = new VBox(6, configTable, controls);
        dlg.getDialogPane().setContent(content);

        // ← Sauvegarde dans le header LIFT si OK
        dlg.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                persistConfigToHeader(title, configTable.getItems());
            }
            return null;
        });

        dlg.showAndWait();
    }
    private void persistConfigToHeader(String dialogTitle, List<ConfigRow> rows) {
        if (currentDictionary == null) return;
        LiftFactory factory = getFactory(currentDictionary);
        if (factory == null) return;
        LiftHeader header = currentDictionary.getLiftDictionaryComponents().getHeader();
        if (header == null) return;

        // Détermine le rangeId selon le titre du dialogue
        String rangeId = null;
        if (dialogTitle.equals(I18n.get("menu.config.noteTypes")) || dialogTitle.equals(I18n.get("nav.cfgManageNoteTypes")))        rangeId = "note-type";
        else if (dialogTitle.equals(I18n.get("menu.config.translationTypes")) || dialogTitle.equals(I18n.get("nav.cfgManageTransTypes"))) rangeId = "translation-type";
        else if (dialogTitle.equals(I18n.get("menu.config.traitTypes")))   rangeId = "grammatical-info";
        else if (dialogTitle.equals(I18n.get("menu.config.annotationTypes")) || dialogTitle.equals(I18n.get("nav.cfgManageAnnotationTypes"))) rangeId = "annotation-type";
        else if (dialogTitle.equals(I18n.get("nav.cfgManageRelationTypes"))) rangeId = "lexical-relation";

        if (rangeId != null) {
            // Trouve ou crée le range
            final String finalRangeId = rangeId;
            LiftHeaderRange range = header.getRanges().stream()
                    .filter(r -> finalRangeId.equals(r.getId())).findFirst()
                    .orElseGet(() -> factory.createRange(finalRangeId, header));

            // Ajoute les nouveaux éléments manquants
            Set<String> existing = range.getRangeElements().stream()
                    .map(LiftHeaderRangeElement::getId).collect(Collectors.toSet());
            List<String> metaLangs = getMetaLanguages();
            String descLang = metaLangs.isEmpty() ? "en" : metaLangs.get(0);

            for (ConfigRow row : rows) {
                String val = row.abbrev().get();
                if (val == null || val.isBlank()) continue;
                if (!existing.contains(val)) {
                    LiftHeaderRangeElement elem = factory.createRangeElement(val, range);
                    String desc = row.description().get();
                    if (desc != null && !desc.isBlank()) {
                        elem.getDescription().add(new Form(descLang, desc));
                    }
                }
            }

            // Retire les éléments supprimés (non utilisés)
            Set<String> newValues = rows.stream()
                    .map(r -> r.abbrev().get())
                    .filter(v -> v != null && !v.isBlank())
                    .collect(Collectors.toSet());
            range.getRangeElements().removeIf(re -> !newValues.contains(re.getId()));

            rebuildHeaderCfgChildren();
        }
    }

    private LiftDictionary loadDemoDictionary() {
        try {
            java.nio.file.Path tempDir = Files.createTempDirectory("dict-default-");
            tempDir.toFile().deleteOnExit();
            File liftFile = tempDir.resolve("20260302.lift").toFile();
            File rangesFile = tempDir.resolve("20260302.lift-ranges").toFile();
            try (InputStream inLift = MainController.class.getResourceAsStream("/lift/20260302.lift");
                 InputStream inRanges = MainController.class.getResourceAsStream("/lift/20260302.lift-ranges")) {
                if (inLift == null) return loadFallbackDemo();
                try (FileOutputStream outLift = new FileOutputStream(liftFile)) { inLift.transferTo(outLift); }
                if (inRanges != null) {
                    try (FileOutputStream outRanges = new FileOutputStream(rangesFile)) { inRanges.transferTo(outRanges); }
                }
            }
            return LiftDictionary.loadDictionaryWithFile(liftFile);
        } catch (Exception e) { return loadFallbackDemo(); }
    }

    private LiftDictionary loadFallbackDemo() {
        try (InputStream in = MainController.class.getResourceAsStream("/lift/demo.lift")) {
            if (in == null) return null;
            File tmp = Files.createTempFile("dict-demo-", ".lift").toFile();
            tmp.deleteOnExit();
            try (FileOutputStream out = new FileOutputStream(tmp)) { in.transferTo(out); }
            return LiftDictionary.loadDictionaryWithFile(tmp);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Impossible de charger le dictionnaire de démonstration", e);
            return null;
        }
    }

    private static LiftFactory getFactory(LiftDictionary d) { return d != null && d.getLiftDictionaryComponents() instanceof LiftFactory lf ? lf : null; }
    private static String safeTrim(String s) { return s == null ? "" : s.trim(); }
    private static void appendSep(StringBuilder sb, String part) { if (part != null && !part.isBlank()) { if (!sb.isEmpty()) sb.append("; "); sb.append(part); } }
}
