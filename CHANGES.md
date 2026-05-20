# ChatApp — Changes summary

This release fixes the incoming-call cancellation bug, adds full group
chats / meetings, and ships a Call History feature with SQL persistence.
Everything below preserves the existing 1:1 chat / call behavior.

## 1. Incoming-call cancellation bug (fixed)

### Symptoms
- When the caller cancelled before the recipient picked up, the recipient
  popup stayed up forever and the per-user call state stuck in "ringing."
- The call window sometimes labelled the peer as "Peer" instead of the real
  username (timing race in the controller).

### Fixes
- New protocol message `CALL_CANCEL` (client→server) and `CALL_CANCELLED`
  (server→recipient).
- `AppelManager.annulerAppel(...)` cancels a `RINGING` call from the caller
  side; `terminerAppel` also accepts the `RINGING` state so dropping a still-
  ringing call no longer fails silently. (`src/main/java/server/AppelManager.java`)
- `clientHandler.traiterAnnulerAppel` forwards the cancellation to the
  recipient, persists the call row as `cancelled`, and replies `CALL_CANCEL_OK`.
- `START_AUDIO` now carries the peer's username
  (`START_AUDIO|<remoteIp>|<remotePort>|<peerUsername>`) so the receiver can
  pin a stable name onto the `CallManager` window — defeating the race that
  produced the "Peer" label.
- The incoming-call dialog is now non-modal (`alert.show()` with `setOnHidden`
  callback), so the controller can dismiss it when `CALL_CANCELLED` arrives
  (`ChatController.handleCallCancelled`).
- The hangup button now picks the right protocol verb automatically:
  `CALL_CANCEL` while still ringing (no `CallManager` yet), `CALL_END` once
  the stream is live.
- `stopCallLocally()` always tears down `CallManager`, hides the banner,
  flushes call state, and (when relevant) logs a system message into the chat.

### DB schema additions
- `calls.status` accepts a new value `cancelled`.
- `calls.group_id` is a new nullable foreign key (used by group meetings).

See `schema.sql` for the `ALTER` migrations to apply on an existing DB.

## 2. Groups & meetings (new)

### UI
- The sidebar has three tabs: **Chats / Groups / Calls** (toggle buttons at
  the top of the sidebar). The active tab is highlighted in green.
- A dedicated **"Create Group" button** appears only on the Groups tab.
  It opens a dialog with: group name, member checkboxes, and per-member
  admin toggle. The creator is locked in as an admin.
- Tapping a group opens the group conversation. The header shows the member
  count instead of online status.
- The **Info button** on a group opens a group info dialog: members list with
  admin badges. If you are an admin you can:
  - add a member by username,
  - remove members,
  - promote/demote admins.

### Server protocol (additions)
```
GROUP_CREATE|<name>|<members,csv>|<admins,csv>
GROUP_LIST                               → GROUP_INFO|<id>|<name>|<members>|<admins>...
GROUP_HISTORY|<gid>                      → GROUP_HISTORY|<gid>|<sender>|<TYPE>|<content>...
GROUP_MSG|<gid>|MID:<n>|<content>        → broadcast GROUP_MSG|<gid>|<sender>|TEXT|<content>
GROUP_TYPING|<gid>                       → other members get GROUP_TYPING|<gid>|<user>
GROUP_ADD|<gid>|<user>                   (admin only)
GROUP_REMOVE|<gid>|<user>                (admin only) → user receives GROUP_REMOVED|<gid>
GROUP_PROMOTE|<gid>|<user>               (admin only)
GROUP_DEMOTE|<gid>|<user>                (admin only)
```

### Permissions
- Membership is gated by `GroupDAO.isMember`.
- Admin actions are gated by `GroupDAO.isAdmin`. Non-admins receive
  `GROUP_FORBIDDEN|<gid>` and the UI toasts a friendly error.

### Group messaging
- Real-time delivery via socket broadcast to every online member.
- The server persists every group message into the `messages` table with the
  `group_id` column populated. New members can fetch full history via
  `GROUP_HISTORY|<gid>`.
- Unread counters live in the client (`groupUnread`) and update from the
  broadcast — clearing whenever the user opens that group.
- A typing indicator strip appears at the top of the chat area while another
  member is typing; it auto-hides after 3s of silence.

