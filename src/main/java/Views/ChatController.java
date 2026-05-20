package Views;

import dao.ContactDAO;
import dao.UserDAO;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
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
import model.ChatTarget;
import model.Group;
import model.User;
import server.clientAPP;
import services.JSONMessageStore;
import streaming.CallManager;
import streaming.GroupCallSession;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.InetAddress;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ChatController {

    @FXML private Label currentUserLabel;
    @FXML private TextField searchField;
    @FXML private VBox contactsList;
    @FXML private VBox groupsList;
    @FXML private VBox callsList;
    @FXML private ScrollPane contactsScroll;
    @FXML private ScrollPane groupsScroll;
    @FXML private ScrollPane callsScroll;
    @FXML private Button chatsTabBtn;
    @FXML private Button groupsTabBtn;
    @FXML private Button callsTabBtn;
    @FXML private Button newGroupBtn;
    @FXML private Button addContactBtn;
    @FXML private Button themeToggleBtn;
    @FXML private HBox meAvatarBox;
    @FXML private HBox joinMeetingBanner;
    @FXML private Label joinMeetingLabel;

    @FXML private Label activeChatTitle;
    @FXML private Label activeChatStatus;
    @FXML private HBox activeAvatarBox;
    @FXML private ScrollPane messagesScroll;
    @FXML private VBox messagesBox;
    @FXML private TextField messageInput;

    @FXML private Button audioBtn;
    @FXML private Button sendBtn;
    @FXML private Button imageBtn;
    @FXML private Button fileBtn;

    @FXML private Button audioCallBtn;
    @FXML private Button videoCallBtn;
    @FXML private Button infoBtn;
    @FXML private Button addToContactsBtn;
    @FXML private Button endCallBtn;
    @FXML private HBox callBanner;
    @FXML private Label callBannerLabel;
    @FXML private HBox typingIndicator;
    @FXML private Label typingLabel;

    @FXML private HBox recordingBar;
    @FXML private HBox waveformBox;
    @FXML private Label recordingTimer;

    private final UserDAO userDAO = new UserDAO();
    private final ContactDAO contactDAO = new ContactDAO();
    private final JSONMessageStore localStore = new JSONMessageStore();

    private clientAPP client;
    private String currentUsername;
    private int currentUserId = -1;

    private ChatTarget activeTarget;
    private final List<Contact> contacts = new ArrayList<>();
    private final List<Group> groups = new ArrayList<>();
    private final List<CallHistoryEntry> callsHistory = new ArrayList<>();
    private final Map<String, Integer> groupUnread = new HashMap<>();
    private enum Tab { CHATS, GROUPS, CALLS }
    private Tab activeTab = Tab.CHATS;

    private final Map<Long, Label> tickByMid = new HashMap<>();
    private final Map<String, List<Label>> pendingTicksByContact = new HashMap<>();

    private final Map<String, MessageBubble> bubbleIndex = new HashMap<>();
    private final Set<String> blockedByMe = new HashSet<>();
    private final Set<String> contactAddPrompted = new HashSet<>();

    private static class MessageBubble {
        final VBox bubble;
        final HBox row;
        Node content;
        final Label editedTag;
        final String mediaType;
        final boolean mine;
        MessageBubble(VBox bubble, HBox row, Node content, Label editedTag, String mediaType, boolean mine) {
            this.bubble = bubble; this.row = row; this.content = content;
            this.editedTag = editedTag; this.mediaType = mediaType; this.mine = mine;
        }
    }

    private boolean recordingAudio = false;
    private TargetDataLine recordingLine;
    private File recordingFile;
    private Timeline waveformAnim;
    private Timeline recordingClock;
    private long recordingStartMs;
    private final Random random = new Random();

    private static final String TICK_SENT = "✓";
    private static final String TICK_DOUBLE = "✓✓";
    private static final String[] AVATAR_COLORS = {
            "#25d366", "#128c7e", "#34b7f1", "#f4a261", "#e76f51",
            "#9b5de5", "#f15bb5", "#00bbf9", "#fb8500", "#06d6a0"
    };

    private boolean callActive = false;
    private String pendingCallType = "AUDIO";
    private String currentCallPeer;
    private CallManager activeCall;
    private boolean callOriginatedByMe = false;
    private long callStartMs = 0;
    private Alert incomingCallAlert;
    private static final int CALLER_AUDIO_PORT = 6000;
    private static final int RECIPIENT_AUDIO_PORT = 6001;

    private GroupCallSession activeGroupCall;
    private static final int GROUP_AUDIO_PORT = 7000;
    private static final int GROUP_VIDEO_PORT = 7100;
    private final Map<Integer, Alert> incomingGroupCallAlerts = new HashMap<>();

    private PauseTransition typingHideTimer;

    @FXML
    private void initialize() {
        currentUsername = SceneManager.getCurrentUsername();
        client = SceneManager.getCurrentClient();
        if (currentUsername == null || client == null) {
            SceneManager.switchTo("login.fxml");
            return;
        }
        User me = userDAO.getByUsername(currentUsername);
        if (me != null) currentUserId = me.getId();

        String myName = (me != null && me.getDisplayName() != null && !me.getDisplayName().isBlank())
                ? me.getDisplayName().trim() : currentUsername;
        currentUserLabel.setText(myName);
        meAvatarBox.getChildren().setAll(buildAvatar(currentUsername, 38));

        applyButtonIcons();
        applyThemeIcon();
        loadContacts();
        bindSearch();
        startServerListener();
        updateCallButtons();
        renderCurrentTab();

        client.send("GROUP_LIST");
        client.send("CALL_HISTORY");
        client.send("BLOCK_LIST");

        if (messageInput != null) {
            messageInput.textProperty().addListener((obs, old, val) -> {
                if (val == null || val.isBlank()) return;
                long now = System.currentTimeMillis();
                if (now - lastTypingSentMs < 1000) return;
                lastTypingSentMs = now;
                if (activeTarget == null) return;
                if (activeTarget.isGroup()) {
                    client.send("GROUP_TYPING|" + activeTarget.group.getId());
                } else {
                    client.send("TYPING|" + activeTarget.contact.username);
                }
            });
        }
    }

    private long lastTypingSentMs = 0;

    @FXML private void onShowChatsTab()  { switchTab(Tab.CHATS); }
    @FXML private void onShowGroupsTab() { switchTab(Tab.GROUPS); }
    @FXML private void onShowCallsTab()  { switchTab(Tab.CALLS); }

    private void switchTab(Tab tab) {
        activeTab = tab;
        renderCurrentTab();
        chatsTabBtn.getStyleClass().remove("tab-btn-active");
        groupsTabBtn.getStyleClass().remove("tab-btn-active");
        callsTabBtn.getStyleClass().remove("tab-btn-active");
        switch (tab) {
            case CHATS:  chatsTabBtn.getStyleClass().add("tab-btn-active");  break;
            case GROUPS: groupsTabBtn.getStyleClass().add("tab-btn-active"); break;
            case CALLS:  callsTabBtn.getStyleClass().add("tab-btn-active");  break;
        }
        boolean isGroups = tab == Tab.GROUPS;
        newGroupBtn.setVisible(isGroups);
        newGroupBtn.setManaged(isGroups);
        addContactBtn.setVisible(tab == Tab.CHATS);
        addContactBtn.setManaged(tab == Tab.CHATS);
    }

    private void renderCurrentTab() {
        contactsScroll.setVisible(activeTab == Tab.CHATS);
        contactsScroll.setManaged(activeTab == Tab.CHATS);
        groupsScroll.setVisible(activeTab == Tab.GROUPS);
        groupsScroll.setManaged(activeTab == Tab.GROUPS);
        callsScroll.setVisible(activeTab == Tab.CALLS);
        callsScroll.setManaged(activeTab == Tab.CALLS);
        switch (activeTab) {
            case CHATS:  renderContacts(searchField.getText() == null ? "" : searchField.getText()); break;
            case GROUPS: renderGroups(searchField.getText() == null ? "" : searchField.getText());   break;
            case CALLS:  renderCalls();                                                              break;
        }
    }

    private void applyButtonIcons() {
        if (audioCallBtn  != null) audioCallBtn.setGraphic(makeIcon(IconShape.PHONE,  18, "icon-white"));
        if (videoCallBtn  != null) videoCallBtn.setGraphic(makeIcon(IconShape.VIDEO,  20, "icon-white"));
        if (infoBtn       != null) infoBtn.setGraphic(makeIcon(IconShape.INFO,        18, "icon-white"));
        if (endCallBtn    != null) endCallBtn.setGraphic(makeIcon(IconShape.HANGUP,   20, "icon-white"));
        if (sendBtn       != null) sendBtn.setGraphic(makeIcon(IconShape.PLANE,       18, "icon-white"));
        if (audioBtn      != null) audioBtn.setGraphic(makeIcon(IconShape.MIC,        18, "icon-grey"));
        if (imageBtn      != null) imageBtn.setGraphic(makeIcon(IconShape.IMAGE,      18, "icon-grey"));
        if (fileBtn       != null) fileBtn.setGraphic(makeIcon(IconShape.FILE,        18, "icon-grey"));
        if (addContactBtn != null) addContactBtn.setGraphic(makeIcon(IconShape.PLUS,  18, "icon-white"));
        if (newGroupBtn   != null) newGroupBtn.setGraphic(makeIcon(IconShape.GROUP,   18, "icon-white"));
    }

    private enum IconShape { PHONE, VIDEO, PLANE, INFO, MIC, PLUS, HANGUP, GROUP, IMAGE, FILE, SUN, MOON }

    private SVGPath makeIcon(IconShape shape, double size, String style) {
        SVGPath p = new SVGPath();
        switch (shape) {
            case PHONE:  p.setContent("M20 15.5c-1.25 0-2.45-.2-3.57-.57a1 1 0 0 0-1.02.24l-2.2 2.2a15.05 15.05 0 0 1-6.59-6.59l2.2-2.2a1 1 0 0 0 .24-1.02A11.36 11.36 0 0 1 8.5 4a1 1 0 0 0-1-1H4a1 1 0 0 0-1 1c0 9.39 7.61 17 17 17a1 1 0 0 0 1-1v-3.5a1 1 0 0 0-1-1z"); break;
            case VIDEO:  p.setContent("M17 10.5V7a1 1 0 0 0-1-1H4a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-3.5l4 4v-11l-4 4z"); break;
            case PLANE:  p.setContent("M2.01 21l20.99-9L2.01 3 2 10l15 2-15 2 .01 7z"); break;
            case INFO:   p.setContent("M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm1 15h-2v-6h2zm0-8h-2V7h2z"); break;
            case MIC:    p.setContent("M12 14a3 3 0 0 0 3-3V5a3 3 0 0 0-6 0v6a3 3 0 0 0 3 3zm5-3a5 5 0 0 1-10 0H5a7 7 0 0 0 6 6.92V21h2v-3.08A7 7 0 0 0 19 11z"); break;
            case PLUS:   p.setContent("M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6z"); break;
            case HANGUP: p.setContent("M12 9c-1.6 0-3.15.25-4.6.72v3.1c0 .39-.23.74-.56.9-.98.49-1.87 1.12-2.66 1.85-.18.18-.43.28-.7.28a.97.97 0 0 1-.7-.29L.29 13.08a.96.96 0 0 1-.29-.7c0-.28.11-.53.29-.71C3.34 8.78 7.46 7 12 7s8.66 1.78 11.71 4.67c.18.18.29.43.29.71 0 .28-.11.53-.29.71l-2.48 2.48a.97.97 0 0 1-.7.29c-.27 0-.52-.11-.7-.28a11.7 11.7 0 0 0-2.67-1.85.996.996 0 0 1-.56-.9v-3.1A16.2 16.2 0 0 0 12 9z"); break;
            case GROUP:  p.setContent("M12 12.75a4 4 0 1 0-4-4 4 4 0 0 0 4 4zm-7 7.5C5 16.69 8.69 14 12 14s7 2.69 7 6.25V21H5v-.75z"); break;
            case IMAGE:  p.setContent("M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.9 13.98l2.1 2.53 3.1-3.99L18 18H6l2.9-4.02z"); break;
            case FILE:   p.setContent("M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm-1 7V3.5L18.5 9H13z"); break;
            case SUN:    p.setContent("M12 7a5 5 0 1 0 5 5 5 5 0 0 0-5-5zm0-5v3m0 14v3m9-12h-3M5 12H2m15.5-6.5l-2.1 2.1m-7.8 7.8l-2.1 2.1m12-2.1l-2.1-2.1m-7.8-7.8L6.5 6.5z"); break;
            case MOON:   p.setContent("M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"); break;
        }
        p.getStyleClass().add(style);
        double s = size / 24.0;
        p.setScaleX(s);
        p.setScaleY(s);
        return p;
    }

    private void loadContacts() {
        contacts.clear();
        if (currentUserId > 0) contacts.addAll(contactDAO.getContacts(currentUserId));

        Set<String> known = new HashSet<>();
        for (Contact c : contacts) known.add(c.username.toLowerCase());

        for (String peer : localStore.getConversationPeers(currentUsername)) {
            if (peer == null || peer.isBlank()) continue;
            if (peer.equalsIgnoreCase(currentUsername)) continue;
            if (known.contains(peer.toLowerCase())) continue;
            User u = userDAO.getByUsername(peer);
            Contact c;
            if (u != null) {
                c = new Contact(u.getId(), u.getUsername(),
                        "online".equalsIgnoreCase(u.getStatus()), u.isBlocked(), false, "now");
                c.displayName = u.getDisplayName();
            } else {
                c = new Contact(-Math.abs(peer.toLowerCase().hashCode()), peer, false, false, false, "now");
            }
            List<ChatMessage> conv = localStore.getConversation(currentUsername, peer, false, peer);
            if (conv != null && !conv.isEmpty()) {
                ChatMessage last = conv.get(conv.size() - 1);
                c.lastMessagePreview = previewOf(last.getContent());
                c.lastMessageTime = last.getTimestamp();
                int unread = 0;
                for (ChatMessage m : conv) {
                    if (currentUsername.equalsIgnoreCase(m.getReceiver())
                            && !"read".equalsIgnoreCase(m.getStatus())) unread++;
                }
                c.unreadCount = unread;
            }
            contacts.add(c);
            known.add(peer.toLowerCase());
        }
        renderCurrentTab();
    }

    private String peerLabel(Contact c) {
        if (c == null) return "";
        if (c.alias != null && !c.alias.isBlank()) return c.alias.trim();
        if (c.displayName != null && !c.displayName.isBlank()) return c.displayName.trim();
        return displayNameFor(c.username);
    }

    private final Map<String, String> nameCache = new HashMap<>();

    String displayNameFor(String username) {
        if (username == null || username.isBlank()) return "";
        for (Contact c : contacts) {
            if (c.username != null && c.username.equalsIgnoreCase(username)) {
                if (c.alias != null && !c.alias.isBlank()) return c.alias.trim();
                if (c.displayName != null && !c.displayName.isBlank()) return c.displayName.trim();
                break;
            }
        }
        String key = username.toLowerCase();
        String cached = nameCache.get(key);
        if (cached != null) return cached.isEmpty() ? username : cached;
        String resolved = "";
        try {
            User u = userDAO.getByUsername(username);
            if (u != null && u.getDisplayName() != null && !u.getDisplayName().isBlank())
                resolved = u.getDisplayName().trim();
        } catch (Exception ignored) {}
        nameCache.put(key, resolved);
        return resolved.isEmpty() ? username : resolved;
    }

    private String targetLabel(ChatTarget t) {
        if (t == null) return "";
        return t.isGroup() ? t.group.getName() : peerLabel(t.contact);
    }

    private void bindSearch() {
        searchField.textProperty().addListener((obs, oldV, newV) -> renderCurrentTab());
    }

    private void renderContacts(String filter) {
        contactsList.getChildren().clear();
        for (Contact c : contacts) {
            String label = peerLabel(c);
            if (!filter.isEmpty()
                    && !c.username.toLowerCase().contains(filter.toLowerCase())
                    && !label.toLowerCase().contains(filter.toLowerCase())) continue;
            boolean blocked = blockedByMe.contains(c.username.toLowerCase());
            HBox row = buildSidebarRow(buildAvatar(c.username, 44), label,
                    blocked ? "blocked"
                            : (c.isOnline ? "online" : (c.lastMessagePreview == null ? "" : c.lastMessagePreview)),
                    c.unreadCount,
                    c.isOnline && !blocked,
                    activeTarget != null && activeTarget.isContact()
                            && activeTarget.contact.username.equalsIgnoreCase(c.username));
            if (blocked) row.getStyleClass().add("contact-row-blocked");
            row.setOnMouseClicked(e -> openConversation(ChatTarget.ofContact(c)));
            contactsList.getChildren().add(row);
        }
    }

    private void renderGroups(String filter) {
        groupsList.getChildren().clear();
        for (Group g : groups) {
            if (!filter.isEmpty() && !g.getName().toLowerCase().contains(filter.toLowerCase())) continue;
            String sub = g.getMemberCount() + " members";
            int unread = groupUnread.getOrDefault("G:" + g.getId(), 0);
            HBox row = buildSidebarRow(buildAvatar(g.getName(), 44), g.getName(), sub, unread, false,
                    activeTarget != null && activeTarget.isGroup()
                            && activeTarget.group.getId() == g.getId());
            row.setOnMouseClicked(e -> openConversation(ChatTarget.ofGroup(g)));
            groupsList.getChildren().add(row);
        }
        if (groupsList.getChildren().isEmpty()) {
            Label empty = new Label("No groups yet. Tap + to create one.");
            empty.getStyleClass().add("text-muted");
            empty.setPadding(new Insets(18));
            groupsList.getChildren().add(empty);
        }
    }

    private void renderCalls() {
        callsList.getChildren().clear();
        if (callsHistory.isEmpty()) {
            Label empty = new Label("No calls yet.");
            empty.getStyleClass().add("text-muted");
            empty.setPadding(new Insets(18));
            callsList.getChildren().add(empty);
            return;
        }
        for (CallHistoryEntry e : callsHistory) callsList.getChildren().add(buildCallRow(e));
    }

    private HBox buildCallRow(CallHistoryEntry e) {
        HBox row = new HBox(12);
        row.getStyleClass().add("call-row");
        row.setAlignment(Pos.CENTER_LEFT);
        String shownName = e.isGroup ? e.otherName : displayNameFor(e.otherName);
        StackPane avatar = buildAvatar(shownName, 40);
        VBox text = new VBox(2);
        Label name = new Label(shownName + (e.isGroup ? " (group)" : ""));
        name.getStyleClass().add("contact-name");
        boolean missed = "missed".equalsIgnoreCase(e.status) || "cancelled".equalsIgnoreCase(e.status);
        String typeLabel = ("video".equalsIgnoreCase(e.type) ? "Video" : "Voice") + " call";
        String detail = missed ? typeLabel + " · Missed"
                : "ended".equalsIgnoreCase(e.status)
                    ? typeLabel + " · " + formatDuration(e.duration)
                    : typeLabel + " · " + capitalize(e.status);
        Label sub = new Label(detail);
        sub.getStyleClass().add(missed ? "call-missed" : "call-ended");
        Label when = new Label(prettyTime(e.startedAt));
        when.getStyleClass().add("call-meta");
        text.getChildren().addAll(name, sub, when);
        HBox.setHgrow(text, javafx.scene.layout.Priority.ALWAYS);

        Button redial = new Button();
        redial.setGraphic(makeIcon(
                "video".equalsIgnoreCase(e.type) ? IconShape.VIDEO : IconShape.PHONE,
                16, "icon-white"));
        redial.getStyleClass().add("btn-icon-green");
        redial.setOnAction(ev -> redialFrom(e));

        row.getChildren().addAll(avatar, text, redial);
        return row;
    }

    private void redialFrom(CallHistoryEntry e) {
        String type = "video".equalsIgnoreCase(e.type) ? "VIDEO" : "AUDIO";
        if (e.isGroup) {
            Group g = findGroupByName(e.otherName);
            if (g == null) { showInfo("Group not found."); return; }
            openConversation(ChatTarget.ofGroup(g));
            startOutgoingGroupCall(type);
        } else {
            Contact c = findContactByUsername(e.otherName);
            if (c == null) {
                User u = userDAO.getByUsername(e.otherName);
                if (u == null) { showInfo("Contact not found."); return; }
                c = new Contact(u.getId(), u.getUsername(),
                        "online".equalsIgnoreCase(u.getStatus()), u.isBlocked(), false, "now");
                contacts.add(0, c);
            }
            switchTab(Tab.CHATS);
            openConversation(ChatTarget.ofContact(c));
            startOutgoingCall(type);
        }
    }

    private HBox buildSidebarRow(StackPane avatar, String title, String sub, int unread,
                                 boolean online, boolean active) {
        HBox row = new HBox(12);
        row.getStyleClass().add("contact-row");
        if (active) row.getStyleClass().add("contact-row-active");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 14, 10, 14));
        VBox text = new VBox(2);
        Label name = new Label(title);
        name.getStyleClass().add("contact-name");
        Label subL = new Label(sub == null ? "" : sub);
        subL.getStyleClass().add("contact-preview");
        text.getChildren().addAll(name, subL);
        HBox.setHgrow(text, javafx.scene.layout.Priority.ALWAYS);
        Circle dot = new Circle(5);
        dot.getStyleClass().add(online ? "status-online" : "status-offline");
        row.getChildren().addAll(avatar, text, dot);
        if (unread > 0) {
            Label badge = new Label(String.valueOf(unread));
            badge.getStyleClass().add("unread-badge");
            row.getChildren().add(badge);
        }
        return row;
    }

    private StackPane buildAvatar(String username, double size) {
        StackPane sp = new StackPane();
        sp.setMinSize(size, size);
        sp.setPrefSize(size, size);
        sp.setMaxSize(size, size);
        Image pic = loadProfilePicture(username);
        if (pic != null) {
            ImageView iv = new ImageView(pic);
            iv.setFitWidth(size); iv.setFitHeight(size);
            iv.setPreserveRatio(false); iv.setSmooth(true);
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
            byte[] data = u.getProfilePictureData();
            if (data != null && data.length > 0) return new Image(new ByteArrayInputStream(data));
            String path = u.getProfilePicture();
            if (path == null || path.isEmpty()) return null;
            File f = new File(path);
            if (f.exists() && f.isFile()) return new Image(f.toURI().toString(), true);
            if (path.startsWith("http") || path.startsWith("file:")) return new Image(path, true);
            if (path.startsWith("/")) return new Image(getClass().getResourceAsStream(path));
        } catch (Exception ignored) {}
        return null;
    }

    private void openConversation(ChatTarget target) {
        activeTarget = target;
        activeChatTitle.setText(targetLabel(target));
        activeAvatarBox.getChildren().setAll(buildAvatar(target.displayName(), 40));
        messagesBox.getChildren().clear();
        hideTypingIndicator();

        if (target.isContact()) {
            Contact c = target.contact;
            activeChatStatus.setText(c.isOnline ? "online" : "offline");
            c.unreadCount = 0;
            clearTickReferencesFor(c.username);
            bubbleIndex.keySet().removeIf(k -> k.startsWith("P:"));
            List<ChatMessage> hist = localStore.getConversation(currentUsername, c.username, false, c.username);
            for (ChatMessage m : hist) {
                boolean mine = m.getSender().equalsIgnoreCase(currentUsername);
                Label tick = null;
                if (mine) {
                    tick = createTickLabel(m.getStatus());
                    if ("sent".equalsIgnoreCase(m.getStatus())) {
                        tickByMid.put(m.getTimestamp(), tick);
                        pendingTicksByContact
                                .computeIfAbsent(c.username.toLowerCase(), k -> new ArrayList<>())
                                .add(tick);
                    }
                }
                String time = m.getTime() == null ? nowTime() : m.getTime();
                String key = m.getTimestamp() > 0
                        ? privateBubbleKey(c.username, m.getSender(), m.getTimestamp()) : null;
                if ("SYSTEM".equalsIgnoreCase(m.getType())) {
                    addSystemBubble(m.getContent(), time);
                } else {
                    addBubble(createMessageNode(m.getContent()), mine, time, tick, key, m.getType(), false);
                }
            }
            if (hist.isEmpty()) client.send("HISTORY|" + c.username);
        } else {
            Group g = target.group;
            activeChatStatus.setText(g.getMemberCount() + " members");
            groupUnread.put("G:" + g.getId(), 0);
            bubbleIndex.keySet().removeIf(k -> k.startsWith("G:"));

            List<ChatMessage> hist = localStore.getConversation(currentUsername, "G:" + g.getId(), true, "G:" + g.getId());
            for (ChatMessage m : hist) {
                boolean mine = m.getSender().equalsIgnoreCase(currentUsername);
                String time = m.getTime() == null ? nowTime() : m.getTime();
                if ("SYSTEM".equalsIgnoreCase(m.getType())) {
                    addSystemBubble(m.getContent(), time);
                } else {
                    String key = m.getTimestamp() > 0
                            ? groupBubbleKey(g.getId(), m.getSender(), m.getTimestamp()) : null;
                    addGroupBubble(m.getSender(), createMessageNode(m.getContent()),
                            mine, time, key, m.getType(), false);
                }
            }
            if (hist.isEmpty()) client.send("GROUP_HISTORY|" + g.getId());
            client.send("GROUP_CALL_STATUS|" + g.getId());
        }
        renderCurrentTab();
        updateCallButtons();
        refreshAddToContactsButton();
    }

    private void refreshAddToContactsButton() {
        if (addToContactsBtn == null) return;
        boolean show = activeTarget != null
                && activeTarget.isContact()
                && currentUserId > 0
                && (activeTarget.contact.alias == null || activeTarget.contact.alias.isBlank());
        addToContactsBtn.setVisible(show);
        addToContactsBtn.setManaged(show);
    }

    @FXML
    private void onAddCurrentPeer() {
        if (activeTarget == null || !activeTarget.isContact() || currentUserId <= 0) return;
        String number = services.PhoneUtil.canonical(activeTarget.contact.username);
        User target = userDAO.getByUsername(number);
        if (target == null) { showInfo("User not found: " + number); return; }

        TextInputDialog nameDlg = new TextInputDialog(
                target.getDisplayName() == null ? "" : target.getDisplayName());
        nameDlg.setTitle("Add Contact");
        nameDlg.setHeaderText("Name for this contact");
        nameDlg.setContentText("Name (shown instead of the number):");
        applyCurrentThemeToDialog(nameDlg.getDialogPane());
        String alias = nameDlg.showAndWait().map(String::trim).orElse("");

        boolean added = contactDAO.addContact(currentUserId, target.getId());
        if (!alias.isEmpty())
            contactDAO.renameContact(currentUserId, target.getId(), alias);
        loadContacts();

        Contact saved = findContactByUsername(number);
        if (saved != null) openConversation(ChatTarget.ofContact(saved));
        else refreshAddToContactsButton();
        showInfo(added ? "Contact added." : "Contact already existed (name updated).");
    }

    private void clearTickReferencesFor(String contactUsername) {
        if (contactUsername == null) return;
        List<Label> old = pendingTicksByContact.remove(contactUsername.toLowerCase());
        if (old == null) return;
        tickByMid.entrySet().removeIf(e -> old.contains(e.getValue()));
    }

    @FXML
    private void sendMessage() {
        if (activeTarget == null) return;
        String content = messageInput.getText() == null ? "" : messageInput.getText().trim();
        if (content.isEmpty()) return;
        if (activeTarget.isContact() && blockedByMe.contains(activeTarget.contact.username.toLowerCase())) {
            showInfo("You have blocked this user. Unblock them first.");
            return;
        }

        long mid = System.currentTimeMillis();
        String time = nowTime();
        if (activeTarget.isContact()) {
            String peer = activeTarget.contact.username;
            String key = privateBubbleKey(peer, currentUsername, mid);
            client.send("PRIVATE|" + currentUsername + "|" + peer + "|MID:" + mid + "|" + content);
            localStore.addMessage(new ChatMessage(currentUsername, peer, "TEXT", content, time, "sent", mid),
                    false, peer);
            Label tick = createTickLabel("sent");
            tickByMid.put(mid, tick);
            pendingTicksByContact.computeIfAbsent(peer.toLowerCase(), k -> new ArrayList<>()).add(tick);
            addBubble(createMessageNode(content), true, time, tick, key, "TEXT", false);
        } else {
            int gid = activeTarget.group.getId();
            String key = groupBubbleKey(gid, currentUsername, mid);
            client.send("GROUP_MSG|" + gid + "|MID:" + mid + "|" + content);
            localStore.addMessage(new ChatMessage(currentUsername, "G:" + gid, "TEXT", content, time, "sent", mid),
                    true, "G:" + gid);
            addGroupBubble(currentUsername, createMessageNode(content), true, time, key, "TEXT", false);
        }
        messageInput.clear();
        hideTypingIndicator();
        services.NotificationSounds.messageSent();
    }

    private String nowTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private void renderMessage(ChatMessage m, boolean mine, Label tick) {
        String t = m.getTime() == null ? nowTime() : m.getTime();
        if ("SYSTEM".equalsIgnoreCase(m.getType())) {
            addSystemBubble(m.getContent(), t);
            return;
        }
        addBubble(createMessageNode(m.getContent()), mine, t, tick);
    }

    private void addBubble(Node node, boolean mine, String time, Label statusTick) {
        addBubble(node, mine, time, statusTick, null, "TEXT", false);
    }

    private void addBubble(Node node, boolean mine, String time, Label statusTick,
                           String indexKey, String mediaKind, boolean edited) {
        if (indexKey != null && bubbleIndex.containsKey(indexKey)) return;

        HBox row = new HBox();
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        VBox bubble = new VBox(4);
        bubble.setMaxWidth(440);
        bubble.getStyleClass().add(mine ? "bubble-mine" : "bubble-other");
        bubble.setPadding(new Insets(8, 10, 6, 10));
        if (node instanceof Label) ((Label) node).getStyleClass().add("bubble-text");

        Label editedTag = new Label("(edited)");
        editedTag.getStyleClass().add("bubble-edited");
        editedTag.setVisible(edited);
        editedTag.setManaged(edited);

        Label timeLabel = new Label(time == null ? "" : time);
        timeLabel.getStyleClass().add("bubble-time");
        HBox timeRow = new HBox(4);
        timeRow.setAlignment(Pos.CENTER_RIGHT);
        timeRow.getChildren().addAll(editedTag, timeLabel);
        if (mine && statusTick != null) timeRow.getChildren().add(statusTick);
        bubble.getChildren().addAll(node, timeRow);
        row.getChildren().add(bubble);
        messagesBox.getChildren().add(row);

        if (indexKey != null) {
            MessageBubble mb = new MessageBubble(bubble, row, node, editedTag, mediaKind, mine);
            bubbleIndex.put(indexKey, mb);
            if (mine) attachOwnContextMenu(bubble, indexKey, mb);
        }
        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
    }

    private void addGroupBubble(String sender, Node content, boolean mine, String time) {
        addGroupBubble(sender, content, mine, time, null, "TEXT", false);
    }

    private void addGroupBubble(String sender, Node content, boolean mine, String time,
                                String indexKey, String mediaKind, boolean edited) {
        HBox row = new HBox();
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        VBox bubble = new VBox(4);
        bubble.setMaxWidth(440);
        bubble.getStyleClass().add(mine ? "bubble-mine" : "bubble-other");
        bubble.setPadding(new Insets(8, 10, 6, 10));

        if (!mine && sender != null && !sender.isBlank()) {
            Label name = new Label(displayNameFor(sender));
            name.getStyleClass().add("group-sender");
            name.setStyle("-fx-text-fill: " + colorForUsername(sender) + ";");
            bubble.getChildren().add(name);
        }

        if (content instanceof Label) ((Label) content).getStyleClass().add("bubble-text");

        Label editedTag = new Label("(edited)");
        editedTag.getStyleClass().add("bubble-edited");
        editedTag.setVisible(edited);
        editedTag.setManaged(edited);

        Label timeLabel = new Label(time == null ? "" : time);
        timeLabel.getStyleClass().add("bubble-time");
        HBox timeRow = new HBox(4);
        timeRow.setAlignment(Pos.CENTER_RIGHT);
        timeRow.getChildren().addAll(editedTag, timeLabel);

        bubble.getChildren().addAll(content, timeRow);
        row.getChildren().add(bubble);
        messagesBox.getChildren().add(row);

        if (indexKey != null) {
            MessageBubble mb = new MessageBubble(bubble, row, content, editedTag, mediaKind, mine);
            bubbleIndex.put(indexKey, mb);
            if (mine) attachOwnContextMenu(bubble, indexKey, mb);
        }
        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
    }

    private void attachOwnContextMenu(VBox bubble, String indexKey, MessageBubble mb) {
        ContextMenu cm = new ContextMenu();
        if ("TEXT".equalsIgnoreCase(mb.mediaType)) {
            MenuItem edit = new MenuItem("Edit");
            edit.setOnAction(e -> promptEditMessage(indexKey, mb));
            cm.getItems().add(edit);
        }
        MenuItem del = new MenuItem("Delete");
        del.setOnAction(e -> confirmDeleteMessage(indexKey, mb));
        cm.getItems().add(del);
        bubble.setOnContextMenuRequested(ev -> cm.show(bubble, ev.getScreenX(), ev.getScreenY()));
    }

    private void promptEditMessage(String indexKey, MessageBubble mb) {
        String current = (mb.content instanceof Label) ? ((Label) mb.content).getText() : "";
        TextInputDialog dialog = new TextInputDialog(current);
        dialog.setTitle("Edit message");
        dialog.setHeaderText("Edit your message");
        dialog.setContentText("New text:");
        applyCurrentThemeToDialog(dialog.getDialogPane());
        dialog.showAndWait().ifPresent(newText -> {
            String trimmed = newText.trim();
            if (trimmed.isEmpty() || trimmed.equals(current)) return;
            long mid = midFromKey(indexKey);
            if (mid <= 0) return;
            if (indexKey.startsWith("G:")) {
                int gid = gidFromKey(indexKey);
                client.send("MSG_EDIT_GROUP|" + gid + "|" + mid + "|" + trimmed);
            } else if (indexKey.startsWith("P:")) {
                String peer = peerFromKey(indexKey);
                client.send("MSG_EDIT_PRIV|" + peer + "|" + mid + "|" + trimmed);
            }
        });
    }

    private void confirmDeleteMessage(String indexKey, MessageBubble mb) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Delete this message?", ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText(null);
        applyCurrentThemeToDialog(a.getDialogPane());
        a.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            long mid = midFromKey(indexKey);
            if (mid <= 0) return;
            if (indexKey.startsWith("G:")) {
                int gid = gidFromKey(indexKey);
                client.send("MSG_DELETE_GROUP|" + gid + "|" + mid);
            } else if (indexKey.startsWith("P:")) {
                String peer = peerFromKey(indexKey);
                client.send("MSG_DELETE_PRIV|" + peer + "|" + mid);
            }
        });
    }

    private void replaceBubbleText(MessageBubble mb, String newText, boolean edited) {
        if (mb == null) return;
        if (mb.content instanceof Label) {
            ((Label) mb.content).setText(newText);
        } else {
            Label l = new Label(newText);
            l.setWrapText(true);
            l.setMaxWidth(400);
            l.getStyleClass().add("bubble-text");
            int idx = mb.bubble.getChildren().indexOf(mb.content);
            if (idx >= 0) mb.bubble.getChildren().set(idx, l);
            mb.content = l;
        }
        if (mb.editedTag != null) {
            mb.editedTag.setVisible(edited);
            mb.editedTag.setManaged(edited);
        }
    }

    private void markBubbleDeleted(MessageBubble mb) {
        if (mb == null) return;
        Label l = new Label("[message deleted]");
        l.getStyleClass().addAll("bubble-text", "bubble-deleted");
        int idx = mb.bubble.getChildren().indexOf(mb.content);
        if (idx >= 0) mb.bubble.getChildren().set(idx, l);
        mb.content = l;
        if (mb.editedTag != null) {
            mb.editedTag.setVisible(false);
            mb.editedTag.setManaged(false);
        }
        mb.bubble.setOnContextMenuRequested(null);
    }

    private long midFromKey(String key) {
        int last = key.lastIndexOf(':');
        if (last < 0) return -1;
        try { return Long.parseLong(key.substring(last + 1)); }
        catch (NumberFormatException e) { return -1; }
    }

    private int gidFromKey(String key) {
        String[] p = key.split(":", 4);
        if (p.length < 2) return -1;
        try { return Integer.parseInt(p[1]); } catch (NumberFormatException e) { return -1; }
    }

    private String peerFromKey(String key) {
        String[] p = key.split(":", 4);
        return p.length >= 2 ? p[1] : "";
    }

    private static String privateBubbleKey(String peer, String sender, long mid) {
        return "P:" + (peer == null ? "" : peer.toLowerCase())
                + ":" + (sender == null ? "" : sender.toLowerCase())
                + ":" + mid;
    }

    private static String groupBubbleKey(int gid, String sender, long mid) {
        return "G:" + gid + ":" + (sender == null ? "" : sender.toLowerCase()) + ":" + mid;
    }

    private String colorForUsername(String username) {
        if (username == null || username.isEmpty()) return "#25d366";
        int idx = Math.abs(username.toLowerCase().hashCode()) % AVATAR_COLORS.length;
        return AVATAR_COLORS[idx];
    }

    private void addSystemBubble(String text, String time) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER);
        VBox bubble = new VBox(2);
        bubble.getStyleClass().add("bubble-system");
        Label l = new Label(text);
        l.getStyleClass().add("bubble-system-text");
        bubble.getChildren().add(l);
        row.getChildren().add(bubble);
        messagesBox.getChildren().add(row);
        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
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
            case "delivered": tick.setText(TICK_DOUBLE); tick.getStyleClass().add("bubble-tick-delivered"); break;
            case "read":      tick.setText(TICK_DOUBLE); tick.getStyleClass().add("bubble-tick-read");      break;
            default:          tick.setText(TICK_SENT);   tick.getStyleClass().add("bubble-tick-sent");      break;
        }
    }

    private Node createMessageNode(String content) {
        if (content != null && content.startsWith("MEDIA_MSG|")) {
            String[] fields = content.split("\\|", 6);
            if (fields.length >= 6) {
                String mediaType = fields[1];
                String fileName = fields[2];
                String sizeStr = fields[3];
                String encoded = fields[5];
                File file = persistTempFile(fileName, encoded);
                if ("IMAGE".equals(mediaType)) {
                    ImageView imageView = new ImageView(new Image(file.toURI().toString()));
                    imageView.setFitWidth(220); imageView.setPreserveRatio(true);
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
                if ("FILE".equals(mediaType)) {
                    return buildFileChip(file, fileName, sizeStr);
                }
            }
        }
        Label text = new Label(content == null ? "" : content);
        text.setWrapText(true); text.setMaxWidth(400);
        text.getStyleClass().add("bubble-text");
        return text;
    }

    /** A clickable chip showing the file name + size; click to download/save. */
    private Node buildFileChip(File cached, String fileName, String sizeStr) {
        HBox chip = new HBox(10);
        chip.getStyleClass().add("file-chip");
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setPadding(new Insets(4, 4, 4, 4));

        SVGPath icon = makeIcon(IconShape.FILE, 20, "icon-grey");
        StackPane iconBox = new StackPane(icon);
        iconBox.setPrefSize(28, 28);

        VBox meta = new VBox(2);
        Label name = new Label(fileName);
        name.getStyleClass().add("file-chip-name");
        name.setMaxWidth(260);
        long sizeBytes = 0;
        try { sizeBytes = Long.parseLong(sizeStr); } catch (Exception ignored) {}
        Label size = new Label(prettySize(sizeBytes) + " · click to save");
        size.getStyleClass().add("file-chip-meta");
        meta.getChildren().addAll(name, size);

        chip.getChildren().addAll(iconBox, meta);
        chip.setOnMouseClicked(ev -> saveFileAs(cached, fileName));
        chip.setStyle("-fx-cursor: hand;");
        return chip;
    }

    private void saveFileAs(File source, String suggestedName) {
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Save file");
            fc.setInitialFileName(suggestedName);
            File target = fc.showSaveDialog(messagesBox.getScene().getWindow());
            if (target == null) return;
            Files.copy(source.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            showInfo("Could not save file: " + e.getMessage());
        }
    }

    private static String prettySize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
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

    private void startServerListener() {
        Thread listener = new Thread(() -> {
            while (true) {
                String raw = client.read();
                if (raw == null) break;
                Platform.runLater(() -> handleIncoming(raw));
            }
        }, "chat-server-listener");
        listener.setDaemon(true);
        listener.start();
    }

    private void handleIncoming(String raw) {
        if (raw == null || raw.isEmpty()) return;
        String[] p = raw.split("\\|", 4);
        String type = p[0];

        switch (type) {
            case "PRIVATE":          handlePrivate(p);            return;
            case "TYPING":           handlePrivateTyping(p);      return;
            case "MSG_STATUS":       handleMsgStatus(p);          return;
            case "CONV_DELIVERED":   handleConvDelivered(p);      return;
            case "HISTORY":          handleHistory(p);            return;
            case "USER_STATUS":      handleUserStatus(p);         return;
            case "CALL_REQUEST":     handleIncomingCallRequest(p);return;
            case "CALL_RINGING":     showCallBanner("Ringing " + (p.length>1 ? displayNameFor(p[1]) : "") + "..."); return;
            case "CALL_ACCEPTED":    handleCallAccepted(p);       return;
            case "START_AUDIO":      handleStartAudio(p);         return;
            case "CALL_REFUSED":     handleCallRefused(p);        return;
            case "CALL_CANCELLED":   handleCallCancelled(p);      return;
            case "CALL_ENDED":       handleCallEnded(p);          return;
            case "CALL_UNAVAILABLE": showInfo("User is offline."); stopCallLocally(); return;
            case "CALL_BUSY":        showInfo("User is busy."); stopCallLocally(); return;
            case "CALL_END_OK":      stopCallLocally(); return;
            case "CALL_REFUSE_OK":   stopCallLocally(); return;
            case "CALL_CANCEL_OK":   stopCallLocally(); return;
            case "WAIT_CALLER_READY": return;
            case "GROUP_INFO":       handleGroupInfo(p, raw);     return;
            case "GROUP_LIST_END":   renderCurrentTab();          return;
            case "GROUP_HISTORY":    handleGroupHistory(p, raw);  return;
            case "GROUP_HISTORY_END": return;
            case "GROUP_MSG":        handleGroupMsg(p, raw);      return;
            case "GROUP_TYPING":     handleGroupTyping(p);        return;
            case "GROUP_REMOVED":    handleGroupRemoved(p);       return;
            case "GROUP_FORBIDDEN":  showInfo("You don't have permission for that action."); return;
            case "GROUP_CALL_INVITE":     handleGroupCallInvite(p);     return;
            case "GROUP_CALL_STARTED":    return;
            case "GROUP_CALL_PEERS":      handleGroupCallPeers(p, raw); return;
            case "GROUP_CALL_PEER_JOINED":handleGroupCallPeerJoined(p, raw); return;
            case "GROUP_CALL_PEER_LEFT":  handleGroupCallPeerLeft(p);   return;
            case "GROUP_CALL_ACTIVE":     handleGroupCallActive(p);     return;
            case "GROUP_CALL_NOT_FOUND":  showInfo("Meeting has ended.");return;
            case "GROUP_CALL_STATUS":     handleGroupCallStatus(raw);   return;
            case "MSG_EDITED_PRIV":       handleMsgEditedPriv(raw);     return;
            case "MSG_DELETED_PRIV":      handleMsgDeletedPriv(p);      return;
            case "MSG_EDITED_GROUP":      handleMsgEditedGroup(raw);    return;
            case "MSG_DELETED_GROUP":     handleMsgDeletedGroup(p);     return;
            case "MSG_EDIT_FAIL":         showInfo("Could not edit message."); return;
            case "MSG_DELETE_FAIL":       showInfo("Could not delete message."); return;
            case "BLOCKED":               handleBlockedNotice(p);       return;
            case "BLOCKED_OK":            handleBlockedOk(p, true);     return;
            case "UNBLOCKED_OK":          handleBlockedOk(p, false);    return;
            case "BLOCK_LIST":            handleBlockList(p);           return;
            case "BLOCK_NOOP":            return;
            case "GROUP_LEFT":            handleGroupLeft(p);           return;
            case "CALL_HISTORY":     handleCallHistory(p, raw);   return;
            case "CALL_HISTORY_END": renderCurrentTab();          return;
        }
    }

    private void handlePrivate(String[] p) {
        if (p.length < 4) return;
        String from = p[1];
        String rawContent = p[3];

        long mid = 0;
        String text = rawContent;
        if (rawContent.startsWith("MID:")) {
            int sep = rawContent.indexOf('|');
            if (sep > 0) {
                try { mid = Long.parseLong(rawContent.substring(4, sep)); } catch (Exception ignored) {}
                text = rawContent.substring(sep + 1);
            }
        }

        String time = nowTime();
        ensureContactVisible(from);
        localStore.addMessage(new ChatMessage(from, currentUsername, "TEXT", text, time, "recv", mid > 0 ? mid : System.currentTimeMillis()),
                false, from);
        hideTypingIndicator();
        services.NotificationSounds.messageReceived();
        maybePromptAddContact(from);

        if (activeTarget == null) {
            Contact senderContact = findContactByUsername(from);
            if (senderContact != null) {
                switchTab(Tab.CHATS);
                openConversation(ChatTarget.ofContact(senderContact));
                return;
            }
        }
        if (activeTarget != null && activeTarget.isContact()
                && activeTarget.contact.username.equalsIgnoreCase(from)) {
            String key = mid > 0 ? privateBubbleKey(from, from, mid) : null;
            addBubble(createMessageNode(text), false, time, null, key, "TEXT", false);
        } else {
            Contact c = findContactByUsername(from);
            if (c != null) {
                c.unreadCount++;
                c.lastMessagePreview = previewOf(text);
                c.lastMessageTime = System.currentTimeMillis();
                renderCurrentTab();
            }
        }
    }

    private void handleMsgStatus(String[] p) {
        if (p.length < 3) return;
        String recipient = p[1];
        String tail = p.length >= 4 ? p[2] + "|" + p[3] : p[2];
        String[] tailParts = tail.split("\\|", 2);
        if (tailParts.length < 2) return;
        long mid;
        try { mid = Long.parseLong(tailParts[0].trim()); } catch (NumberFormatException e) { return; }
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
    }

    private void handleConvDelivered(String[] p) {
        if (p.length < 2) return;
        String recipient = p[1];
        List<Label> list = pendingTicksByContact.remove(recipient.toLowerCase());
        if (list != null) {
            for (Label t : list) applyTickStatus(t, "delivered");
            tickByMid.entrySet().removeIf(e -> list.contains(e.getValue()));
        }
        try { localStore.markPendingDelivered(currentUsername, recipient); } catch (Exception ignored) {}
    }

    private void handleHistory(String[] p) {
        if (p.length < 4) return;
        String other = p[1], sender = p[2], rawTail = p[3];
        if (activeTarget == null || !activeTarget.isContact()
                || !activeTarget.contact.username.equalsIgnoreCase(other)) return;

        long mid = 0;
        boolean edited = false;
        String text = rawTail;
        if (rawTail.startsWith("MID:")) {
            int sep = rawTail.indexOf('|');
            if (sep > 0) {
                try { mid = Long.parseLong(rawTail.substring(4, sep)); } catch (Exception ignored) {}
                text = rawTail.substring(sep + 1);
            }
        }
        if (text.startsWith("EDITED|")) { edited = true; text = text.substring(7); }
        boolean mine = sender.equalsIgnoreCase(currentUsername);
        boolean deleted = "[message deleted]".equals(text);

        String key = mid > 0 ? privateBubbleKey(other, sender, mid) : null;
        if (deleted) {
            Label l = new Label("[message deleted]");
            l.getStyleClass().addAll("bubble-text", "bubble-deleted");
            addBubble(l, mine, nowTime(), null, key, "TEXT", false);
        } else {
            addBubble(createMessageNode(text), mine, nowTime(), null, key, "TEXT", edited);
        }
    }

    private void handleUserStatus(String[] p) {
        if (p.length < 3) return;
        String who = p[1];
        boolean online = "online".equalsIgnoreCase(p[2]);
        for (Contact c : contacts) if (c.username.equalsIgnoreCase(who)) c.isOnline = online;
        renderCurrentTab();
        if (activeTarget != null && activeTarget.isContact()
                && activeTarget.contact.username.equalsIgnoreCase(who)) {
            activeChatStatus.setText(online ? "online" : "offline");
        }
    }

    private void handleIncomingCallRequest(String[] p) {
        if (p.length < 2) return;
        String from = p[1];
        String inType = (p.length >= 3 && "VIDEO".equalsIgnoreCase(p[2])) ? "VIDEO" : "AUDIO";
        if (callActive) { client.send("CALL_REFUSE|" + from); return; }
        currentCallPeer = from;
        pendingCallType = inType;
        callOriginatedByMe = false;
        services.NotificationSounds.startIncomingRing();

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Incoming " + inType.toLowerCase() + " call");
        alert.setHeaderText(displayNameFor(from) + " is calling");
        alert.setContentText(inType.equalsIgnoreCase("VIDEO") ? "Accept video call?" : "Accept audio call?");
        ButtonType accept = new ButtonType("Accept");
        ButtonType reject = new ButtonType("Reject", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(accept, reject);
        alert.setOnHidden(e -> {
            ButtonType result = alert.getResult();
            if (result == null || result == ButtonType.CANCEL || result == reject) {
                if (incomingCallAlert == alert) {
                    incomingCallAlert = null;
                    if (currentCallPeer != null && from.equalsIgnoreCase(currentCallPeer)) {
                        client.send("CALL_REFUSE|" + from);
                    }
                    stopCallLocally();
                }
            } else if (result == accept) {
                services.NotificationSounds.stopIncomingRing();
                callActive = true;
                callStartMs = System.currentTimeMillis();
                showCallBanner("Connecting " + inType.toLowerCase() + " call with " + displayNameFor(from) + "...");
                updateCallButtons();
                client.send("CALL_ACCEPT|" + RECIPIENT_AUDIO_PORT + "|" + server.clientAPP.getLocalIp());
                incomingCallAlert = null;
            }
        });
        incomingCallAlert = alert;
        alert.show();
    }

    private void handleCallAccepted(String[] p) {
        if (p.length >= 2 && p[1] != null && !p[1].isBlank()) currentCallPeer = p[1];
        services.NotificationSounds.stopOutgoingDial();
        client.send("CALL_READY|" + CALLER_AUDIO_PORT + "|" + server.clientAPP.getLocalIp());
        callStartMs = System.currentTimeMillis();
        showCallBanner("Call connected with " + displayNameFor(currentCallPeer));
    }

    private void handleStartAudio(String[] p) {
        if (p.length < 2) return;
        String remoteIp = p[1];
        String peerName = null;
        if (p.length >= 3) {
            String tail = p.length >= 4 ? p[2] + "|" + p[3] : p[2];
            String[] tp = tail.split("\\|");
            if (tp.length >= 2) peerName = tp[1];
        }
        if (peerName != null && !peerName.isBlank()) currentCallPeer = peerName;
        startStream(remoteIp, callOriginatedByMe);
    }

    private void handleCallRefused(String[] p) {
        stopCallLocally();
        showInfo("Call refused.");
    }

    private void handleCallCancelled(String[] p) {
        if (incomingCallAlert != null) {
            Alert toClose = incomingCallAlert;
            incomingCallAlert = null;
            currentCallPeer = null;
            try { toClose.setResult(ButtonType.CANCEL); } catch (Exception ignored) {}
            try { toClose.close(); } catch (Exception ignored) {}
        }
        showInfo("Caller cancelled the call.");
        stopCallLocally();
    }

    private void handleCallEnded(String[] p) {
        stopCallLocally();
    }

    @FXML
    private void onAudioCall() {
        if (activeTarget != null && activeTarget.isGroup()) startOutgoingGroupCall("AUDIO");
        else startOutgoingCall("AUDIO");
    }

    @FXML
    private void onVideoCall() {
        if (activeTarget != null && activeTarget.isGroup()) startOutgoingGroupCall("VIDEO");
        else startOutgoingCall("VIDEO");
    }

    private void startOutgoingCall(String type) {
        if (activeTarget == null || !activeTarget.isContact()) {
            showInfo("Select a contact first.");
            return;
        }
        if (callActive) { showInfo("A call is already active."); return; }
        pendingCallType = type;
        currentCallPeer = activeTarget.contact.username;
        callOriginatedByMe = true;
        callActive = true;
        callStartMs = System.currentTimeMillis();
        showCallBanner((type.equals("VIDEO") ? "Video calling " : "Calling ") + displayNameFor(currentCallPeer) + "...");
        updateCallButtons();
        services.NotificationSounds.startOutgoingDial();
        client.send("CALL_REQUEST|" + currentCallPeer + "|" + type);
    }

    @FXML
    private void onEndCall() {
        if (currentCallPeer != null) {
            try {
                if (activeCall == null) client.send("CALL_CANCEL|" + currentCallPeer);
                else                    client.send("CALL_END|" + currentCallPeer);
            } catch (Exception ignored) {}
        }
        stopCallLocally();
    }

    private void startStream(String remoteIp, boolean isCaller) {
        if (activeCall != null) {
            try { activeCall.stopCall(); } catch (Exception ignored) {}
            activeCall = null;
        }
        boolean isVideo = "VIDEO".equalsIgnoreCase(pendingCallType);
        try {
            String peerName = currentCallPeer == null ? "Peer" : displayNameFor(currentCallPeer);
            activeCall = new CallManager(remoteIp, isCaller, isVideo, peerName);
            final String peerAtStart = currentCallPeer;
            activeCall.setOnEnd(() -> Platform.runLater(() -> {
                if (peerAtStart != null) {
                    try { client.send("CALL_END|" + peerAtStart); } catch (Exception ignored) {}
                }
                stopCallLocally();
            }));
            activeCall.startCall();
            showCallBanner("In " + (isVideo ? "video" : "audio") + " call with " + peerName);
        } catch (Exception e) {
            showInfo("Failed to start media stream: " + e.getMessage());
            stopCallLocally();
        }
    }

    private void stopCallLocally() {
        services.NotificationSounds.stopIncomingRing();
        services.NotificationSounds.stopOutgoingDial();
        if (activeCall != null) {
            try { activeCall.stopCall(); } catch (Exception ignored) {}
            activeCall = null;
        }
        if (callStartMs > 0 && currentCallPeer != null && callActive) {
            long durationSecs = Math.max(0, (System.currentTimeMillis() - callStartMs) / 1000);
            recordCallSystemMessage(currentCallPeer, pendingCallType, durationSecs, false);
        }
        callActive = false;
        callOriginatedByMe = false;
        currentCallPeer = null;
        pendingCallType = "AUDIO";
        callStartMs = 0;
        hideCallBanner();
        updateCallButtons();
        client.send("CALL_HISTORY");
    }

    private void recordCallSystemMessage(String peer, String type, long secs, boolean missed) {
        String summary = ("VIDEO".equalsIgnoreCase(type) ? "Video call" : "Voice call")
                + " • " + (missed ? "Missed" : formatDuration((int) secs));
        long ts = System.currentTimeMillis();
        ChatMessage m = new ChatMessage(currentUsername, peer, "SYSTEM", summary,
                nowTime(), "delivered", ts);
        try { localStore.addMessage(m, false, peer); } catch (Exception ignored) {}
        if (activeTarget != null && activeTarget.isContact()
                && activeTarget.contact.username.equalsIgnoreCase(peer)) {
            addSystemBubble(summary, nowTime());
        }
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
        boolean hasContact = activeTarget != null;
        boolean isGroup = activeTarget != null && activeTarget.isGroup();
        boolean anyCallActive = callActive || activeGroupCall != null;
        if (audioCallBtn != null) {
            audioCallBtn.setDisable(!hasContact || anyCallActive);
            audioCallBtn.setVisible(!anyCallActive);
            audioCallBtn.setManaged(!anyCallActive);
        }
        if (videoCallBtn != null) {
            videoCallBtn.setDisable(!hasContact || anyCallActive);
            videoCallBtn.setVisible(!anyCallActive);
            videoCallBtn.setManaged(!anyCallActive);
        }
        if (endCallBtn != null) {
            endCallBtn.setVisible(anyCallActive);
            endCallBtn.setManaged(anyCallActive);
        }
        if (infoBtn != null) infoBtn.setDisable(!hasContact);
    }

    private void handleGroupInfo(String[] p, String raw) {
        String[] f = raw.split("\\|", 5);
        if (f.length < 5) return;
        int gid; try { gid = Integer.parseInt(f[1]); } catch (NumberFormatException e) { return; }
        String name = f[2];
        String[] members = f[3].isEmpty() ? new String[0] : f[3].split(",");
        Set<String> admins = new HashSet<>();
        if (!f[4].isEmpty()) for (String a : f[4].split(",")) admins.add(a.toLowerCase());

        Group g = findGroupById(gid);
        if (g == null) { g = new Group(gid, name, 0); groups.add(g); }
        else { g.setName(name); g.getMemberIds().clear(); g.getAdminIds().clear(); }
        for (String m : members) {
            User u = userDAO.getByUsername(m.trim());
            int id = u != null ? u.getId() : -Math.abs(m.toLowerCase().hashCode());
            g.addMember(id);
            if (admins.contains(m.toLowerCase())) g.promote(id);
        }
        renderCurrentTab();
        if (activeTarget != null && activeTarget.isGroup() && activeTarget.group.getId() == gid) {
            activeChatStatus.setText(g.getMemberCount() + " members");
        }
    }

    private void handleGroupRemoved(String[] p) {
        if (p.length < 2) return;
        int gid; try { gid = Integer.parseInt(p[1]); } catch (NumberFormatException e) { return; }
        groups.removeIf(g -> g.getId() == gid);
        if (activeTarget != null && activeTarget.isGroup() && activeTarget.group.getId() == gid) {
            activeTarget = null;
            activeChatTitle.setText("Select a conversation");
            activeChatStatus.setText("");
            messagesBox.getChildren().clear();
            updateCallButtons();
        }
        renderCurrentTab();
    }

    private void handleGroupHistory(String[] p, String raw) {
        String[] f = raw.split("\\|", 5);
        if (f.length < 5) return;
        int gid; try { gid = Integer.parseInt(f[1]); } catch (NumberFormatException e) { return; }
        if (activeTarget == null || !activeTarget.isGroup() || activeTarget.group.getId() != gid) return;
        String sender = f[2];
        String msgType = f[3];
        String tail = f[4];

        long mid = 0;
        boolean edited = false;
        if (tail.startsWith("MID:")) {
            int sep = tail.indexOf('|');
            if (sep > 0) {
                try { mid = Long.parseLong(tail.substring(4, sep)); } catch (Exception ignored) {}
                tail = tail.substring(sep + 1);
            }
        }
        if (tail.startsWith("EDITED|")) {
            edited = true;
            tail = tail.substring(7);
        }
        boolean deleted = "[message deleted]".equals(tail);
        boolean mine = sender.equalsIgnoreCase(currentUsername);

        if ("SYSTEM".equalsIgnoreCase(msgType)) {
            addSystemBubble(tail, nowTime());
            return;
        }
        String key = mid > 0 ? groupBubbleKey(gid, sender, mid) : null;
        if (deleted) {
            Label l = new Label("[message deleted]");
            l.getStyleClass().addAll("bubble-text", "bubble-deleted");
            addGroupBubble(sender, l, mine, nowTime(), key, "TEXT", false);
        } else {
            addGroupBubble(sender, createMessageNode(tail), mine, nowTime(), key, msgType, edited);
        }
    }

    private void handleGroupMsg(String[] p, String raw) {
        String[] f = raw.split("\\|", 5);
        if (f.length < 5) return;
        int gid; try { gid = Integer.parseInt(f[1]); } catch (NumberFormatException e) { return; }
        String sender = f[2];
        String msgType = f[3];
        String tail = f[4];

        long mid = 0;
        String content = tail;
        if (tail.startsWith("MID:")) {
            int sep = tail.indexOf('|');
            if (sep > 0) {
                try { mid = Long.parseLong(tail.substring(4, sep)); } catch (Exception ignored) {}
                content = tail.substring(sep + 1);
            }
        }
        boolean mine = sender.equalsIgnoreCase(currentUsername);

            if (!mine) services.NotificationSounds.messageReceived();


        long ts = System.currentTimeMillis();
        ChatMessage cm = new ChatMessage(sender, "G:" + gid, msgType, content, nowTime(), "recv", ts);
        try { localStore.addMessage(cm, true, "G:" + gid); } catch (Exception ignored) {}

        if (activeTarget != null && activeTarget.isGroup() && activeTarget.group.getId() == gid) {
            if ("SYSTEM".equalsIgnoreCase(msgType)) addSystemBubble(content, nowTime());
            else {
                String key = mid > 0 ? groupBubbleKey(gid, sender, mid) : null;
                addGroupBubble(sender, createMessageNode(content), false, nowTime(),
                        key, msgType, false);
            }
            hideTypingIndicator();
        } else {
            String unreadKey = "G:" + gid;
            groupUnread.merge(unreadKey, 1, Integer::sum);
            renderCurrentTab();
        }
        services.NotificationSounds.messageReceived();
    }

    private void handleGroupTyping(String[] p) {
        if (p.length < 3) return;
        int gid; try { gid = Integer.parseInt(p[1]); } catch (NumberFormatException e) { return; }
        if (activeTarget == null || !activeTarget.isGroup() || activeTarget.group.getId() != gid) return;
        String who = p[2];
        showTypingIndicator(who + " is typing…");
    }

    private void handlePrivateTyping(String[] p) {
        if (p.length < 2) return;
        String from = p[1];
        if (activeTarget == null || !activeTarget.isContact()
                || !activeTarget.contact.username.equalsIgnoreCase(from)) return;
        showTypingIndicator(from + " is typing…");
    }

    private void showTypingIndicator(String text) {
        if (typingIndicator == null) return;
        typingLabel.setText(text);
        typingIndicator.setVisible(true);
        typingIndicator.setManaged(true);
        if (typingHideTimer != null) typingHideTimer.stop();
        typingHideTimer = new PauseTransition(Duration.seconds(3));
        typingHideTimer.setOnFinished(e -> hideTypingIndicator());
        typingHideTimer.play();
    }

    private void hideTypingIndicator() {
        if (typingIndicator == null) return;
        typingIndicator.setVisible(false);
        typingIndicator.setManaged(false);
    }

    @FXML
    private void onCreateGroup() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Create Group");
        dialog.setHeaderText("New group");
        dialog.setResultConverter(btn -> btn);

        TextField nameField = new TextField();
        nameField.setPromptText("Group name");

        VBox memberRows = new VBox(6);
        Set<String> selected = new HashSet<>();
        Set<String> admins = new HashSet<>();
        admins.add(currentUsername.toLowerCase());
        for (Contact c : contacts) {
            CheckBox cb = new CheckBox(peerLabel(c));
            CheckBox adminCb = new CheckBox("admin");
            adminCb.setDisable(true);
            cb.selectedProperty().addListener((obs, old, val) -> {
                if (val) selected.add(c.username); else {
                    selected.remove(c.username);
                    admins.remove(c.username.toLowerCase());
                    adminCb.setSelected(false);
                }
                adminCb.setDisable(!val);
            });
            adminCb.selectedProperty().addListener((obs, old, val) -> {
                if (val) admins.add(c.username.toLowerCase());
                else admins.remove(c.username.toLowerCase());
            });
            HBox row = new HBox(10, cb, adminCb);
            row.setAlignment(Pos.CENTER_LEFT);
            memberRows.getChildren().add(row);
        }
        ScrollPane sp = new ScrollPane(memberRows);
        sp.setFitToWidth(true);
        sp.setPrefViewportHeight(220);

        VBox box = new VBox(8,
                new Label("Group name"),
                nameField,
                new Label("Members (you are auto-included as admin)"),
                sp);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isEmpty()) { showInfo("Group name is required."); return; }

        String members = String.join(",", selected);
        String adminCsv = String.join(",", admins);
        client.send("GROUP_CREATE|" + name + "|" + members + "|" + adminCsv);
    }

    private void openGroupInfoDialog(Group g) {
        boolean iAmAdmin = g.isAdmin(currentUserId);
        Dialog<Void> d = new Dialog<>();
        d.setTitle("Group info");
        d.setHeaderText(g.getName());

        VBox content = new VBox(10);
        content.setPrefWidth(420);

        Label countLabel = new Label(g.getMemberCount() + " members");
        countLabel.getStyleClass().add("text-muted");

        VBox memberRows = new VBox(4);
        for (Integer mid : g.getMemberIds()) {
            User u = userDAO.getById(mid);
            String uname = u == null ? "user#" + mid : u.getUsername();
            boolean adm = g.isAdmin(mid);
            Label name = new Label(displayNameFor(uname) + (adm ? " · admin" : ""));
            name.getStyleClass().add("contact-name");
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().add(name);
            HBox.setHgrow(name, javafx.scene.layout.Priority.ALWAYS);

            if (iAmAdmin && mid != currentUserId) {
                Button toggleAdmin = new Button(adm ? "Demote" : "Promote");
                toggleAdmin.getStyleClass().add("btn-ghost");
                toggleAdmin.setOnAction(e -> {
                    String cmd = adm ? "GROUP_DEMOTE" : "GROUP_PROMOTE";
                    client.send(cmd + "|" + g.getId() + "|" + uname);
                    d.close();
                });
                Button remove = new Button("Remove");
                remove.getStyleClass().add("btn-danger");
                remove.setOnAction(e -> {
                    client.send("GROUP_REMOVE|" + g.getId() + "|" + uname);
                    d.close();
                });
                row.getChildren().addAll(toggleAdmin, remove);
            }
            memberRows.getChildren().add(row);
        }
        ScrollPane sp = new ScrollPane(memberRows);
        sp.setFitToWidth(true);
        sp.setPrefViewportHeight(240);
        content.getChildren().addAll(countLabel, sp);

        if (iAmAdmin) {
            TextField addField = new TextField();
            addField.setPromptText("username to add");
            Button addBtn = new Button("Add member");
            addBtn.getStyleClass().add("btn-primary");
            addBtn.setOnAction(e -> {
                String u = addField.getText() == null ? "" : addField.getText().trim();
                if (!u.isEmpty()) {
                    client.send("GROUP_ADD|" + g.getId() + "|" + u);
                    d.close();
                }
            });
            HBox addRow = new HBox(8, addField, addBtn);
            addRow.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(addField, javafx.scene.layout.Priority.ALWAYS);
            content.getChildren().add(addRow);
        }

        Button leaveBtn = new Button("Leave group");
        leaveBtn.getStyleClass().add("btn-danger");
        leaveBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Leave \"" + g.getName() + "\"?", ButtonType.OK, ButtonType.CANCEL);
            confirm.setHeaderText(null);
            applyCurrentThemeToDialog(confirm.getDialogPane());
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.OK) {
                    client.send("GROUP_LEAVE|" + g.getId());
                    d.close();
                }
            });
        });
        content.getChildren().add(leaveBtn);

        d.getDialogPane().setContent(content);
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        applyCurrentThemeToDialog(d.getDialogPane());
        d.showAndWait();
    }

    private void handleGroupCallInvite(String[] p) {
        if (p.length < 3) return;
        int gid; try { gid = Integer.parseInt(p[1]); } catch (NumberFormatException e) { return; }
        String caller = p[2];
        String type = p.length >= 4 ? p[3] : "audio";

        Group g = findGroupById(gid);
        String name = g == null ? ("Group #" + gid) : g.getName();
        if (incomingGroupCallAlerts.containsKey(gid)) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Group meeting");
        alert.setHeaderText(displayNameFor(caller) + " started a " + type + " meeting in " + name);
        alert.setContentText("Join the meeting?");
        ButtonType join = new ButtonType("Join");
        ButtonType later = new ButtonType("Ignore", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(join, later);
        alert.setOnHidden(e -> {
            incomingGroupCallAlerts.remove(gid);
            if (alert.getResult() == join) startOrJoinGroupCall(gid, type, false);
        });
        incomingGroupCallAlerts.put(gid, alert);
        alert.show();
    }

    private void handleGroupCallPeers(String[] p, String raw) {
        String[] f = raw.split("\\|", 4);
        if (f.length < 3) return;
        if (activeGroupCall == null) return;
        if (f.length == 3) return;
        String[] peerList = f[3].split(";");
        for (String entry : peerList) {
            if (entry.isBlank()) continue;
            String[] parts = entry.split(",");
            if (parts.length < 4) continue;
            try {
                InetAddress addr = InetAddress.getByName(parts[1]);
                activeGroupCall.addPeer(new GroupCallSession.Peer(
                        parts[0], addr,
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3])));
            } catch (Exception ignored) {}
        }
    }

    private void handleGroupCallPeerJoined(String[] p, String raw) {
        String[] f = raw.split("\\|");
        if (f.length < 6) return;
        if (activeGroupCall == null) return;
        try {
            InetAddress addr = InetAddress.getByName(f[3]);
            activeGroupCall.addPeer(new GroupCallSession.Peer(
                    f[2], addr,
                    Integer.parseInt(f[4]),
                    Integer.parseInt(f[5])));
        } catch (Exception ignored) {}
    }

    private void handleGroupCallPeerLeft(String[] p) {
        if (p.length < 3) return;
        if (activeGroupCall == null) return;
        activeGroupCall.removePeer(p[2]);
    }

    private void handleGroupCallActive(String[] p) {
        if (p.length < 3) return;
        int gid; try { gid = Integer.parseInt(p[1]); } catch (NumberFormatException e) { return; }
        String type = p[2];
        if (activeGroupCall != null) return;
        startOrJoinGroupCall(gid, type, false);
    }

    private void handleGroupCallStatus(String raw) {
        String[] f = raw.split("\\|");
        if (f.length < 3) return;
        int gid; try { gid = Integer.parseInt(f[1]); } catch (NumberFormatException e) { return; }
        if ("active".equalsIgnoreCase(f[2]) && activeTarget != null
                && activeTarget.isGroup() && activeTarget.group.getId() == gid) {
            String type = f.length > 3 ? f[3] : "audio";
            int participants = f.length > 4 ? safeInt(f[4]) : 0;
            showJoinMeetingBanner(gid, type, participants);
        } else if (activeTarget != null && activeTarget.isGroup() && activeTarget.group.getId() == gid) {
            hideJoinMeetingBanner();
        }
    }

    private static int safeInt(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 0; } }

    private void handleMsgEditedPriv(String raw) {
        String[] f = raw.split("\\|", 5);
        if (f.length < 5) return;
        long mid; try { mid = Long.parseLong(f[3]); } catch (NumberFormatException e) { return; }
        String me = currentUsername;
        String other = f[1].equalsIgnoreCase(me) ? f[2] : f[1];
        String key = privateBubbleKey(other, f[1], mid);
        MessageBubble mb = bubbleIndex.get(key);
        if (mb != null) replaceBubbleText(mb, f[4], true);
        try { localStore.editMessage(currentUsername, other, false, mid, f[4]); } catch (Exception ignored) {}
    }

    private void handleMsgDeletedPriv(String[] p) {
        String sender = p[1];
        String tail = p.length >= 4 ? p[2] + "|" + p[3] : p[2];
        String[] tp = tail.split("\\|");
        if (tp.length < 2) return;
        String peer = tp[0];
        long mid; try { mid = Long.parseLong(tp[1]); } catch (NumberFormatException e) { return; }
        String other = sender.equalsIgnoreCase(currentUsername) ? peer : sender;
        MessageBubble mb = bubbleIndex.get(privateBubbleKey(other, sender, mid));
        markBubbleDeleted(mb);
        try { localStore.deleteMessage(currentUsername, other, false, mid); } catch (Exception ignored) {}
    }

    private void handleMsgEditedGroup(String raw) {
        String[] f = raw.split("\\|", 5);
        if (f.length < 5) return;
        int gid; try { gid = Integer.parseInt(f[1]); } catch (NumberFormatException e) { return; }
        long mid; try { mid = Long.parseLong(f[3]); } catch (NumberFormatException e) { return; }
        MessageBubble mb = bubbleIndex.get(groupBubbleKey(gid, f[2], mid));
        if (mb != null) replaceBubbleText(mb, f[4], true);
        try { localStore.editMessage(currentUsername, "G:" + gid, true, mid, f[4]); } catch (Exception ignored) {}
    }

    private void handleMsgDeletedGroup(String[] p) {
        if (p.length < 4) return;
        int gid; try { gid = Integer.parseInt(p[1]); } catch (NumberFormatException e) { return; }
        long mid; try { mid = Long.parseLong(p[3]); } catch (NumberFormatException e) { return; }
        MessageBubble mb = bubbleIndex.get(groupBubbleKey(gid, p[2], mid));
        markBubbleDeleted(mb);
        try { localStore.deleteMessage(currentUsername, "G:" + gid, true, mid); } catch (Exception ignored) {}
    }

    private void handleBlockedNotice(String[] p) {
        String peer = p.length > 1 ? p[1] : "";
        showInfo("Cannot deliver — " + peer + " has blocked you.");
    }

    private void handleBlockedOk(String[] p, boolean blocked) {
        if (p.length < 2) return;
        String peer = p[1];
        if (blocked) blockedByMe.add(peer.toLowerCase());
        else         blockedByMe.remove(peer.toLowerCase());
        renderCurrentTab();
        if (activeTarget != null && activeTarget.isContact()
                && activeTarget.contact.username.equalsIgnoreCase(peer)) {
            activeChatStatus.setText(blocked ? "blocked" : (activeTarget.contact.isOnline ? "online" : "offline"));
        }
    }

    private void handleBlockList(String[] p) {
        blockedByMe.clear();
        if (p.length >= 2 && p[1] != null && !p[1].isEmpty()) {
            for (String name : p[1].split(",")) {
                if (!name.isBlank()) blockedByMe.add(name.trim().toLowerCase());
            }
        }
        renderCurrentTab();
    }

    private void handleGroupLeft(String[] p) {
        if (p.length < 2) return;
        int gid; try { gid = Integer.parseInt(p[1]); } catch (NumberFormatException e) { return; }
        groups.removeIf(g -> g.getId() == gid);
        groupUnread.remove("G:" + gid);
        if (activeTarget != null && activeTarget.isGroup() && activeTarget.group.getId() == gid) {
            activeTarget = null;
            activeChatTitle.setText("Select a conversation");
            activeChatStatus.setText("");
            messagesBox.getChildren().clear();
            updateCallButtons();
        }
        renderCurrentTab();
    }

    private void showJoinMeetingBanner(int gid, String type, int participants) {
        if (joinMeetingBanner == null) return;
        joinMeetingBanner.setUserData(new int[]{ gid, "video".equalsIgnoreCase(type) ? 1 : 0 });
        joinMeetingLabel.setText(participants > 0
                ? type + " meeting in progress · " + participants + " participant" + (participants == 1 ? "" : "s")
                : type + " meeting in progress");
        joinMeetingBanner.setVisible(true);
        joinMeetingBanner.setManaged(true);
    }

    private void hideJoinMeetingBanner() {
        if (joinMeetingBanner == null) return;
        joinMeetingBanner.setVisible(false);
        joinMeetingBanner.setManaged(false);
    }

    @FXML
    private void onJoinMeetingBanner() {
        if (joinMeetingBanner == null) return;
        Object data = joinMeetingBanner.getUserData();
        if (!(data instanceof int[])) return;
        int[] arr = (int[]) data;
        startOrJoinGroupCall(arr[0], arr[1] == 1 ? "video" : "audio", false);
        hideJoinMeetingBanner();
    }

    private void startOutgoingGroupCall(String type) {
        if (activeTarget == null || !activeTarget.isGroup()) return;
        startOrJoinGroupCall(activeTarget.group.getId(), type, true);
    }

    private void startOrJoinGroupCall(int gid, String type, boolean asHost) {
        if (activeGroupCall != null) { showInfo("You are already in a meeting."); return; }
        Group g = findGroupById(gid);
        String groupName = g == null ? "Group #" + gid : g.getName();
        boolean isVideo = "VIDEO".equalsIgnoreCase(type);
        activeGroupCall = new GroupCallSession(currentUsername, gid, groupName,
                isVideo, GROUP_AUDIO_PORT, GROUP_VIDEO_PORT);
        activeGroupCall.setNameResolver(this::displayNameFor);
        activeGroupCall.setOnEnd(() -> Platform.runLater(() -> {
            client.send("GROUP_CALL_LEAVE|" + gid);
            activeGroupCall = null;
            hideCallBanner();
            updateCallButtons();
            client.send("CALL_HISTORY");
        }));
        activeGroupCall.start();
        if (asHost) {
            client.send("GROUP_CALL_START|" + gid + "|" + (isVideo ? "VIDEO" : "AUDIO") + "|"
                    + GROUP_AUDIO_PORT + "|" + GROUP_VIDEO_PORT + "|" + server.clientAPP.getLocalIp());
        }
        client.send("GROUP_CALL_JOIN|" + gid + "|" + GROUP_AUDIO_PORT + "|" + GROUP_VIDEO_PORT
                + "|" + server.clientAPP.getLocalIp());
        showCallBanner("In " + (isVideo ? "video" : "audio") + " meeting · " + groupName);
        updateCallButtons();
    }

    private void handleCallHistory(String[] p, String raw) {
        String[] f = raw.split("\\|", 9);
        if (f.length < 9) return;
        try {
            CallHistoryEntry e = new CallHistoryEntry();
            e.id        = Integer.parseInt(f[1]);
            e.callerName = f[2];
            e.otherName = f[3];
            e.type      = f[4];
            e.status    = f[5];
            e.duration  = Integer.parseInt(f[6]);
            e.startedAt = f[7];
            e.isGroup   = "1".equals(f[8]);
            callsHistory.removeIf(x -> x.id == e.id);
            callsHistory.add(e);
        } catch (Exception ignored) {}
    }

    @FXML
    private void onShowInfo() {
        if (activeTarget == null) { showInfo("Select a conversation first."); return; }
        if (activeTarget.isGroup()) { openGroupInfoDialog(activeTarget.group); return; }

        User u = userDAO.getByUsername(activeTarget.contact.username);
        if (u == null) { showInfo("User not found."); return; }
        String chosenName = (u.getDisplayName() != null && !u.getDisplayName().isBlank())
                ? u.getDisplayName().trim() : u.getUsername();

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Contact info");
        dialog.setHeaderText(chosenName);

        StackPane bigAvatar = buildAvatar(u.getUsername(), 96);
        VBox content = new VBox(10);
        content.setAlignment(Pos.CENTER);
        content.getStyleClass().add("card");
        content.setPadding(new Insets(20));

        Label name = new Label(chosenName); name.getStyleClass().add("title-md");
        Label status = new Label("Status: " + (u.getStatus() == null ? "Offline" : u.getStatus()));
        status.getStyleClass().add("text-muted");
        String join = (u.getJoinDate() == null || u.getJoinDate().isEmpty() || "null".equals(u.getJoinDate()))
                ? "Unknown" : u.getJoinDate();
        Label joined = new Label("Joined: " + join); joined.getStyleClass().add("text-muted");
        Label emailLbl = new Label("Phone: " + (u.getPhone() == null ? "-" : u.getPhone()));
        emailLbl.getStyleClass().add("text-muted");
        Label bioTitle = new Label("Bio"); bioTitle.getStyleClass().add("section-title");
        Label bio = new Label((u.getBio() == null || u.getBio().isEmpty()) ? "No bio" : u.getBio());
        bio.setWrapText(true);
        boolean isBlocked = blockedByMe.contains(u.getUsername().toLowerCase());
        Button blockBtn = new Button(isBlocked ? "Unblock" : "Block");
        blockBtn.getStyleClass().add(isBlocked ? "btn-secondary" : "btn-danger");
        blockBtn.setOnAction(e -> {
            client.send((isBlocked ? "UNBLOCK_USER|" : "BLOCK_USER|") + u.getUsername());
            dialog.close();
        });
        content.getChildren().addAll(bigAvatar, name, status, joined, emailLbl, bioTitle, bio, blockBtn);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        applyCurrentThemeToDialog(dialog.getDialogPane());
        dialog.showAndWait();
    }

    private void ensureContactVisible(String username) {
        if (username == null || username.isBlank()) return;
        String normalized = username.trim();
        for (Contact c : contacts) {
            if (c.username.equalsIgnoreCase(normalized)) {
                contacts.remove(c);
                contacts.add(0, c);
                renderCurrentTab();
                return;
            }
        }
        User sender = userDAO.getByUsername(normalized);
        Contact c;
        if (sender != null) {
            c = new Contact(sender.getId(), sender.getUsername(),
                    "online".equalsIgnoreCase(sender.getStatus()),
                    sender.isBlocked(), false, "now");
        } else {
            c = new Contact(-Math.abs(normalized.toLowerCase().hashCode()),
                    normalized, true, false, false, "now");
        }
        contacts.add(0, c);
        renderCurrentTab();
    }

    private void maybePromptAddContact(String fromUsername) {
        if (fromUsername == null || fromUsername.isBlank() || currentUserId <= 0) return;
        String key = fromUsername.trim().toLowerCase();
        if (contactAddPrompted.contains(key)) return;
        User sender = userDAO.getByUsername(fromUsername.trim());
        if (sender == null) return;
        if (contactDAO.exists(currentUserId, sender.getId())) return;
        contactAddPrompted.add(key);

        Runnable ask = () -> {
            String shown = (sender.getDisplayName() != null && !sender.getDisplayName().isBlank())
                    ? sender.getDisplayName().trim() + " (" + sender.getUsername() + ")"
                    : sender.getUsername();
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("New message");
            a.setHeaderText(shown + " is not in your contacts");
            a.setContentText("Add them to your contacts?");
            a.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
            applyCurrentThemeToDialog(a.getDialogPane());
            a.showAndWait().ifPresent(bt -> {
                if (bt != ButtonType.YES) return;
                TextInputDialog nameDlg = new TextInputDialog(
                        sender.getDisplayName() == null ? "" : sender.getDisplayName());
                nameDlg.setTitle("Add Contact");
                nameDlg.setHeaderText("Name for this contact");
                nameDlg.setContentText("Name (shown instead of the number):");
                applyCurrentThemeToDialog(nameDlg.getDialogPane());
                String alias = nameDlg.showAndWait().map(String::trim).orElse("");
                contactDAO.addContact(currentUserId, sender.getId());
                if (!alias.isEmpty())
                    contactDAO.renameContact(currentUserId, sender.getId(), alias);
                loadContacts();
            });
        };
        if (javafx.application.Platform.isFxApplicationThread()) ask.run();
        else javafx.application.Platform.runLater(ask);
    }

    private Contact findContactByUsername(String username) {
        if (username == null) return null;
        for (Contact c : contacts) if (c.username.equalsIgnoreCase(username.trim())) return c;
        return null;
    }

    private Group findGroupById(int id) {
        for (Group g : groups) if (g.getId() == id) return g;
        return null;
    }

    private Group findGroupByName(String name) {
        if (name == null) return null;
        for (Group g : groups) if (g.getName().equalsIgnoreCase(name.trim())) return g;
        return null;
    }

    @FXML
    private void onAddContact() {
        if (currentUserId <= 0) { showInfo("Unable to add contact."); return; }
        TextInputDialog d = new TextInputDialog();
        d.setTitle("Add Contact");
        d.setHeaderText("Add a contact by phone number");
        d.setContentText("Phone number:");
        applyCurrentThemeToDialog(d.getDialogPane());
        d.showAndWait().ifPresent(input -> {
            String username = services.PhoneUtil.canonical(input.trim());
            if (username.isEmpty() || username.equalsIgnoreCase(currentUsername)) {
                showInfo("Invalid contact number."); return;
            }
            User target = userDAO.getByUsername(username);
            if (target == null) { showInfo("User not found: " + username); return; }

            TextInputDialog nameDlg = new TextInputDialog(
                    target.getDisplayName() == null ? "" : target.getDisplayName());
            nameDlg.setTitle("Add Contact");
            nameDlg.setHeaderText("Name for this contact");
            nameDlg.setContentText("Name (shown instead of the number):");
            applyCurrentThemeToDialog(nameDlg.getDialogPane());
            String alias = nameDlg.showAndWait().map(String::trim).orElse("");

            boolean added = contactDAO.addContact(currentUserId, target.getId());
            if (!alias.isEmpty()) {
                contactDAO.renameContact(currentUserId, target.getId(), alias);
            }
            loadContacts();
            showInfo(added ? "Contact added."
                           : "Contact already existed (name updated).");
        });
    }

    @FXML
    private void onToggleTheme() {
        SceneManager.toggleTheme();
        applyThemeIcon();
    }

    private void applyThemeIcon() {
        if (themeToggleBtn == null) return;
        boolean dark = SceneManager.getTheme() == SceneManager.Theme.DARK;
        themeToggleBtn.setGraphic(makeIcon(dark ? IconShape.SUN : IconShape.MOON, 18, "icon-grey"));
        themeToggleBtn.setTooltip(new Tooltip(dark ? "Switch to light theme" : "Switch to dark theme"));
    }

    private void applyCurrentThemeToDialog(DialogPane pane) {
        SceneManager.applyTheme(pane);
    }

    @FXML
    private void onLogout() {
        services.NotificationSounds.stopAll();
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
        TextField nameField = new TextField(me.getDisplayName() == null ? "" : me.getDisplayName());
        TextField bioField = new TextField(me.getBio() == null ? "" : me.getBio());
        final byte[][] pendingBytes = { null };
        Label picStatus = new Label(me.getProfilePictureData() != null && me.getProfilePictureData().length > 0
                ? "Current picture: stored in database" : "No picture set");
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
                if (compressed == null) { showInfo("Could not read that image."); return; }
                pendingBytes[0] = compressed;
                picStatus.setText("Selected: " + f.getName() + " (" + (compressed.length / 1024) + " KB)");
            } catch (Exception ex) { showInfo("Failed to load image: " + ex.getMessage()); }
        });
        clearBtn.setOnAction(e -> { pendingBytes[0] = new byte[0]; picStatus.setText("Picture will be removed"); });
        HBox picRow = new HBox(8, chooseBtn, clearBtn);
        VBox v = new VBox(8, new Label("Name"), nameField,
                new Label("Bio"), bioField,
                new Label("Profile picture"), picStatus, picRow);
        d.getDialogPane().setContent(v);
        d.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            userDAO.updateDisplayName(me.getId(), nameField.getText());
            userDAO.updateProfile(me.getId(), bioField.getText(), me.getProfilePicture());
            if (pendingBytes[0] != null) userDAO.updateProfilePictureData(me.getId(), pendingBytes[0]);
            meAvatarBox.getChildren().setAll(buildAvatar(currentUsername, 38));
            loadContacts();
        });
    }

    private byte[] compressAvatar(File source) throws Exception {
        return services.ImageUtil.compressAvatar(source);
    }

    private static final long MAX_ATTACHMENT_BYTES = 3 * 1024 * 1024;

    @FXML
    private void onSendImage() {
        if (activeTarget == null) { showInfo("Select a conversation first."); return; }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose image");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Images", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp"));
        File file = chooser.showOpenDialog(messagesBox.getScene().getWindow());
        if (file == null) return;
        sendMediaToActiveTarget(file, "IMAGE");
    }

    @FXML
    private void onSendFile() {
        if (activeTarget == null) { showInfo("Select a conversation first."); return; }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose file");
        File file = chooser.showOpenDialog(messagesBox.getScene().getWindow());
        if (file == null) return;
        sendMediaToActiveTarget(file, "FILE");
    }

    @FXML
    private void onAudioMessage() {
        if (activeTarget == null) { showInfo("Select a conversation first."); return; }
        if (!recordingAudio) startAudioRecording(); else stopAudioRecordingAndSend();
    }

    private void sendMediaToActiveTarget(File file, String mediaType) {
        if (file.length() > MAX_ATTACHMENT_BYTES) {
            showInfo("File too large (max " + (MAX_ATTACHMENT_BYTES / (1024 * 1024)) + " MB).");
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String ext = getExtension(file.getName());
            String payload = "MEDIA_MSG|" + mediaType + "|" + file.getName() + "|"
                    + file.length() + "|" + ext + "|" + base64;
            long mid = System.currentTimeMillis();
            String time = nowTime();
            if (activeTarget.isContact()) {
                String peer = activeTarget.contact.username;
                String key = privateBubbleKey(peer, currentUsername, mid);
                client.send("PRIVATE|" + currentUsername + "|" + peer + "|MID:" + mid + "|" + payload);
                localStore.addMessage(new ChatMessage(currentUsername, peer, mediaType, payload, time, "sent", mid),
                        false, peer);
                Label tick = createTickLabel("sent");
                tickByMid.put(mid, tick);
                pendingTicksByContact.computeIfAbsent(peer.toLowerCase(), k -> new ArrayList<>()).add(tick);
                addBubble(createMessageNode(payload), true, time, tick, key, mediaType, false);
            } else {
                int gid = activeTarget.group.getId();
                String key = groupBubbleKey(gid, currentUsername, mid);
                client.send("GROUP_MSG|" + gid + "|MID:" + mid + "|" + payload);
                localStore.addMessage(new ChatMessage(currentUsername, "G:" + gid, mediaType, payload, time, "sent", mid),
                        true, "G:" + gid);
                addGroupBubble(currentUsername, createMessageNode(payload), true, time, key, mediaType, false);
            }
            services.NotificationSounds.messageSent();
        } catch (Exception e) {
            showInfo("Failed to send file: " + e.getMessage());
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

    private static String formatDuration(int secs) {
        if (secs <= 0) return "0s";
        int m = secs / 60;
        int s = secs % 60;
        if (m == 0) return s + "s";
        if (m < 60) return m + "m " + (s < 10 ? "0" + s : s) + "s";
        int h = m / 60;
        m = m % 60;
        return h + "h " + (m < 10 ? "0" + m : m) + "m";
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private static String prettyTime(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        int tIdx = iso.indexOf('T');
        if (tIdx < 0) return iso;
        String date = iso.substring(0, tIdx);
        String time = iso.substring(tIdx + 1);
        if (time.length() > 5) time = time.substring(0, 5);
        return date + " " + time;
    }

    // ─── Audio recording (voice note) ─────────────────────────────
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
            if (recordingLine != null) { recordingLine.stop(); recordingLine.close(); }
        } catch (Exception ignored) {}
        recordingAudio = false;
        stopWaveformAnimation();
        if (recordingFile != null && recordingFile.exists() && recordingFile.length() > 0) {
            sendMediaToActiveTarget(recordingFile, "AUDIO_MSG");
        } else {
            showInfo("Recording is empty.");
        }
    }

    private void startWaveformAnimation() {
        if (waveformBox == null || recordingBar == null) return;
        recordingBar.setVisible(true); recordingBar.setManaged(true);
        waveformBox.getChildren().clear();
        for (int i = 0; i < 16; i++) {
            Rectangle r = new Rectangle(3, 6);
            r.getStyleClass().add("wave-bar");
            r.setArcWidth(3); r.setArcHeight(3);
            waveformBox.getChildren().add(r);
        }
        waveformAnim = new Timeline(new KeyFrame(Duration.millis(120), e -> {
            for (Node n : waveformBox.getChildren()) {
                if (n instanceof Rectangle) ((Rectangle) n).setHeight(4 + random.nextInt(20));
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
        if (waveformAnim != null) { waveformAnim.stop(); waveformAnim = null; }
        if (recordingClock != null) { recordingClock.stop(); recordingClock = null; }
        if (waveformBox != null) waveformBox.getChildren().clear();
        if (recordingBar != null) { recordingBar.setVisible(false); recordingBar.setManaged(false); }
    }

    private void openImagePreview(String localPath, String title) {
        try {
            File f = new File(localPath);
            if (!f.exists()) return;
            ImageView view = new ImageView(new Image(f.toURI().toString()));
            view.setPreserveRatio(true); view.setFitWidth(900);
            ScrollPane pane = new ScrollPane(view);
            Dialog<Void> d = new Dialog<>();
            d.setTitle(title);
            d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            d.getDialogPane().setContent(pane);
            d.showAndWait();
        } catch (Exception ignored) {}
    }

    // ─── Internal call-history row ────────────────────────────────
    private static class CallHistoryEntry {
        int id;
        String callerName;
        String otherName;
        String type;
        String status;
        int duration;
        String startedAt;
        boolean isGroup;
    }
}
