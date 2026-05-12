package Views;

import dao.GroupDAO;
import dao.UserDAO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import model.User;
import server.clientAPP;
import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

public class GroupChatController {
    @FXML private Label groupNameLabel;
    @FXML private Label memberCountLabel;
    @FXML private HBox avatarBox;
    @FXML private HBox memberAvatarsBox;
    @FXML private VBox messagesBox;
    @FXML private ScrollPane messagesScroll;
    @FXML private TextField messageInput;
    @FXML private Button addMemberBtn;
    @FXML private Button audioBtn;

    private clientAPP client;
    private String currentUsername;
    private int groupId;
    private String groupName;
    private boolean isCreator;
    
    private final GroupDAO groupDAO = new GroupDAO();
    private final UserDAO userDAO = new UserDAO();
    private List<User> currentMembers = new ArrayList<>();

    // Audio recording
    private TargetDataLine recordingLine;
    private File recordingFile;
    private boolean recordingAudio = false;

    public void initData(clientAPP client, String currentUsername, int groupId, String groupName, boolean isCreator) {
        this.client = client;
        this.currentUsername = currentUsername;
        this.groupId = groupId;
        this.groupName = groupName;
        this.isCreator = isCreator;

        groupNameLabel.setText(groupName);
        addMemberBtn.setVisible(isCreator);
        
        avatarBox.getChildren().setAll(buildAvatar(groupName, 40));
        refreshMembers();
        loadHistory();
    }

    private void refreshMembers() {
        new Thread(() -> {
            List<Integer> ids = groupDAO.getMembers(groupId);
            List<User> members = new ArrayList<>();
            for (int id : ids) {
                User u = userDAO.getById(id);
                if (u != null) members.add(u);
            }
            this.currentMembers = members;
            Platform.runLater(() -> {
                memberCountLabel.setText(members.size() + " membres");
                updateHeaderAvatars();
            });
        }).start();
    }

    private void updateHeaderAvatars() {
        memberAvatarsBox.getChildren().clear();
        int count = Math.min(currentMembers.size(), 4);
        for (int i = 0; i < count; i++) {
            User u = currentMembers.get(i);
            StackPane avatar = buildAvatar(u.getUsername(), 28);
            avatar.setStyle(avatar.getStyle() + "-fx-border-color: #f0f2f5; -fx-border-width: 2; -fx-border-radius: 50;");
            memberAvatarsBox.getChildren().add(avatar);
        }
        if (currentMembers.size() > 4) {
            Label plus = new Label("+" + (currentMembers.size() - 4));
            plus.setStyle("-fx-text-fill: #667781; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 0 0 0 5;");
            memberAvatarsBox.getChildren().add(plus);
        }
    }

    @FXML
    private void sendMessage() {
        String content = messageInput.getText();
        if (content == null || content.trim().isEmpty()) return;
        client.sendGroupMessage(groupId, content);
        messageInput.clear();
    }