### Group calls / meetings
Server signalling (peer-to-peer mesh, server only brokers addresses):
```
GROUP_CALL_START|<gid>|AUDIO|VIDEO|<audioPort>|<videoPort>
GROUP_CALL_JOIN|<gid>|<audioPort>|<videoPort>
GROUP_CALL_LEAVE|<gid>

Server → clients:
GROUP_CALL_INVITE|<gid>|<caller>|<type>             (sent to every other member)
GROUP_CALL_STARTED|<gid>|<type>                     (host confirmation)
GROUP_CALL_PEERS|<gid>|<type>|<user,ip,aPort,vPort>;...    (the roster on join)
GROUP_CALL_PEER_JOINED|<gid>|<user>|<ip>|<aPort>|<vPort>
GROUP_CALL_PEER_LEFT|<gid>|<user>
GROUP_CALL_ACTIVE|<gid>|<type>                      (already running)
GROUP_CALL_NOT_FOUND|<gid>
```

- Server: `GroupCallManager` (new) tracks each active meeting + participants.
  When everyone leaves, it ends the meeting and updates the `calls` row with
  the final duration.
- Client: `streaming.GroupCallSession` (new) is a full-mesh UDP audio/video
  session with a participant tile grid, Mute and Hide Camera toggles, and a
  Leave button. Every participant sends its captured stream to every other
  peer. Ports 7000 (audio) and 7100 (video) are reserved for meetings and do
  not collide with the 1:1 ports (5000/5001/6000/6001).
- Clean shutdown: closing the meeting window or leaving releases the
  microphone/camera/UDP sockets and tells the server, which broadcasts
  `GROUP_CALL_PEER_LEFT`. When the last participant leaves, the meeting
  row is marked `ended` with the right duration.
- Join/Leave notifications: every join/leave triggers a peer event on every
  other participant's UI (tile is added/removed live).

## 3. Call history (new)

### Where to find it
- A dedicated **Calls tab** in the sidebar lists every voice/video call the
  current user was part of (including group meetings).
- Each row shows:
  - the other party (peer username or group name),
  - the call type (Voice/Video),
  - the status (Missed / Cancelled / Ended) and duration when applicable,
  - the timestamp (`YYYY-MM-DD HH:mm`),
  - a green **Redial button** with the right icon (phone or video) that
    immediately reinitiates the same kind of call to the same target.

### Persistence (SQL)
- The `calls` table now records the full lifecycle of every call:
  the row is inserted as `ongoing` when the call is requested, then patched
  to `cancelled` / `refused` / `ended` (with `duration`). Group calls store
  the `group_id` instead of `receiver_id`. (See migrations in `schema.sql`.)
- New DAO methods:
  - `CallDAO.startCall(callerId, receiverId, type)` and
    `CallDAO.startGroupCall(callerId, groupId, type)`.
  - `CallDAO.setStatus(...)` plus convenience `missedCall`, `refusedCall`,
    `cancelledCall`, `endCall(duration)`.
  - `CallDAO.getCallHistory(userId)` returns a `DISTINCT` set spanning 1:1
    calls and group meetings the user belongs to, ordered by recency.
- Indexes: `idx_call_caller`, `idx_call_receiver`, and a new
  `idx_call_group` keep the join/order step cheap.

### Chat-message integration
- When a call ends, the client adds a SYSTEM bubble to the chat with a
  WhatsApp-style summary:
  - `Voice call • 12m 21s`
  - `Video call • Missed`
- These are stored in the local `data_messages.json` for offline replay and
  rendered with a distinct yellow "system" bubble style (`.bubble-system`).
- The server also exposes a `SYSTEM` value in the `messages.type` enum so
  group meeting summaries can be persisted server-side later if needed.

### Quick redial
- Clicking the green button on any call-history row resolves the peer/group,
  switches to the appropriate sidebar tab, opens the conversation, and
  fires the original call type — `onAudioCall` / `onVideoCall` → server
  `CALL_REQUEST` (1:1) or `GROUP_CALL_START` (group).

## 4. Other technical cleanups

- All long-lived listeners cleaned up via JavaFX daemon threads, JavaSound
  `close()`/`stop()`, and `DatagramSocket.close()` paths.
- The 1:1 `CallManager` exposes `setPeerName(...)` so a late-arriving real
  username can replace the placeholder if the controller missed it during
  the race window.
- `clientHandler.Deconnexion()` is fully idempotent and:
  - tears down any ongoing 1:1 call (with the correct cancelled/ended status
    in the DB),
  - removes the user from every active group meeting and notifies remaining
    participants.
- Sidebar UI consolidates contact rows, group rows and call rows behind one
  `buildSidebarRow(...)` helper. The contact list, group list and call list
  share the same `StackPane` slot — switching tabs simply toggles
  visibility, no scroll position lost.
- New JavaSE-free `ChatTarget` model unifies contact and group chats so the
  message-send code path is identical for both. The chat header switches
  status text between "online/offline" (contact) and "N members" (group)
  automatically.

