package Views;

import dao.ContactDAO;
import dao.UserDAO;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import model.ChatMessage;
import model.Contact;
import model.User;
import server.clientAPP;
import services.JSONMessageStore;
import services.conversation.ConversationService;
import services.conversation.ConversationServiceFactory;
import streaming.CallManager;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

public class ChatController {
    @FXML private Label currentUserLabel;
    @FXML private TextField searchField;
    @FXML private VBox contactsList;
    @FXML private HBox meAvatarBox;

    @FXML private Label activeChatTitle;
    @FXML private Label activeChatStatus;
    @FXML private HBox activeAvatarBox;
    @FXML private ScrollPane messagesScroll;
    @FXML private VBox messagesBox;
    @FXML private TextField messageInput;

    @FXML private Button audioBtn;
    @FXML private Button sendBtn;
    @FXML private Button attachBtn;
    @FXML private Button addContactBtn;

    @FXML private Button audioCallBtn;
    @FXML private Button videoCallBtn;
    @FXML private Button infoBtn;
    @FXML private Button endCallBtn;
    @FXML private HBox callBanner;
    @FXML private Label callBannerLabel;

    @FXML private HBox recordingBar;
    @FXML private HBox waveformBox;
    @FXML private Label recordingTimer;

    private final UserDAO userDAO = new UserDAO();
    private final ContactDAO contactDAO = new ContactDAO();
    private final JSONMessageStore localStore = new JSONMessageStore();
    private final ConversationServiceFactory conversationFactory = new ConversationServiceFactory(localStore);
    // The service for the currently-open conversation. Recomputed
    // whenever activeContact changes; identical contract for groups.
    private ConversationService activeConversation;
    // VBox of each rendered bubble keyed by client mid, so incoming
    // MSG_EDITED / MSG_DELETED can mutate the right bubble in place
    // instead of forcing a full conversation reload.
    private final Map<Long, VBox> bubbleByMid = new HashMap<>();

    private clientAPP client;
    private String currentUsername;
    private int currentUserId = -1;
    private Contact activeContact;
    private final List<Contact> contacts = new ArrayList<>();

    // Tick status tracking. Each "mine" bubble has a Label whose text/style
    // reflects "sent" (✓), "delivered" (✓✓), or "read" (✓✓ blue).
    private final Map<Long, Label> tickByMid = new HashMap<>();
    private final Map<String, List<Label>> pendingTicksByContact = new HashMap<>();

    private static final String TICK_SENT = "✓";        // ✓
    private static final String TICK_DOUBLE = "✓✓"; // ✓✓

    private boolean recordingAudio = false;
    private TargetDataLine recordingLine;
    private File recordingFile;

    private Timeline waveformAnim;
    private Timeline recordingClock;
    private long recordingStartMs;
    private final Random random = new Random();

    // ─── Call state ────────────────────────────────────────
    private boolean callActive = false;
    private String pendingCallType = "AUDIO";   // type of call we requested or are receiving
    private String currentCallPeer;             // username of remote peer
    private CallManager activeCall;
    // Hardcoded UDP ports must match CallManager (caller=5000/6000, recipient=5001/6001)
    private static final int CALLER_AUDIO_PORT = 6000;
    private static final int RECIPIENT_AUDIO_PORT = 6001;

    private static final String[] AVATAR_COLORS = {
            "#25d366", "#128c7e", "#34b7f1", "#f4a261", "#e76f51",
            "#9b5de5", "#f15bb5", "#00bbf9", "#fb8500", "#06d6a0"
    };

    @FXML
    private void initialize() {
        currentUsername = SceneManager.getCurrentUsername();
        client = SceneManager.getCurrentClient();

        if (currentUsername == null || client == null) {
            SceneManager.switchTo("login.fxml");
            return;
        }

        currentUserLabel.setText(currentUsername);
        meAvatarBox.getChildren().setAll(buildAvatar(currentUsername, 38));

        User me = userDAO.getByUsername(currentUsername);
        if (me != null) currentUserId = me.getId();

        applyButtonIcons();
        loadContacts();
        bindSearch();
        startServerListener();
        updateCallButtons();
    }

    // ── Icons ─────────────────────────────────────────────
    private void applyButtonIcons() {
        if (audioCallBtn  != null) audioCallBtn.setGraphic(makeIcon(IconShape.PHONE,  18, "icon-white"));
        if (videoCallBtn  != null) videoCallBtn.setGraphic(makeIcon(IconShape.VIDEO,  20, "icon-white"));
        if (infoBtn       != null) infoBtn.setGraphic(makeIcon(IconShape.INFO,        18, "icon-white"));
        if (endCallBtn    != null) endCallBtn.setGraphic(makeIcon(IconShape.HANGUP,   20, "icon-white"));
        if (sendBtn       != null) sendBtn.setGraphic(makeIcon(IconShape.PLANE,       18, "icon-white"));
        if (audioBtn      != null) audioBtn.setGraphic(makeIcon(IconShape.MIC,        18, "icon-grey"));
        if (attachBtn     != null) attachBtn.setGraphic(makeIcon(IconShape.PLUS,      18, "icon-grey"));
        if (addContactBtn != null) addContactBtn.setGraphic(makeIcon(IconShape.PLUS,  18, "icon-white"));
    }

    private enum IconShape { PHONE, VIDEO, PLANE, INFO, MIC, PLUS, HANGUP }

