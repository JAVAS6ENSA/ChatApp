package Views;

import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import model.ChatMessage;
import services.JSONMessageStore;

public class MainView extends BorderPane {

    private static final String BG_SIDEBAR = "#0f1117";
    private static final String BG_CHAT = "#13151f";
    private static final String BG_BUBBLE_RECV = "#1e2130";
    private static final String BG_BUBBLE_SENT = "#2a3a6e";
    private static final String ACCENT = "#4f8ef7";
    private static final String TEXT_MAIN = "#e8eaf0";
    private static final String TEXT_MUTED = "#6b7280";
    private static final String ONLINE_DOT = "#22c55e";
    private static final String BLOCKED = "#ef4444";
    private static final String BG_SELECT = "#252836";

    private VBox contactsListUI;
    private BorderPane chatPane;
    private VBox messagesBox;
    private ScrollPane messagesScroll;

    private TextField inputField;
    private Button attachBtn;
    private Label recordingLabel;
    private Button sendBtn;

    private boolean isRecording = false;
    private TargetDataLine targetLine;
    private File audioFile;
    private File pendingAudioFile;
    private Timeline audioTimer;
    private int recordSeconds = 0;
    private static final String MEDIA_PREFIX = "MEDIA_MSG";

    private Contact activeContact = null;
    private List<Contact> mockContacts;
    
    private String myUsername;
    private JSONMessageStore store;
    private server.clientAPP client;
    private CallStage activeCallStage = null;
    private streaming.CallManager currentCallManager = null;
    private final int localAudioPort = 5000;
    private boolean isCurrentUserCaller = false;

    private class Contact {
        int id; String username; boolean isOnline; boolean isBlocked; boolean isGroup; String dateCreated;
        long lastMessageTime;
        String lastMessagePreview;
        int unreadCount;
        
        public Contact(int id, String u, boolean on, boolean bl, boolean grp, String d) {
            this.id=id; this.username=u; this.isOnline=on; this.isBlocked=bl; this.isGroup=grp; this.dateCreated=d;
            this.lastMessageTime = 0;
            this.lastMessagePreview = "Dernier message ici...";
            this.unreadCount = 0;
        }
    }

    public MainView(String currentUsername, server.clientAPP client) {
        this.myUsername = currentUsername;
        this.client = client;
        this.store = new JSONMessageStore();
        setPrefSize(1200, 750);
        
        mockContacts = new ArrayList<>();
        mockContacts.add(new Contact(1, "youssef_x", true, false, false, "01/01/2026"));
        mockContacts.add(new Contact(2, "salma_s", false, false, false, "15/02/2026"));
        mockContacts.add(new Contact(3, "adam_dev", true, false, false, "10/03/2026"));
        mockContacts.add(new Contact(4, "nadia_k", false, false, false, "05/04/2026"));
        mockContacts.add(new Contact(5, "omar_admin", false, false, false, "01/01/2025"));

        hydrateContactsFromHistory();

        setLeft(buildSidebar(currentUsername));
        
        chatPane = new BorderPane();
        chatPane.setStyle("-fx-background-color: " + BG_CHAT + ";");
        showEmptyState();
        setCenter(chatPane);
        
        startServerListener();
    }

