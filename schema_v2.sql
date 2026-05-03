-- =========================================================
-- ChatApp - schema migration v2
-- Adds support for editing and (soft) deleting messages.
-- Works for both private chats and group chats — the same
-- columns are reused once group messaging is wired in.
--
-- USAGE
--   * Fresh install:  run schema.sql, then this file.
--   * Existing DB:    only run the ALTERs in section [B].
--
-- The migration is idempotent on MariaDB / MySQL >= 10.0.2
-- thanks to the IF NOT EXISTS clauses.
-- =========================================================

USE chat_app;

-- =========================================================
-- [A] New / updated table definition (CREATE-IF-NOT-EXISTS)
--     Use this only on a fresh database.
-- =========================================================
CREATE TABLE IF NOT EXISTS messages (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    sender_id   INT NOT NULL,
    receiver_id INT NULL,
    group_id    INT NULL,
    content     TEXT NOT NULL,
    type        ENUM('TEXT','IMAGE','AUDIO','VIDEO','FILE','DELETED') DEFAULT 'TEXT',
    status      ENUM('sent','delivered','read') DEFAULT 'sent',
    DATE_Msg    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- new in v2
    client_mid  BIGINT NULL,        -- client-generated id (System.currentTimeMillis())
    is_edited   TINYINT(1) DEFAULT 0,
    is_deleted  TINYINT(1) DEFAULT 0,
    edited_at   TIMESTAMP NULL,

    INDEX idx_msg_priv  (sender_id, receiver_id, DATE_Msg),
    INDEX idx_msg_group (group_id, DATE_Msg),
    INDEX idx_msg_mid   (client_mid),
    CONSTRAINT fk_msg_sender   FOREIGN KEY (sender_id)   REFERENCES users(id)    ON DELETE CASCADE,
    CONSTRAINT fk_msg_receiver FOREIGN KEY (receiver_id) REFERENCES users(id)    ON DELETE CASCADE,
    CONSTRAINT fk_msg_group    FOREIGN KEY (group_id)    REFERENCES `groups`(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =========================================================
-- [B] Migration of an existing database
--     Run these once on a previously-created chat_app DB.
-- =========================================================
ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS client_mid BIGINT NULL          AFTER DATE_Msg,
    ADD COLUMN IF NOT EXISTS is_edited  TINYINT(1) DEFAULT 0 AFTER client_mid,
    ADD COLUMN IF NOT EXISTS is_deleted TINYINT(1) DEFAULT 0 AFTER is_edited,
    ADD COLUMN IF NOT EXISTS edited_at  TIMESTAMP NULL       AFTER is_deleted;

-- DELETED needs to be a valid type value (used as the sentinel
-- for soft-deleted bubbles so receivers can render "message deleted").
ALTER TABLE messages
    MODIFY COLUMN type ENUM('TEXT','IMAGE','AUDIO','VIDEO','FILE','DELETED') DEFAULT 'TEXT';

ALTER TABLE messages
    ADD INDEX IF NOT EXISTS idx_msg_mid (client_mid);


-- =========================================================
-- [C] New queries used by MessageDAO + clientHandler
-- =========================================================

-- Save a message (now also persists the client mid so edit/delete
-- requests can be matched without round-tripping the DB id).
-- INSERT INTO messages
--   (sender_id, receiver_id, group_id, content, type, status, DATE_Msg, client_mid)
-- VALUES (?, ?, ?, ?, ?, 'sent', NOW(), ?);

-- Edit a message's content. Only the original sender is allowed
-- (enforced in DAO + server). Group admins get the same right
-- once GroupConversationService overrides canEdit().
-- UPDATE messages
--    SET content    = ?,
--        is_edited  = 1,
--        edited_at  = NOW()
--  WHERE client_mid = ?
--    AND sender_id  = ?
--    AND is_deleted = 0;

-- Soft-delete a message. We keep the row so the conversation
-- history stays consistent on every connected client; only the
-- content is cleared and the type is flipped to DELETED so the
-- UI can render a tombstone.
-- UPDATE messages
--    SET content    = '',
--        type       = 'DELETED',
--        is_deleted = 1,
--        edited_at  = NOW()
--  WHERE client_mid = ?
--    AND sender_id  = ?;

-- Lookup a message id from the client-generated mid + sender.
-- Used by the server when relaying status updates.
-- SELECT id FROM messages WHERE client_mid = ? AND sender_id = ?;