## 5. Files added or rewritten

- Added:
  - `src/main/java/model/ChatTarget.java`
  - `src/main/java/server/GroupCallManager.java`
  - `src/main/java/streaming/GroupCallSession.java`
  - `CHANGES.md` (this file)
- Rewritten (kept backward compatible at the protocol level):
  - `src/main/java/server/clientHandler.java`
  - `src/main/java/server/AppelManager.java`
  - `src/main/java/server/serveur.java`
  - `src/main/java/dao/GroupDAO.java`
  - `src/main/java/dao/CallDAO.java`
  - `src/main/java/dao/MessageDAO.java`
  - `src/main/java/model/Group.java`
  - `src/main/java/model/Call.java`
  - `src/main/java/Views/ChatController.java`
  - `src/main/resources/chat.fxml`
  - `src/main/resources/app-dark.css`
  - `schema.sql`
- Touched:
  - `src/main/java/streaming/CallManager.java` (added `setPeerName(...)`).
  - `src/main/java/dao/TestDAO.java` (new `addMember(gid, uid, isAdmin)` signature).

## 6. Migration: how to update an existing database

Run these on the existing `chat_app` schema before launching the new build:

```sql
ALTER TABLE group_members
    ADD COLUMN is_admin TINYINT(1) NOT NULL DEFAULT 0;

ALTER TABLE messages
    MODIFY COLUMN type ENUM('TEXT','IMAGE','AUDIO','VIDEO','FILE','SYSTEM') DEFAULT 'TEXT';

ALTER TABLE calls
    MODIFY COLUMN status ENUM('ongoing','ended','missed','refused','cancelled') DEFAULT 'ongoing';

ALTER TABLE calls
    ADD COLUMN group_id INT NULL AFTER receiver_id,
    ADD CONSTRAINT fk_call_group FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE,
    MODIFY COLUMN receiver_id INT NULL,
    ADD INDEX idx_call_group (group_id, started_at);
```

## 7. Compilation

```bash
mvn -q -o -DskipTests compile
```

passes cleanly with no warnings.

---

# Release 2 — UX & Multimedia Enhancements

## 8. Audio recording in group chats

The mic button now works in groups exactly like it does in 1:1: start
recording on first press, stop and broadcast on second press. The captured
WAV is base64-encoded inside a `MEDIA_MSG|AUDIO_MSG|...` payload, persisted
to the `messages` table with `type = 'AUDIO'`, and replayed by every
recipient via the existing `[Play]` button. (`ChatController.onAudioMessage` /
`sendMediaToActiveTarget`).

## 9. File & picture uploads with separate icons

The chat input bar now has **two distinct attachment icons**:
- 🖼  **Image** — opens a picture-only file chooser; renders as a 220px
  thumbnail with a click-to-zoom preview.
- 📎  **File** — opens any-file chooser; renders as a clickable "file chip"
  showing the file name, size, and a "click to save" hint. Clicking the chip
  opens a Save dialog so the user can write it anywhere on disk.

Both work in 1:1 chats and group chats. Group attachments are persisted on
the server (`messages.content` is now `MEDIUMTEXT`, so the entire base64
payload fits) and the `type` column is set to `IMAGE` / `AUDIO` / `FILE`
based on the payload type.

A 3 MB per-attachment cap protects the wire and the DB row size; bigger
files are rejected client-side with an explanatory dialog.

## 10. Colored username in group messages

Non-owned group bubbles now show the sender's username as a small bold
label inside the bubble in a **deterministic colour** drawn from the
shared avatar palette (`AVATAR_COLORS`). Each username always renders in
the same hue so members are easy to tell apart at a glance — no more
`[username]` brackets.

The colour helper `ChatController.colorForUsername(username)` is shared
between the avatar circles and the in-bubble name labels so both surfaces
stay in lockstep. Styling is in `.group-sender` (CSS).

## 11. Enter-to-send

`messageInput`'s `onAction` is wired to `sendMessage` (both via FXML and via
the existing Send button). Pressing Enter now ships the message; clicking
the Send button continues to work as before.

## 12. Typing indicator in private chats

The existing typing-indicator strip now reacts to both group and 1:1
conversations. New socket verb:

```
TYPING|<peer>       (client → server)
TYPING|<sender>     (server → recipient)
```

The client throttles outgoing typing events to **one per second** while the
input box is non-empty (covers both 1:1 and groups), and the indicator
auto-hides after 3 s of silence. Sending a message or receiving a real
message also clears the indicator immediately.

## 13. Notification sounds

