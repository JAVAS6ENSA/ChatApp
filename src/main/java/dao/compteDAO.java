package dao;

import databases.DBConnection;
import java.sql.*;

public class compteDAO {
    public static boolean register(String user, String pass, String email) {
        Connection conn = DBConnection.getInstance();
        if (conn == null) return false;

        // Check if username or email already exists
        if (isUserExists(user, email)) {
            System.err.println("Register fail: User or Email already exists");
            return false;
        }

        String sqlCompte = "INSERT INTO comptes (email, password) VALUES (?, ?)";
        String sqlUser = "INSERT INTO users (id, username, status, is_blocked, role) VALUES (?, ?, 'offline', 0, 'user')";

        try {
            conn.setAutoCommit(false); // Start transaction

            try (PreparedStatement psCompte = conn.prepareStatement(sqlCompte, Statement.RETURN_GENERATED_KEYS)) {
                psCompte.setString(1, email);
                psCompte.setString(2, pass);
                int affected = psCompte.executeUpdate();

                if (affected == 0) {
                    conn.rollback();
                    return false;
                }

                try (ResultSet generatedKeys = psCompte.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        long id = generatedKeys.getLong(1);
                        try (PreparedStatement psUser = conn.prepareStatement(sqlUser)) {
                            psUser.setLong(1, id);
                            psUser.setString(2, user);
                            psUser.executeUpdate();
                        }
                    } else {
                        conn.rollback();
                        return false;
                    }
                }
            }

            conn.commit(); // Success
            return true;
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            System.err.println("Erreur register: " + e.getMessage());
            return false;
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean isUserExists(String username, String email) {
        String sqlUsers = "SELECT 1 FROM users WHERE username = ?";
        String sqlComptes = "SELECT 1 FROM comptes WHERE email = ?";
        
        try {
            try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sqlUsers)) {
                ps.setString(1, username);
                if (ps.executeQuery().next()) return true;
            }
            try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sqlComptes)) {
                ps.setString(1, email);
                if (ps.executeQuery().next()) return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return true;
        }
        return false;
    }
}
