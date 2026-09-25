package com.taskscheduler.dao;

import com.taskscheduler.exception.DataAccessException;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseConnection {

    private static final String DB_DIR = "data";
    private static final String DB_FILE = "taskscheduler.db";
    private static final String DB_URL;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new DataAccessException("SQLite JDBC driver not found", e);
        }
        File dir = new File(DB_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            System.err.println("Warning: could not create data directory: " + dir.getAbsolutePath());
        }
        DB_URL = "jdbc:sqlite:" + DB_DIR + File.separator + DB_FILE;
    }

    private DatabaseConnection() {}

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static void initializeSchema() {
        String createTasks = """
            CREATE TABLE IF NOT EXISTS tasks (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                title        TEXT    NOT NULL,
                description  TEXT    DEFAULT '',
                priority     TEXT    NOT NULL DEFAULT 'MEDIUM',
                status       TEXT    NOT NULL DEFAULT 'PENDING',
                category     TEXT    NOT NULL DEFAULT 'OTHER',
                due_date     TEXT    NOT NULL,
                progress     INTEGER NOT NULL DEFAULT 0 CHECK (progress BETWEEN 0 AND 100),
                assigned_to  TEXT    DEFAULT '',
                tag_color    INTEGER DEFAULT 0
            )
            """;

        String createIndex = """
            CREATE INDEX IF NOT EXISTS idx_tasks_due_date ON tasks(due_date)
            """;

        String createUniqueIdx = """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_tasks_title_due ON tasks(title, due_date)
            """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTasks);
            stmt.execute(createIndex);
            stmt.execute(createUniqueIdx);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to initialize database schema", e);
        }
    }

    public static String getDbUrl() {
        return DB_URL;
    }
}
