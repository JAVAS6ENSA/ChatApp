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

    private Contact activeContact = null;
    private List<Contact> mockContacts;
    
    private String myUsername;
    private JSONMessageStore store;

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

    public MainView(String currentUsername) {
        this.myUsername = currentUsername;
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
    }

    private void hydrateContactsFromHistory() {
        for (Contact c : mockContacts) {
            c.isBlocked = store.isBlocked(c.username);
            
            List<ChatMessage> hist = store.getConversation(myUsername, c.username, c.isGroup, c.username);
            if (hist != null && !hist.isEmpty()) {
                ChatMessage lastMsg = hist.get(hist.size() - 1);
                c.lastMessageTime = lastMsg.getTimestamp();
                
                String prefix = lastMsg.getSender().equals(myUsername) ? "Vous: " : "";
                String preview = lastMsg.getContent();
                
                if (lastMsg.getType().equals("AUDIO")) preview = "🎵 Audio";
                else if (lastMsg.getType().equals("IMAGE")) preview = "🖼️ Image";
                else if (lastMsg.getType().equals("VIDEO")) preview = "🎬 Vidéo";
                else if (lastMsg.getType().equals("FILE")) preview = "📄 Fichier";
                else if (lastMsg.getType().equals("DELETED")) preview = "🚫 Message retiré";
                
                c.lastMessagePreview = prefix + preview;
                
                int unread = 0;
                for (ChatMessage m : hist) {
                    if (!m.getSender().equals(myUsername) && m.getStatus().equals("✔")) {
                        unread++;
                    }
                }
                c.unreadCount = unread;
            } else {
                c.unreadCount = 0;
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

        // Header
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

        Region headSpacer = new Region();
        HBox.setHgrow(headSpacer, Priority.ALWAYS);
        Button addGroupBtn = createIconButton("➕");
        addGroupBtn.setOnAction(e -> showCreateGroupDialog());

        header.getChildren().addAll(avatarStack, userInfo, headSpacer, addGroupBtn);

        // Search
        HBox searchContainer = new HBox();
        searchContainer.setPadding(new Insets(0, 24, 20, 24));
        TextField searchField = new TextField();
        searchField.setPromptText("🔍 Rechercher un contact...");
        searchField.setStyle("-fx-background-color: " + BG_BUBBLE_RECV + "; -fx-text-fill: " + TEXT_MAIN + 
            "; -fx-prompt-text-fill: " + TEXT_MUTED + "; -fx-background-radius: 20; -fx-padding: 10 16; -fx-font-size: 13;");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchContainer.getChildren().add(searchField);

        searchField.textProperty().addListener((obs, old, val) -> {
            String q = val.toLowerCase();
            for(Node n : contactsListUI.getChildren()) {
                if(n instanceof HBox) {
                    Contact c = (Contact) n.getUserData();
                    boolean match = c.username.toLowerCase().contains(q);
                    n.setVisible(match);
                    n.setManaged(match);
                }
            }
        });

        contactsListUI = new VBox();
        sortAndRenderSidebar();

        ScrollPane scrollContacts = new ScrollPane(contactsListUI);
        scrollContacts.setFitToWidth(true);
        scrollContacts.setStyle("-fx-background: " + BG_SIDEBAR + "; -fx-background-color: transparent; -fx-control-inner-background: " + BG_SIDEBAR + ";");
        scrollContacts.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollContacts.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        
        VBox.setVgrow(scrollContacts, Priority.ALWAYS);
        sidebar.getChildren().addAll(header, searchContainer, scrollContacts);
        return sidebar;
    }

    private void sortAndRenderSidebar() {
        if (contactsListUI == null) return;
        contactsListUI.getChildren().clear();
        
        mockContacts.sort((a, b) -> Long.compare(b.lastMessageTime, a.lastMessageTime));
        
        for(Contact c : mockContacts) {
            if (store.isHidden(myUsername, c.username, c.isGroup)) {
                // Soft Delete cache la conversation de la gauche
                continue;
            }
            
            String preview = c.lastMessagePreview;
            if (c.isBlocked) preview = "🚫 Vous avez bloqué ce profil";
            
            String timeStr = "12:00";
            if (c.lastMessageTime > 0) {
               timeStr = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(c.lastMessageTime), java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"));
            }

            HBox item = buildContactItem(c, preview, timeStr, c.unreadCount);
            item.setUserData(c);
            
            if (activeContact == c) {
                item.setStyle("-fx-background-color: " + BG_SELECT + ";");
                Circle selectedDot = (Circle)((StackPane)item.getChildren().get(0)).getChildren().get(2);
                selectedDot.setStroke(Color.web(BG_SELECT));
            }
            
            contactsListUI.getChildren().add(item);
        }
    }

    private HBox buildContactItem(Contact c, String lastMsg, String time, int unread) {
        HBox item = new HBox(12);
        item.setPadding(new Insets(14, 24, 14, 24));
        item.setCursor(Cursor.HAND);
        item.setStyle("-fx-background-color: transparent;");
        
        item.setOnMouseEntered(e -> { 
            if(activeContact != c) item.setStyle("-fx-background-color: #161922;"); 
        });
        item.setOnMouseExited(e -> {
            if(activeContact != c) item.setStyle("-fx-background-color: transparent;");
            else item.setStyle("-fx-background-color: " + BG_SELECT + ";");
        });

        item.setOnMouseClicked(e -> selectContact(c, item));

        // Avatar
        Circle avatar = new Circle(24, Color.web(c.isBlocked ? "#331212" : (c.isGroup ? "#8e44ad" : "#3d4b73")));
        Label initial = new Label(c.isGroup ? "👥" : c.username.substring(0,1).toUpperCase());
        initial.setTextFill(Color.WHITE);
        initial.setFont(Font.font("System", FontWeight.BOLD, c.isGroup ? 20 : 18));
        
        Circle dot = new Circle(6);
        dot.setFill(Color.web(c.isOnline && !c.isBlocked && !c.isGroup ? ONLINE_DOT : TEXT_MUTED));
        if (c.isGroup) dot.setVisible(false);
        dot.setStroke(Color.web(BG_SIDEBAR));
        dot.setStrokeWidth(2);
        StackPane.setAlignment(dot, Pos.BOTTOM_RIGHT);
        StackPane avatarStack = new StackPane(avatar, initial, dot);

        // Name & Msg
        VBox center = new VBox(5);
        center.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(center, Priority.ALWAYS);
        Label nameLbl = new Label(c.username);
        nameLbl.setTextFill(Color.web(c.isBlocked ? BLOCKED : TEXT_MAIN));
        nameLbl.setFont(Font.font("System", FontWeight.BOLD, 15));
        
        Label msgLbl = new Label(lastMsg);
        msgLbl.setTextFill(c.isBlocked ? Color.web(BLOCKED) : Color.web(TEXT_MUTED));
        msgLbl.setFont(Font.font("System", 13));
        center.getChildren().addAll(nameLbl, msgLbl);

        // Time & Badge
        VBox right = new VBox(6);
        right.setAlignment(Pos.TOP_RIGHT);
        Label timeLbl = new Label(time);
        timeLbl.setTextFill(Color.web(TEXT_MUTED));
        timeLbl.setFont(Font.font("System", 11));
        
        right.getChildren().add(timeLbl);
        
        if (c.isBlocked) {
            Label badge = new Label("Bloqué");
            badge.setStyle("-fx-background-color: " + BLOCKED + "20; -fx-text-fill: " + BLOCKED + "; -fx-padding: 3 8; -fx-background-radius: 12; -fx-font-size: 10; -fx-font-weight: bold;");
            right.getChildren().add(badge);
        } else if (unread > 0) {
            Label badge = new Label(String.valueOf(unread));
            badge.setStyle("-fx-background-color: " + ACCENT + "; -fx-text-fill: white; -fx-padding: 2 6; -fx-background-radius: 12; -fx-font-size: 11; -fx-font-weight: bold;");
            right.getChildren().add(badge);
        }

        item.getChildren().addAll(avatarStack, center, right);
        return item;
    }

    private void selectContact(Contact c, HBox item) {
        if(activeContact == c) return;
        activeContact = c;
        c.unreadCount = 0;
        
        sortAndRenderSidebar();
        loadConversation(c);
    }

    private void loadConversation(Contact c) {
        HBox header = new HBox(15);
        header.setPadding(new Insets(18, 30, 18, 30));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: " + BG_CHAT + "; -fx-border-color: #252836; -fx-border-width: 0 0 1 0;");

        Circle avatar = new Circle(22, Color.web(c.isBlocked ? "#331212" : (c.isGroup ? "#8e44ad" : ACCENT)));
        Label initial = new Label(c.isGroup ? "👥" : c.username.substring(0,1).toUpperCase());
        initial.setTextFill(Color.WHITE);
        initial.setFont(Font.font("System", FontWeight.BOLD, c.isGroup ? 18 : 16));
        StackPane avatarStack = new StackPane(avatar, initial);
        avatarStack.setCursor(Cursor.HAND);
        
        avatarStack.setOnMouseClicked(e -> {
            if(!c.isGroup) showProfileDialog(c);
        });

        VBox titleBox = new VBox(3);
        Label nameLbl = new Label(c.username);
        nameLbl.setTextFill(Color.web(c.isBlocked ? BLOCKED : TEXT_MAIN));
        nameLbl.setFont(Font.font("System", FontWeight.BOLD, 17));
        Label statusLbl = new Label(c.isGroup ? "Groupe" : (c.isBlocked ? "Contact Bloqué" : (c.isOnline ? "En ligne" : "Hors ligne")));
        statusLbl.setTextFill(Color.web(c.isGroup ? TEXT_MAIN : (c.isBlocked ? BLOCKED : (c.isOnline ? ONLINE_DOT : TEXT_MUTED))));
        statusLbl.setFont(Font.font("System", 13));
        titleBox.getChildren().addAll(nameLbl, statusLbl);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button callBtn = createIconButton("📞");
        Button videoBtn = createIconButton("📹");
        Button menuBtn = createIconButton("⁝");
        
        callBtn.setOnAction(e -> startCallUI(c.username, false));
        videoBtn.setOnAction(e -> startCallUI(c.username, true));
        
        // Configuration Context Menu HEADER
        ContextMenu headerMenu = new ContextMenu();
        MenuItem audioCallItem = new MenuItem("📞 Appel audio");
        MenuItem videoCallItem = new MenuItem("🎥 Appel vidéo");
        MenuItem blockItem = new MenuItem(c.isBlocked ? "✅ Débloquer l'utilisateur" : "🚫 Bloquer l'utilisateur");
        MenuItem deleteItem = new MenuItem("🗑️ Supprimer la conversation");
        
        audioCallItem.setOnAction(e -> startCallUI(c.username, false));
        videoCallItem.setOnAction(e -> startCallUI(c.username, true));
        
        blockItem.setOnAction(e -> toggleBlockContact(c));
        deleteItem.setOnAction(e -> deleteCurrentConversation(c));
        
        headerMenu.getItems().addAll(audioCallItem, videoCallItem, new SeparatorMenuItem(), blockItem, deleteItem);
        menuBtn.setOnMouseClicked(e -> headerMenu.show(menuBtn, e.getScreenX(), e.getScreenY()));

        header.getChildren().addAll(avatarStack, titleBox, spacer, callBtn, videoBtn, menuBtn);
        chatPane.setTop(header);

        messagesBox = new VBox(20);
        messagesBox.setPadding(new Insets(30, 40, 30, 40));
        
        messagesScroll = new ScrollPane(messagesBox);
        messagesScroll.setFitToWidth(true);
        messagesScroll.setStyle("-fx-background: " + BG_CHAT + "; -fx-background-color: transparent; -fx-control-inner-background: " + BG_CHAT + ";");
        messagesBox.heightProperty().addListener((obs, old, val) -> messagesScroll.setVvalue(1.0));
        chatPane.setCenter(messagesScroll);

        List<ChatMessage> hist = store.getConversation(myUsername, c.username, c.isGroup, c.username);
        if (hist.isEmpty()) {
            addBubble(new ChatMessage("bot", myUsername, "TEXT", "Début de la conversation avec " + c.username, "12:00", "", 0), false);
        } else {
            for(ChatMessage m : hist) {
                boolean isMe = m.getSender().equals(myUsername);
                addBubble(m, isMe);
            }
        }

        if (!c.isBlocked) {
            buildInputFooter();
        } else {
            VBox blockInfo = new VBox();
            blockInfo.setAlignment(Pos.CENTER);
            blockInfo.setPadding(new Insets(20));
            blockInfo.setStyle("-fx-background-color: " + BG_CHAT + ";");
            Label blLbl = new Label("Toute communication est bloquée avec ce contact.");
            blLbl.setTextFill(Color.web(BLOCKED));
            blockInfo.getChildren().add(blLbl);
            chatPane.setBottom(blockInfo);
        }
    }
    
    private void toggleBlockContact(Contact c) {
        if(c.isBlocked) store.unblockUser(c.username);
        else store.blockUser(c.username);
        c.isBlocked = store.isBlocked(c.username);
        hydrateContactsFromHistory(); // sync with the hidden state again
        sortAndRenderSidebar();
        if (activeContact == c) loadConversation(c);
    }
    
    private void deleteCurrentConversation(Contact c) {
        store.deleteConversation(myUsername, c.username, c.isGroup);
        chatPane.setCenter(new VBox()); // Clear visually
        chatPane.setBottom(null);
        chatPane.setTop(null);
        activeContact = null;
        sortAndRenderSidebar();
        showEmptyState();
    }

    private void startCallUI(String targetUsername, boolean isVideo) {
        // Lancer l'interface d'appel (CallStage)
        Platform.runLater(() -> {
            try {
                model.StatutAppel statut = model.StatutAppel.RINGING;
                CallStage callStage = new CallStage(targetUsername, statut);
                callStage.show();
                
                // Envoi de la requête au serveur si on est connecté (ignorer l'erreur si clientAPP n'est pas branché)
                try {
                    new server.clientAPP().send("CALL_REQUEST|" + targetUsername);
                } catch (Exception e) {}
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private void sendMessage(String content, String type) {
        try {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            long currentTimestamp = System.currentTimeMillis();
            
            ChatMessage msg = new ChatMessage(myUsername, activeContact.username, type, content, time, "✔", currentTimestamp);
            addBubble(msg, true);
            
            store.addMessage(msg, activeContact.isGroup, activeContact.username);
            
            activeContact.lastMessageTime = currentTimestamp;
            String prefix = "Vous: ";
            if(type.equals("AUDIO")) activeContact.lastMessagePreview = prefix + "🎵 Audio";
            else if(type.equals("IMAGE")) activeContact.lastMessagePreview = prefix + "🖼️ Image";
            else if(type.equals("VIDEO")) activeContact.lastMessagePreview = prefix + "🎬 Vidéo";
            else if(type.equals("FILE")) activeContact.lastMessagePreview = prefix + "📄 Fichier";
            else activeContact.lastMessagePreview = prefix + content;
            
            sortAndRenderSidebar();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void buildInputFooter() {
        HBox footer = new HBox(15);
        footer.setPadding(new Insets(20, 40, 20, 40));
        footer.setAlignment(Pos.CENTER);
        footer.setStyle("-fx-background-color: " + BG_CHAT + ";");

        attachBtn = createIconButton("📎");
        setupAttachMenu();

        Button micBtn = createIconButton("🎤");
        
        recordingLabel = new Label("00:00");
        recordingLabel.setStyle("-fx-text-fill: " + BLOCKED + "; -fx-font-weight: bold; -fx-font-size: 15;");
        recordingLabel.setVisible(false);
        recordingLabel.setManaged(false);

        micBtn.setOnAction(e -> toggleRecording());

        inputField = new TextField();
        inputField.setPromptText("Écrire un message...");
        inputField.setStyle("-fx-background-color: " + BG_BUBBLE_RECV + "; -fx-text-fill: " + TEXT_MAIN + 
            "; -prompt-text-fill: " + TEXT_MUTED + "; -fx-background-radius: 25; -fx-padding: 12 20; -fx-font-size: 14;");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        sendBtn = new Button("➤");
        sendBtn.setCursor(Cursor.HAND);
        sendBtn.setStyle("-fx-background-color: " + ACCENT + "; -fx-text-fill: white; -fx-background-radius: 25; -fx-min-width: 48; -fx-min-height: 48; -fx-font-size: 18;");
        sendBtn.setOnAction(e -> {
            if (pendingAudioFile != null) {
                sendMessage(pendingAudioFile.getAbsolutePath(), "AUDIO");
                pendingAudioFile = null;
                recordingLabel.setVisible(false); recordingLabel.setManaged(false);
                inputField.setVisible(true); inputField.setManaged(true);
                attachBtn.setDisable(false);
                micBtn.setDisable(false);
            } else if (!inputField.getText().trim().isEmpty()){
                sendMessage(inputField.getText(), "TEXT");
                inputField.clear();
            }
        });

        footer.getChildren().addAll(attachBtn, micBtn, recordingLabel, inputField, sendBtn);
        chatPane.setBottom(footer);
    }

    private void setupAttachMenu() {
        // ... (truncated unchanged body logic, kept identical just remapped)
        ContextMenu attachMenu = new ContextMenu();
        attachMenu.setStyle("-fx-background-color: #1e2130;");
        MenuItem imgItem = new MenuItem("🖼 Image");
        MenuItem vidItem = new MenuItem("🎬 Vidéo");
        MenuItem docItem = new MenuItem("📄 Fichier");

        imgItem.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
            File f = fc.showOpenDialog(getScene().getWindow());
            if(f != null) sendMessage("Local|" + f.getName(), "IMAGE");
        });

        vidItem.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Vidéos", "*.mp4", "*.avi", "*.mkv"));
            File f = fc.showOpenDialog(getScene().getWindow());
            if(f != null) sendMessage("Local|" + f.getName(), "VIDEO");
        });

        docItem.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            File f = fc.showOpenDialog(getScene().getWindow());
            if(f != null) sendMessage(f.getName() + "|" + (f.length() / 1024) + " KB", "FILE");
        });

        attachMenu.getItems().addAll(imgItem, vidItem, docItem);
        attachBtn.setOnMouseClicked(e -> attachMenu.show(attachBtn, Side.TOP, 0, 0));
    }

    private void toggleRecording() {
        if (!isRecording) startRecording();
        else stopRecording();
    }

    private void startRecording() {
        try {
            AudioFormat format = new AudioFormat(44100, 16, 1, true, true);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) return;

            targetLine = (TargetDataLine) AudioSystem.getLine(info);
            targetLine.open(format);
            targetLine.start();

            audioFile = new File("record_" + System.currentTimeMillis() + ".wav");
            isRecording = true;

            Thread recordThread = new Thread(() -> {
                try {
                    AudioInputStream ais = new AudioInputStream(targetLine);
                    AudioSystem.write(ais, AudioFileFormat.Type.WAVE, audioFile);
                } catch (Exception ex) {}
            });
            recordThread.setDaemon(true);
            recordThread.start();

            recordSeconds = 0;
            recordingLabel.setStyle("-fx-text-fill: " + BLOCKED + "; -fx-font-weight: bold; -fx-font-size: 15;");
            recordingLabel.setText("00:00 (🔴 Enregistrement...)");
            recordingLabel.setVisible(true);
            recordingLabel.setManaged(true);
            inputField.setVisible(false);
            inputField.setManaged(false);
            attachBtn.setDisable(true);

            if (audioTimer == null) {
                audioTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
                    recordSeconds++;
                    recordingLabel.setText(String.format("%02d:%02d (🔴)", recordSeconds / 60, recordSeconds % 60));
                }));
                audioTimer.setCycleCount(Timeline.INDEFINITE);
            }
            audioTimer.play();

        } catch (Exception ex) {}
    }

    private void stopRecording() {
        if (targetLine != null) {
            targetLine.stop();
            targetLine.close();
        }
        isRecording = false;
        if (audioTimer != null) audioTimer.stop();

        if (recordSeconds >= 0) {
            pendingAudioFile = audioFile;
            recordingLabel.setStyle("-fx-text-fill: " + ONLINE_DOT + "; -fx-font-weight: bold; -fx-font-size: 14;");
            recordingLabel.setText("🎵 Fichier vocal (" + recordSeconds + "s) prêt. Cliquez sur ➤ pour l'envoyer.");
        } else {
            recordingLabel.setVisible(false); recordingLabel.setManaged(false);
            inputField.setVisible(true); inputField.setManaged(true);
            attachBtn.setDisable(false);
        }
    }

    private Button createIconButton(String iconText) {
        Button btn = new Button(iconText);
        btn.setCursor(Cursor.HAND);
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + TEXT_MUTED + "; -fx-font-size: 20;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: " + BG_BUBBLE_RECV + "; -fx-text-fill: " + TEXT_MAIN + "; -fx-font-size: 20; -fx-background-radius: 20;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + TEXT_MUTED + "; -fx-font-size: 20;"));
        return btn;
    }

    private void addBubble(ChatMessage msg, boolean isSent) {
        HBox row = new HBox();
        row.setPadding(new Insets(2, 0, 2, 0));
        row.setAlignment(isSent ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox bubble = new VBox(6);
        bubble.setMaxWidth(500);
        String radius = isSent ? "18 18 4 18" : "18 18 18 4";
        String bgColor = isSent ? BG_BUBBLE_SENT : BG_BUBBLE_RECV;
        bubble.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: " + radius + "; -fx-padding: 14 18;");

        Node contentNode = null;
        if (msg.getType().equals("DELETED")) {
            Label delLbl = new Label("🚫 Ce message a été supprimé.");
            delLbl.setTextFill(Color.web(TEXT_MUTED));
            delLbl.setFont(Font.font("System", FontPosture.ITALIC, 14));
            contentNode = delLbl;
        } else if(msg.getType().equals("TEXT")) {
            Text text = new Text(msg.getContent());
            text.setFill(Color.web(TEXT_MAIN));
            text.setFont(Font.font("System", 14));
            text.setWrappingWidth(400); 
            contentNode = new TextFlow(text);
        } else if(msg.getType().equals("FILE")) {
            HBox fBox = new HBox(15);
            fBox.setAlignment(Pos.CENTER_LEFT);
            Label docIcon = new Label("📄");
            docIcon.setStyle("-fx-font-size: 28; -fx-text-fill: " + ACCENT + ";");
            String[] parts = msg.getContent().split("\\|");
            VBox fInfo = new VBox(3);
            Label fName = new Label(parts.length>0 ? parts[0] : "Fichier");
            fName.setTextFill(Color.web(TEXT_MAIN));
            fName.setFont(Font.font("System", FontWeight.BOLD, 14));
            fName.setMaxWidth(200); 
            Label fSize = new Label(parts.length>1 ? parts[1] : "? KB");
            fSize.setTextFill(Color.web(TEXT_MUTED));
            fSize.setFont(Font.font("System", 12));
            fInfo.getChildren().addAll(fName, fSize);
            Button dlBtn = new Button("⬇");
            dlBtn.setStyle("-fx-background-color: " + BG_BUBBLE_RECV + "; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: 14; -fx-background-radius: 15;");
            fBox.getChildren().addAll(docIcon, fInfo, dlBtn);
            contentNode = fBox;
        } else if(msg.getType().equals("AUDIO")) {
            HBox aBox = new HBox(12);
            aBox.setAlignment(Pos.CENTER_LEFT);
            Button playBtn = new Button("▶");
            playBtn.setStyle("-fx-background-color: " + ACCENT + "; -fx-text-fill: white; -fx-background-radius: 20; -fx-min-width: 40; -fx-min-height: 40; -fx-font-size: 14;");
            
            playBtn.setOnAction(e -> {
                if (new File(msg.getContent()).exists()) {
                    try {
                        Clip clip = AudioSystem.getClip();
                        clip.open(AudioSystem.getAudioInputStream(new File(msg.getContent())));
                        clip.start();
                    } catch(Exception ex) {}
                }
            });

            HBox barBox = new HBox(3);
            barBox.setAlignment(Pos.CENTER_LEFT);
            for(int i=0; i<20; i++) {
                Rectangle r = new Rectangle(4, Math.random()*20 + 8, Color.web(TEXT_MUTED));
                r.setArcWidth(3); r.setArcHeight(3);
                barBox.getChildren().add(r);
            }
            Label aDur = new Label("Audio");
            aDur.setTextFill(Color.web(TEXT_MUTED));
            aDur.setFont(Font.font("System", 12));
            aBox.getChildren().addAll(playBtn, barBox, aDur);
            contentNode = aBox;
        } else if(msg.getType().equals("IMAGE") || msg.getType().equals("VIDEO")) {
            StackPane imgBox = new StackPane();
            Rectangle mockImg = new Rectangle(240, 160, Color.web("#222533"));
            mockImg.setArcWidth(12); mockImg.setArcHeight(12);
            
            String[] split = msg.getContent().split("\\|");
            String subLabel = split.length > 1 ? split[1] : "";
            
            Label imgLabel = new Label((msg.getType().equals("VIDEO") ? "🎬 Vidéo\n" : "🏞️ Image Thumbnail\n") + subLabel);
            imgLabel.setTextFill(Color.web(TEXT_MUTED));
            imgLabel.setAlignment(Pos.CENTER);
            
            if(msg.getType().equals("VIDEO")) {
                Button playVBtn = new Button("▶");
                playVBtn.setStyle("-fx-background-color: rgba(0,0,0,0.5); -fx-text-fill: white; -fx-background-radius: 20; -fx-font-size: 18;");
                imgBox.getChildren().addAll(mockImg, imgLabel, playVBtn);
            } else {
                imgBox.getChildren().addAll(mockImg, imgLabel);
            }
            contentNode = imgBox;
        }

        HBox timeBox = new HBox(6);
        timeBox.setAlignment(Pos.CENTER_RIGHT);
        
        if (msg.isEdited()) {
            Label editedLbl = new Label("(modifié)");
            editedLbl.setTextFill(Color.web(TEXT_MUTED));
            editedLbl.setFont(Font.font("System", FontPosture.ITALIC, 10));
            timeBox.getChildren().add(editedLbl);
        }
        
        Label tLbl = new Label(msg.getTime());
        tLbl.setTextFill(Color.web(isSent ? "#9aa6c8" : TEXT_MUTED));
        tLbl.setFont(Font.font("System", 11));
        timeBox.getChildren().add(tLbl);

        if(isSent && !msg.getType().equals("DELETED")) {
            Label sLbl = new Label(msg.getStatus());
            sLbl.setTextFill(Color.web(msg.getStatus().equals("✔✔") ? "#6fa3ff" : "#9aa6c8"));
            sLbl.setFont(Font.font("System", 11));
            timeBox.getChildren().add(sLbl);

            if (!msg.getStatus().equals("✔✔")) {
                PauseTransition pt = new PauseTransition(Duration.seconds(1.5));
                pt.setOnFinished(ev -> {
                    sLbl.setText("✔✔");
                    sLbl.setTextFill(Color.web("#6fa3ff")); 
                });
                pt.play();
            }
            
            // MENU CONTEXTUEL POUR MESSAGES ENVOYÉS
            ContextMenu ctx = new ContextMenu();
            MenuItem editItem = new MenuItem("✏️ Modifier");
            MenuItem deleteItem = new MenuItem("🗑️ Supprimer le message");
            
            editItem.setOnAction(e -> {
                TextInputDialog dialog = new TextInputDialog(msg.getContent());
                dialog.setTitle("Modifier");
                dialog.setHeaderText("Modifiez votre message :");
                dialog.showAndWait().ifPresent(newText -> {
                    store.editMessage(myUsername, activeContact.username, activeContact.isGroup, msg.getTimestamp(), newText);
                    loadConversation(activeContact); // Recharge le layout !
                });
            });
            deleteItem.setOnAction(e -> {
                store.deleteMessage(myUsername, activeContact.username, activeContact.isGroup, msg.getTimestamp());
                loadConversation(activeContact); 
                hydrateContactsFromHistory();
                sortAndRenderSidebar();
            });
            
            ctx.getItems().addAll(editItem, deleteItem);
            bubble.setOnContextMenuRequested(e -> ctx.show(bubble, e.getScreenX(), e.getScreenY()));
            bubble.setCursor(Cursor.HAND);
        }

        bubble.getChildren().addAll(contentNode, timeBox);
        HBox.setMargin(bubble, new Insets(0, isSent?0:60, 0, isSent?60:0));
        row.getChildren().add(bubble);
        messagesBox.getChildren().add(row);
    }
    
    // ======== NOUVELLES MODALES DE PROFIL ======== //

    private void showProfileDialog(Contact c) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.UTILITY);
        stage.setTitle("Profil Utilisateur");

        BorderPane layout = new BorderPane();
        layout.setStyle("-fx-background-color: " + BG_CHAT + ";");
        
        // Header de la dialog avec le menu (droite)
        HBox topMenu = new HBox();
        topMenu.setAlignment(Pos.TOP_RIGHT);
        topMenu.setPadding(new Insets(10));
        Button optBtn = createIconButton("⁝");
        
        ContextMenu profMenu = new ContextMenu();
        MenuItem blockItem = new MenuItem(c.isBlocked ? "✅ Débloquer l'utilisateur" : "🚫 Bloquer l'utilisateur");
        MenuItem deleteItem = new MenuItem("🗑️ Supprimer la conversation");
        
        blockItem.setOnAction(e -> { stage.close(); toggleBlockContact(c); });
        deleteItem.setOnAction(e -> { stage.close(); deleteCurrentConversation(c); });
        profMenu.getItems().addAll(blockItem, deleteItem);
        optBtn.setOnMouseClicked(e -> profMenu.show(optBtn, e.getScreenX(), e.getScreenY()));
        topMenu.getChildren().add(optBtn);
        layout.setTop(topMenu);

        // Centre
        VBox content = new VBox(20);
        content.setPadding(new Insets(10, 30, 40, 30));
        content.setAlignment(Pos.CENTER);

        Circle avatar = new Circle(40, Color.web(c.isBlocked ? "#331212" : ACCENT));
        Label initial = new Label(c.username.substring(0,1).toUpperCase());
        initial.setTextFill(Color.WHITE);
        initial.setFont(Font.font("System", FontWeight.BOLD, 30));
        StackPane avatarStack = new StackPane(avatar, initial);

        Label nameLbl = new Label(c.username);
        nameLbl.setTextFill(Color.web(c.isBlocked ? BLOCKED : TEXT_MAIN));
        nameLbl.setFont(Font.font("System", FontWeight.BOLD, 22));

        String statusTxt = c.isBlocked ? "Contact bloqué" : (c.isOnline ? "En ligne" : "Hors ligne");
        Label statLbl = new Label("Statut: " + statusTxt);
        statLbl.setTextFill(Color.web(TEXT_MUTED));

        Label dateLbl = new Label("Membre depuis : " + c.dateCreated);
        dateLbl.setTextFill(Color.web(TEXT_MUTED));
        
        Button okBtn = new Button("Fermer");
        okBtn.setStyle("-fx-background-color: " + BG_BUBBLE_RECV + "; -fx-text-fill: " + TEXT_MAIN + ";");
        okBtn.setOnAction(e -> stage.close());

        content.getChildren().addAll(avatarStack, nameLbl, statLbl, dateLbl, okBtn);
        layout.setCenter(content);
        
        Scene scene = new Scene(layout, 300, 350);
        stage.setScene(scene);
        stage.showAndWait();
    }

    private void showCreateGroupDialog() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.UTILITY);
        stage.setTitle("Nouveau Groupe");

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: " + BG_CHAT + ";");

        Label title = new Label("Créer un Groupe");
        title.setTextFill(Color.web(TEXT_MAIN));
        title.setFont(Font.font("System", FontWeight.BOLD, 18));

        TextField nameInput = new TextField();
        nameInput.setPromptText("Nom du groupe");
        nameInput.setStyle("-fx-background-color: " + BG_BUBBLE_RECV + "; -fx-text-fill: " + TEXT_MAIN + ";");

        Label sub = new Label("Sélectionnez les membres :");
        sub.setTextFill(Color.web(TEXT_MUTED));

        VBox membersList = new VBox(10);
        List<Contact> selectedMembers = new ArrayList<>();

        for(Contact c : mockContacts) {
            if(!c.isGroup && !c.username.equals(myUsername)) {
                CheckBox cb = new CheckBox(c.username);
                cb.setTextFill(Color.web(TEXT_MAIN));
                cb.setOnAction(e -> {
                    if(cb.isSelected()) selectedMembers.add(c);
                    else selectedMembers.remove(c);
                });
                membersList.getChildren().add(cb);
            }
        }

        Button createBtn = new Button("Créer");
        createBtn.setStyle("-fx-background-color: " + ACCENT + "; -fx-text-fill: white; -fx-font-weight: bold;");
        createBtn.setOnAction(e -> {
            if(!nameInput.getText().trim().isEmpty() && !selectedMembers.isEmpty()) {
                Contact newGrp = new Contact((int)(Math.random()*100), nameInput.getText(), true, false, true, "Maintenant");
                newGrp.lastMessageTime = System.currentTimeMillis();
                newGrp.lastMessagePreview = "Groupe créé !";
                mockContacts.add(newGrp); // add natively, sort will take care of pos 0.
                sortAndRenderSidebar();
                stage.close();
            }
        });

        content.getChildren().addAll(title, nameInput, sub, membersList, createBtn);
        
        Scene scene = new Scene(content, 350, 450);
        stage.setScene(scene);
        stage.showAndWait();
    }

    public void disconnect() {
        System.out.println("MainView - Déconnexion API backend");
    }
}
