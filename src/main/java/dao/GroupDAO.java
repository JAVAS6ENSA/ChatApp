package dao;

import databases.DBConnection;
import model.Group;
import java.sql.*;
import java.util.*;

public class GroupDAO {

    // Créer un groupe
    public int createGroup(String name, int createdBy) {
        String sql = "INSERT INTO `groups` (name, created_by) VALUES (?, ?)";
        try (PreparedStatement ps = DBConnection.getInstance()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setInt(2, createdBy);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) {
            System.err.println("Erreur createGroup: " + e.getMessage());
        }
        return -1;
    }

    // Ajouter membre
    public void addMember(int groupId, int userId) {
        String sql = "INSERT IGNORE INTO group_members (group_id, user_id) VALUES (?, ?)";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erreur addMember: " + e.getMessage());
        }
    }

    // Retirer membre
    public void removeMember(int groupId, int userId) {
        String sql = "DELETE FROM group_members WHERE group_id=? AND user_id=?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erreur removeMember: " + e.getMessage());
        }
    }

    // Liste membres d'un groupe
    public List<Integer> getMembers(int groupId) {
        List<Integer> ids = new ArrayList<>();
        String sql = "SELECT user_id FROM group_members WHERE group_id=?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) ids.add(rs.getInt("user_id"));
        } catch (SQLException e) {
            System.err.println("Erreur getMembers: " + e.getMessage());
        }
        return ids;
    }

    // Supprimer un groupe
    public void deleteGroup(int groupId) {
        String sql = "DELETE FROM `groups` WHERE id=?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erreur deleteGroup: " + e.getMessage());
        }
    }

    // Groupes d'un utilisateur
    public List<Group> getUserGroups(int userId) {
        List<Group> groups = new ArrayList<>();
        String sql = "SELECT g.* FROM `groups` g " +
                "JOIN group_members gm ON gm.group_id = g.id " +
                "WHERE gm.user_id = ?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Group group = new Group(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("created_by")
                );
                group.setCreatedAt(rs.getTimestamp("created_at"));
                groups.add(group);
            }
        } catch (SQLException e) {
            System.err.println("Erreur getUserGroups: " + e.getMessage());
        }
        return groups;
    }
    public Group getGroupById(int groupId) {
        String sql = "SELECT * FROM `groups` WHERE id=?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Group g = new Group(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getInt("created_by")
                );
                g.setCreatedAt(rs.getTimestamp("created_at"));
                return g;
            }
        } catch (SQLException e) {
            System.err.println("Erreur getGroupById: " + e.getMessage());
        }
        return null;
    }
}
