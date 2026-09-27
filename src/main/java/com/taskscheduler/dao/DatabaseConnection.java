package com.taskscheduler.dao;

import com.taskscheduler.exception.DataAccessException;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseConnection {

    private static final String DB_FILE = "taskscheduler.db";
    private static final String DB_URL;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new DataAccessException("SQLite JDBC driver not found", e);
        }
        File dir = resolveDataDir();
        if (!dir.exists() && !dir.mkdirs()) {
            System.err.println("Warning: could not create data directory: " + dir.getAbsolutePath()
                    + " — falling back to local ./data");
            File fallback = new File("data");
            if (!fallback.exists() && !fallback.mkdirs()) {
                System.err.println("Warning: fallback data directory also could not be created.");
            }
            dir = fallback;
        }
        DB_URL = "jdbc:sqlite:" + dir.getAbsolutePath() + File.separator + DB_FILE;
        System.out.println("[DB] Using database: " + DB_URL);
    }

    private static File resolveDataDir() {
        String override = System.getProperty("taskscheduler.db.dir");
        if (override != null && !override.isBlank()) {
            return new File(override);
        }
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return new File(appData, "TaskScheduler");
            }
            String userProfile = System.getenv("USERPROFILE");
            File base = userProfile != null ? new File(userProfile, "AppData/Roaming") : new File(System.getProperty("user.home"));
            return new File(base, "TaskScheduler");
        }
        if (os.contains("mac")) {
            return new File(System.getProperty("user.home"), "Library/Application Support/TaskScheduler");
        }
        return new File(System.getProperty("user.home"), ".taskscheduler");
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