    @FXML
    private void onAttachFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Envoyer un fichier au groupe");
        File file = fc.showOpenDialog(messagesBox.getScene().getWindow());
        if (file != null) {
            new Thread(() -> {
                try {
                    byte[] bytes = Files.readAllBytes(file.toPath());
                    String base64 = Base64.getEncoder().encodeToString(bytes);
                    client.sendGroupFile(groupId, file.getName(), base64);
                    Platform.runLater(() -> addFileBubble(currentUsername, file.getName(), bytes, true, nowTime()));
                } catch (Exception e) {
                    Platform.runLater(() -> showInfo("Erreur d'envoi: " + e.getMessage()));
                }
            }).start();
        }
    }

    @FXML
    private void onStartRecording() {
        try {
            AudioFormat format = new AudioFormat(44100, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            recordingLine = (TargetDataLine) AudioSystem.getLine(info);
            recordingLine.open(format);
            recordingLine.start();

            File folder = new File("chat_audio");
            if (!folder.exists()) folder.mkdirs();
            recordingFile = new File(folder, "rec_group_" + System.currentTimeMillis() + ".wav");

            recordingAudio = true;
            audioBtn.setStyle("-fx-text-fill: #ea4335;");

            Thread writer = new Thread(() -> {
                try (AudioInputStream stream = new AudioInputStream(recordingLine)) {
                    AudioSystem.write(stream, AudioFileFormat.Type.WAVE, recordingFile);
                } catch (Exception ignored) {}
            }, "group-audio-recorder");
            writer.setDaemon(true);
            writer.start();
        } catch (Exception e) {
            showInfo("Microphone non disponible.");
            recordingAudio = false;
        }
    }

    @FXML
    private void onStopRecording() {
        if (!recordingAudio) return;
        try {
            if (recordingLine != null) {
                recordingLine.stop();
                recordingLine.close();
            }
        } catch (Exception ignored) {}

        recordingAudio = false;
        audioBtn.setStyle("");

        if (recordingFile != null && recordingFile.exists() && recordingFile.length() > 0) {
            new Thread(() -> {
                try {
                    byte[] bytes = Files.readAllBytes(recordingFile.toPath());
                    String base64 = Base64.getEncoder().encodeToString(bytes);
                    client.sendGroupAudio(groupId, base64);
                    Platform.runLater(() -> addAudioBubble(currentUsername, bytes, true, nowTime()));
                } catch (Exception e) {
                    Platform.runLater(() -> showInfo("Erreur audio: " + e.getMessage()));
                }
            }).start();
        }
    }

    public void handleIncomingMessage(String sender, String content) {
        if (content.contains("joined the group") || content.contains("left the group")) {
            refreshMembers();
        }
        Platform.runLater(() -> {
            boolean mine = sender.equalsIgnoreCase(currentUsername);
            addBubble(sender, content, mine, nowTime());
        });
    }

    public void handleIncomingMedia(String sender, String type, String data, String fileName) {
        Platform.runLater(() -> {
            byte[] bytes = Base64.getDecoder().decode(data);
            boolean mine = sender.equalsIgnoreCase(currentUsername);
            if ("AUDIO".equals(type)) {
                addAudioBubble(sender, bytes, mine, nowTime());
            } else if ("FILE".equals(type)) {
                addFileBubble(sender, fileName, bytes, mine, nowTime());
            }
        });
    }

    private void loadHistory() {
        messagesBox.getChildren().clear();
        client.getGroupHistory(groupId);
    }

    public void appendHistory(String sender, String content) {
        Platform.runLater(() -> {
            boolean mine = sender.equalsIgnoreCase(currentUsername);
            addBubble(sender, content, mine, nowTime());
        });
    }

    private void addBubble(String sender, String content, boolean mine, String time) {
        HBox row = new HBox();
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 0, 2, 0));

        VBox bubble = buildBubbleBase(sender, mine, time);
        Label text = new Label(content);
        text.setWrapText(true);
        text.getStyleClass().add("bubble-text");
        
        bubble.getChildren().add(1, text);
        row.getChildren().add(bubble);
        messagesBox.getChildren().add(row);
        scrollDown();
    }

    private void addAudioBubble(String sender, byte[] audioData, boolean mine, String time) {
        HBox row = new HBox();
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        
        VBox bubble = buildBubbleBase(sender, mine, time);
        
        Button playBtn = new Button("▶ Play");
        playBtn.getStyleClass().add("btn-primary");
        playBtn.setOnAction(e -> playAudio(audioData));
        
        bubble.getChildren().add(1, playBtn);
        row.getChildren().add(bubble);
        messagesBox.getChildren().add(row);
        scrollDown();
    }

    private void addFileBubble(String sender, String fileName, byte[] fileData, boolean mine, String time) {
        HBox row = new HBox();
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        
        VBox bubble = buildBubbleBase(sender, mine, time);
        
        VBox fileBox = new VBox(5);
        Label nameLbl = new Label(fileName);
        nameLbl.setStyle("-fx-font-weight: bold;");
        Button downloadBtn = new Button("⬇ Télécharger");
        downloadBtn.getStyleClass().add("btn-secondary");
        downloadBtn.setOnAction(e -> saveFile(fileName, fileData));
        
        fileBox.getChildren().addAll(nameLbl, downloadBtn);
        bubble.getChildren().add(1, fileBox);
        row.getChildren().add(bubble);
        messagesBox.getChildren().add(row);
        scrollDown();
    }

    private VBox buildBubbleBase(String sender, boolean mine, String time) {
        VBox bubble = new VBox(2);
        bubble.setMaxWidth(480);
        bubble.getStyleClass().add(mine ? "bubble-mine" : "bubble-other");

        if (!mine) {
            Label senderLabel = new Label(sender);
            senderLabel.getStyleClass().add("sender-name");
            bubble.getChildren().add(senderLabel);
        } else {
            // Placeholder for sender name to keep structure consistent
            Region r = new Region();
            r.setPrefHeight(0);
            bubble.getChildren().add(r);
        }

        Label timeLabel = new Label(time);
        timeLabel.getStyleClass().add("bubble-time");
        HBox timeRow = new HBox(timeLabel);
        timeRow.setAlignment(Pos.CENTER_RIGHT);
        bubble.getChildren().add(timeRow);

        return bubble;
    }

    private void playAudio(byte[] data) {
        new Thread(() -> {
            try {
                InputStream is = new ByteArrayInputStream(data);
                AudioInputStream ais = AudioSystem.getAudioInputStream(is);
                Clip clip = AudioSystem.getClip();
                clip.open(ais);
                clip.start();
            } catch (Exception e) {
                Platform.runLater(() -> showInfo("Erreur lecture audio"));
            }
        }).start();
    }

    private void saveFile(String name, byte[] data) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Enregistrer le fichier");
        fc.setInitialFileName(name);
        File file = fc.showSaveDialog(messagesBox.getScene().getWindow());
        if (file != null) {
            try {
                Files.write(file.toPath(), data);
            } catch (IOException e) {
                showInfo("Erreur enregistrement");
            }
        }
    }

    private void scrollDown() {
        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
    }

    @FXML
    private void onAddMember() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Ajouter un membre");
        dialog.setHeaderText("Ajouter à " + groupName);
        dialog.setContentText("Nom d'utilisateur :");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(u -> {
            client.addGroupMember(groupId, u);
            refreshMembers();
        });
    }

    @FXML
    private void onShowInfo() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Infos du groupe");
        dialog.setHeaderText(null);
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(350);
        content.setAlignment(Pos.CENTER);

        StackPane bigAvatar = buildAvatar(groupName, 80);
        Label nameLbl = new Label(groupName);
        nameLbl.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        
        model.Group g = groupDAO.getGroupById(groupId);
        String dateStr = (g != null && g.getCreatedAt() != null) ? g.getCreatedAt().toString() : "Inconnue";
        Label dateLbl = new Label("Créé le : " + dateStr);
        dateLbl.getStyleClass().add("text-muted");

        Label membersTitle = new Label("Membres (" + currentMembers.size() + ")");
        membersTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        VBox membersList = new VBox(5);
        for (User u : currentMembers) {
            HBox item = new HBox(10);
            item.setAlignment(Pos.CENTER_LEFT);
            item.getStyleClass().add("member-item");
            
            StackPane av = buildAvatar(u.getUsername(), 32);
            Label n = new Label(u.getUsername());
            n.setPrefWidth(150);
            
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            
            item.getChildren().addAll(av, n, spacer);
            
            if (g != null && u.getId() == g.getCreatedBy()) {
                Label adminTag = new Label("Admin");
                adminTag.getStyleClass().add("admin-tag");
                item.getChildren().add(adminTag);
            }

            item.setOnMouseClicked(e -> showMemberMenu(u, g));
            membersList.getChildren().add(item);
        }
        
        ScrollPane scroll = new ScrollPane(membersList);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(200);
        scroll.getStyleClass().add("scroll-dark");

        content.getChildren().addAll(bigAvatar, nameLbl, dateLbl, new Separator(), membersTitle, scroll);

        if (isCreator) {
            Button deleteBtn = new Button("Supprimer le groupe");
            deleteBtn.getStyleClass().add("btn-danger");
            deleteBtn.setMaxWidth(Double.MAX_VALUE);
            deleteBtn.setOnAction(e -> dialog.close());
            content.getChildren().add(deleteBtn);
        }

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private void showMemberMenu(User targetUser, model.Group group) {
        if (!isCreator || targetUser.getUsername().equalsIgnoreCase(currentUsername)) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Gérer le membre");
        alert.setHeaderText(targetUser.getUsername());
        alert.setContentText("Voulez-vous retirer ce membre du groupe ?");

        ButtonType removeBtn = new ButtonType("Retirer du groupe", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(removeBtn, cancelBtn);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == removeBtn) {
            client.removeGroupMember(groupId, targetUser.getUsername());
            refreshMembers();
        }
    }

    private String nowTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private StackPane buildAvatar(String name, double size) {
        StackPane sp = new StackPane();
        Circle bg = new Circle(size / 2.0);
        int hash = name.hashCode();
        String[] colors = {"#128c7e", "#34b7f1", "#ea4335", "#f39c12", "#9b59b6", "#e67e22"};
        bg.setFill(Color.web(colors[Math.abs(hash) % colors.length]));
        
        Label l = new Label(name.substring(0, Math.min(name.length(), 1)).toUpperCase());
        l.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: " + (size * 0.45) + "px;");
        sp.getChildren().addAll(bg, l);
        return sp;
    }

    private void showInfo(String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, text, ButtonType.OK);
        a.setHeaderText(null);
        a.show();
    }
}
