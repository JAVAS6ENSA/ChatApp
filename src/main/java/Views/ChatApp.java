package Views;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ChatApp extends Application {

    @Override
    public void start(Stage stage) {
        // Test: currentUserId=1, targetUserId=2, targetUsername="Sara"
        // Dev1 ghadi ybdel had les valeurs b login réel
        ChatView chatView = new ChatView("1", "2");

        Scene scene = new Scene(chatView, 820, 600);
        stage.setTitle("Chat — Messagerie Privée");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> chatView.disconnect());
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}