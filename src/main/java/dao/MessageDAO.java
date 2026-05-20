package dao;

import databases.DBConnection;
import model.Message;
import java.sql.*;
import java.util.*;

public class MessageDAO {

    public boolean saveMessage(Message msg) {
        String sql = "INSERT INTO messages " +
                "(sender_id, receiver_id, group_id, content, type, status, client_mid, DATE_Msg) " +
                "VALUES (?, ?, ?, ?, ?, 'sent', ?, NOW())";
        try (PreparedStatement ps = DBConnection.getInstance()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, msg.getSenderId());
            if (msg.getReceiverId() != 0) ps.setInt(2, msg.getReceiverId());
            else                          ps.setNull(2, Types.INTEGER);
            if (msg.getGroupId() != 0)    ps.setInt(3, msg.getGroupId());
            else                          ps.setNull(3, Types.INTEGER);
            ps.setString(4, msg.getContent());
            ps.setString(5, msg.getType() == null ? "TEXT" : msg.getType().toUpperCase());
            if (msg.getClientMid() == null) ps.setNull(6, Types.BIGINT);
            else                            ps.setLong(6, msg.getClientMid());
            if (ps.executeUpdate() > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) msg.setId(keys.getInt(1));
                }
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Erreur saveMessage: " + e.getMessage());
        }
        return false;
    }

    /** Edit the content of a sender-owned message addressed by (senderId, clientMid). */
    public boolean editMessage(int senderId, long clientMid, String newContent) {
        String sql = "UPDATE messages SET content=?, edited_at=NOW() " +
                "WHERE sender_id=? AND client_mid=? AND deleted=0";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setString(1, newContent);
            ps.setInt(2, senderId);
            ps.setLong(3, clientMid);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur editMessage: " + e.getMessage());
            return false;
        }
    }

    /** Soft-delete a sender-owned message. */
    public boolean deleteMessage(int senderId, long clientMid) {
        String sql = "UPDATE messages SET deleted=1, content='' " +
                "WHERE sender_id=? AND client_mid=?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, senderId);
            ps.setLong(2, clientMid);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur deleteMessage: " + e.getMessage());
            return false;
        }
    }

    public List<Message> getConversation(int user1, int user2) {
        List<Message> msgs = new ArrayList<>();
        String sql = "SELECT * FROM messages " +
                "WHERE ((sender_id=? AND receiver_id=?) " +
                "    OR (sender_id=? AND receiver_id=?)) " +
                "  AND group_id IS NULL " +
                "ORDER BY DATE_Msg ASC";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, user1); ps.setInt(2, user2);
            ps.setInt(3, user2); ps.setInt(4, user1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) msgs.add(mapMessage(rs));
            }
        } catch (SQLException e) {
            System.err.println("Erreur getConversation: " + e.getMessage());
        }
        return msgs;
    }

    public List<Message> getGroupMessages(int groupId) {
        List<Message> msgs = new ArrayList<>();
        String sql = "SELECT * FROM messages WHERE group_id=? ORDER BY DATE_Msg ASC";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) msgs.add(mapMessage(rs));
            }
        } catch (SQLException e) {
            System.err.println("Erreur getGroupMessages: " + e.getMessage());
        }
        return msgs;
    }

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

    public List<Message> getUnread(int receiverId) {
        List<Message> msgs = new ArrayList<>();
        String sql = "SELECT * FROM messages WHERE receiver_id=? AND status != 'read'";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, receiverId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) msgs.add(mapMessage(rs));
            }
        } catch (SQLException e) {
            System.err.println("Erreur getUnread: " + e.getMessage());
        }
        return msgs;
    }

    public List<Message> getPendingDelivery(int receiverId) {
        List<Message> msgs = new ArrayList<>();
        String sql = "SELECT * FROM messages WHERE receiver_id=? AND status='sent' " +
                "AND group_id IS NULL AND deleted=0 ORDER BY DATE_Msg ASC";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setInt(1, receiverId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) msgs.add(mapMessage(rs));
            }
        } catch (SQLException e) {
            System.err.println("Erreur getPendingDelivery: " + e.getMessage());
        }
        return msgs;
    }

    private Message mapMessage(ResultSet rs) throws SQLException {
        int recv = rs.getInt("receiver_id");
        if (rs.wasNull()) recv = 0;
        int gid = rs.getInt("group_id");
        if (rs.wasNull()) gid = 0;
        Message m = new Message(
                rs.getInt("id"),
                rs.getInt("sender_id"),
                recv,
                gid,
                rs.getString("content"),
                rs.getString("type"),
                rs.getString("status")
        );
        Timestamp ts = rs.getTimestamp("DATE_Msg");
        if (ts != null) m.setDateMsg(ts.toLocalDateTime());

        // New columns — wrapped in try/catch so older DBs (no migration yet)
        // still read the legacy schema without blowing up.
        try {
            long cmid = rs.getLong("client_mid");
            if (!rs.wasNull()) m.setClientMid(cmid);
        } catch (SQLException ignored) {}
        try {
            Timestamp ed = rs.getTimestamp("edited_at");
            if (ed != null) m.setEditedAt(ed.toLocalDateTime());
        } catch (SQLException ignored) {}
        try { m.setDeleted(rs.getBoolean("deleted")); } catch (SQLException ignored) {}
        return m;
    }
}
