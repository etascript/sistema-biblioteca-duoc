package com.function;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseHelper {

    private static final String DB_URL = System.getenv("ORACLE_DB_URL") != null
            ? System.getenv("ORACLE_DB_URL")
            : "jdbc:oracle:thin:@localhost:1521/XEPDB1";

    private static final String DB_USER = System.getenv("ORACLE_DB_USER") != null
            ? System.getenv("ORACLE_DB_USER")
            : "SYSTEM";

    private static final String DB_PASSWORD = System.getenv("ORACLE_DB_PASSWORD") != null
            ? System.getenv("ORACLE_DB_PASSWORD")
            : "Admin123Password";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
}
