package dao;

import databases.DBConnection;
import java.sql.*;

public class compteDAO {

    /**
     * Create a passwordless account from a phone number.
     * The internal username defaults to the phone number (the rest of the app
     * keys everything on username); users can give each other private aliases.
     *
     * @return true on success, false if the phone is already registered.
     */
    public static boolean registerPhone(String phone) {
        return registerPhone(phone, "");
    }

    /**
     * Same as {@link #registerPhone(String)} but also stores the public
     * display name the user chose on the registration screen.
     */
    public static boolean registerPhone(String phone, String displayName) {
        Connection conn = DBConnection.getInstance();
        if (conn == null) return false;

        if (phoneExists(phone)) {
            System.err.println("Register fail: phone already exists");
            return false;
        }

        String safeName = displayName == null ? "" : displayName.trim();

        String sqlCompte = "INSERT INTO comptes (phone) VALUES (?)";
        String sqlUser   = "INSERT INTO users (id, username, display_name, status, is_blocked, role) " +
                           "VALUES (?, ?, ?, 'offline', 0, 'user')";
        try {
            conn.setAutoCommit(false);

            try (PreparedStatement psCompte =
                         conn.prepareStatement(sqlCompte, Statement.RETURN_GENERATED_KEYS)) {
                psCompte.setString(1, phone);
                if (psCompte.executeUpdate() == 0) { conn.rollback(); return false; }

                try (ResultSet keys = psCompte.getGeneratedKeys()) {
                    if (!keys.next()) { conn.rollback(); return false; }
                    long id = keys.getLong(1);
                    try (PreparedStatement psUser = conn.prepareStatement(sqlUser)) {
                        psUser.setLong(1, id);
                        psUser.setString(2, phone);   // username == phone by default
                        psUser.setString(3, safeName);
                        psUser.executeUpdate();
                    }
                }
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            System.err.println("Erreur registerPhone: " + e.getMessage());
            return false;
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    public static boolean phoneExists(String phone) {
        String sql = "SELECT 1 FROM comptes WHERE phone = ?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setString(1, phone);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return true; // fail safe: treat as existing so we don't double-insert
        }
    }
}
