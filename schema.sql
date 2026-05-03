-- =========================================================
-- ChatApp - MySQL / MariaDB schema and queries
-- Database name expected by the app: chat_app
-- (matches DBConnection: jdbc:mariadb://localhost:3306/chat_app)
-- =========================================================

CREATE DATABASE IF NOT EXISTS chat_app
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE chat_app;

-- =========================================================
-- 1. comptes : holds login credentials (email + password)
-- =========================================================
CREATE TABLE IF NOT EXISTS comptes (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    email     VARCHAR(120) NOT NULL UNIQUE,
    password  VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- =========================================================
-- 2. users : profile information; id mirrors comptes.id
-- =========================================================
CREATE TABLE IF NOT EXISTS users (
    id                   INT PRIMARY KEY,
    username             VARCHAR(60) NOT NULL UNIQUE,
    status               ENUM('online','offline','away') DEFAULT 'offline',
    is_blocked           TINYINT(1) DEFAULT 0,
    role                 ENUM('user','admin') DEFAULT 'user',
    join_date            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    bio                  VARCHAR(255) DEFAULT '',
    profile_picture      VARCHAR(500) DEFAULT '',  -- legacy: file path or URL (kept for backward compat)
    profile_picture_data LONGBLOB DEFAULT NULL,    -- raw image bytes; portable across machines
    CONSTRAINT fk_user_compte FOREIGN KEY (id)
        REFERENCES comptes(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Migration for existing databases (run once on already-created DBs):
-- ALTER TABLE users ADD COLUMN profile_picture_data LONGBLOB DEFAULT NULL AFTER profile_picture;

-- =========================================================
-- 3. contacts : "user_id added contact_id"
-- =========================================================
CREATE TABLE IF NOT EXISTS contacts (
    user_id    INT NOT NULL,
    contact_id INT NOT NULL,
    added_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, contact_id),
    CONSTRAINT fk_contact_user FOREIGN KEY (user_id)    REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_contact_peer FOREIGN KEY (contact_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =========================================================
-- 4. groups : group conversations
-- =========================================================
CREATE TABLE IF NOT EXISTS `groups` (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(80) NOT NULL,
    created_by  INT NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_group_owner FOREIGN KEY (created_by)
        REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS group_members (
    group_id   INT NOT NULL,
    user_id    INT NOT NULL,
    joined_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id, user_id),
    CONSTRAINT fk_gm_group FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE,
    CONSTRAINT fk_gm_user  FOREIGN KEY (user_id)  REFERENCES users(id)    ON DELETE CASCADE
) ENGINE=InnoDB;

-- =========================================================
-- 5. messages : private and group messages
-- =========================================================
CREATE TABLE IF NOT EXISTS messages (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    sender_id   INT NOT NULL,
    receiver_id INT NULL,                 -- NULL when message goes to a group
    group_id    INT NULL,                 -- NULL when private message
    content     TEXT NOT NULL,
    type        ENUM('TEXT','IMAGE','AUDIO','VIDEO','FILE') DEFAULT 'TEXT',
    status      ENUM('sent','delivered','read') DEFAULT 'sent',
    DATE_Msg    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_msg_priv (sender_id, receiver_id, DATE_Msg),
    INDEX idx_msg_group (group_id, DATE_Msg),
    CONSTRAINT fk_msg_sender   FOREIGN KEY (sender_id)   REFERENCES users(id)    ON DELETE CASCADE,
    CONSTRAINT fk_msg_receiver FOREIGN KEY (receiver_id) REFERENCES users(id)    ON DELETE CASCADE,
    CONSTRAINT fk_msg_group    FOREIGN KEY (group_id)    REFERENCES `groups`(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =========================================================
-- 6. calls : call history (audio / video)
-- =========================================================
CREATE TABLE IF NOT EXISTS calls (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    caller_id   INT NOT NULL,
    receiver_id INT NOT NULL,
    type        ENUM('audio','video') DEFAULT 'audio',
    status      ENUM('ongoing','ended','missed','refused') DEFAULT 'ongoing',
    duration    INT DEFAULT 0,
    started_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_call_caller   (caller_id, started_at),
    INDEX idx_call_receiver (receiver_id, started_at),
    CONSTRAINT fk_call_caller   FOREIGN KEY (caller_id)   REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_call_receiver FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;


-- =========================================================
-- ============   COMMON QUERIES (cheat-sheet)   ===========
-- =========================================================

-- ---------- AUTH / REGISTER ----------

-- 1. Register a new account (transactional in app)
-- INSERT INTO comptes (email, password) VALUES (?, ?);          -- returns ID
-- INSERT INTO users   (id, username, status, is_blocked, role)
--   VALUES (?, ?, 'offline', 0, 'user');

-- 2. Login (UserDAO.login)
SELECT u.*, c.email
FROM   users u
JOIN   comptes c ON c.id = u.id
WHERE  (c.email = ? OR u.username = ?)
  AND   c.password = ?
  AND   u.is_blocked = 0;

-- ---------- USER ----------

-- 3. Find a user by username
SELECT u.*, c.email
FROM   users u JOIN comptes c ON c.id = u.id
WHERE  u.username = ?;

-- 4. Find a user by id
SELECT u.*, c.email
FROM   users u JOIN comptes c ON c.id = u.id
WHERE  u.id = ?;

-- 5. List online users
SELECT u.*, c.email
FROM   users u JOIN comptes c ON c.id = u.id
WHERE  u.status = 'online' AND u.is_blocked = 0;

-- 6. Update online status
UPDATE users SET status = ? WHERE id = ?;

-- 7. Block / unblock user
UPDATE users SET is_blocked = ? WHERE id = ?;

-- 8. Update profile (bio + profile picture path)
UPDATE users
SET    bio = ?, profile_picture = ?
WHERE  id = ?;

-- 8b. Update profile picture as raw bytes (portable: works on any client machine)
UPDATE users SET profile_picture_data = ? WHERE id = ?;

-- 9. Get profile picture only
SELECT profile_picture, profile_picture_data FROM users WHERE username = ?;

-- 10. Delete a user (cascades to all related rows)
DELETE FROM comptes WHERE id = ?;

-- ---------- CONTACTS ----------

-- 11. Add a contact (idempotent)
INSERT IGNORE INTO contacts (user_id, contact_id) VALUES (?, ?);

-- 12. Remove a contact
DELETE FROM contacts WHERE user_id = ? AND contact_id = ?;

-- 13. List contacts of a user (with their online status)
SELECT u.id, u.username, u.status, u.is_blocked, u.profile_picture, c.added_at
FROM   contacts c
JOIN   users u ON u.id = c.contact_id
WHERE  c.user_id = ?
ORDER  BY u.username;

-- 14. Check if a contact already exists
SELECT 1 FROM contacts WHERE user_id = ? AND contact_id = ?;

-- ---------- MESSAGES ----------

-- 15. Send a private message
INSERT INTO messages (sender_id, receiver_id, content, type, status, DATE_Msg)
VALUES (?, ?, ?, 'TEXT', 'sent', NOW());

-- 16. Send a group message
INSERT INTO messages (sender_id, group_id, content, type, status, DATE_Msg)
VALUES (?, ?, ?, 'TEXT', 'sent', NOW());

-- 17. Private conversation history
SELECT *
FROM   messages
WHERE  (sender_id = ? AND receiver_id = ?)
   OR  (sender_id = ? AND receiver_id = ?)
ORDER  BY DATE_Msg ASC;

-- 18. Group history
SELECT *
FROM   messages
WHERE  group_id = ?
ORDER  BY DATE_Msg ASC;

-- 19. Mark a single message status
UPDATE messages SET status = ? WHERE id = ?;

-- 20. Mark a whole conversation read
UPDATE messages
SET    status = 'read'
WHERE  receiver_id = ? AND sender_id = ?;

-- 21. Get all unread messages for a user
SELECT *
FROM   messages
WHERE  receiver_id = ? AND status <> 'read';

-- ---------- GROUPS ----------

-- 22. Create a group
INSERT INTO `groups` (name, created_by) VALUES (?, ?);

-- 23. Add / remove member
INSERT IGNORE INTO group_members (group_id, user_id) VALUES (?, ?);
DELETE FROM group_members WHERE group_id = ? AND user_id = ?;

-- 24. List a user's groups
SELECT g.*
FROM   `groups` g
JOIN   group_members gm ON gm.group_id = g.id
WHERE  gm.user_id = ?;

-- 25. List members of a group
SELECT u.id, u.username
FROM   group_members gm
JOIN   users u ON u.id = gm.user_id
WHERE  gm.group_id = ?;

-- 26. Delete a group (cascades to group_members + group messages)
DELETE FROM `groups` WHERE id = ?;

-- ---------- CALLS ----------

-- 27. Start a call (returns generated id)
INSERT INTO calls (caller_id, receiver_id, type, status)
VALUES (?, ?, ?, 'ongoing');

-- 28. End a call
UPDATE calls SET status = 'ended', duration = ? WHERE id = ?;

-- 29. Mark call missed / refused
UPDATE calls SET status = 'missed'  WHERE id = ?;
UPDATE calls SET status = 'refused' WHERE id = ?;

-- 30. Call history of a user (sender or receiver)
SELECT *
FROM   calls
WHERE  caller_id = ? OR receiver_id = ?
ORDER  BY started_at DESC;

-- 31. Last 20 audio calls for a user
SELECT *
FROM   calls
WHERE  (caller_id = ? OR receiver_id = ?)
  AND   type = 'audio'
ORDER  BY started_at DESC
LIMIT  20;

-- ---------- USEFUL READS FOR THE UI ----------

-- 32. Sidebar: contacts + last-message preview + unread count
SELECT  u.id,
        u.username,
        u.status,
        u.profile_picture,
        m.content                        AS last_message,
        m.DATE_Msg                       AS last_message_at,
        (SELECT COUNT(*)
           FROM messages
          WHERE sender_id = u.id
            AND receiver_id = ?
            AND status <> 'read')        AS unread_count
FROM    contacts c
JOIN    users u ON u.id = c.contact_id
LEFT JOIN messages m
       ON m.id = (
            SELECT MAX(id) FROM messages
            WHERE (sender_id = u.id   AND receiver_id = ?)
               OR (sender_id = ?      AND receiver_id = u.id)
       )
WHERE   c.user_id = ?
ORDER  BY last_message_at DESC;

-- 33. Contact info dialog (header row)
SELECT  u.username,
        c.email,
        u.status,
        u.bio,
        u.profile_picture,
        u.join_date
FROM    users u
JOIN    comptes c ON c.id = u.id
WHERE   u.username = ?;
