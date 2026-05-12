package dao;

import databases.DBConnection;
import model.User;
import java.sql.*;
import java.util.*;

public class UserDAO {

    // Login
    public User login(String identifier, String password) {
        String sql = "SELECT u.*, c.email FROM users u " +
                "JOIN comptes c ON c.id = u.id " +
                "WHERE (c.email = ? OR u.username = ?) AND c.password = ? AND u.is_blocked = 0";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setString(1, identifier);
            ps.setString(2, identifier);
            ps.setString(3, password);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapUser(rs);
        } catch (SQLException e) {
            System.err.println("Erreur login: " + e.getMessage());
        }
        return null;
    }

    public User getByUsername(String username) {
        String sql = "SELECT u.*, c.email FROM users u " +
                "JOIN comptes c ON c.id = u.id " +
                "WHERE u.username = ?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapUser(rs);
        } catch (SQLException e) {
            System.err.println("Erreur getByUsername: " + e.getMessage());
        }
        return null;
    }

    public User getById(int id) {
        String sql = "SELECT u.*, c.email FROM users u " +
                "JOIN comptes c ON c.id = u.id " +
                "WHERE u.id = ?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapUser(rs);
        } catch (SQLException e) {
            System.err.println("Erreur getById: " + e.getMessage());
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


    public int resetAllOffline() {
        String sql = "UPDATE users SET status = 'offline' WHERE status <> 'offline'";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erreur resetAllOffline: " + e.getMessage());
            return 0;
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

    // Tous les utilisateurs
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

    public boolean updateProfile(int userId, String bio, String profilePicture) {
        String sql = "UPDATE users SET bio = ?, profile_picture = ? WHERE id = ?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setString(1, bio == null ? "" : bio);
            ps.setString(2, profilePicture == null ? "" : profilePicture);
            ps.setInt(3, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur updateProfile: " + e.getMessage());
            return false;
        }
    }


    public boolean updateProfilePictureData(int userId, byte[] data) {
        String sql = "UPDATE users SET profile_picture_data = ? WHERE id = ?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            if (data == null || data.length == 0) ps.setNull(1, Types.BLOB);
            else                                  ps.setBytes(1, data);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur updateProfilePictureData: " + e.getMessage());
            return false;
        }
    }

    public byte[] getProfilePictureData(String username) {
        String sql = "SELECT profile_picture_data FROM users WHERE username = ?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getBytes(1);
        } catch (SQLException e) {
            System.err.println("Erreur getProfilePictureData: " + e.getMessage());
        }
        return null;
    }

    private User mapUser(ResultSet rs) throws SQLException {
        User u = new User(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("status"),
                rs.getBoolean("is_blocked"),
                rs.getString("role")
        );
        try { u.setJoinDate(String.valueOf(rs.getTimestamp("join_date"))); } catch (SQLException ignored) {}
        try { u.setBio(rs.getString("bio")); } catch (SQLException ignored) {}
        try { u.setProfilePicture(rs.getString("profile_picture")); } catch (SQLException ignored) {}
        try { u.setProfilePictureData(rs.getBytes("profile_picture_data")); } catch (SQLException ignored) {}
        return u;
    }
}