package Views;

import dao.MessageDAO;
import dao.UserDAO;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import model.Message;
import model.User;

import java.util.List;

public class PrivateChatController extends BorderPane {
    private static final String BG_CHAT = "#13151f";
    private static final String BG_BUBBLE_RECV = "#2f3136";
    private static final String BG_BUBBLE_SENT = "#25d366";
    private static final String TEXT_MAIN = "#f5f5f5";
    private static final String TEXT_MUTED = "#9ca3af";

    private final UserDAO userDAO = new UserDAO();
    private final MessageDAO messageDAO = new MessageDAO();

    private final String currentUsername;
    private final String targetUsername;

    private User currentUser;
    private User targetUser;

    private VBox messagesBox;
    private ScrollPane messagesScroll;
    private TextField inputField;
    private Timeline refreshTimeline;

    public PrivateChatController(String currentUsername, String targetUsername) {
        this.currentUsername = currentUsername;
        this.targetUsername = targetUsername;
        setStyle("-fx-background-color: " + BG_CHAT + ";");
        setPrefSize(900, 700);

        initUsers();
        buildLayout();
        loadConversation();
        startAutoRefresh();
    }

    private void initUsers() {
        currentUser = userDAO.getByUsername(currentUsername);
        targetUser = userDAO.getByUsername(targetUsername);
    }

    private void buildLayout() {
        Label title = new Label(targetUsername);
        title.setTextFill(Color.web(TEXT_MAIN));
        title.setFont(Font.font(18));
        BorderPane.setMargin(title, new Insets(16, 20, 10, 20));
        setTop(title);

        messagesBox = new VBox(10);
        messagesBox.setPadding(new Insets(15));
        messagesScroll = new ScrollPane(messagesBox);
        messagesScroll.setFitToWidth(true);
        messagesScroll.setStyle("-fx-background: " + BG_CHAT + "; -fx-background-color: transparent;");
        setCenter(messagesScroll);

        HBox footer = new HBox(10);
        footer.setPadding(new Insets(10, 15, 15, 15));
        inputField = new TextField();
        inputField.setPromptText("Ecrire un message...");
        HBox.setHgrow(inputField, Priority.ALWAYS);
        Button sendButton = new Button("Envoyer");
        sendButton.setOnAction(e -> sendMessage());
        inputField.setOnAction(e -> sendMessage());
        footer.getChildren().addAll(inputField, sendButton);
        setBottom(footer);
    }

    private void loadConversation() {
        messagesBox.getChildren().clear();

        if (currentUser == null || targetUser == null) {
            Label error = new Label("Impossible de charger la conversation. Utilisateur introuvable.");
            error.setTextFill(Color.web("#ef4444"));
            messagesBox.getChildren().add(error);
            return;
        }

        messageDAO.markConversationAsRead(currentUser.getId(), targetUser.getId());
        List<Message> messages = messageDAO.getConversation(currentUser.getId(), targetUser.getId());

        // Historique en ordre chrono (ancien -> récent)
        for (Message message : messages) {
            addMessageBubble(message);
        }

        scrollToBottom();
    }

    private void addMessageBubble(Message message) {
        boolean isSentByMe = message.getSenderId() == currentUser.getId();

        HBox row = new HBox();
        row.setAlignment(isSentByMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox bubble = new VBox(4);
        bubble.setMaxWidth(450);
        bubble.setPadding(new Insets(10, 12, 8, 12));
        bubble.setStyle(
                "-fx-background-color: " + (isSentByMe ? BG_BUBBLE_SENT : BG_BUBBLE_RECV) + ";" +
                "-fx-background-radius: 16;"
        );

        Text content = new Text(message.getContent());
        content.setFill(Color.web(TEXT_MAIN));
        content.setWrappingWidth(420);
        TextFlow textFlow = new TextFlow(content);

        Label time = new Label(message.getFormattedTime());
        time.setTextFill(Color.web(TEXT_MUTED));
        time.setStyle("-fx-font-size: 11px;");
        HBox timeRow = new HBox(time);
        timeRow.setAlignment(Pos.CENTER_RIGHT);

        bubble.getChildren().addAll(textFlow, timeRow);
        row.getChildren().add(bubble);
        HBox.setMargin(bubble, new Insets(0, isSentByMe ? 0 : 60, 0, isSentByMe ? 60 : 0));
        messagesBox.getChildren().add(row);
    }

    private void sendMessage() {
        if (currentUser == null || targetUser == null) {
            return;
        }
        String content = inputField.getText();
        if (content == null || content.isBlank()) {
            return;
        }

        Message newMessage = new Message(currentUser.getId(), targetUser.getId(), content.trim());
        if (messageDAO.saveMessage(newMessage)) {
            inputField.clear();
            loadConversation();
        }
    }

    private void scrollToBottom() {
        Platform.runLater(() -> messagesScroll.setVvalue(1.0));
    }

    private void startAutoRefresh() {
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(2), e -> loadConversation()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    public void disconnect() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
    }
}
