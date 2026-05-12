package dao;

import databases.DBConnection;
import model.Call;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class CallDAO {

    // Démarrer un appel
    public int startCall(int callerId, int receiverId, String type) {
        String sql = "INSERT INTO calls (caller_id, receiver_id, type, status) " +
                "VALUES (?, ?, ?, 'ongoing')";
        try (PreparedStatement ps = DBConnection.getInstance()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, callerId);
            ps.setInt(2, receiverId);
            ps.setString(3, type);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) {
            System.err.println("Erreur startCall: " + e.getMessage());
        }
        return -1;
    }

    // Terminer un appel
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

    // Appel manqué
    public void missedCall(int callId) {
        String sql = "UPDATE calls SET status='missed' WHERE id=?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, callId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erreur missedCall: " + e.getMessage());
        }
    }

    // Historique appels d'un utilisateur
    public List<Call> getCallHistory(int userId) {
        List<Call> calls = new ArrayList<>();
        String sql = "SELECT * FROM calls " +
                "WHERE caller_id=? OR receiver_id=? " +
                "ORDER BY started_at DESC";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) calls.add(mapCall(rs));
        } catch (SQLException e) {
            System.err.println("Erreur getCallHistory: " + e.getMessage());
        }
        return calls;
    }

    private Call mapCall(ResultSet rs) throws SQLException {
        return new Call(
                rs.getInt("id"),
                rs.getInt("caller_id"),
                rs.getInt("receiver_id"),
                rs.getString("type"),
                rs.getInt("duration"),
                rs.getTimestamp("started_at").toLocalDateTime(),
                rs.getString("status")
        );
    }
}
