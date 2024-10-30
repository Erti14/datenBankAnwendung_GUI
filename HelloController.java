package com.example.projekt;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;

import java.sql.*;

public class HelloController {
    // Textfelder für die Server-Verbindungsdetails
    @FXML
    private TextField serverTextField;

    @FXML
    private TextField portTextField;

    @FXML
    private TextField userTextField;

    @FXML
    private TextField passwordTextField;

    // Buttons zur Verbindung, Trennung und Bearbeitung
    @FXML
    private Button btn_connect;

    @FXML
    private Button btn_disconnect;

    @FXML
    private Button btn_add;

    @FXML
    private Button btn_delete;

    // Kombinationsboxen zur Auswahl der Datenbank und Tabelle
    @FXML
    private ComboBox<String> databaseComboBox;

    @FXML
    private ComboBox<String> tableComboBox;

    // Tabelle zur Anzeige und Bearbeitung der Tabellendaten
    @FXML
    private TableView<ObservableList<String>> tableView;

    private String selectedDatabase; // Speichert die ausgewählte Datenbank
    private String selectedTable; // Speichert die ausgewählte Tabelle

    @FXML
    protected void onConnectBtnClick() {
        String server = serverTextField.getText();
        String port = portTextField.getText();
        String user = userTextField.getText();
        String password = passwordTextField.getText();

        try {
            // Versucht, eine Datenbankverbindung herzustellen
            DBClass.makeConnection(server, port, user, password);

            // Bei erfolgreicher Verbindung werden die Eingabefelder deaktiviert und die UI aktualisiert
            serverTextField.setEditable(false);
            portTextField.setEditable(false);
            userTextField.setEditable(false);
            passwordTextField.setEditable(false);
            btn_connect.setDisable(true);
            btn_disconnect.setDisable(false);

            ObservableList<String> databaseNames = DBClass.getDatabaseNames(server, port, user, password);
            databaseComboBox.setItems(databaseNames); // Datenbankauswahl füllen
            tableComboBox.getItems().clear();
        } catch (SQLException e) {
            // Fehlerbehandlung: Stacktrace ausgeben und eine Fehlermeldung für den Benutzer anzeigen
            e.printStackTrace();
            showAlert("Datenbankverbindungsfehler", "Verbindung zur Datenbank fehlgeschlagen. Bitte überprüfen Sie die Verbindungsparameter und versuchen Sie es erneut.");

            // Eingabefelder zurücksetzen und Verbinden-Button wieder aktivieren
            serverTextField.setEditable(true);
            portTextField.setEditable(true);
            userTextField.setEditable(true);
            passwordTextField.setEditable(true);
            btn_connect.setDisable(false);
        }
    }

