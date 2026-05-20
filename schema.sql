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
-- 1. comptes : holds the login identity (phone number only)
-- Authentication is passwordless: a one-time code is sent by SMS
-- (see the otp_codes table below). email/password were removed.
-- Migration for existing databases:
--   ALTER TABLE comptes ADD COLUMN phone VARCHAR(20) UNIQUE AFTER id;
--   -- (backfill phone for existing rows, then:)
--   ALTER TABLE comptes DROP COLUMN email, DROP COLUMN password;
-- =========================================================
CREATE TABLE IF NOT EXISTS comptes (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    phone     VARCHAR(20) NOT NULL UNIQUE,   -- E.164 format, e.g. +212600000000
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- =========================================================
-- 1b. otp_codes : short-lived SMS verification codes.
-- One in-flight code per phone number (PK on phone => upsert/replace).
-- =========================================================
CREATE TABLE IF NOT EXISTS otp_codes (
    phone      VARCHAR(20) PRIMARY KEY,
    code       VARCHAR(6)  NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    attempts   INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- =========================================================
-- 2. users : profile information; id mirrors comptes.id
-- =========================================================
CREATE TABLE IF NOT EXISTS users (
    id                   INT PRIMARY KEY,
    username             VARCHAR(60) NOT NULL UNIQUE,
    display_name         VARCHAR(60) NOT NULL DEFAULT '', -- public self-chosen name (set at registration, editable)
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
-- ALTER TABLE users ADD COLUMN display_name VARCHAR(60) NOT NULL DEFAULT '' AFTER username;
-- (The app also applies this automatically at startup via DBConnection.ensureSchema().)

-- =========================================================
-- 3. contacts : "user_id added contact_id"
-- =========================================================
-- `alias` is the private name the owner gives this contact (since usernames
-- are now phone numbers, each user can label peers however they like).
-- Migration for existing databases:
--   ALTER TABLE contacts ADD COLUMN alias VARCHAR(60) NULL;
CREATE TABLE IF NOT EXISTS contacts (
    user_id    INT NOT NULL,
    contact_id INT NOT NULL,
    alias      VARCHAR(60) NULL,
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

-- group_members now carries an is_admin flag (multiple admins allowed per group).
-- For existing databases, run:
--   ALTER TABLE group_members ADD COLUMN is_admin TINYINT(1) NOT NULL DEFAULT 0;
CREATE TABLE IF NOT EXISTS group_members (
    group_id   INT NOT NULL,
    user_id    INT NOT NULL,
    is_admin   TINYINT(1) NOT NULL DEFAULT 0,
    joined_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id, user_id),
    CONSTRAINT fk_gm_group FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE,
    CONSTRAINT fk_gm_user  FOREIGN KEY (user_id)  REFERENCES users(id)    ON DELETE CASCADE
) ENGINE=InnoDB;

-- =========================================================
-- 5. messages : private and group messages
-- =========================================================
-- The `type` column now also supports SYSTEM messages used for in-chat
-- call summaries ("Voice call • 12m 21s" / "Video call • Missed").
-- The `content` column is widened to MEDIUMTEXT so it can fit base64-encoded
-- group attachments (images, voice notes, files).
-- `client_mid`, `edited_at` and `deleted` support edit/delete history and
-- the stable client-side message identifier we use for in-place updates.
-- Migration for existing databases:
--   ALTER TABLE messages MODIFY COLUMN type ENUM('TEXT','IMAGE','AUDIO','VIDEO','FILE','SYSTEM') DEFAULT 'TEXT';
--   ALTER TABLE messages MODIFY COLUMN content MEDIUMTEXT NOT NULL;
--   ALTER TABLE messages
--       ADD COLUMN client_mid BIGINT NULL,
--       ADD COLUMN edited_at  TIMESTAMP NULL,
--       ADD COLUMN deleted    TINYINT(1) NOT NULL DEFAULT 0,
--       ADD INDEX idx_msg_client_mid (sender_id, client_mid);
CREATE TABLE IF NOT EXISTS messages (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    sender_id   INT NOT NULL,
    receiver_id INT NULL,                 -- NULL when message goes to a group
    group_id    INT NULL,                 -- NULL when private message
    content     MEDIUMTEXT NOT NULL,        -- holds base64 payloads for media (up to 16 MB)
    type        ENUM('TEXT','IMAGE','AUDIO','VIDEO','FILE','SYSTEM') DEFAULT 'TEXT',
    status      ENUM('sent','delivered','read') DEFAULT 'sent',
    client_mid  BIGINT NULL,                -- client-generated id; (sender_id, client_mid) is the addressable key for edit/delete
    edited_at   TIMESTAMP NULL,             -- last edit time; NULL means "never edited"
    deleted     TINYINT(1) NOT NULL DEFAULT 0,
    DATE_Msg    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_msg_priv (sender_id, receiver_id, DATE_Msg),
    INDEX idx_msg_group (group_id, DATE_Msg),
    INDEX idx_msg_client_mid (sender_id, client_mid),
    CONSTRAINT fk_msg_sender   FOREIGN KEY (sender_id)   REFERENCES users(id)    ON DELETE CASCADE,
    CONSTRAINT fk_msg_receiver FOREIGN KEY (receiver_id) REFERENCES users(id)    ON DELETE CASCADE,
    CONSTRAINT fk_msg_group    FOREIGN KEY (group_id)    REFERENCES `groups`(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =========================================================
-- 5b. blocked_users : per-user "I do not want messages from X"
-- (separate from users.is_blocked, which is the global admin flag)
-- =========================================================
CREATE TABLE IF NOT EXISTS blocked_users (
    blocker_id INT NOT NULL,
    blocked_id INT NOT NULL,
    blocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT fk_block_blocker FOREIGN KEY (blocker_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_block_blocked FOREIGN KEY (blocked_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =========================================================
-- 6. calls : call history (audio / video, 1:1 and group)
-- =========================================================
-- "cancelled" was added for the case where the caller hangs up before the
-- recipient picks up. The group_id column makes it possible to log group
-- meetings (caller_id remains the user who initiated). For existing DBs:
--   ALTER TABLE calls MODIFY COLUMN status ENUM('ongoing','ended','missed','refused','cancelled') DEFAULT 'ongoing';
--   ALTER TABLE calls ADD COLUMN group_id INT NULL AFTER receiver_id,
--                     ADD CONSTRAINT fk_call_group FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE;
--   ALTER TABLE calls MODIFY COLUMN receiver_id INT NULL;
CREATE TABLE IF NOT EXISTS calls (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    caller_id   INT NOT NULL,
    receiver_id INT NULL,
    group_id    INT NULL,
    type        ENUM('audio','video') DEFAULT 'audio',
    status      ENUM('ongoing','ended','missed','refused','cancelled') DEFAULT 'ongoing',
    duration    INT DEFAULT 0,
    started_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_call_caller   (caller_id, started_at),
    INDEX idx_call_receiver (receiver_id, started_at),
    INDEX idx_call_group    (group_id, started_at),
    CONSTRAINT fk_call_caller   FOREIGN KEY (caller_id)   REFERENCES users(id)    ON DELETE CASCADE,
    CONSTRAINT fk_call_receiver FOREIGN KEY (receiver_id) REFERENCES users(id)    ON DELETE CASCADE,
    CONSTRAINT fk_call_group    FOREIGN KEY (group_id)    REFERENCES `groups`(id) ON DELETE CASCADE
) ENGINE=InnoDB;