New `services.NotificationSounds` class with **synthesised** clips so no
binary audio assets are shipped (works the same on Linux, macOS, Windows
via the standard Java Sound API):
- `messageSent()` — short outgoing blip (880 Hz, ~70 ms).
- `messageReceived()` — gentle two-tone ding (660 → 990 Hz).
- `startIncomingRing()` / `stopIncomingRing()` — classic two-burst landline
  ring loop, played while an incoming-call popup is up.
- `startOutgoingDial()` / `stopOutgoingDial()` — 350+440 Hz dial tone loop,
  played while the caller waits for the recipient to pick up.

Wired into `sendMessage`, `handlePrivate`, `handleGroupMsg`,
`handleIncomingCallRequest`, `handleCallAccepted`, `startOutgoingCall`,
`stopCallLocally`, and `onLogout`. The class exposes a global
`setEnabled(false)` kill switch so future settings can mute everything.

---

# Release 3 — Editing, blocking, theming & meeting rejoin

## 15. Delete / edit messages (private and group)

- Every message now carries a stable `client_mid` (millisecond timestamp the
  client generated when sending). The server persists it in
  `messages.client_mid`, indexes it (`idx_msg_client_mid`), and echoes it in
  every `PRIVATE`, `HISTORY`, `GROUP_MSG`, and `GROUP_HISTORY` payload via
  an optional `MID:<n>|` prefix — making each message addressable for the
  lifetime of the conversation.
- New verbs:
  - `MSG_EDIT_PRIV|<peer>|<mid>|<newContent>`
  - `MSG_DELETE_PRIV|<peer>|<mid>`
  - `MSG_EDIT_GROUP|<gid>|<mid>|<newContent>`
  - `MSG_DELETE_GROUP|<gid>|<mid>`
- Server enforces ownership: only the original sender can edit/delete (the
  DAO `editMessage` / `deleteMessage` SQL is scoped to
  `sender_id=? AND client_mid=?`).
- Edits set `edited_at = NOW()` on the row; deletes set `deleted=1` and
  blank the content. Subsequent `HISTORY` / `GROUP_HISTORY` replies emit a
  tombstone (`[message deleted]`) and the `EDITED|` flag where appropriate.
- The bubble UI right-click context menu (`attachOwnContextMenu`) offers
  **Edit** (text only) and **Delete**. In-place updates use the
  `bubbleIndex` map keyed by `P:<peer>:<sender>:<mid>` /
  `G:<gid>:<sender>:<mid>`. Edited messages get a small "(edited)" tag.

## 16. Duplicate-media bug fixed

- Root cause: every `GROUP_MSG` the server broadcasts also goes back to the
  sender, which previously re-rendered + re-persisted the bubble.
- `handleGroupMsg` now short-circuits when `sender == currentUsername` —
  the optimistic bubble inserted during `sendMessage` /
  `sendMediaToActiveTarget` is the only copy that hits the UI and the JSON
  store.
- `openConversation` (group path) only requests `GROUP_HISTORY` when the
  local cache is empty, matching the existing 1:1 behaviour, so reopening
  a group never doubles previously-seen messages.

## 17. Rejoin ongoing group meetings

- New verb `GROUP_CALL_STATUS|<gid>` → server replies
  `GROUP_CALL_STATUS|<gid>|active|<type>|<n>` or `…|none`.
- The client queries this whenever the user opens a group. If a meeting is
  live, a green "Join meeting" banner is shown above the chat with a
  one-click **Join** button.
- When the client tries to start a meeting that's already running, the
  server's existing `GROUP_CALL_ACTIVE` reply now triggers an automatic
  `startOrJoinGroupCall(..., asHost=false)` so the user transparently joins
  the existing call instead of getting an inert response.

## 18. Leave group

- New verb `GROUP_LEAVE|<gid>`. Server removes the membership row, sends
  `GROUP_LEFT|<gid>` to the leaver, and re-broadcasts `GROUP_INFO` to the
  remaining members so their member counts update.
- Group info dialog now ends with a red **Leave group** button (with
  confirmation). Leaving clears the chat view + drops the group from the
  sidebar.

## 19. Light/dark theme toggle

- New `app-night.css` overrides the base palette for dark mode (deep
  background `#0b141a`, panels `#1f2c33`, bubbles `#005c4b` / `#202c33`).
- `SceneManager` is the new theme authority (`SceneManager.Theme`
  enum + `applyTheme(Scene)` / `applyTheme(DialogPane)` /
  `toggleTheme()`). Light mode keeps using `app-dark.css` alone, dark mode
  stacks both files so every existing class is automatically restyled.