    private SVGPath makeIcon(IconShape shape, double size, String style) {
        SVGPath p = new SVGPath();
        switch (shape) {
            case PHONE:
                p.setContent("M20 15.5c-1.25 0-2.45-.2-3.57-.57a1 1 0 0 0-1.02.24l-2.2 2.2a15.05 15.05 0 0 1-6.59-6.59l2.2-2.2a1 1 0 0 0 .24-1.02A11.36 11.36 0 0 1 8.5 4a1 1 0 0 0-1-1H4a1 1 0 0 0-1 1c0 9.39 7.61 17 17 17a1 1 0 0 0 1-1v-3.5a1 1 0 0 0-1-1z");
                break;
            case VIDEO:
                p.setContent("M17 10.5V7a1 1 0 0 0-1-1H4a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-3.5l4 4v-11l-4 4z");
                break;
            case PLANE:
                p.setContent("M2.01 21l20.99-9L2.01 3 2 10l15 2-15 2 .01 7z");
                break;
            case INFO:
                p.setContent("M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm1 15h-2v-6h2zm0-8h-2V7h2z");
                break;
            case MIC:
                p.setContent("M12 14a3 3 0 0 0 3-3V5a3 3 0 0 0-6 0v6a3 3 0 0 0 3 3zm5-3a5 5 0 0 1-10 0H5a7 7 0 0 0 6 6.92V21h2v-3.08A7 7 0 0 0 19 11z");
                break;
            case PLUS:
                p.setContent("M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6z");
                break;
            case HANGUP:
                p.setContent("M12 9c-1.6 0-3.15.25-4.6.72v3.1c0 .39-.23.74-.56.9-.98.49-1.87 1.12-2.66 1.85-.18.18-.43.28-.7.28a.97.97 0 0 1-.7-.29L.29 13.08a.96.96 0 0 1-.29-.7c0-.28.11-.53.29-.71C3.34 8.78 7.46 7 12 7s8.66 1.78 11.71 4.67c.18.18.29.43.29.71 0 .28-.11.53-.29.71l-2.48 2.48a.97.97 0 0 1-.7.29c-.27 0-.52-.11-.7-.28a11.7 11.7 0 0 0-2.67-1.85.996.996 0 0 1-.56-.9v-3.1A16.2 16.2 0 0 0 12 9z");
                break;
        }
        p.getStyleClass().add(style);
        // scale path to requested size: source viewBox is approx 24
        double s = size / 24.0;
        p.setScaleX(s);
        p.setScaleY(s);
        return p;
    }

    // ── Contacts ──────────────────────────────────────────
    private void loadContacts() {
        contacts.clear();
        if (currentUserId > 0) contacts.addAll(contactDAO.getContacts(currentUserId));

        // Surface anyone we already have a local conversation with even if
        // they aren't in the contacts table. Without this, a message from a
        // brand-new account would silently land in data_messages.json with
        // no way for the recipient to see it because the sidebar only
        // listed entries from the contacts table.
        Set<String> known = new HashSet<>();
        for (Contact c : contacts) known.add(c.username.toLowerCase());

        for (String peer : localStore.getConversationPeers(currentUsername)) {
            if (peer == null || peer.isBlank()) continue;
            if (peer.equalsIgnoreCase(currentUsername)) continue;
            if (known.contains(peer.toLowerCase())) continue;

            User u = userDAO.getByUsername(peer);
            Contact c;
            if (u != null) {
                c = new Contact(
                        u.getId(), u.getUsername(),
                        "online".equalsIgnoreCase(u.getStatus()),
                        u.isBlocked(), false, "now");
                if (currentUserId > 0) {
                    try { contactDAO.addContact(currentUserId, u.getId()); } catch (Exception ignored) {}
                }
            } else {
                int fallbackId = -Math.abs(peer.toLowerCase().hashCode());
                c = new Contact(fallbackId, peer, false, false, false, "now");
            }
            List<ChatMessage> conv = localStore.getConversation(currentUsername, peer, false, peer);
            if (conv != null && !conv.isEmpty()) {
                ChatMessage last = conv.get(conv.size() - 1);
                c.lastMessagePreview = previewOf(last.getContent());
                c.lastMessageTime = last.getTimestamp();
                int unread = 0;
                for (ChatMessage m : conv) {
                    if (currentUsername.equalsIgnoreCase(m.getReceiver())
                            && !"read".equalsIgnoreCase(m.getStatus())) {
                        unread++;
                    }
                }
                c.unreadCount = unread;
            }
            contacts.add(c);
            known.add(peer.toLowerCase());
        }
        refreshContactsView();
    }

    private void bindSearch() {
        searchField.textProperty().addListener((obs, oldV, newV) -> renderContacts(newV == null ? "" : newV));
    }

