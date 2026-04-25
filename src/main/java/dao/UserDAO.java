package dao;

import databases.DBConnection;
import model.User;
import java.sql.*;
import java.util.*;

public class UserDAO {

    // Login
    public User login(String email, String password) {
        String sql = "SELECT u.*, c.email FROM users u " +
                "JOIN comptes c ON c.id = u.id " +
                "WHERE c.email = ? AND c.password = ? AND u.is_blocked = 0";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapUser(rs);
        } catch (SQLException e) {
            System.err.println("Erreur login: " + e.getMessage());
        }
        return null;
    }

    // Changer statut online/offline/away
    public void updateStatus(int userId, String status) {
        String sql = "UPDATE users SET status = ? WHERE id = ?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erreur updateStatus: " + e.getMessage());
        }
    }

    // Liste utilisateurs connectés
    public List<User> getOnlineUsers() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT u.*, c.email FROM users u " +
                "JOIN comptes c ON c.id = u.id " +
                "WHERE u.status = 'online' AND u.is_blocked = 0";
        try (Statement st = DBConnection.getInstance().createStatement()) {
            ResultSet rs = st.executeQuery(sql);
            while (rs.next()) list.add(mapUser(rs));
        } catch (SQLException e) {
            System.err.println("Erreur getOnlineUsers: " + e.getMessage());
        }
        return list;
    }

    // Bloquer / débloquer
    public void setBlocked(int userId, boolean blocked) {
        String sql = "UPDATE users SET is_blocked = ? WHERE id = ?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, blocked ? 1 : 0);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erreur setBlocked: " + e.getMessage());
        }
    }

    // Supprimer un utilisateur
    public void deleteUser(int userId) {
        String sql = "DELETE FROM comptes WHERE id = ?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erreur deleteUser: " + e.getMessage());
        }
    }

    // Tous les utilisateurs (admin)
    public List<User> getAllUsers() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT u.*, c.email FROM users u JOIN comptes c ON c.id = u.id";
        try (Statement st = DBConnection.getInstance().createStatement()) {
            ResultSet rs = st.executeQuery(sql);
            while (rs.next()) list.add(mapUser(rs));
        } catch (SQLException e) {
            System.err.println("Erreur getAllUsers: " + e.getMessage());
        }
        return list;
    }

    private User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("status"),
                rs.getBoolean("is_blocked"),
                rs.getString("role")
        );
    }
}