    private void startServerListener() {
        Thread thread = new Thread(() -> {
            try {
                while (true) {
                    String raw = client.read();
                    if (raw == null) break;
                    Platform.runLater(() -> handleIncomingData(raw));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void handleIncomingData(String raw) {
        String[] parts = raw.split("\\|", 4);
        if (parts.length < 2) return;

        String type = parts[0].trim();
        if (type.equals("PRIVATE")) {
            String sender = parts[1].trim();
            String content = parts[3];
            ChatMessage msg = parseIncomingPrivateMessage(sender, content);
            
            store.addMessage(msg, false, sender);
            
            if (activeContact != null && activeContact.username.equalsIgnoreCase(sender)) {
                addBubble(msg, false);
            } else {
                boolean found = false;
                for (Contact c : mockContacts) {
                    if (c.username.equalsIgnoreCase(sender)) {
                        c.unreadCount++;
                        c.lastMessageTime = msg.getTimestamp();
                        c.lastMessagePreview = buildPreviewText(msg);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    Contact newC = new Contact(mockContacts.size() + 1, sender, true, false, false, "Aujourd'hui");
                    newC.unreadCount = 1;
                    newC.lastMessageTime = msg.getTimestamp();
                    newC.lastMessagePreview = buildPreviewText(msg);
                    mockContacts.add(newC);
                }
                sortAndRenderSidebar();
            }
        } else if (type.equals("CALL_REQUEST")) {
            String caller = parts[1];
            isCurrentUserCaller = false;
            model.StatutAppel statut = model.StatutAppel.RINGING;
            CallStage callStage = new CallStage(caller, statut);
            activeCallStage = callStage;
            callStage.getView().setOnAccept(() -> client.send("CALL_ACCEPT|" + localAudioPort));
            callStage.getView().setOnDecline(() -> client.send("CALL_REFUSE|" + caller));
            callStage.getView().setOnHangUp(() -> client.send("CALL_END|" + caller));
            callStage.show();
        } else if (type.equals("CALL_ACCEPTED")) {
            // L'autre personne a accepté l'appel, l'appelant envoie son port UDP
            if (activeCallStage != null) {
                activeCallStage.getView().updateState(model.StatutAppel.IN_CALL);
            }
            client.send("CALL_READY|" + localAudioPort);
        } else if (type.equals("WAIT_CALLER_READY")) {
            if (activeCallStage != null) {
                activeCallStage.getView().updateState(model.StatutAppel.IN_CALL);
            }
        } else if (type.equals("START_AUDIO")) {
            if (parts.length >= 3) {
                if (currentCallManager != null) {
                    currentCallManager.stopCall();
                }
                currentCallManager = new streaming.CallManager(parts[1], isCurrentUserCaller);
                currentCallManager.startCall();
            }
        } else if (type.equals("CALL_REFUSED")) {
            // L'autre personne a refusé l'appel
            if (activeCallStage != null) {
                activeCallStage.getView().updateState(model.StatutAppel.REFUSED);
                activeCallStage = null;
            }
            if (currentCallManager != null) {
                currentCallManager.stopCall();
                currentCallManager = null;
            }
        } else if (type.equals("CALL_ENDED")) {
            // L'appel est terminé
            if (activeCallStage != null) {
                activeCallStage.getView().updateState(model.StatutAppel.ENDED);
                activeCallStage = null;
            }
            if (currentCallManager != null) {
                currentCallManager.stopCall();
                currentCallManager = null;
            }
        }
    }

    private void hydrateContactsFromHistory() {
        for (Contact c : mockContacts) {
            c.isBlocked = store.isBlocked(c.username);
            List<ChatMessage> hist = store.getConversation(myUsername, c.username, c.isGroup, c.username);
            if (hist != null && !hist.isEmpty()) {
                ChatMessage lastMsg = hist.get(hist.size() - 1);
                c.lastMessageTime = lastMsg.getTimestamp();
                String prefix = lastMsg.getSender().equals(myUsername) ? "Vous: " : "";
                String preview = buildPreviewText(lastMsg);
                c.lastMessagePreview = prefix + preview;
            }
        }
    }

    private void showEmptyState() {
        VBox emptyBox = new VBox();
        emptyBox.setAlignment(Pos.CENTER);
        emptyBox.setStyle("-fx-background-color: " + BG_CHAT + ";");
        Label lbl = new Label("Sélectionnez une conversation pour commencer");
        lbl.setTextFill(Color.web(TEXT_MUTED));
        lbl.setFont(Font.font("System", 16));
        emptyBox.getChildren().add(lbl);
        chatPane.setCenter(emptyBox);
        chatPane.setTop(null);
        chatPane.setBottom(null);
    }

    private VBox buildSidebar(String currentUsername) {
        VBox sidebar = new VBox();
        sidebar.setPrefWidth(350);
        sidebar.setStyle("-fx-background-color: " + BG_SIDEBAR + "; -fx-border-color: #252836; -fx-border-width: 0 1 0 0;");

        HBox header = new HBox(15);
        header.setPadding(new Insets(24));
        header.setAlignment(Pos.CENTER_LEFT);
        
        Circle avatar = new Circle(24, Color.web(ACCENT));
        Label avatarInitial = new Label(currentUsername.substring(0,1).toUpperCase());
        avatarInitial.setTextFill(Color.WHITE);
        avatarInitial.setFont(Font.font("System", FontWeight.BOLD, 18));
        StackPane avatarStack = new StackPane(avatar, avatarInitial);

        VBox userInfo = new VBox(3);
        Label nameLbl = new Label("Moi (" + currentUsername + ")");
        nameLbl.setTextFill(Color.web(TEXT_MAIN));
        nameLbl.setFont(Font.font("System", FontWeight.BOLD, 16));
        
        Label statusLbl = new Label("● En ligne");
        statusLbl.setTextFill(Color.web(ONLINE_DOT));
        statusLbl.setFont(Font.font("System", 13));
        userInfo.getChildren().addAll(nameLbl, statusLbl);

        Button addContactBtn = new Button("+");
        addContactBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + ACCENT + "; -fx-font-size: 24; -fx-cursor: hand;");
        addContactBtn.setOnAction(e -> showAddContactDialog());
        
        Region hSpacer = new Region();
        HBox.setHgrow(hSpacer, Priority.ALWAYS);

        header.getChildren().addAll(avatarStack, userInfo, hSpacer, addContactBtn);

        HBox searchContainer = new HBox();
        searchContainer.setPadding(new Insets(0, 24, 20, 24));
        TextField searchField = new TextField();
        searchField.setPromptText("🔍 Rechercher un contact...");
        searchField.setStyle("-fx-background-color: " + BG_BUBBLE_RECV + "; -fx-text-fill: " + TEXT_MAIN + 
            "; -fx-prompt-text-fill: " + TEXT_MUTED + "; -fx-background-radius: 20; -fx-padding: 10 16; -fx-font-size: 13;");
        searchField.textProperty().addListener((obs, old, val) -> sortAndRenderSidebar(val));
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchContainer.getChildren().add(searchField);

        contactsListUI = new VBox();
        sortAndRenderSidebar();

        ScrollPane scrollContacts = new ScrollPane(contactsListUI);
        scrollContacts.setFitToWidth(true);
        scrollContacts.setStyle("-fx-background: " + BG_SIDEBAR + "; -fx-background-color: transparent;");
        
        VBox.setVgrow(scrollContacts, Priority.ALWAYS);
        sidebar.getChildren().addAll(header, searchContainer, scrollContacts);
        return sidebar;
    }

    private void sortAndRenderSidebar() {
        sortAndRenderSidebar("");
    }

    private void sortAndRenderSidebar(String filter) {
        if (contactsListUI == null) return;
        contactsListUI.getChildren().clear();
        mockContacts.sort((a, b) -> Long.compare(b.lastMessageTime, a.lastMessageTime));
        for(Contact c : mockContacts) {
            if (c.username.equals(myUsername)) continue;
            if (!filter.isEmpty() && !c.username.toLowerCase().contains(filter.toLowerCase())) continue;
            
            HBox item = buildContactItem(c, c.lastMessagePreview, "12:00", c.unreadCount);
            if (activeContact == c) item.setStyle("-fx-background-color: " + BG_SELECT + ";");
            contactsListUI.getChildren().add(item);
        }
    }

    private void showAddContactDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Ajouter un contact");
        dialog.setHeaderText("Entrez le nom d'utilisateur du contact");
        dialog.setContentText("Username:");
        dialog.showAndWait().ifPresent(username -> {
            if (!username.trim().isEmpty()) {
                Contact newC = new Contact(mockContacts.size()+1, username.trim(), true, false, false, "Today");
                mockContacts.add(newC);
                sortAndRenderSidebar();
            }
        });
    }

    private HBox buildContactItem(Contact c, String lastMsg, String time, int unread) {
        HBox item = new HBox(12);
        item.setPadding(new Insets(14, 24, 14, 24));
        item.setCursor(Cursor.HAND);
        item.setOnMouseClicked(e -> { activeContact = c; c.unreadCount = 0; sortAndRenderSidebar(); loadConversation(c); });

        Circle avatar = new Circle(24, Color.web(c.isGroup ? "#8e44ad" : "#3d4b73"));
        Label initial = new Label(c.username.substring(0,1).toUpperCase());
        initial.setTextFill(Color.WHITE);
        StackPane avatarStack = new StackPane(avatar, initial);

        VBox center = new VBox(5);
        Label nameLbl = new Label(c.username);
        nameLbl.setTextFill(Color.web(TEXT_MAIN));
        Label msgLbl = new Label(lastMsg);
        msgLbl.setTextFill(Color.web(TEXT_MUTED));
        center.getChildren().addAll(nameLbl, msgLbl);

        item.getChildren().addAll(avatarStack, center);
        return item;
    }

    private void loadConversation(Contact c) {
        HBox header = new HBox(15);
        header.setPadding(new Insets(18, 30, 18, 30));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: " + BG_CHAT + "; -fx-border-color: #252836; -fx-border-width: 0 0 1 0;");

        Label nameLbl = new Label(c.username);
        nameLbl.setTextFill(Color.web(TEXT_MAIN));
        nameLbl.setFont(Font.font("System", FontWeight.BOLD, 17));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button callBtn = createIconButton("📞");
        Button videoBtn = createIconButton("📹");
        callBtn.setOnAction(e -> startCallUI(c.username, false));
        videoBtn.setOnAction(e -> startCallUI(c.username, true));

        header.getChildren().addAll(nameLbl, spacer, callBtn, videoBtn);
        chatPane.setTop(header);

        messagesBox = new VBox(20);
        messagesBox.setPadding(new Insets(30, 40, 30, 40));
        messagesScroll = new ScrollPane(messagesBox);
        messagesScroll.setFitToWidth(true);
        messagesScroll.setStyle("-fx-background: " + BG_CHAT + "; -fx-background-color: transparent;");
        messagesBox.heightProperty().addListener((obs, old, val) -> messagesScroll.setVvalue(1.0));
        chatPane.setCenter(messagesScroll);

        List<ChatMessage> hist = store.getConversation(myUsername, c.username, c.isGroup, c.username);
        for(ChatMessage m : hist) addBubble(m, m.getSender().equals(myUsername));

        buildInputFooter();
    }

    private void startCallUI(String targetUsername, boolean isVideo) {
        Platform.runLater(() -> {
            try {
                CallStage callStage = new CallStage(targetUsername, model.StatutAppel.LIBRE);
                activeCallStage = callStage;
                isCurrentUserCaller = true;
                callStage.getView().setOnHangUp(() -> {
                    client.send("CALL_END|" + targetUsername);
                    if (currentCallManager != null) {
                        currentCallManager.stopCall();
                        currentCallManager = null;
                    }
                    activeCallStage = null;
                });
                callStage.show();
                client.send("CALL_REQUEST|" + targetUsername);
            } catch (Exception ex) {}
        });
    }

    private void buildInputFooter() {
        HBox footer = new HBox(15);
        footer.setPadding(new Insets(20, 40, 20, 40));
        footer.setAlignment(Pos.CENTER);
        footer.setStyle("-fx-background-color: " + BG_CHAT + ";");

        Button micBtn = createIconButton("🎤");
        micBtn.setOnAction(e -> toggleRecording());
        attachBtn = createIconButton("📎");
        attachBtn.setOnAction(e -> choisirEtEnvoyerFichier());

        inputField = new TextField();
        inputField.setPromptText("Écrire un message...");
        inputField.setStyle("-fx-background-color: " + BG_BUBBLE_RECV + "; -fx-text-fill: " + TEXT_MAIN + "; -fx-background-radius: 25; -fx-padding: 12 20;");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        sendBtn = new Button("➤");
        sendBtn.setStyle("-fx-background-color: " + ACCENT + "; -fx-text-fill: white; -fx-background-radius: 25; -fx-min-width: 48; -fx-min-height: 48;");
        sendBtn.setOnAction(e -> {
            if (!inputField.getText().trim().isEmpty()){
                sendMessage(inputField.getText(), "TEXT");
                inputField.clear();
            }
        });

        footer.getChildren().addAll(micBtn, attachBtn, inputField, sendBtn);
        chatPane.setBottom(footer);
    }

    private void toggleRecording() {
        if (!isRecording) {
            try {
                AudioFormat format = new AudioFormat(44100, 16, 1, true, true);
                targetLine = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, format));
                targetLine.open(format); targetLine.start();
                audioFile = new File("rec_" + System.currentTimeMillis() + ".wav");
                new Thread(() -> { try { AudioSystem.write(new AudioInputStream(targetLine), AudioFileFormat.Type.WAVE, audioFile); } catch (Exception ex) {} }).start();
                isRecording = true;
                inputField.setText("Recording...");
            } catch (Exception ex) {}
        } else {
            targetLine.stop(); targetLine.close(); isRecording = false;
            sendAudioMessage(audioFile);
            inputField.clear();
        }
    }

    private void sendMessage(String content, String type) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        ChatMessage msg = new ChatMessage(myUsername, activeContact.username, type, content, time, "✔", System.currentTimeMillis());
        addBubble(msg, true);
        store.addMessage(msg, false, activeContact.username);
        client.send("PRIVATE|" + myUsername + "|" + activeContact.username + "|" + content);
        activeContact.lastMessagePreview = buildPreviewText(msg);
        sortAndRenderSidebar();
    }

    private void addBubble(ChatMessage msg, boolean isSent) {
        HBox row = new HBox();
        row.setAlignment(isSent ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        VBox bubble = new VBox(6);
        bubble.setStyle("-fx-background-color: " + (isSent ? BG_BUBBLE_SENT : BG_BUBBLE_RECV) + "; -fx-background-radius: 10; -fx-padding: 10;");
        
        Node contentNode;
        if (msg.getType().equals("AUDIO_MSG")) {
            contentNode = createAudioNode(msg.getContent());
        } else if (msg.getType().equals("IMAGE")) {
            contentNode = createImageNode(msg.getContent());
        } else if (msg.getType().equals("VIDEO") || msg.getType().equals("DOC")) {
            contentNode = createDownloadNode(msg.getContent(), msg.getType());
        } else {
            Text text = new Text(msg.getContent());
            text.setFill(Color.web(TEXT_MAIN));
            contentNode = new TextFlow(text);
        }

        Label tLbl = new Label(msg.getTime() + " " + (isSent ? msg.getStatus() : ""));
        tLbl.setTextFill(Color.web(TEXT_MUTED));
        tLbl.setFont(Font.font("System", 11));
        
        bubble.getChildren().addAll(contentNode, tLbl);
        
        if (isSent && msg.getStatus().equals("✔")) {
             PauseTransition pt = new PauseTransition(Duration.seconds(1.5));
             pt.setOnFinished(e -> tLbl.setText(msg.getTime() + " ✔✔"));
             pt.play();
        }
        
        row.getChildren().add(bubble);
        messagesBox.getChildren().add(row);
    }

    private Button createIconButton(String iconText) {
        Button btn = new Button(iconText);
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + TEXT_MUTED + "; -fx-font-size: 20;");
        return btn;
    }

    private void sendAudioMessage(File audioSource) {
        if (audioSource == null || activeContact == null) return;
        String extension = getExtension(audioSource.getName());
        if (extension.isEmpty()) extension = "wav";
        sendMediaFile(audioSource, "AUDIO_MSG", extension);
    }

    private void choisirEtEnvoyerFichier() {
        if (activeContact == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir un fichier");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png", "*.gif"),
            new FileChooser.ExtensionFilter("Vidéos", "*.mp4", "*.avi"),
            new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.docx", "*.txt")
        );
        File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;

        String ext = getExtension(file.getName()).toLowerCase();
        if (isImageExt(ext)) {
            sendMediaFile(file, "IMAGE", ext);
        } else if (isVideoExt(ext)) {
            sendMediaFile(file, "VIDEO", ext);
        } else if (isDocExt(ext)) {
            sendMediaFile(file, "DOC", ext);
        }
    }

