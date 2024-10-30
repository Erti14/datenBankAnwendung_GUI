package com.example.projekt;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.*;

public class DBClass {

    public static Connection myConn = null;

    //Erstellung der Verbindung
    public static void makeConnection(String hostname, String port, String benutzername, String passwort) throws SQLException {

        myConn = DriverManager.getConnection("jdbc:mysql://" + hostname + ":" + port + "/", benutzername, passwort);

    }


    //Trennung der Verbindung
    public static void closeConnection() throws SQLException {
        try {
            if (myConn != null) {
                myConn.close();
            }
        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
        } finally {
            System.out.println("Connection closed successfully");
        }

    }


    //Methode um die Datenbanken zu bekommen
    public static ObservableList<String> getDatabaseNames(String hostname, String port, String username, String password) throws SQLException {
        ObservableList<String> databaseNames = FXCollections.observableArrayList();
        Connection connection = null;
        ResultSet resultSet = null;

        try {
            // Verbindung aufbauen
            connection = DriverManager.getConnection("jdbc:mysql://" + hostname + ":" + port + "/", username, password);
            // Metadaten abrufen
            DatabaseMetaData metaData = connection.getMetaData();
            // Ergebnismenge der Datenbanknamen abrufen
            resultSet = metaData.getCatalogs();

            // Datenbanknamen zur Liste hinzufügen
            while (resultSet.next()) {
                String dbName = resultSet.getString("TABLE_CAT");
                databaseNames.add(dbName);
            }
        } finally {
            // Ressourcen schließen
            if (resultSet != null) {
                resultSet.close();
            }
            if (connection != null) {
                connection.close();
            }
        }

        return databaseNames;
    }


    // Methode um die Tabellen zu bekommen
    public static ObservableList<String> getTableNames(String hostname, String port, String username, String password, String databaseName) throws SQLException {
        ObservableList<String> tableNames = FXCollections.observableArrayList();
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://" + hostname + ":" + port + "/" + databaseName, username, password)) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet resultSet = metaData.getTables(databaseName, username, "%", new String[]{"TABLE"})) {
                while (resultSet.next()) {
                    String tableName = resultSet.getString("TABLE_NAME");
                    tableNames.add(tableName);
                }
            }
        }
        return tableNames;
    }


    // Methode um die Daten der Tabelle zu bekommen
    public static ObservableList<ObservableList<String>> getTableData(String hostname, String port, String username, String password, String databaseName,
                                                                      String tableName) throws SQLException {
        ObservableList<ObservableList<String>> data = FXCollections.observableArrayList();
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://" + hostname + ":" + port + "/" + databaseName, username, password);
             Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
             ResultSet resultSet = statement.executeQuery("SELECT * FROM " + tableName)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Hinzufügen von Zeilendaten zur Datenliste
            while (resultSet.next()) {
                ObservableList<String> row = FXCollections.observableArrayList();
                for (int i = 1; i <= columnCount; i++) {
                    row.add(resultSet.getString(i));
                }
                data.add(row);
            }
        }
        return data;
    }


    // Methode zum Abrufen von Tabellen-Metadaten für eine bestimmte Datenbank und Tabelle
    public static ResultSetMetaData getTableMetaData(String hostname, String port, String username, String password, String databaseName, String tableName)
            throws SQLException {
        ResultSetMetaData metaData = null;
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://" + hostname + ":" + port + "/" + databaseName, username, password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM " + tableName + " LIMIT 1")) {
            metaData = resultSet.getMetaData();
        }
        return metaData;
    }


    // Methode um eine Zeile der Tabelle zu löschen
    public static void deleteRow(String hostname, String port, String username, String password, String databaseName, String tableName, String primaryKeyValue)
            throws SQLException {
        if (databaseName == null || databaseName.isEmpty() || tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException("Database name and table name must be provided.");
        }
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://" + hostname + ":" + port + "/" + databaseName, username, password)) {
            // Abrufen des Namens der Primärschlüsselspalte aus den Metadaten der Tabelle
            String primaryKeyColumnName = getPrimaryKeyColumnName(connection, databaseName, tableName);

            // Erstellung einer DELETE-Anweisung mit dem Namen der Primärschlüsselspalte
            String sql = "DELETE FROM " + tableName + " WHERE " + primaryKeyColumnName + " = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                // Setzen des Primärschlüsselwerts als Parameter
                preparedStatement.setString(1, primaryKeyValue);
                // Ausführen der DELETE-Anweisung
                preparedStatement.executeUpdate();
            }
        }
    }


    // Methode um die Primary key zu bekommen
    private static String getPrimaryKeyColumnName(Connection connection, String databaseName, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getPrimaryKeys(databaseName, null, tableName)) {
            if (resultSet.next()) {
                return resultSet.getString("COLUMN_NAME");
            } else {
                throw new SQLException("Primary key column not found for table: " + tableName);
            }
        }
    }
}



