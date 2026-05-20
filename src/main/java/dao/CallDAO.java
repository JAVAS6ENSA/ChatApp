package dao;

import databases.DBConnection;
import model.Call;
import java.sql.*;
import java.util.*;

public class CallDAO {

    /** Insert a 1:1 call row in "ongoing" state. Returns generated id, or -1. */
    public int startCall(int callerId, int receiverId, String type) {
        String sql = "INSERT INTO calls (caller_id, receiver_id, type, status) " +
                "VALUES (?, ?, ?, 'ongoing')";
        try (PreparedStatement ps = DBConnection.getInstance()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, callerId);
            ps.setInt(2, receiverId);
            ps.setString(3, type);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Erreur startCall: " + e.getMessage());
        }
        return -1;
    }

    /** Insert a group call row. Returns generated id, or -1. */
    public int startGroupCall(int callerId, int groupId, String type) {
        String sql = "INSERT INTO calls (caller_id, group_id, type, status) " +
                "VALUES (?, ?, ?, 'ongoing')";
        try (PreparedStatement ps = DBConnection.getInstance()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, callerId);
            ps.setInt(2, groupId);
            ps.setString(3, type);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Erreur startGroupCall: " + e.getMessage());
        }
        return -1;
    }

    public void endCall(int callId, int duration) {
        String sql = "UPDATE calls SET status='ended', duration=? WHERE id=?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, duration);
            ps.setInt(2, callId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erreur endCall: " + e.getMessage());
        }
    }

    public void setStatus(int callId, String status) {
        String sql = "UPDATE calls SET status=? WHERE id=?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, callId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erreur setStatus(call): " + e.getMessage());
        }
    }

    public void missedCall(int callId)   { setStatus(callId, "missed"); }
    public void refusedCall(int callId)  { setStatus(callId, "refused"); }
    public void cancelledCall(int callId){ setStatus(callId, "cancelled"); }

    /** History for a user: 1:1 calls they were part of OR groups they belong to. */
    public List<Call> getCallHistory(int userId) {
        List<Call> calls = new ArrayList<>();
        String sql = "SELECT DISTINCT c.* FROM calls c " +
                "LEFT JOIN group_members gm " +
                "       ON gm.group_id = c.group_id AND gm.user_id = ? " +
                "WHERE c.caller_id = ? " +
                "   OR c.receiver_id = ? " +
                "   OR gm.user_id IS NOT NULL " +
                "ORDER BY c.started_at DESC";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            ps.setInt(3, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) calls.add(mapCall(rs));
            }
        } catch (SQLException e) {
            System.err.println("Erreur getCallHistory: " + e.getMessage());
        }
        return calls;
    }

    private Call mapCall(ResultSet rs) throws SQLException {
        int recv = rs.getInt("receiver_id");
        if (rs.wasNull()) recv = 0;
        int gid = rs.getInt("group_id");
        if (rs.wasNull()) gid = 0;
        Call c = new Call(
                rs.getInt("id"),
                rs.getInt("caller_id"),
                recv,
                rs.getString("type"),
                rs.getInt("duration"),
                rs.getTimestamp("started_at").toLocalDateTime(),
                rs.getString("status")
        );
        c.setGroupId(gid);
        return c;
    }
}