    private void renderContacts(String filter) {
        contactsList.getChildren().clear();
        for (Contact c : contacts) {
            if (!filter.isEmpty() && !c.username.toLowerCase().contains(filter.toLowerCase())) continue;

            HBox row = new HBox(12);
            row.getStyleClass().add("contact-row");
            if (activeContact != null && activeContact.username.equalsIgnoreCase(c.username)) {
                row.getStyleClass().add("contact-row-active");
            }
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(10, 14, 10, 14));

            StackPane avatar = buildAvatar(c.username, 44);

            VBox text = new VBox(2);
            Label name = new Label(c.username);
            name.getStyleClass().add("contact-name");

            String preview = c.lastMessagePreview == null ? "" : c.lastMessagePreview;
            Label sub = new Label(c.isOnline ? "online" : preview);
            sub.getStyleClass().add("contact-preview");

            text.getChildren().addAll(name, sub);
            HBox.setHgrow(text, javafx.scene.layout.Priority.ALWAYS);

            Circle dot = new Circle(5);
            dot.getStyleClass().add(c.isOnline ? "status-online" : "status-offline");

            row.getChildren().addAll(avatar, text, dot);
            if (c.unreadCount > 0) {
                Label badge = new Label(String.valueOf(c.unreadCount));
                badge.getStyleClass().add("unread-badge");
                row.getChildren().add(badge);
            }
            row.setOnMouseClicked(e -> openConversation(c));
            contactsList.getChildren().add(row);
        }
    }

    // ── Avatar (profile picture if available, else initial) ──────────
    private StackPane buildAvatar(String username, double size) {
        Circle clip = new Circle(size / 2.0, size / 2.0, size / 2.0);
        StackPane sp = new StackPane();
        sp.setMinSize(size, size);
        sp.setPrefSize(size, size);
        sp.setMaxSize(size, size);

        Image pic = loadProfilePicture(username);
        if (pic != null) {
            ImageView iv = new ImageView(pic);
            iv.setFitWidth(size);
            iv.setFitHeight(size);
            iv.setPreserveRatio(false);
            iv.setSmooth(true);
            iv.setClip(new Circle(size / 2.0, size / 2.0, size / 2.0));
            sp.getChildren().add(iv);
        } else {
            String letter = (username == null || username.isEmpty()) ? "?" : username.substring(0, 1).toUpperCase();
            int idx = Math.abs((username == null ? 0 : username.toLowerCase().hashCode())) % AVATAR_COLORS.length;
            Circle bg = new Circle(size / 2.0);
            bg.setFill(Color.web(AVATAR_COLORS[idx]));
            Label l = new Label(letter);
            l.getStyleClass().add("avatar-letter");
            l.setStyle("-fx-font-size: " + (size * 0.42) + ";");
            sp.getChildren().addAll(bg, l);
        }
        return sp;
    }

    private Image loadProfilePicture(String username) {
        if (username == null || username.isEmpty()) return null;
        try {
            User u = userDAO.getByUsername(username);
            if (u == null) return null;

            // Preferred path: image bytes stored in DB — works on every client.
            byte[] data = u.getProfilePictureData();
            if (data != null && data.length > 0) {
                return new Image(new ByteArrayInputStream(data));
            }

            // Legacy fallback: path/URL stored from older code.
            String path = u.getProfilePicture();
            if (path == null || path.isEmpty()) return null;
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                return new Image(f.toURI().toString(), true);
            }
            if (path.startsWith("http") || path.startsWith("file:")) {
                return new Image(path, true);
            }
            if (path.startsWith("/")) {
                return new Image(getClass().getResourceAsStream(path));
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ── Conversation ──────────────────────────────────────
    private void openConversation(Contact contact) {
        activeContact = contact;
        activeChatTitle.setText(contact.username);
        activeChatStatus.setText(contact.isOnline ? "online" : "offline");
        activeAvatarBox.getChildren().setAll(buildAvatar(contact.username, 40));
        messagesBox.getChildren().clear();
        bubbleByMid.clear();
        contact.unreadCount = 0;

        // Bind the conversation service for this contact. Switching
        // to a group later is just `forGroup(...)` here.
        activeConversation = conversationFactory.forPrivate(currentUsername, contact.username);

        // We're rebuilding the visible bubble list for this contact, so drop
        // any stale tick references the old bubbles registered.
        clearTickReferencesFor(contact.username);

        List<ChatMessage> hist = activeConversation.getMessages();
        for (ChatMessage m : hist) {
            boolean mine = m.getSender().equalsIgnoreCase(currentUsername);
            Label tick = null;
            if (mine) {
                tick = createTickLabel(m.getStatus());
                if ("sent".equalsIgnoreCase(m.getStatus())) {
                    tickByMid.put(m.getTimestamp(), tick);
                    pendingTicksByContact
                            .computeIfAbsent(contact.username.toLowerCase(), k -> new ArrayList<>())
                            .add(tick);
                }
            }
            addBubble(renderForMessage(m), mine,
                    m.getTime() == null ? nowTime() : m.getTime(),
                    tick, m, mine);
        }

        // Only seed from the server when our local copy is empty. Otherwise the
        // server's HISTORY reply would re-render bubbles we already drew from
        // the local store, doubling every message in the view.
        if (hist.isEmpty()) {
            client.send("HISTORY|" + contact.username);
        }
        renderContacts(searchField.getText() == null ? "" : searchField.getText());
        updateCallButtons();
    }

    private void clearTickReferencesFor(String contactUsername) {
        if (contactUsername == null) return;
        List<Label> old = pendingTicksByContact.remove(contactUsername.toLowerCase());
        if (old == null) return;
        tickByMid.entrySet().removeIf(e -> old.contains(e.getValue()));
    }

    @FXML
    private void sendMessage() {
        if (activeContact == null) return;
        String content = messageInput.getText() == null ? "" : messageInput.getText().trim();
        if (content.isEmpty()) return;

        long mid = System.currentTimeMillis();
        String time = nowTime();
        client.send("PRIVATE|" + currentUsername + "|" + activeContact.username + "|MID:" + mid + "|" + content);
        ChatMessage msg = new ChatMessage(currentUsername, activeContact.username, "TEXT", content,
                                          time, "sent", mid);
        if (activeConversation != null) {
            activeConversation.addMessage(msg);
        } else {
            localStore.addMessage(msg, false, activeContact.username);
        }
        Label tick = createTickLabel("sent");
        tickByMid.put(mid, tick);
        pendingTicksByContact
                .computeIfAbsent(activeContact.username.toLowerCase(), k -> new ArrayList<>())
                .add(tick);
        addBubble(createMessageNode(content), true, time, tick, msg, true);
        messageInput.clear();
    }

    private String nowTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private void addBubble(Node node, boolean mine, String time) {
        addBubble(node, mine, time, null, null, mine);
    }

    private void addBubble(Node node, boolean mine, String time, Label statusTick) {
        addBubble(node, mine, time, statusTick, null, mine);
    }

    private void addBubble(Node node, boolean mine, String time, Label statusTick,
                           ChatMessage message, boolean canModify) {
        HBox row = new HBox();
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox bubble = new VBox(4);
        bubble.setMaxWidth(440);
        bubble.getStyleClass().add(mine ? "bubble-mine" : "bubble-other");
        bubble.setPadding(new Insets(8, 10, 6, 10));

        if (node instanceof Label) {
            ((Label) node).getStyleClass().add("bubble-text");
        }

        Label timeLabel = new Label(time == null ? "" : time);
        timeLabel.getStyleClass().add("bubble-time");

        HBox timeRow = new HBox(4);
        timeRow.setAlignment(Pos.CENTER_RIGHT);
        // "(edited)" hint sits in the time row so it stays close to the timestamp.
        if (message != null && message.isEdited()
                && !"DELETED".equalsIgnoreCase(message.getType())) {
            Label editedTag = new Label("(edited)");
            editedTag.getStyleClass().add("bubble-time");
            editedTag.setStyle("-fx-font-style: italic;");
            timeRow.getChildren().add(editedTag);
        }
        timeRow.getChildren().add(timeLabel);
        if (mine && statusTick != null) {
            timeRow.getChildren().add(statusTick);
        }

        bubble.getChildren().addAll(node, timeRow);

        // Wire the right-click "Edit / Delete" menu only on bubbles
        // owned by the current user and that aren't already deleted.
        if (message != null && canModify && activeConversation != null
                && !"DELETED".equalsIgnoreCase(message.getType())) {
            attachMessageContextMenu(bubble, message);
        }

        row.getChildren().add(bubble);
        messagesBox.getChildren().add(row);
        if (message != null && message.getTimestamp() > 0) {
            bubbleByMid.put(message.getTimestamp(), bubble);
        }
        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
    }

    // ── Edit / delete UI ─────────────────────────────────
    // Right-click on a bubble I own → menu with Edit + Delete.
    // The actual mutation goes through the ConversationService so
    // private and (later) group conversations share one code path.
    private void attachMessageContextMenu(VBox bubble, ChatMessage msg) {
        ContextMenu menu = new ContextMenu();
        MenuItem edit   = new MenuItem("Edit");
        MenuItem delete = new MenuItem("Delete");
        edit.setOnAction(e -> onEditMessage(msg));
        delete.setOnAction(e -> onDeleteMessage(msg));
        // Media bubbles cannot be re-typed; only allow delete on those.
        if (msg.getType() != null && !"TEXT".equalsIgnoreCase(msg.getType())) {
            menu.getItems().add(delete);
        } else {
            menu.getItems().addAll(edit, delete);
        }
        bubble.setOnContextMenuRequested(e -> menu.show(bubble, e.getScreenX(), e.getScreenY()));
    }

    private void onEditMessage(ChatMessage msg) {
        if (activeConversation == null) return;
        if (!activeConversation.canEdit(msg, currentUsername)) {
            showInfo("You can only edit your own messages.");
            return;
        }
        TextInputDialog d = new TextInputDialog(msg.getContent() == null ? "" : msg.getContent());
        d.setTitle("Edit message");
        d.setHeaderText("Update your message");
        d.setContentText("Message:");
        Optional<String> res = d.showAndWait();
        if (res.isEmpty()) return;
        String updated = res.get().trim();
        if (updated.isEmpty()) return;
        if (updated.equals(msg.getContent())) return;

        // Local optimistic update + persist + tell the server to relay.
        boolean ok = activeConversation.editMessage(msg.getTimestamp(), updated, currentUsername);
        if (!ok) {
            showInfo("Edit refused.");
            return;
        }
        client.send(activeConversation.editWireCommand(msg.getTimestamp(), updated));
        applyEditToBubble(msg.getTimestamp(), updated);
    }

    private void onDeleteMessage(ChatMessage msg) {
        if (activeConversation == null) return;
        if (!activeConversation.canDelete(msg, currentUsername)) {
            showInfo("You can only delete your own messages.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete this message? This cannot be undone.",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        Optional<ButtonType> ans = confirm.showAndWait();
        if (ans.isEmpty() || ans.get() != ButtonType.OK) return;

        boolean ok = activeConversation.deleteMessage(msg.getTimestamp(), currentUsername);
        if (!ok) { showInfo("Delete refused."); return; }
        client.send(activeConversation.deleteWireCommand(msg.getTimestamp()));
        applyDeleteToBubble(msg.getTimestamp());
    }

    // Replace the visible content of a bubble after an edit (mine or peer's).
    private void applyEditToBubble(long mid, String newContent) {
        VBox bubble = bubbleByMid.get(mid);
        if (bubble == null) return;
        Label text = new Label(newContent);
        text.setWrapText(true);
        text.setMaxWidth(400);
        text.getStyleClass().addAll("bubble-text");
        // Bubble layout: [content, timeRow]. Replace content node only
        // and stamp "(edited)" into the timeRow if it isn't there yet.
        if (!bubble.getChildren().isEmpty()) {
            bubble.getChildren().set(0, text);
        }
        if (bubble.getChildren().size() >= 2 && bubble.getChildren().get(1) instanceof HBox) {
            HBox timeRow = (HBox) bubble.getChildren().get(1);
            boolean hasTag = timeRow.getChildren().stream()
                    .anyMatch(n -> n instanceof Label && "(edited)".equals(((Label) n).getText()));
            if (!hasTag) {
                Label tag = new Label("(edited)");
                tag.getStyleClass().add("bubble-time");
                tag.setStyle("-fx-font-style: italic;");
                timeRow.getChildren().add(0, tag);
            }
        }
    }

    private void applyDeleteToBubble(long mid) {
        VBox bubble = bubbleByMid.get(mid);
        if (bubble == null) return;
        Label tomb = new Label("🗑  message deleted");
        tomb.setStyle("-fx-font-style: italic; -fx-text-fill: #9ca3af;");
        if (!bubble.getChildren().isEmpty()) {
            bubble.getChildren().set(0, tomb);
        }
        // Strip the context menu so a tombstone can't be re-edited.
        bubble.setOnContextMenuRequested(null);
    }

    // Renders a stored message into a node, accounting for soft-delete.
    private Node renderForMessage(ChatMessage m) {
        if (m == null) {
            return createMessageNode("");
        }
        if ("DELETED".equalsIgnoreCase(m.getType())) {
            Label tomb = new Label("🗑  message deleted");
            tomb.setStyle("-fx-font-style: italic; -fx-text-fill: #9ca3af;");
            return tomb;
        }
        return createMessageNode(m.getContent());
    }

    private Label createTickLabel(String status) {
        Label l = new Label();
        l.getStyleClass().add("bubble-tick");
        applyTickStatus(l, status);
        return l;
    }

    private void applyTickStatus(Label tick, String status) {
        if (tick == null) return;
        String s = status == null ? "sent" : status.toLowerCase();
        tick.getStyleClass().removeAll("bubble-tick-sent", "bubble-tick-delivered", "bubble-tick-read");
        switch (s) {
            case "delivered":
                tick.setText(TICK_DOUBLE);
                tick.getStyleClass().add("bubble-tick-delivered");
                break;
            case "read":
                tick.setText(TICK_DOUBLE);
                tick.getStyleClass().add("bubble-tick-read");
                break;
            default: // sent / queued
                tick.setText(TICK_SENT);
                tick.getStyleClass().add("bubble-tick-sent");
                break;
        }
    }

    private Node createMessageNode(String content) {
        if (content != null && content.startsWith("MEDIA_MSG|")) {
            String[] fields = content.split("\\|", 6);
            if (fields.length >= 6) {
                String mediaType = fields[1];
                String fileName = fields[2];
                String encoded = fields[5];
                File file = persistTempFile(fileName, encoded);

                if ("IMAGE".equals(mediaType)) {
                    ImageView imageView = new ImageView(new Image(file.toURI().toString()));
                    imageView.setFitWidth(220);
                    imageView.setPreserveRatio(true);
                    imageView.setStyle("-fx-cursor: hand;");
                    imageView.setOnMouseClicked(e -> openImagePreview(file.getAbsolutePath(), fileName));
                    return imageView;
                }
                if ("AUDIO_MSG".equals(mediaType)) {
                    HBox box = new HBox(8);
                    box.setAlignment(Pos.CENTER_LEFT);
                    Button play = new Button("Play");
                    play.getStyleClass().add("btn-ghost");
                    play.setOnAction(e -> playAudio(file));
                    Label name = new Label(fileName);
                    name.getStyleClass().add("bubble-text");
                    box.getChildren().addAll(play, name);
                    return box;
                }
            }
        }

        Label text = new Label(content == null ? "" : content);
        text.setWrapText(true);
        text.setMaxWidth(400);
        text.getStyleClass().add("bubble-text");
        return text;
    }

    private File persistTempFile(String fileName, String base64) {
        try {
            File folder = new File("chat_tmp");
            if (!folder.exists()) folder.mkdirs();
            File out = new File(folder, System.currentTimeMillis() + "_" + fileName.replaceAll("[^a-zA-Z0-9._-]", "_"));
            byte[] data = Base64.getDecoder().decode(base64);
            Files.write(out.toPath(), data);
            return out;
        } catch (Exception e) {
            return new File(fileName);
        }
    }

    private void playAudio(File file) {
        try {
            Clip clip = AudioSystem.getClip();
            clip.open(AudioSystem.getAudioInputStream(file));
            clip.start();
        } catch (Exception ignored) {}
    }

    // ── Server listener ───────────────────────────────────
    private void startServerListener() {
        Thread listener = new Thread(() -> {
            while (true) {
                String raw = client.read();
                if (raw == null) break;
                Platform.runLater(() -> handleIncoming(raw));
            }
        });
        listener.setDaemon(true);
        listener.start();
    }

    private void handleIncoming(String raw) {
        if (raw == null || raw.isEmpty()) return;
        String[] p = raw.split("\\|", 4);
        String type = p[0];

        if ("PRIVATE".equals(type) && p.length == 4) {
            String from = p[1];
            String tail = p[3];
            // Parse the optional MID:<n>|... prefix so we can store the
            // received message under the sender's mid. That way later
            // MSG_EDITED / MSG_DELETED notifications find the bubble.
            long incomingMid = System.currentTimeMillis();
            String text = tail;
            if (tail.startsWith("MID:")) {
                int sep = tail.indexOf('|');
                if (sep > 0) {
                    try { incomingMid = Long.parseLong(tail.substring(4, sep)); } catch (NumberFormatException ignored) {}
                    text = tail.substring(sep + 1);
                }
            }
            String time = nowTime();

            ensureContactVisible(from);

            ChatMessage incoming = new ChatMessage(from, currentUsername, "TEXT", text,
                    time, "recv", incomingMid);
            localStore.addMessage(incoming, false, from);

            if (activeContact == null) {
                Contact senderContact = findContactByUsername(from);
                if (senderContact != null) {
                    openConversation(senderContact);
                    return;
                }
            }

            if (activeContact != null && activeContact.username.equalsIgnoreCase(from)) {
                addBubble(createMessageNode(text), false, time, null, incoming, false);
            } else {
                // Message arrived for a non-active conversation: bump unread
                // count and refresh the preview so the sidebar surfaces it.
                Contact senderContact = findContactByUsername(from);
                if (senderContact != null) {
                    senderContact.unreadCount++;
                    senderContact.lastMessagePreview = previewOf(text);
                    senderContact.lastMessageTime = System.currentTimeMillis();
                    renderContacts(searchField.getText() == null ? "" : searchField.getText());
                }
            }
            return;
        }

        if ("MSG_STATUS".equals(type) && p.length >= 3) {
            // MSG_STATUS|<recipient>|<mid>|<status>     (status part is in p[2] tail because split limit is 4)
            // We re-split p[2] to get mid and status reliably.
            String recipient = p[1];
            String tail = p.length >= 4 ? p[2] + "|" + p[3] : p[2];
            String[] tailParts = tail.split("\\|", 2);
            if (tailParts.length < 2) return;
            long mid;
            try { mid = Long.parseLong(tailParts[0].trim()); } catch (NumberFormatException ex) { return; }
            String status = tailParts[1].trim();

            Label tick = tickByMid.get(mid);
            if (tick != null) {
                applyTickStatus(tick, status);
                if (!"sent".equalsIgnoreCase(status)) {
                    tickByMid.remove(mid);
                    List<Label> list = pendingTicksByContact.get(recipient.toLowerCase());
                    if (list != null) list.remove(tick);
                }
            }
            try { localStore.updateMessageStatus(currentUsername, recipient, mid, status); } catch (Exception ignored) {}
            return;
        }

        if ("MSG_EDITED".equals(type) && p.length >= 4) {
            // MSG_EDITED|<peer>|<mid>|<newContent>
            String peer = p[1];
            long mid;
            try { mid = Long.parseLong(p[2].trim()); } catch (NumberFormatException ex) { return; }
            String newContent = p[3];
            try {
                ConversationService svc = conversationFactory.forPrivate(currentUsername, peer);
                svc.editMessage(mid, newContent, peer);
            } catch (Exception ignored) {}
            if (activeContact != null && activeContact.username.equalsIgnoreCase(peer)) {
                applyEditToBubble(mid, newContent);
            }
            return;
        }

        if ("MSG_DELETED".equals(type) && p.length >= 3) {
            // MSG_DELETED|<peer>|<mid>
            String peer = p[1];
            long mid;
            try { mid = Long.parseLong(p[2].trim()); } catch (NumberFormatException ex) { return; }
            try {
                ConversationService svc = conversationFactory.forPrivate(currentUsername, peer);
                svc.deleteMessage(mid, peer);
            } catch (Exception ignored) {}
            if (activeContact != null && activeContact.username.equalsIgnoreCase(peer)) {
                applyDeleteToBubble(mid);
            }
            return;
        }

        if ("CONV_DELIVERED".equals(type) && p.length >= 2) {
            String recipient = p[1];
            List<Label> list = pendingTicksByContact.remove(recipient.toLowerCase());
            if (list != null) {
                for (Label t : list) applyTickStatus(t, "delivered");
                tickByMid.entrySet().removeIf(e -> list.contains(e.getValue()));
            }
            try { localStore.markPendingDelivered(currentUsername, recipient); } catch (Exception ignored) {}
            return;
        }

        if ("HISTORY".equals(type) && p.length == 4) {
            String other = p[1];
            String sender = p[2];
            String text = p[3];
            if (activeContact != null && activeContact.username.equalsIgnoreCase(other)) {
                addBubble(createMessageNode(text), sender.equalsIgnoreCase(currentUsername), nowTime());
            }
            return;
        }

        if ("USER_STATUS".equals(type) && p.length >= 3) {
            String who = p[1];
            boolean online = "online".equalsIgnoreCase(p[2]);
            for (Contact c : contacts) if (c.username.equalsIgnoreCase(who)) c.isOnline = online;
            renderContacts(searchField.getText());
            if (activeContact != null && activeContact.username.equalsIgnoreCase(who)) {
                activeChatStatus.setText(online ? "online" : "offline");
            }
            return;
        }

        // Incoming call request: server format CALL_REQUEST|fromUser[|VIDEO]
        if ("CALL_REQUEST".equals(type) && p.length >= 2) {
            String from = p[1];
            String inType = (p.length >= 3 && "VIDEO".equalsIgnoreCase(p[2])) ? "VIDEO" : "AUDIO";
            handleIncomingCallRequest(from, inType);
            return;
        }

        // Caller side: CALL_ACCEPTED|recvUser|recvIp|port
        if ("CALL_ACCEPTED".equals(type)) {
            // We are the caller. Reply CALL_READY|<our audio port>
            client.send("CALL_READY|" + CALLER_AUDIO_PORT);
            showCallBanner("Call connected with " + currentCallPeer);
            return;
        }

        // START_AUDIO|remoteIp|remotePort  → start the actual stream
        if ("START_AUDIO".equals(type) && p.length >= 2) {
            String remoteIp = p[1];
            startStream(remoteIp, currentCallPeer != null && isCaller());
            return;
        }

        if ("CALL_REFUSED".equals(type)) {
            stopCallLocally();
            showInfo("Call refused.");
            return;
        }

        if ("CALL_ENDED".equals(type)) {
            stopCallLocally();
            return;
        }
    }

    // ── Call flow ─────────────────────────────────────────
    private boolean isCaller() {
        // We mark caller=true when we initiated. Tracked by callOriginatedByMe flag.
        return callOriginatedByMe;
    }

    private boolean callOriginatedByMe = false;

    private void handleIncomingCallRequest(String from, String inType) {
        if (callActive) {
            // Already in a call - auto-refuse
            client.send("CALL_REFUSE|" + from);
            return;
        }
        currentCallPeer = from;
        pendingCallType = inType;
        callOriginatedByMe = false;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Incoming " + inType.toLowerCase() + " call");
        alert.setHeaderText(from + " is calling");
        alert.setContentText(inType.equalsIgnoreCase("VIDEO")
                ? "Accept video call?" : "Accept audio call?");
        ButtonType accept = new ButtonType("Accept");
        ButtonType reject = new ButtonType("Reject", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(accept, reject);
        Optional<ButtonType> res = alert.showAndWait();
        if (res.isPresent() && res.get() == accept) {
            callActive = true;
            showCallBanner("Connecting " + inType.toLowerCase() + " call with " + from + "...");
            updateCallButtons();
            client.send("CALL_ACCEPT|" + RECIPIENT_AUDIO_PORT);
        } else {
            client.send("CALL_REFUSE|" + from);
            currentCallPeer = null;
            pendingCallType = "AUDIO";
        }
    }

    @FXML
    private void onAudioCall() {
        startOutgoingCall("AUDIO");
    }

    @FXML
    private void onVideoCall() {
        startOutgoingCall("VIDEO");
    }

    private void startOutgoingCall(String type) {
        if (activeContact == null) {
            showInfo("Select a contact first.");
            return;
        }
        if (callActive) {
            showInfo("A call is already active.");
            return;
        }
        pendingCallType = type;
        currentCallPeer = activeContact.username;
        callOriginatedByMe = true;
        callActive = true;
        showCallBanner((type.equals("VIDEO") ? "Video calling " : "Calling ") + activeContact.username + "...");
        updateCallButtons();
        client.send("CALL_REQUEST|" + activeContact.username + "|" + type);
    }

    @FXML
    private void onEndCall() {
        if (currentCallPeer != null) {
            try { client.send("CALL_END|" + currentCallPeer); } catch (Exception ignored) {}
        }
        stopCallLocally();
    }

    private void startStream(String remoteIp, boolean isCaller) {
        // Stop any previous stream
        if (activeCall != null) {
            try { activeCall.stopCall(); } catch (Exception ignored) {}
            activeCall = null;
        }
        boolean isVideo = "VIDEO".equalsIgnoreCase(pendingCallType);
        try {
            activeCall = new CallManager(remoteIp, isCaller, isVideo, currentCallPeer);
            // The user can hang up from inside the call window — when that
            // happens, route it through the same path as clicking the in-chat
            // End-call button so the peer + server learn the call ended.
            final String peerAtStart = currentCallPeer;
            activeCall.setOnEnd(() -> Platform.runLater(() -> {
                if (peerAtStart != null) {
                    try { client.send("CALL_END|" + peerAtStart); } catch (Exception ignored) {}
                }
                stopCallLocally();
            }));
            activeCall.startCall();
            showCallBanner("In " + (isVideo ? "video" : "audio") + " call with " + currentCallPeer);
        } catch (Exception e) {
            showInfo("Failed to start media stream: " + e.getMessage());
            stopCallLocally();
        }
    }

    private void stopCallLocally() {
        if (activeCall != null) {
            try { activeCall.stopCall(); } catch (Exception ignored) {}
            activeCall = null;
        }
        callActive = false;
        callOriginatedByMe = false;
        currentCallPeer = null;
        pendingCallType = "AUDIO";
        hideCallBanner();
        updateCallButtons();
    }

    private void showCallBanner(String text) {
        if (callBanner == null) return;
        callBannerLabel.setText(text);
        callBanner.setVisible(true);
        callBanner.setManaged(true);
    }

    private void hideCallBanner() {
        if (callBanner == null) return;
        callBanner.setVisible(false);
        callBanner.setManaged(false);
    }

    private void updateCallButtons() {
        boolean hasContact = activeContact != null;
        if (audioCallBtn != null) {
            audioCallBtn.setDisable(!hasContact || callActive);
            audioCallBtn.setVisible(!callActive);
            audioCallBtn.setManaged(!callActive);
        }
        if (videoCallBtn != null) {
            videoCallBtn.setDisable(!hasContact || callActive);
            videoCallBtn.setVisible(!callActive);
            videoCallBtn.setManaged(!callActive);
        }
        if (endCallBtn != null) {
            endCallBtn.setVisible(callActive);
            endCallBtn.setManaged(callActive);
        }
        if (infoBtn != null) {
            infoBtn.setDisable(!hasContact);
        }
    }

    // ── Info dialog ───────────────────────────────────────
    @FXML
    private void onShowInfo() {
        if (activeContact == null) {
            showInfo("Select a contact first.");
            return;
        }
        User u = userDAO.getByUsername(activeContact.username);
        if (u == null) {
            showInfo("User not found.");
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Contact info");
        dialog.setHeaderText(u.getUsername());

        StackPane bigAvatar = buildAvatar(u.getUsername(), 96);

        VBox content = new VBox(10);
        content.setAlignment(Pos.CENTER);
        content.getStyleClass().add("card");
        content.setPadding(new Insets(20));

        Label name = new Label(u.getUsername());
        name.getStyleClass().add("title-md");

        Label status = new Label("Status: " + (u.getStatus() == null ? "Offline" : u.getStatus()));
        status.getStyleClass().add("text-muted");

        String join = (u.getJoinDate() == null || u.getJoinDate().isEmpty() || "null".equals(u.getJoinDate()))
                ? "Unknown" : u.getJoinDate();
        Label joined = new Label("Joined: " + join);
        joined.getStyleClass().add("text-muted");

        Label emailLbl = new Label("Email: " + (u.getEmail() == null ? "-" : u.getEmail()));
        emailLbl.getStyleClass().add("text-muted");

        Label bioTitle = new Label("Bio");
        bioTitle.getStyleClass().add("section-title");
        Label bio = new Label((u.getBio() == null || u.getBio().isEmpty()) ? "No bio" : u.getBio());
        bio.setWrapText(true);

        content.getChildren().addAll(bigAvatar, name, status, joined, emailLbl, bioTitle, bio);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        try {
            dialog.getDialogPane().getStylesheets().add(
                    getClass().getResource("/app-dark.css").toExternalForm());
        } catch (Exception ignored) {}
        dialog.showAndWait();
    }

    // ── Contact helpers ───────────────────────────────────
    private void ensureContactVisible(String username) {
        if (username == null || username.isBlank()) return;
        String normalized = username.trim();

        for (Contact c : contacts) {
            if (c.username.equalsIgnoreCase(normalized)) {
                contacts.remove(c);
                contacts.add(0, c);
                refreshContactsView();
                return;
            }
        }

        User sender = userDAO.getByUsername(normalized);

        if (sender != null && currentUserId > 0) {
            try { contactDAO.addContact(currentUserId, sender.getId()); } catch (Exception ignored) {}
        }

        Contact newContact;
        if (sender != null) {
            newContact = new Contact(
                    sender.getId(),
                    sender.getUsername(),
                    "online".equalsIgnoreCase(sender.getStatus()),
                    sender.isBlocked(),
                    false,
                    "now"
            );
        } else {
            int fallbackId = -Math.abs(normalized.toLowerCase().hashCode());
            newContact = new Contact(
                    fallbackId,
                    normalized,
                    true,
                    false,
                    false,
                    "now"
            );
        }

        contacts.add(0, newContact);
        refreshContactsView();
    }

    private Contact findContactByUsername(String username) {
        if (username == null) return null;
        for (Contact c : contacts) {
            if (c.username.equalsIgnoreCase(username.trim())) return c;
        }
        return null;
    }

    private void refreshContactsView() {
        if (searchField != null) searchField.clear();
        renderContacts("");
    }

    // ── Add contact / profile / logout ────────────────────
    @FXML
    private void onAddContact() {
        if (currentUserId <= 0) {
            showInfo("Unable to add contact: your user id is not loaded.");
            return;
        }
        TextInputDialog d = new TextInputDialog();
        d.setTitle("Add Contact");
        d.setHeaderText("Add a contact by username");
        d.setContentText("Username:");
        d.showAndWait().ifPresent(name -> {
            String username = name.trim();
            if (username.isEmpty() || username.equalsIgnoreCase(currentUsername)) {
                showInfo("Invalid contact username.");
                return;
            }
            User target = userDAO.getByUsername(username);
            if (target == null) {
                showInfo("User not found: " + username);
                return;
            }
            if (contactDAO.addContact(currentUserId, target.getId())) {
                loadContacts();
                showInfo("Contact added: " + username);
            } else {
                loadContacts();
                showInfo("Contact already exists or could not be added.");
            }
        });
    }

    @FXML
    private void onLogout() {
        try { client.send("LOGOUT"); } catch (Exception ignored) {}
        try { client.deconnecter(); } catch (Exception ignored) {}
        SceneManager.setSession(null, null);
        SceneManager.switchTo("login.fxml");
    }

    @FXML
    private void onEditProfile() {
        User me = userDAO.getByUsername(currentUsername);
        if (me == null) return;

        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Edit Profile");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField bioField = new TextField(me.getBio() == null ? "" : me.getBio());

        // Pending bytes the user picked in this dialog. null = no change.
        // Empty array = user clicked "Remove" to clear the avatar.
        final byte[][] pendingBytes = { null };

        Label picStatus = new Label(me.getProfilePictureData() != null && me.getProfilePictureData().length > 0
                ? "Current picture: stored in database"
                : "No picture set");
        Button chooseBtn = new Button("Choose image…");
        Button clearBtn  = new Button("Remove");
        chooseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Choose profile picture");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "Images", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp"));
            File f = fc.showOpenDialog(d.getDialogPane().getScene().getWindow());
            if (f == null) return;
            try {
                byte[] compressed = compressAvatar(f);
                if (compressed == null) {
                    showInfo("Could not read that image.");
                    return;
                }
                pendingBytes[0] = compressed;
                picStatus.setText("Selected: " + f.getName() + " (" + (compressed.length / 1024) + " KB)");
            } catch (Exception ex) {
                showInfo("Failed to load image: " + ex.getMessage());
            }
        });
        clearBtn.setOnAction(e -> {
            pendingBytes[0] = new byte[0];
            picStatus.setText("Picture will be removed");
        });

        HBox picRow = new HBox(8, chooseBtn, clearBtn);
        VBox v = new VBox(8,
                new Label("Bio"), bioField,
                new Label("Profile picture"), picStatus, picRow);
        d.getDialogPane().setContent(v);

        d.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            // Bio is always saved; legacy path field is left untouched.
            userDAO.updateProfile(me.getId(), bioField.getText(), me.getProfilePicture());
            if (pendingBytes[0] != null) {
                userDAO.updateProfilePictureData(me.getId(), pendingBytes[0]);
            }
            meAvatarBox.getChildren().setAll(buildAvatar(currentUsername, 38));
        });
    }

    // Downscale to a 256x256 JPEG so the BLOB stays in the tens of KB range.
    private byte[] compressAvatar(File source) throws Exception {
        BufferedImage src = ImageIO.read(source);
        if (src == null) return null;
        int target = 256;
        BufferedImage scaled = new BufferedImage(target, target, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, target, target, null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(scaled, "jpg", out);
        return out.toByteArray();
    }

    @FXML
    private void onSendImage() {
        if (activeContact == null) {
            showInfo("Select a contact first.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png", "*.gif"));
        File file = chooser.showOpenDialog(messagesBox.getScene().getWindow());
        if (file == null) return;
        sendMediaFile(file, "IMAGE");
    }

    @FXML
    private void onAudioMessage() {
        if (activeContact == null) {
            showInfo("Select a contact first.");
            return;
        }
        if (!recordingAudio) {
            startAudioRecording();
        } else {
            stopAudioRecordingAndSend();
        }
    }

    private void sendMediaFile(File file, String mediaType) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String ext = getExtension(file.getName());
            String payload = "MEDIA_MSG|" + mediaType + "|" + file.getName() + "|" + file.length() + "|" + ext + "|" + base64;
            long mid = System.currentTimeMillis();
            String time = nowTime();
            client.send("PRIVATE|" + currentUsername + "|" + activeContact.username + "|MID:" + mid + "|" + payload);
            localStore.addMessage(new ChatMessage(currentUsername, activeContact.username, mediaType, payload,
                            time, "sent", mid),
                    false, activeContact.username);
            Label tick = createTickLabel("sent");
            tickByMid.put(mid, tick);
            pendingTicksByContact
                    .computeIfAbsent(activeContact.username.toLowerCase(), k -> new ArrayList<>())
                    .add(tick);
            addBubble(createMessageNode(payload), true, time, tick);
        } catch (Exception e) {
            showInfo("Failed to send file.");
        }
    }

    private String previewOf(String content) {
        if (content == null) return "";
        if (content.startsWith("MEDIA_MSG|")) {
            String[] f = content.split("\\|", 6);
            if (f.length >= 2 && "IMAGE".equalsIgnoreCase(f[1]))     return "[Image]";
            if (f.length >= 2 && "AUDIO_MSG".equalsIgnoreCase(f[1])) return "[Voice note]";
            return "[Attachment]";
        }
        return content.length() > 40 ? content.substring(0, 40) + "…" : content;
    }

    private String getExtension(String fileName) {
        int i = fileName.lastIndexOf('.');
        if (i < 0 || i == fileName.length() - 1) return "";
        return fileName.substring(i + 1);
    }

    private void showInfo(String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, text, ButtonType.OK);
        a.setHeaderText(null);
        a.show();
    }

    // ── Audio recording (voice note) ──────────────────────
    private void startAudioRecording() {
        try {
            AudioFormat format = new AudioFormat(44100, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            recordingLine = (TargetDataLine) AudioSystem.getLine(info);
            recordingLine.open(format);
            recordingLine.start();

            File folder = new File("chat_audio");
            if (!folder.exists()) folder.mkdirs();
            recordingFile = new File(folder, "rec_" + System.currentTimeMillis() + ".wav");

            recordingAudio = true;
            startWaveformAnimation();

            Thread writer = new Thread(() -> {
                try (AudioInputStream stream = new AudioInputStream(recordingLine)) {
                    AudioSystem.write(stream, AudioFileFormat.Type.WAVE, recordingFile);
                } catch (Exception ignored) {}
            }, "audio-recorder-writer");
            writer.setDaemon(true);
            writer.start();
        } catch (Exception e) {
            showInfo("Microphone unavailable.");
            recordingAudio = false;
            stopWaveformAnimation();
        }
    }

    private void stopAudioRecordingAndSend() {
        try {
            if (recordingLine != null) {
                recordingLine.stop();
                recordingLine.close();
            }
        } catch (Exception ignored) {}

        recordingAudio = false;
        stopWaveformAnimation();

        if (recordingFile != null && recordingFile.exists() && recordingFile.length() > 0) {
            sendMediaFile(recordingFile, "AUDIO_MSG");
        } else {
            showInfo("Recording is empty.");
        }
    }

    private void startWaveformAnimation() {
        if (waveformBox == null || recordingBar == null) return;
        recordingBar.setVisible(true);
        recordingBar.setManaged(true);

        waveformBox.getChildren().clear();
        int bars = 16;
        for (int i = 0; i < bars; i++) {
            Rectangle r = new Rectangle(3, 6);
            r.getStyleClass().add("wave-bar");
            r.setArcWidth(3);
            r.setArcHeight(3);
            waveformBox.getChildren().add(r);
        }

        waveformAnim = new Timeline(new KeyFrame(Duration.millis(120), e -> {
            for (Node n : waveformBox.getChildren()) {
                if (n instanceof Rectangle) {
                    Rectangle r = (Rectangle) n;
                    r.setHeight(4 + random.nextInt(20));
                }
            }
        }));
        waveformAnim.setCycleCount(Animation.INDEFINITE);
        waveformAnim.play();

        recordingStartMs = System.currentTimeMillis();
        recordingTimer.setText("00:00");
        recordingClock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            long s = (System.currentTimeMillis() - recordingStartMs) / 1000;
            recordingTimer.setText(String.format("%02d:%02d", s / 60, s % 60));
        }));
        recordingClock.setCycleCount(Animation.INDEFINITE);
        recordingClock.play();
    }

    private void stopWaveformAnimation() {
        if (waveformAnim != null) {
            waveformAnim.stop();
            waveformAnim = null;
        }
        if (recordingClock != null) {
            recordingClock.stop();
            recordingClock = null;
        }
        if (waveformBox != null) waveformBox.getChildren().clear();
        if (recordingBar != null) {
            recordingBar.setVisible(false);
            recordingBar.setManaged(false);
        }
    }

    private void openImagePreview(String localPath, String title) {
        try {
            File f = new File(localPath);
            if (!f.exists()) return;
            ImageView view = new ImageView(new Image(f.toURI().toString()));
            view.setPreserveRatio(true);
            view.setFitWidth(900);
            ScrollPane pane = new ScrollPane(view);
            Dialog<Void> d = new Dialog<>();
            d.setTitle(title);
            d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            d.getDialogPane().setContent(pane);
            d.showAndWait();
        } catch (Exception ignored) {}
    }
}
