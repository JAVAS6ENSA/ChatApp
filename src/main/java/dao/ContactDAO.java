package dao;

import databases.DBConnection;
import model.Contact;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple DAO that manages the relationship "user -> contacts".
 * Each user only sees the contacts he/she has added.
 */
public class ContactDAO {

    // Add a new contact for the current user
    public boolean addContact(int userId, int contactId) {
        if (userId == contactId) return false; // user cannot add himself
        String sql = "INSERT IGNORE INTO contacts (user_id, contact_id) VALUES (?, ?)";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, contactId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur addContact: " + e.getMessage());
            return false;
        }
    }

    // Remove a contact from the current user's list
    public boolean removeContact(int userId, int contactId) {
        String sql = "DELETE FROM contacts WHERE user_id=? AND contact_id=?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, contactId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur removeContact: " + e.getMessage());
            return false;
        }
    }

    // Set (or clear, when alias is null/empty) the private name for a contact
    public boolean renameContact(int userId, int contactId, String alias) {
        String sql = "UPDATE contacts SET alias = ? WHERE user_id = ? AND contact_id = ?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            if (alias == null || alias.isBlank()) ps.setNull(1, java.sql.Types.VARCHAR);
            else                                  ps.setString(1, alias.trim());
            ps.setInt(2, userId);
            ps.setInt(3, contactId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur renameContact: " + e.getMessage());
            return false;
        }
    }

    // Get the contacts of a given user (only the ones he added)
    public List<Contact> getContacts(int userId) {
        List<Contact> list = new ArrayList<>();
        String sql = "SELECT u.id, u.username, u.status, u.is_blocked, u.display_name, c.alias, c.added_at " +
                     "FROM contacts c " +
                     "JOIN users u ON u.id = c.contact_id " +
                     "WHERE c.user_id = ?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                boolean online = "online".equalsIgnoreCase(rs.getString("status"));
                Contact c = new Contact(
                        rs.getInt("id"),
                        rs.getString("username"),
                        online,
                        rs.getBoolean("is_blocked"),
                        false,
                        rs.getString("added_at")
                );
                c.alias = rs.getString("alias");
                try { c.displayName = rs.getString("display_name"); } catch (SQLException ignored) {}
                list.add(c);
            }
        } catch (SQLException e) {
            System.err.println("Erreur getContacts: " + e.getMessage());
        }
        return list;
    }

    // Check if a contact already exists
    public boolean exists(int userId, int contactId) {
        String sql = "SELECT 1 FROM contacts WHERE user_id=? AND contact_id=?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, contactId);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            System.err.println("Erreur exists: " + e.getMessage());
            return false;
        }
    }
}