    private void sendMediaFile(File sourceFile, String messageType, String extension) {
        new Thread(() -> {
            try {
                String localPath = copyToLocalMedia(sourceFile, "sent");
                long size = sourceFile.length();
                String fileName = sourceFile.getName();
                byte[] data = Files.readAllBytes(sourceFile.toPath());
                String base64 = Base64.getEncoder().encodeToString(data);
                String payload = buildMediaPayload(messageType, fileName, size, extension, base64);

                ChatMessage msg = new ChatMessage(
                    myUsername,
                    activeContact.username,
                    messageType,
                    localPath + "|" + fileName + "|" + size,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                    "✔",
                    System.currentTimeMillis()
                );

                Platform.runLater(() -> {
                    addBubble(msg, true);
                    store.addMessage(msg, false, activeContact.username);
                    activeContact.lastMessagePreview = buildPreviewText(msg);
                    sortAndRenderSidebar();
                });

                client.send("PRIVATE|" + myUsername + "|" + activeContact.username + "|" + payload);
            } catch (Exception e) {
                Platform.runLater(() -> addSystemMessageInChat("Erreur envoi fichier: " + e.getMessage()));
            }
        }, "media-send-thread").start();
    }

    private ChatMessage parseIncomingPrivateMessage(String sender, String content) {
        if (!content.startsWith(MEDIA_PREFIX + "|")) {
            return new ChatMessage(
                sender,
                myUsername,
                "TEXT",
                content,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                "✔",
                System.currentTimeMillis()
            );
        }

        String[] fields = content.split("\\|", 6);
        if (fields.length < 6) {
            return new ChatMessage(sender, myUsername, "TEXT", content, LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), "✔", System.currentTimeMillis());
        }

