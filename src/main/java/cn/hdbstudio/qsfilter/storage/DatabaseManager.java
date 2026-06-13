package cn.hdbstudio.qsfilter.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * SQLite 数据库连接管理。
 * JDBC 4.0+ 通过 META-INF/services 自动发现驱动，无需 Class.forName。
 */
public class DatabaseManager {

    private final File dbFile;
    private Connection connection;

    public DatabaseManager(File dbFile) {
        this.dbFile = dbFile;
        if (!dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }
        initDatabase();
    }

    private void initDatabase() {
        try {
            // JDBC 4.0+ 自动通过 java.sql.Driver service loader 加载驱动
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            connection.setAutoCommit(true);

            try (var stmt = connection.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS transactions (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        material    TEXT    NOT NULL,
                        item_name   TEXT    NOT NULL,
                        price       REAL    NOT NULL,
                        amount      INTEGER NOT NULL DEFAULT 1,
                        timestamp   INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
                    )
                """);

                stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_transactions_material
                    ON transactions(material)
                """);

                stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_transactions_timestamp
                    ON transactions(timestamp)
                """);
            }
        } catch (Exception e) {
            throw new RuntimeException("数据库初始化失败", e);
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            }
        } catch (SQLException e) {
            throw new RuntimeException("数据库连接失败", e);
        }
        return connection;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {
        }
    }
}
