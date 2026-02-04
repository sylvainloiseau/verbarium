package com.example.ui;

import com.example.ui.model.ExampleRow;
import com.example.ui.model.EntryRow;
import com.example.ui.model.FormRow;
import com.example.ui.model.SenseValueRow;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

public final class MainController {

    @FXML
    private TableView<EntryRow> entryTable;

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
                "Code",
                "Métalangue",
                "Contenu linguistique"
        ));
        filterByCombo.getSelectionModel().selectFirst();

        languageFilterCombo.setItems(FXCollections.observableArrayList(
                "Toutes",
                "Français",
                "EN",
                "ES",
                "ESP",
                "tpi",
                "tww"
        ));
        languageFilterCombo.getSelectionModel().selectFirst();

        sortByCombo.setItems(FXCollections.observableArrayList(
                "Entrée",
                "Code",
                "Prononciation",
                "Langue",
                "Définitions",
                "Métalangue"
        ));
        sortByCombo.getSelectionModel().selectFirst();

        entryTable.setItems(FXCollections.observableArrayList(
                new EntryRow("chat", "n./fr", "/ʃa/", "Français", "cat; chat", "cat | chat", "fr"),
                new EntryRow("chien", "n./fr", "/ʃjɛ̃/", "Français", "dog; chien", "dog | chien", "fr"),
                new EntryRow("blanc", "adj./fr", "/blɑ̃/", "Français", "white; blank", "1. white | 2. blank", "fr"),
                new EntryRow("maison", "adj./fr", "/mɛzɔ̃/", "Français", "house; maison", "house | maison", "fr")
        ));

        tableCountLabel.setText("4 entrées affichées sur 124");

        entryTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                editEntryTitle.setText("(sélectionne une entrée)");
                editEntryCode.setText("");
                formsTable.setItems(FXCollections.observableArrayList());
                sensesTable.setItems(FXCollections.observableArrayList());
                examplesTable.setItems(FXCollections.observableArrayList());
                return;
            }

            editEntryTitle.setText(newV.getEntry());
            editEntryCode.setText(newV.getCode());

            formsTable.setItems(FXCollections.observableArrayList(
                    new FormRow("US", "white"),
                    new FormRow("UK", "white"),
                    new FormRow("Ger", "weiß")
            ));

            sensesTable.setItems(FXCollections.observableArrayList(
                    new SenseValueRow("FR", newV.getEntry()),
                    new SenseValueRow("EN", "white"),
                    new SenseValueRow("ES", "blanco")
            ));

            examplesTable.setItems(FXCollections.observableArrayList(
                    new ExampleRow("phrase", "un ouragan approche", "is coming"),
                    new ExampleRow("phrase", "aa hurricane", "is coming")
            ));
        });
    }
}