        String msgType = fields[1];
        String fileName = fields[2];
        long size = parseLongSafe(fields[3]);
        String extension = fields[4];
        String base64 = fields[5];
        String savedPath;
        try {
            savedPath = saveIncomingMedia(sender, fileName, extension, base64);
        } catch (Exception e) {
            savedPath = "";
        }

        return new ChatMessage(
            sender,
            myUsername,
            msgType,
            savedPath + "|" + fileName + "|" + size,
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
            "✔",
            System.currentTimeMillis()
        );
    }

    private String buildMediaPayload(String messageType, String fileName, long size, String ext, String base64) {
        return MEDIA_PREFIX + "|" + messageType + "|" + fileName + "|" + size + "|" + ext + "|" + base64;
    }

    private String saveIncomingMedia(String sender, String originalName, String ext, String base64) throws IOException {
        byte[] bytes = Base64.getDecoder().decode(base64);
        Path folder = Paths.get("received_files", sender);
        Files.createDirectories(folder);
        String safeName = System.currentTimeMillis() + "_" + sanitizeName(originalName);
        if (!safeName.toLowerCase().endsWith("." + ext.toLowerCase()) && !ext.isEmpty()) {
            safeName = safeName + "." + ext;
        }
        Path filePath = folder.resolve(safeName);
        Files.write(filePath, bytes);
        return filePath.toString();
    }

    private String copyToLocalMedia(File source, String folderName) throws IOException {
        Path folder = Paths.get("chat_files", folderName);
        Files.createDirectories(folder);
        String targetName = System.currentTimeMillis() + "_" + sanitizeName(source.getName());
        Path target = folder.resolve(targetName);
        Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        return target.toString();
    }

    private String buildPreviewText(ChatMessage msg) {
        switch (msg.getType()) {
            case "AUDIO_MSG":
                return "► Audio message";
            case "IMAGE":
                return "🖼️ Image";
            case "VIDEO":
                return "🎬 Video";
            case "DOC":
                return "📄 Document";
            default:
                return msg.getContent();
        }
    }

    private Node createAudioNode(String packedContent) {
        String[] meta = packedContent.split("\\|", 3);
        String localPath = meta.length > 0 ? meta[0] : "";
        String fileName = meta.length > 1 ? meta[1] : "audio";
        long size = meta.length > 2 ? parseLongSafe(meta[2]) : 0;

        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label("► Audio message  •  " + humanReadableSize(size));
        label.setTextFill(Color.web(TEXT_MAIN));
        Button play = new Button("Play/Pause");
        play.setOnAction(e -> {
            try {
                Clip clip = AudioSystem.getClip();
                clip.open(AudioSystem.getAudioInputStream(new File(localPath)));
                if (clip.isRunning()) clip.stop(); else clip.start();
            } catch (Exception ignored) {}
        });
        Tooltip.install(play, new Tooltip(fileName));
        box.getChildren().addAll(play, label);
        return box;
    }

    private Node createImageNode(String packedContent) {
        String[] meta = packedContent.split("\\|", 3);
        String localPath = meta.length > 0 ? meta[0] : "";
        String fileName = meta.length > 1 ? meta[1] : "image";
        long size = meta.length > 2 ? parseLongSafe(meta[2]) : 0;

        VBox box = new VBox(6);
        ImageView imageView = new ImageView();
        imageView.setFitWidth(220);
        imageView.setPreserveRatio(true);
        try {
            imageView.setImage(new Image(new File(localPath).toURI().toString(), true));
        } catch (Exception ignored) {}
        Label info = new Label(fileName + " • " + humanReadableSize(size));
        info.setTextFill(Color.web(TEXT_MAIN));
        box.getChildren().addAll(imageView, info);
        return box;
    }

    private Node createDownloadNode(String packedContent, String type) {
        String[] meta = packedContent.split("\\|", 3);
        String localPath = meta.length > 0 ? meta[0] : "";
        String fileName = meta.length > 1 ? meta[1] : "file";
        long size = meta.length > 2 ? parseLongSafe(meta[2]) : 0;

        VBox box = new VBox(6);
        Label label = new Label(fileName + " • " + humanReadableSize(size));
        label.setTextFill(Color.web(TEXT_MAIN));
        Button action = new Button(type.equals("VIDEO") ? "Download/Open Video" : "Download/Open Document");
        action.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().open(new File(localPath));
            } catch (Exception ignored) {}
        });
        box.getChildren().addAll(label, action);
        return box;
    }

    private void addSystemMessageInChat(String text) {
        if (messagesBox == null) return;
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER);
        Label label = new Label(text);
        label.setTextFill(Color.web(TEXT_MUTED));
        row.getChildren().add(label);
        messagesBox.getChildren().add(row);
    }

    private boolean isImageExt(String ext) {
        return "jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext) || "gif".equals(ext);
    }

    private boolean isVideoExt(String ext) {
        return "mp4".equals(ext) || "avi".equals(ext);
    }

    private boolean isDocExt(String ext) {
        return "pdf".equals(ext) || "docx".equals(ext) || "txt".equals(ext);
    }

    private String getExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx > 0 ? fileName.substring(idx + 1) : "";
    }

    private String sanitizeName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String humanReadableSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return (size / 1024) + " KB";
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    private long parseLongSafe(String val) {
        try {
            return Long.parseLong(val);
        } catch (Exception e) {
            return 0L;
        }
    }
}
