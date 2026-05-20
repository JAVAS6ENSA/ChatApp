package dao;

import databases.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;


public class BlockDAO {

    public boolean block(int blockerId, int blockedId) {
        if (blockerId == blockedId) return false;
        String sql = "INSERT IGNORE INTO blocked_users (blocker_id, blocked_id) VALUES (?, ?)";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, blockerId);
            ps.setInt(2, blockedId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur block: " + e.getMessage());
            return false;
        }
    }

    public boolean unblock(int blockerId, int blockedId) {
        String sql = "DELETE FROM blocked_users WHERE blocker_id=? AND blocked_id=?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, blockerId);
            ps.setInt(2, blockedId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur unblock: " + e.getMessage());
            return false;
        }
    }

    public boolean isBlocked(int blockerId, int blockedId) {
        String sql = "SELECT 1 FROM blocked_users WHERE blocker_id=? AND blocked_id=?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, blockerId);
            ps.setInt(2, blockedId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Erreur isBlocked: " + e.getMessage());
            return false;
        }
    }

    public List<String> listBlockedUsernames(int blockerId) {
        List<String> out = new ArrayList<>();
        String sql = "SELECT u.username FROM blocked_users b JOIN users u ON u.id = b.blocked_id " +
                "WHERE b.blocker_id=? ORDER BY u.username";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, blockerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        } catch (SQLException e) {
            System.err.println("Erreur listBlockedUsernames: " + e.getMessage());
        }
        return out;
    }
}
