package dao;

import databases.DBConnection;
import model.Message;
import java.sql.*;
import java.util.*;

public class MessageDAO {

    // Sauvegarder un message
    public boolean saveMessage(Message msg) {
        String sql = "INSERT INTO messages " +
                "(sender_id, receiver_id, group_id, content, type, status, DATE_Msg, client_mid) " +
                "VALUES (?, ?, ?, ?, ?, 'sent', NOW(), ?)";
        try (PreparedStatement ps = DBConnection.getInstance()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, msg.getSenderId());
            if (msg.getReceiverId() != 0) ps.setInt(2, msg.getReceiverId());
            else                          ps.setNull(2, Types.INTEGER);
            if (msg.getGroupId() != 0)   ps.setInt(3, msg.getGroupId());
            else                          ps.setNull(3, Types.INTEGER);
            ps.setString(4, msg.getContent());
            ps.setString(5, msg.getType());
            if (msg.getClientMid() > 0) ps.setLong(6, msg.getClientMid());
            else                        ps.setNull(6, Types.BIGINT);
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

    // Messages still pending delivery (recipient was offline when sent)
    public List<Message> getPendingDelivery(int receiverId) {
        List<Message> msgs = new ArrayList<>();
        String sql = "SELECT * FROM messages WHERE receiver_id=? AND status='sent' ORDER BY DATE_Msg ASC";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, receiverId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) msgs.add(mapMessage(rs));
        } catch (SQLException e) {
            System.err.println("Erreur getPendingDelivery: " + e.getMessage());
        }
        return msgs;
    }

    // ─── Edit / soft-delete ────────────────────────────────
    // Both operations are scoped by (client_mid, sender_id) so the
    // server can authorise the requester without trusting them with
    // a raw DB id. The same DAO methods are used by the private
    // chat path today and will be reused by the group chat path
    // (GroupConversationService) when groups are wired in.

    public boolean editMessage(long clientMid, int senderId, String newContent) {
        String sql = "UPDATE messages SET content=?, is_edited=1, edited_at=NOW() " +
                "WHERE client_mid=? AND sender_id=? AND is_deleted=0";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setString(1, newContent);
            ps.setLong(2, clientMid);
            ps.setInt(3, senderId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur editMessage: " + e.getMessage());
            return false;
        }
    }

    public boolean softDeleteMessage(long clientMid, int senderId) {
        String sql = "UPDATE messages SET content='', type='DELETED', is_deleted=1, edited_at=NOW() " +
                "WHERE client_mid=? AND sender_id=?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setLong(1, clientMid);
            ps.setInt(2, senderId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur softDeleteMessage: " + e.getMessage());
            return false;
        }
    }

    public Message getByClientMid(long clientMid, int senderId) {
        String sql = "SELECT * FROM messages WHERE client_mid=? AND sender_id=?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setLong(1, clientMid);
            ps.setInt(2, senderId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapMessage(rs);
        } catch (SQLException e) {
            System.err.println("Erreur getByClientMid: " + e.getMessage());
        }
        return null;
    }

    private Message mapMessage(ResultSet rs) throws SQLException {
        Message m = new Message(
                rs.getInt("id"),
                rs.getInt("sender_id"),
                rs.getInt("receiver_id"),
                rs.getInt("group_id"),
                rs.getString("content"),
                rs.getString("type"),
                rs.getString("status")
        );
        // The new v2 columns may be missing on a DB that hasn't been migrated.
        // Tolerate that so the app keeps booting and just reports !edited / !deleted.
        try { m.setClientMid(rs.getLong("client_mid")); } catch (SQLException ignored) {}
        try { m.setEdited(rs.getBoolean("is_edited")); }  catch (SQLException ignored) {}
        try { m.setDeleted(rs.getBoolean("is_deleted")); } catch (SQLException ignored) {}
        return m;
    }
}
