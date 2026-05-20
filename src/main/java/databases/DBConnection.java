package databases;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DBConnection {

    private static final String URL = buildUrl();
    private static final String USER =
            System.getenv().getOrDefault("DB_USER", "chatapp");
    private static final String PASSWORD =
            System.getenv().getOrDefault("DB_PASSWORD", "7uGCe9ZnyDAbYfWJ80BZd9");

    private static String buildUrl() {
        String base = System.getenv().getOrDefault("DB_URL",
                "jdbc:mariadb://15.236.189.148:3306/chat_app");
        String sep = base.contains("?") ? "&" : "?";
        return base + sep + "connectTimeout=4000&socketTimeout=15000";
    }

    private static Connection instance = null;

    private DBConnection() {}

    public static Connection getInstance() {
        try {
            if (instance == null || instance.isClosed()) {
                instance = DriverManager.getConnection(URL, USER, PASSWORD);
                System.out.println("✓ Connexion BDD réussie");
                ensureSchema(instance);
            }
        } catch (SQLException e) {
            System.err.println("✗ Erreur connexion BDD : " + e.getMessage());
        }
        return instance;
    }

    private static void ensureSchema(Connection conn) {
        String[] migrations = {
            "ALTER TABLE users ADD COLUMN IF NOT EXISTS display_name VARCHAR(60) NOT NULL DEFAULT ''"
        };
        for (String sql : migrations) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate(sql);
            } catch (SQLException e) {
                System.err.println("Schema migration skipped (" + e.getMessage() + "): " + sql);
            }
        }
    }
}