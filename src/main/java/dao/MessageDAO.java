package dao;

import databases.DBConnection;
import model.Message;
import java.sql.*;
import java.util.*;

public class MessageDAO {

    // Sauvegarder un message
    public boolean saveMessage(Message msg) {
        String sql = "INSERT INTO messages " +
                "(sender_id, receiver_id, group_id, content, type, status, DATE_Msg) " +
                "VALUES (?, ?, ?, ?, ?, 'sent', NOW())";
        try (PreparedStatement ps = DBConnection.getInstance()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, msg.getSenderId());
            if (msg.getReceiverId() != 0) ps.setInt(2, msg.getReceiverId());
            else                          ps.setNull(2, Types.INTEGER);
            if (msg.getGroupId() != 0)   ps.setInt(3, msg.getGroupId());
            else                          ps.setNull(3, Types.INTEGER);
            ps.setString(4, msg.getContent());
            ps.setString(5, msg.getType());
            if (ps.executeUpdate() > 0) {
                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) msg.setId(keys.getInt(1));
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Erreur saveMessage: " + e.getMessage());
        }
        return false;
    }

    // Historique conversation privée
    public List<Message> getConversation(int user1, int user2) {
        List<Message> msgs = new ArrayList<>();
        String sql = "SELECT * FROM messages " +
                "WHERE (sender_id=? AND receiver_id=?) " +
                "   OR (sender_id=? AND receiver_id=?) " +
                "ORDER BY DATE_Msg ASC";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, user1); ps.setInt(2, user2);
            ps.setInt(3, user2); ps.setInt(4, user1);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) msgs.add(mapMessage(rs));
        } catch (SQLException e) {
            System.err.println("Erreur getConversation: " + e.getMessage());
        }
        return msgs;
    }

    // Historique groupe
    public List<Message> getGroupMessages(int groupId) {
        List<Message> msgs = new ArrayList<>();
        String sql = "SELECT * FROM messages WHERE group_id=? ORDER BY DATE_Msg ASC";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) msgs.add(mapMessage(rs));
        } catch (SQLException e) {
            System.err.println("Erreur getGroupMessages: " + e.getMessage());
        }
        return msgs;
    }

    // Mettre à jour statut sent→delivered→read
    public void updateStatus(int messageId, String status) {
        String sql = "UPDATE messages SET status=? WHERE id=?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, messageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erreur updateStatus: " + e.getMessage());
        }
    }

    // Marquer conversation comme lue
    public void markConversationAsRead(int userId, int targetId) {
        String sql = "UPDATE messages SET status='read' WHERE receiver_id=? AND sender_id=?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, targetId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erreur markConversationAsRead: " + e.getMessage());
        }
    }

    // Messages non lus
    public List<Message> getUnread(int receiverId) {
        List<Message> msgs = new ArrayList<>();
        String sql = "SELECT * FROM messages WHERE receiver_id=? AND status != 'read'";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, receiverId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) msgs.add(mapMessage(rs));
        } catch (SQLException e) {
            System.err.println("Erreur getUnread: " + e.getMessage());
        }
        return msgs;
    }

    private Message mapMessage(ResultSet rs) throws SQLException {
        return new Message(
                rs.getInt("id"),
                rs.getInt("sender_id"),
                rs.getInt("receiver_id"),
                rs.getInt("group_id"),
                rs.getString("content"),
                rs.getString("type"),
                rs.getString("status")
        );
    }
}