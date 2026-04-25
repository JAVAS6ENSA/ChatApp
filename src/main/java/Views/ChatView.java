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

    // ── UI ────────────────────────────────────────────────────────────────────
    private VBox      messagesBox;
    private ScrollPane scrollPane;
    private TextField  inputField;
    private Label      statusLabel;

    // currentUsername = username dyal user connecté
    // targetUsername  = username dyal contact
    public ChatView(String currentUsername, String targetUsername) {
        this.currentUsername = currentUsername;
        this.targetUsername  = targetUsername;

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

        Label connLabel = new Label("🔒 Chiffré");
        connLabel.setFont(Font.font("System", 11));
        connLabel.setTextFill(Color.web(TEXT_MUTED));

        header.getChildren().addAll(avatar, nameBox, spacer, connLabel);
        return header;
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

        Label label = new Label(String.valueOf(name.charAt(0)).toUpperCase());
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

            Text text = new Text(content);
            text.setFill(Color.web(TEXT_MAIN));
            text.setFont(Font.font("System", 13));
            text.setWrappingWidth(380);

            Label timeLabel = new Label(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
            timeLabel.setFont(Font.font("System", 10));
            timeLabel.setTextFill(Color.web(TEXT_MUTED));

            bubble.getChildren().addAll(new TextFlow(text), timeLabel);

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

        clientAPP.getInstance().send(
                "PRIVATE|" + currentUsername + "|" + targetUsername + "|" + content
        );
        addMessage(content, true);
        inputField.clear();
    }

    // ── Connection ────────────────────────────────────────────────────────────

    private void connectToServer() {
        new Thread(() -> {
            clientAPP client = clientAPP.getInstance();
            client.connect();
            addSystemMessage("Connecté au serveur ✓");

            // Login
            client.send("LOGIN|" + currentUsername + "|hashed_pw_1");

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
                if (parts.length == 4 && parts[1].equals(targetUsername)) {
                    addMessage(parts[3], false);
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

    public void disconnect() {
        clientAPP.getInstance().deconnecter();
    }
}