- A new sun/moon button in the sidebar header (`#themeToggleBtn`) swaps
  themes for the running session. Dialogs (`promptEditMessage`,
  `confirmDeleteMessage`, contact info, group info, etc.) inherit the
  active theme via `applyCurrentThemeToDialog`.

## 20. Block user in private chat

- New `blocked_users (blocker_id, blocked_id, blocked_at)` table and
  `BlockDAO`.
- New verbs:
  - `BLOCK_USER|<username>` / `UNBLOCK_USER|<username>` — toggle.
  - `BLOCK_LIST` — fetched at login so the UI knows the initial state.
- Server-side enforcement in `envoyerMessagePrive`: if the recipient has
  blocked the sender, the message is dropped and the sender gets
  `BLOCKED|<peer>|<mid>` so its optimistic tick can be cleared.
- Client UI:
  - Blocked contacts pick up the `.contact-row-blocked` CSS class (greyed
    out with `-fx-opacity: 0.55`).
  - Status line in the chat header reads "blocked".
  - `sendMessage` short-circuits with an explanatory dialog when the user
    targets a contact they've blocked.
  - **Block** / **Unblock** button at the bottom of the contact info dialog.

## 21. Local self-view in video meetings

- `GroupCallSession.rebuildTiles()` now installs an `ImageView`
  (`selfVideoTile`) in the user's own tile when the meeting is video.
- `videoSenderLoop` paints into `selfVideoTile` from the same JPEG it's
  already sending to peers — no extra camera reads, no extra encode, fully
  reusing the existing pipeline. The PiP is automatically sized to the
  same dimensions as remote tiles (220×160, preserve ratio).
- Camera-off / mute-mic toggles continue to work; the self tile freezes
  on the last frame when the camera is hidden (no flicker).
- On `leave()` / window close, the JPEG pump stops and the tile is GC'd
  along with the rest of the meeting widgets — no resource leak.

## 22. Schema additions / migrations (release 3)

```sql
ALTER TABLE messages
    ADD COLUMN client_mid BIGINT NULL,
    ADD COLUMN edited_at  TIMESTAMP NULL,
    ADD COLUMN deleted    TINYINT(1) NOT NULL DEFAULT 0,
    ADD INDEX idx_msg_client_mid (sender_id, client_mid);

CREATE TABLE IF NOT EXISTS blocked_users (
    blocker_id INT NOT NULL,
    blocked_id INT NOT NULL,
    blocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT fk_block_blocker FOREIGN KEY (blocker_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_block_blocked FOREIGN KEY (blocked_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
```

## 23. Files added or rewritten (release 3)

- Added:
  - `src/main/java/dao/BlockDAO.java`
  - `src/main/resources/app-night.css`
- Rewritten / extended:
  - `src/main/java/Views/ChatController.java` (bubble index for edit/delete,
    context menu, MID parsing, block UI, theme toggle, leave group, join
    meeting banner, dedupe of own group broadcasts).
  - `src/main/java/Views/SceneManager.java` (Theme enum, applyTheme).
  - `src/main/java/server/clientHandler.java` (edit/delete/block/leave/
    meeting-status verbs, MID-aware persistence and broadcast, block
    enforcement on PRIVATE).
  - `src/main/java/dao/MessageDAO.java` (client_mid, edited_at, deleted +
    editMessage / deleteMessage / mapMessage backward-compat).
  - `src/main/java/model/Message.java` (clientMid / editedAt / deleted +
    setContent).
  - `src/main/java/streaming/GroupCallSession.java` (live self-view tile
    for video meetings).
  - `src/main/resources/chat.fxml` (theme toggle button, join-meeting
    banner).
  - `src/main/resources/app-dark.css` (`.bubble-edited`, `.bubble-deleted`,
    `.contact-row-blocked`, `.join-meeting-banner`).
  - `schema.sql` (messages columns + indexes + new table).

## 24. Compilation

`mvn -q -o -DskipTests compile` still passes cleanly with no errors.

## 14. Files added or rewritten in Release 2

- Added:
  - `src/main/java/services/NotificationSounds.java`
- Rewritten / extended:
  - `src/main/java/Views/ChatController.java` (new icons, file/image
    handlers, group audio, colored group sender, private typing, Enter,
    sound integration).
  - `src/main/resources/chat.fxml` (separate file/image icons, Enter
    binding).
  - `src/main/resources/app-dark.css` (`.group-sender`, `.file-chip`).
  - `src/main/java/server/clientHandler.java` (`TYPING` verb, smarter
    media-type detection for group messages).
  - `schema.sql` (`messages.content` widened to `MEDIUMTEXT`).

