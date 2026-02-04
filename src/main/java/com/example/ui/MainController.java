package com.example.ui;

import com.example.ui.model.EntryRow;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
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
    private TableColumn<EntryRow, String> defColumn;

    @FXML
    private TableColumn<EntryRow, Boolean> fieldsColumn;

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        entryColumn.setCellValueFactory(new PropertyValueFactory<>("entry"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        langColumn.setCellValueFactory(new PropertyValueFactory<>("lang"));
        defColumn.setCellValueFactory(new PropertyValueFactory<>("definition"));

        fieldsColumn.setCellValueFactory(new PropertyValueFactory<>("hasFields"));
        fieldsColumn.setCellFactory(CheckBoxTableCell.forTableColumn(fieldsColumn));

        entryTable.setItems(FXCollections.observableArrayList(
                new EntryRow("01", "chien", "Nom", "Français", "animal", true),
                new EntryRow("02", "to dance", "Verbe", "Français", "action", true),
                new EntryRow("03", "ouragan", "Nom", "Français", "vent", true),
                new EntryRow("04", "huracán", "Nom", "ESP", "viento muy fuerte", true)
        ));
    }
}

