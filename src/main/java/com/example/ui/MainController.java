package com.example.ui;

import com.example.ui.model.EntryRow;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;

public final class MainController {

    @FXML
    private TableView<EntryRow> entryTable;

    @FXML
    private TableColumn<EntryRow, String> idColumn;

    @FXML
    private TableColumn<EntryRow, String> entryColumn;

    @FXML
    private TableColumn<EntryRow, String> categoryColumn;

    @FXML
    private TableColumn<EntryRow, String> langColumn;

    @FXML
    private TableColumn<EntryRow, String> metaLangColumn;

    @FXML
    private TableColumn<EntryRow, String> defColumn;

    @FXML
    private TableColumn<EntryRow, Boolean> fieldsColumn;

    @FXML
    private ComboBox<String> languageFilterCombo;

    @FXML
    private ComboBox<String> sortByCombo;

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        entryColumn.setCellValueFactory(new PropertyValueFactory<>("entry"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        langColumn.setCellValueFactory(new PropertyValueFactory<>("lang"));
        metaLangColumn.setCellValueFactory(new PropertyValueFactory<>("metaLang"));
        defColumn.setCellValueFactory(new PropertyValueFactory<>("definition"));

        fieldsColumn.setCellValueFactory(new PropertyValueFactory<>("hasFields"));
        fieldsColumn.setCellFactory(CheckBoxTableCell.forTableColumn(fieldsColumn));

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
                "ID",
                "Entrée",
                "Catégorie",
                "Langue",
                "Définitions",
                "Trait",
                "Date de modification"
        ));
        sortByCombo.getSelectionModel().selectFirst();

        entryTable.setItems(FXCollections.observableArrayList(
                new EntryRow("01", "chien", "Nom", "Français", "standard", "animal", true),
                new EntryRow("02", "to dance", "Verbe", "Français", "standard", "action", true),
                new EntryRow("03", "ouragan", "Nom", "Français", "dialecte A", "vent", true),
                new EntryRow("04", "huracán", "Nom", "ESP", "dialecto", "viento muy fuerte", true)
        ));
    }
}

