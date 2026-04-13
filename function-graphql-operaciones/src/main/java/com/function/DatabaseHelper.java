package com.function;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseHelper {

    private static final String DB_URL = System.getenv("ORACLE_DB_URL") != null
            ? System.getenv("ORACLE_DB_URL")
            : "jdbc:mysql://44.233.50.94:3306/biblioteca";

    private static final String DB_USER = System.getenv("ORACLE_DB_USER") != null
            ? System.getenv("ORACLE_DB_USER")
            : "biblioteca";

    private static final String DB_PASSWORD = System.getenv("ORACLE_DB_PASSWORD") != null
            ? System.getenv("ORACLE_DB_PASSWORD")
            : "Biblioteca123";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
}
