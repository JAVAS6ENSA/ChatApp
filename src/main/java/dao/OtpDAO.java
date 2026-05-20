package dao;

import databases.DBConnection;
import java.sql.*;

/**
 * Persistence for the short-lived SMS verification codes.
 * One in-flight code per phone number; a resend simply REPLACEs the row.
 */
public class OtpDAO {

    /** A code as stored in the DB. */
    public static class Otp {
        public final String code;
        public final Timestamp expiresAt;
        public final int attempts;
        Otp(String code, Timestamp expiresAt, int attempts) {
            this.code = code; this.expiresAt = expiresAt; this.attempts = attempts;
        }
    }

    /** Store (or replace) the code for a phone, valid for ttlMinutes. */
    public boolean save(String phone, String code, int ttlMinutes) {
        String sql = "REPLACE INTO otp_codes (phone, code, expires_at, attempts) " +
                     "VALUES (?, ?, DATE_ADD(NOW(), INTERVAL ? MINUTE), 0)";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setString(1, phone);
            ps.setString(2, code);
            ps.setInt(3, ttlMinutes);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur OtpDAO.save: " + e.getMessage());
            return false;
        }
    }

    public Otp get(String phone) {
        String sql = "SELECT code, expires_at, attempts FROM otp_codes WHERE phone = ?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setString(1, phone);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new Otp(rs.getString("code"),
                               rs.getTimestamp("expires_at"),
                               rs.getInt("attempts"));
            }
        } catch (SQLException e) {
            System.err.println("Erreur OtpDAO.get: " + e.getMessage());
        }
        return null;
    }

    public void incrementAttempts(String phone) {
        String sql = "UPDATE otp_codes SET attempts = attempts + 1 WHERE phone = ?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setString(1, phone);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erreur OtpDAO.incrementAttempts: " + e.getMessage());
        }
    }

    public void delete(String phone) {
        String sql = "DELETE FROM otp_codes WHERE phone = ?";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setString(1, phone);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erreur OtpDAO.delete: " + e.getMessage());
        }
    }
}
