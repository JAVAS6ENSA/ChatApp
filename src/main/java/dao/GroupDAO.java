package dao;

import databases.DBConnection;
import model.Group;
import java.sql.*;
import java.util.*;

public class GroupDAO {

    public int createGroup(String name, int createdBy) {
        Connection cx = DBConnection.getInstance();
        String sql = "INSERT INTO `groups` (name, created_by) VALUES (?, ?)";
        try (PreparedStatement ps = cx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setInt(2, createdBy);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int gid = keys.getInt(1);
                    addMember(gid, createdBy, true);
                    return gid;
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur createGroup: " + e.getMessage());
        }
        return -1;
    }

    public boolean addMember(int groupId, int userId, boolean isAdmin) {
        String sql = "INSERT INTO group_members (group_id, user_id, is_admin) " +
                "VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE is_admin = GREATEST(is_admin, VALUES(is_admin))";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            ps.setInt(3, isAdmin ? 1 : 0);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur addMember: " + e.getMessage());
            return false;
        }
    }

    public boolean removeMember(int groupId, int userId) {
        String sql = "DELETE FROM group_members WHERE group_id=? AND user_id=?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur removeMember: " + e.getMessage());
            return false;
        }
    }

    public boolean setAdmin(int groupId, int userId, boolean isAdmin) {
        String sql = "UPDATE group_members SET is_admin=? WHERE group_id=? AND user_id=?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, isAdmin ? 1 : 0);
            ps.setInt(2, groupId);
            ps.setInt(3, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur setAdmin: " + e.getMessage());
            return false;
        }
    }

    public boolean isAdmin(int groupId, int userId) {
        String sql = "SELECT is_admin FROM group_members WHERE group_id=? AND user_id=?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        } catch (SQLException e) {
            System.err.println("Erreur isAdmin: " + e.getMessage());
            return false;
        }
    }

    public boolean isMember(int groupId, int userId) {
        String sql = "SELECT 1 FROM group_members WHERE group_id=? AND user_id=?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Erreur isMember: " + e.getMessage());
            return false;
        }
    }

    public List<int[]> getMembersWithAdminFlag(int groupId) {
        List<int[]> rows = new ArrayList<>();
        String sql = "SELECT gm.user_id, gm.is_admin " +
                "FROM group_members gm JOIN users u ON u.id = gm.user_id " +
                "WHERE gm.group_id=? " +
                "ORDER BY gm.is_admin DESC, u.username";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(new int[]{ rs.getInt(1), rs.getInt(2) });
            }
        } catch (SQLException e) {
            System.err.println("Erreur getMembersWithAdminFlag: " + e.getMessage());
        }
        return rows;
    }

    public List<Integer> getMembers(int groupId) {
        List<Integer> ids = new ArrayList<>();
        for (int[] row : getMembersWithAdminFlag(groupId)) ids.add(row[0]);
        return ids;
    }

    public void deleteGroup(int groupId) {
        String sql = "DELETE FROM `groups` WHERE id=?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erreur deleteGroup: " + e.getMessage());
        }
    }


    public List<Group> getUserGroups(int userId) {
        List<Group> groups = new ArrayList<>();
        String sql = "SELECT g.* FROM `groups` g " +
                "JOIN group_members gm ON gm.group_id = g.id " +
                "WHERE gm.user_id = ? " +
                "ORDER BY g.name";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Group g = new Group(rs.getInt("id"), rs.getString("name"), rs.getInt("created_by"));
                    groups.add(g);
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur getUserGroups: " + e.getMessage());
            return groups;
        }
        for (Group g : groups) {
            for (int[] row : getMembersWithAdminFlag(g.getId())) {
                g.addMember(row[0]);
                if (row[1] == 1) g.promote(row[0]);
            }
        }
        return groups;
    }

    public Group getGroup(int groupId) {
        String sql = "SELECT * FROM `groups` WHERE id=?";
        Group g = null;
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    g = new Group(rs.getInt("id"), rs.getString("name"), rs.getInt("created_by"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur getGroup: " + e.getMessage());
            return null;
        }
        if (g == null) return null;
        for (int[] row : getMembersWithAdminFlag(g.getId())) {
            g.addMember(row[0]);
            if (row[1] == 1) g.promote(row[0]);
        }
        return g;
    }
}