    // Hilfsmethode zur Anzeige von Fehlermeldungen
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }


    // Methode zum Trennen der Verbindung, wird beim Klick auf "Trennen" aufgerufen
    @FXML
    protected void onDisconnectBtnClick() {
        btn_connect.setDisable(false);
        btn_disconnect.setDisable(true);

        // Leert und aktiviert die Eingabefelder nach Trennung der Verbindung
        serverTextField.clear();
        portTextField.clear();
        userTextField.clear();
        passwordTextField.clear();
        serverTextField.setEditable(true);
        portTextField.setEditable(true);
        userTextField.setEditable(true);
        passwordTextField.setEditable(true);

        databaseComboBox.getItems().clear();
        tableComboBox.getItems().clear();

        tableView.getItems().clear();
        tableView.getColumns().clear();

        try {
            DBClass.closeConnection(); // Beendet die Verbindung zur Datenbank
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // Wird aufgerufen, wenn eine Datenbank aus der ComboBox ausgewählt wird
    @FXML
    protected void onDatabaseSelected() {
        selectedDatabase = databaseComboBox.getValue();

        if (selectedDatabase != null) {
            try {
                tableComboBox.getItems().clear();
                ObservableList<String> tableNames = DBClass.getTableNames(serverTextField.getText(), portTextField.getText(),
                        userTextField.getText(), passwordTextField.getText(), selectedDatabase);
                tableComboBox.setItems(tableNames); // Füllt die Tabellen-Auswahl basierend auf der Datenbank
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            tableComboBox.getItems().clear();
        }
    }

    // Wird aufgerufen, wenn eine Tabelle aus der ComboBox ausgewählt wird
    @FXML
    protected void onTableSelected() {
        selectedTable = tableComboBox.getValue();

        if (selectedDatabase != null && selectedTable != null) {
            try {
                ObservableList<ObservableList<String>> tableData = DBClass.getTableData(serverTextField.getText(), portTextField.getText(),
                        userTextField.getText(), passwordTextField.getText(), selectedDatabase, selectedTable);

                tableView.getColumns().clear();
                tableView.getItems().clear();

                ResultSetMetaData metaData = DBClass.getTableMetaData(serverTextField.getText(), portTextField.getText(),
                        userTextField.getText(), passwordTextField.getText(), selectedDatabase, selectedTable);
                int columnCount = metaData.getColumnCount();

                // Fügt für jede Spalte eine TableColumn hinzu
                for (int i = 1; i <= columnCount; i++) {
                    final int columnIndex = i;
                    TableColumn<ObservableList<String>, String> column = new TableColumn<>(metaData.getColumnName(i));

                    // Setzt die Zellwertfabrik und ermöglicht die Bearbeitung der Zellen
                    column.setCellValueFactory(param -> {
                        ObservableList<String> row = param.getValue();
                        return new SimpleStringProperty(row.get(columnIndex - 1));
                    });

                    column.setCellFactory(TextFieldTableCell.forTableColumn());
                    column.setEditable(true);
                    tableView.setEditable(true);

                    column.setOnEditCommit(event -> {
                        String newValue = event.getNewValue();
                        int row = event.getTablePosition().getRow();
                        int col = columnIndex;

                        tableView.getItems().get(row).set(col - 1, newValue);

                        try {
                            updateRowInDatabase(newValue, row, col);
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    });

                    tableView.getColumns().add(column); // Fügt die Spalte der TableView hinzu
                }

                tableView.setItems(tableData);

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    // Aktualisiert die entsprechende Zeile in der Datenbank
    private void updateRowInDatabase(String newValue, int row, int col) throws SQLException {
        if (selectedDatabase != null && selectedTable != null) {
            String primaryKeyColumn = "id"; // Annahme, dass 'id' die Primärschlüssel-Spalte ist

            try (Connection connection = DriverManager.getConnection(
                    "jdbc:mysql://" + serverTextField.getText() + ":" + portTextField.getText() + "/" + selectedDatabase,
                    userTextField.getText(), passwordTextField.getText());
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE " + selectedTable + " SET " + getColumnLabel(col) + " = ? WHERE " + primaryKeyColumn + " = ?")) {

                statement.setString(1, newValue);

                String primaryKeyValue = tableView.getItems().get(row).get(0);
                statement.setString(2, primaryKeyValue);

                statement.executeUpdate();
            }
        }
    }

    // Holt den Namen einer Spalte basierend auf ihrem Index
    private String getColumnLabel(int col) throws SQLException {
        ResultSetMetaData metaData = DBClass.getTableMetaData(serverTextField.getText(), portTextField.getText(),
                userTextField.getText(), passwordTextField.getText(), selectedDatabase, selectedTable);
        return metaData.getColumnName(col);
    }

    // Speichert eine neue Zeile in der Datenbank
    private void saveNewRowToDatabase(ObservableList<String> rowData) throws SQLException {
        StringBuilder sql = new StringBuilder("INSERT INTO " + selectedTable + " VALUES (");

        for (int i = 0; i < rowData.size(); i++) {
            sql.append("?");
            if (i < rowData.size() - 1) {
                sql.append(", ");
            }
        }
        sql.append(")");

        try (Connection connection = DriverManager.getConnection(
                "jdbc:mysql://" + serverTextField.getText() + ":" + portTextField.getText() + "/" + selectedDatabase,
                userTextField.getText(), passwordTextField.getText());
             PreparedStatement preparedStatement = connection.prepareStatement(sql.toString())) {

            for (int i = 0; i < rowData.size(); i++) {
                preparedStatement.setString(i + 1, rowData.get(i));
            }

            preparedStatement.executeUpdate();
        }
    }

    // Wird aufgerufen, wenn auf den "Löschen" Button geklickt wird
    @FXML
    protected void onDeleteButtonClicked() {
        int selectedIndex = tableView.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0 && selectedIndex < tableView.getItems().size()) {
            String selectedDatabase = databaseComboBox.getValue();
            String selectedTable = tableComboBox.getValue();
            if (selectedDatabase != null && selectedTable != null) {
                String primaryKeyValue = tableView.getItems().get(selectedIndex).get(0);
                try {
                    DBClass.deleteRow(serverTextField.getText(), portTextField.getText(), userTextField.getText(), passwordTextField.getText(), selectedDatabase, selectedTable, primaryKeyValue);
                    refreshTableData(); // Aktualisiert die Tabelle nach dem Löschen
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("Datenbankname und Tabellenname müssen ausgewählt werden.");
            }
        } else {
            System.out.println("Keine Zeile ausgewählt oder ungültiger Index");
        }
    }

    // Lädt die Daten der Tabelle neu in die TableView
    private void refreshTableData() {
        try {
            ObservableList<ObservableList<String>> tableData = DBClass.getTableData(serverTextField.getText(), portTextField.getText(),
                    userTextField.getText(), passwordTextField.getText(), selectedDatabase, selectedTable);
            tableView.setItems(tableData);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
