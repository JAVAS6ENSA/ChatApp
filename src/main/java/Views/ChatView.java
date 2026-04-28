package Views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import server.clientAPP;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.File;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ChatView extends BorderPane {

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final String BG_DARK    = "#0f1117";
    private static final String BG_SIDEBAR = "#1a1d27";
    private static final String BG_CHAT    = "#13151f";
    private static final String BG_INPUT   = "#1e2130";
    private static final String ACCENT     = "#4f8ef7";
    private static final String MSG_SENT   = "#2a3a6e";
    private static final String MSG_RECV   = "#1e2130";
    private static final String TEXT_MAIN  = "#e8eaf0";
    private static final String TEXT_MUTED = "#6b7280";
    private static final String ONLINE_DOT = "#22c55e";

    // ── State ─────────────────────────────────────────────────────────────────
    private final String currentUsername;
    private final String targetUsername;
    private final String password;

    // ── UI ────────────────────────────────────────────────────────────────────
    private VBox      messagesBox;
    private ScrollPane scrollPane;
    private TextField  inputField;
    private Label      statusLabel;

    private streaming.CallManager currentCallManager;
    private CallStage             currentCallStage;
    private clientAPP             client;
    private final int localAudioPort = 5001;
    private boolean isCallerInCurrentCall = false;

    // currentUsername = username dyal user connecté
    // targetUsername  = username dyal contact
    public ChatView(String currentUsername, String targetUsername, String password) {
        this.currentUsername = currentUsername;
        this.targetUsername  = targetUsername;
        this.password        = password;

        this.client = new clientAPP();
        buildUI();
        connectToServer();
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private void buildUI() {
        setStyle("-fx-background-color: " + BG_DARK + ";");
        setPrefSize(820, 600);
        setTop(buildHeader());
        setCenter(buildChatArea());
        setBottom(buildInputArea());
    }

    private HBox buildHeader() {
        HBox header = new HBox(14);
        header.setPadding(new Insets(16, 20, 16, 20));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: " + BG_SIDEBAR + ";" +
                "-fx-border-color: #252836; -fx-border-width: 0 0 1 0;");

        StackPane avatar = buildAvatar(targetUsername);

        VBox nameBox = new VBox(3);
        Label nameLabel = new Label(targetUsername);
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 15));
        nameLabel.setTextFill(Color.web(TEXT_MAIN));

        statusLabel = new Label("● En ligne");
        statusLabel.setFont(Font.font("System", 11));
        statusLabel.setTextFill(Color.web(ONLINE_DOT));
        nameBox.getChildren().addAll(nameLabel, statusLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button callBtn = new Button("📞");
        callBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 16px; -fx-cursor: hand;");
        callBtn.setOnAction(e -> initiateCall());

        Button videoBtn = new Button("📹");
        videoBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 16px; -fx-cursor: hand;");
        videoBtn.setOnAction(e -> initiateCall()); // Pour l'instant on utilise le même déclencheur

        Button endCallBtn = new Button("📵");
        endCallBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ef4444; -fx-font-size: 16px; -fx-cursor: hand;");
        endCallBtn.setOnAction(e -> terminateCall());

        Label connLabel = new Label("🔒 Chiffré");
        connLabel.setFont(Font.font("System", 11));
        connLabel.setTextFill(Color.web(TEXT_MUTED));

        header.getChildren().addAll(avatar, nameBox, spacer, callBtn, videoBtn, endCallBtn, connLabel);
        return header;
    }

    private void initiateCall() {
        addSystemMessage("Tentative d'appel vers " + targetUsername + "...");
        isCallerInCurrentCall = true;
        client.send("CALL_REQUEST|" + targetUsername);
        showCallUI(model.StatutAppel.LIBRE);
    }

    private void terminateCall() {
        client.send("CALL_END|" + targetUsername);
        if (currentCallManager != null) {
            currentCallManager.stopCall();
            currentCallManager = null;
        }
    }

    private ScrollPane buildChatArea() {
        messagesBox = new VBox(10);
        messagesBox.setPadding(new Insets(20, 20, 10, 20));
        messagesBox.setStyle("-fx-background-color: " + BG_CHAT + ";");

        scrollPane = new ScrollPane(messagesBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: " + BG_CHAT + ";" +
                "-fx-background-color: " + BG_CHAT + "; -fx-border-color: transparent;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        messagesBox.heightProperty().addListener((obs, old, val) ->
                scrollPane.setVvalue(1.0));
        return scrollPane;
    }

    private HBox buildInputArea() {
        HBox inputArea = new HBox(12);
        inputArea.setPadding(new Insets(14, 20, 14, 20));
        inputArea.setAlignment(Pos.CENTER);
        inputArea.setStyle("-fx-background-color: " + BG_SIDEBAR + ";" +
                "-fx-border-color: #252836; -fx-border-width: 1 0 0 0;");

        inputField = new TextField();
        inputField.setPromptText("Écrire un message...");
        inputField.setPrefHeight(42);
        inputField.setStyle(
                "-fx-background-color: " + BG_INPUT + ";" +
                        "-fx-text-fill: " + TEXT_MAIN + ";" +
                        "-fx-prompt-text-fill: " + TEXT_MUTED + ";" +
                        "-fx-background-radius: 21; -fx-border-radius: 21;" +
                        "-fx-border-color: #2d3148; -fx-border-width: 1;" +
                        "-fx-padding: 0 16 0 16; -fx-font-size: 13;"
        );
        HBox.setHgrow(inputField, Priority.ALWAYS);
        inputField.setOnAction(e -> sendMessage());

        inputArea.getChildren().addAll(inputField, buildSendButton());
        return inputArea;
    }

    private Button buildSendButton() {
        Button btn = new Button("➤");
        btn.setPrefSize(42, 42);
        String styleNormal = "-fx-background-color: " + ACCENT + "; -fx-text-fill: white;" +
                "-fx-background-radius: 21; -fx-font-size: 14; -fx-cursor: hand;";
        String styleHover  = "-fx-background-color: #6fa3ff; -fx-text-fill: white;" +
                "-fx-background-radius: 21; -fx-font-size: 14; -fx-cursor: hand;";
        btn.setStyle(styleNormal);
        btn.setOnAction(e -> sendMessage());
        btn.setOnMouseEntered(e -> btn.setStyle(styleHover));
        btn.setOnMouseExited(e -> btn.setStyle(styleNormal));
        return btn;
    }

    private StackPane buildAvatar(String name) {
        Circle circle = new Circle(20);
        circle.setFill(Color.web(ACCENT));

        String initial = (name != null && !name.isEmpty()) ? String.valueOf(name.charAt(0)).toUpperCase() : "?";
        Label label = new Label(initial);
        label.setFont(Font.font("System", FontWeight.BOLD, 14));
        label.setTextFill(Color.WHITE);

        Circle dot = new Circle(5);
        dot.setFill(Color.web(ONLINE_DOT));
        dot.setStroke(Color.web(BG_SIDEBAR));
        dot.setStrokeWidth(2);
        StackPane.setAlignment(dot, Pos.BOTTOM_RIGHT);

        StackPane stack = new StackPane(circle, label, dot);
        stack.setPrefSize(40, 40);
        return stack;
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    private void addMessage(String content, boolean isSent) {
        Platform.runLater(() -> {
            HBox row = new HBox();
            row.setPadding(new Insets(2, 0, 2, 0));

            VBox bubble = new VBox(4);
            bubble.setMaxWidth(420);
            bubble.setPadding(new Insets(10, 14, 8, 14));
            bubble.setStyle(
                    "-fx-background-color: " + (isSent ? MSG_SENT : MSG_RECV) + ";" +
                            "-fx-background-radius: " + (isSent ? "18 18 4 18" : "18 18 18 4") + ";" +
                            "-fx-border-color: " + (isSent ? "#3d5299" : "#2d3148") + ";" +
                            "-fx-border-width: 1;" +
                            "-fx-border-radius: " + (isSent ? "18 18 4 18" : "18 18 18 4") + ";"
            );

            javafx.scene.Node messageNode = createMessageNode(content);

            Label timeLabel = new Label(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
            timeLabel.setFont(Font.font("System", 10));
            timeLabel.setTextFill(Color.web(TEXT_MUTED));

            bubble.getChildren().addAll(messageNode, timeLabel);

            if (isSent) {
                row.setAlignment(Pos.CENTER_RIGHT);
                HBox.setMargin(bubble, new Insets(0, 0, 0, 80));
            } else {
                row.setAlignment(Pos.CENTER_LEFT);
                HBox.setMargin(bubble, new Insets(0, 80, 0, 0));
            }

            row.getChildren().add(bubble);
            messagesBox.getChildren().add(row);
        });
    }

    private javafx.scene.Node createMessageNode(String content) {
        if (content.startsWith("MEDIA_MSG|AUDIO_MSG|")) {
            String[] fields = content.split("\\|", 6);
            String fileName = fields.length > 2 ? fields[2] : "audio";
            long size = fields.length > 3 ? parseLongSafe(fields[3]) : 0L;
            String encoded = fields.length > 5 ? fields[5] : "";
            File temp = persistTempMedia(fileName, encoded);

            HBox box = new HBox(8);
            box.setAlignment(Pos.CENTER_LEFT);
            Button play = new Button("Play/Pause");
            play.setOnAction(e -> {
                try {
                    Clip clip = AudioSystem.getClip();
                    clip.open(AudioSystem.getAudioInputStream(temp));
                    if (clip.isRunning()) clip.stop(); else clip.start();
                } catch (Exception ignored) {}
            });
            Label info = new Label("► Audio message • " + humanReadableSize(size));
            info.setTextFill(Color.web(TEXT_MAIN));
            box.getChildren().addAll(play, info);
            return box;
        }

        if (content.startsWith("MEDIA_MSG|")) {
            String[] fields = content.split("\\|", 6);
            String mediaType = fields.length > 1 ? fields[1] : "FILE";
            String fileName = fields.length > 2 ? fields[2] : "file";
            long size = fields.length > 3 ? parseLongSafe(fields[3]) : 0L;
            String encoded = fields.length > 5 ? fields[5] : "";
            File temp = persistTempMedia(fileName, encoded);

            VBox box = new VBox(6);
            Label info = new Label(fileName + " • " + humanReadableSize(size));
            info.setTextFill(Color.web(TEXT_MAIN));
            Button open = new Button(mediaType.equals("IMAGE") ? "Open Image" : "Download/Open");
            open.setOnAction(e -> {
                try { java.awt.Desktop.getDesktop().open(temp); } catch (Exception ignored) {}
            });
            box.getChildren().addAll(info, open);
            return box;
        }

        Text text = new Text(content);
        text.setFill(Color.web(TEXT_MAIN));
        text.setFont(Font.font("System", 13));
        text.setWrappingWidth(380);
        return new TextFlow(text);
    }

    private File persistTempMedia(String fileName, String base64) {
        try {
            java.nio.file.Path folder = java.nio.file.Paths.get("chat_tmp");
            java.nio.file.Files.createDirectories(folder);
            java.nio.file.Path path = folder.resolve(System.currentTimeMillis() + "_" + fileName.replaceAll("[^a-zA-Z0-9._-]", "_"));
            byte[] data = java.util.Base64.getDecoder().decode(base64);
            java.nio.file.Files.write(path, data);
            return path.toFile();
        } catch (Exception e) {
            return new File(fileName);
        }
    }

    private String humanReadableSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return (size / 1024) + " KB";
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    private long parseLongSafe(String v) {
        try { return Long.parseLong(v); } catch (Exception e) { return 0L; }
    }

    private void addSystemMessage(String text) {
        Platform.runLater(() -> {
            Label label = new Label(text);
            label.setFont(Font.font("System", 11));
            label.setTextFill(Color.web(TEXT_MUTED));
            label.setPadding(new Insets(4, 10, 4, 10));
            label.setStyle("-fx-background-color: #1a1d27; -fx-background-radius: 10;");

            HBox row = new HBox(label);
            row.setAlignment(Pos.CENTER);
            row.setPadding(new Insets(6, 0, 6, 0));
            messagesBox.getChildren().add(row);
        });
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    private void sendMessage() {
        String content = inputField.getText().trim();
        if (content.isEmpty()) return;

        client.send(
                "PRIVATE|" + currentUsername + "|" + targetUsername + "|" + content
        );
        addMessage(content, true);
        inputField.clear();
    }

    // ── Connection ────────────────────────────────────────────────────────────

    private void connectToServer() {
        new Thread(() -> {
            if (client.connect()) {
                addSystemMessage("Connecté au serveur ✓");
            } else {
                addSystemMessage("❌ Impossible de se connecter au serveur (vérifiez qu'il est lancé)");
                return;
            }

            // Login
            client.send("LOGIN|" + currentUsername + "|" + password);

            // Listen
            String line;
            while ((line = client.read()) != null) {
                handleIncoming(line);
            }
        }).start();
    }

    private void handleIncoming(String raw) {
        String[] parts = raw.split("\\|", 4);
        switch (parts[0]) {
            case "PRIVATE":
                // PRIVATE|from|to|content
                // On accepte le message si on est dans la bonne fenêtre
                if (parts.length == 4) {
                    addMessage(parts[3], false);
                }
                break;
            case "INCOMING_CALL":
            case "CALL_REQUEST":
                if (parts.length > 1) {
                    Platform.runLater(() -> {
                        isCallerInCurrentCall = false;
                        showCallUI(model.StatutAppel.RINGING);
                    });
                }
                break;
            case "CALL_RINGING":
                addSystemMessage("Appel en cours vers " + (parts.length > 1 ? parts[1] : "") + "...");
                break;
            case "CALL_ACCEPTED":
                addSystemMessage((parts.length > 1 ? parts[1] : "") + " a accepté l'appel.");
                if (currentCallStage != null) currentCallStage.getView().updateState(model.StatutAppel.IN_CALL);
                client.send("CALL_READY|" + localAudioPort);
                break;
            case "WAIT_CALLER_READY":
                addSystemMessage("En attente du port UDP de l'appelant...");
                break;
            case "START_AUDIO":
                addSystemMessage("Canal audio prêt.");
                if (parts.length > 2) {
                    if (currentCallManager != null) currentCallManager.stopCall();
                    currentCallManager = new streaming.CallManager(parts[1], isCallerInCurrentCall);
                    currentCallManager.startCall();
                }
                if (currentCallStage != null) currentCallStage.getView().updateState(model.StatutAppel.IN_CALL);
                break;
            case "CALL_STARTED":
                addSystemMessage("Appel démarré avec " + (parts.length > 1 ? parts[1] : ""));
                if (currentCallStage != null) currentCallStage.getView().updateState(model.StatutAppel.IN_CALL);
                if (parts.length > 2) {
                    currentCallManager = new streaming.CallManager(parts[2], false);
                    currentCallManager.startCall();
                }
                break;
            case "CALL_REFUSED":
            case "CALL_REFUSED_SENT":
                addSystemMessage("Appel refusé.");
                if (currentCallStage != null) {
                    currentCallStage.getView().updateState(model.StatutAppel.REFUSED);
                    closeCallUIWithDelay();
                }
                break;
            case "CALL_ENDED":
                addSystemMessage("Appel terminé.");
                if (currentCallManager != null) {
                    currentCallManager.stopCall();
                    currentCallManager = null;
                }
                if (currentCallStage != null) {
                    currentCallStage.getView().updateState(model.StatutAppel.ENDED);
                    closeCallUIWithDelay();
                }
                break;
            case "LOGIN_OK":
                addSystemMessage("✓ Authentifié");
                break;
            case "ERROR":
                addSystemMessage("❌ " + (parts.length > 1 ? parts[1] : "Erreur"));
                break;
            case "LOGOUT":
                if (parts.length > 1 && parts[1].equals(targetUsername)) {
                    Platform.runLater(() -> {
                        statusLabel.setText("● Hors ligne");
                        statusLabel.setTextFill(Color.web(TEXT_MUTED));
                    });
                    addSystemMessage(targetUsername + " s'est déconnecté");
                }
                break;
        }
    }

    private void showCallUI(model.StatutAppel status) {
        Platform.runLater(() -> {
            if (currentCallStage != null) currentCallStage.close();
            
            currentCallStage = new CallStage(targetUsername, status);
            CallView view = currentCallStage.getView();
            
            view.setOnAccept(() -> client.send("CALL_ACCEPT|" + localAudioPort));
            view.setOnDecline(() -> client.send("CALL_REFUSE|"));
            view.setOnHangUp(() -> terminateCall());
            
            currentCallStage.show();
        });
    }

    private void closeCallUIWithDelay() {
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
        pause.setOnFinished(e -> {
            if (currentCallStage != null) {
                currentCallStage.close();
                currentCallStage = null;
            }
        });
        pause.play();
    }

    public void disconnect() {
        client.deconnecter();
    }
}