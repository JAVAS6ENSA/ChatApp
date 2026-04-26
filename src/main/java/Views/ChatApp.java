package Views;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class ChatApp extends Application {

    @Override
    public void start(Stage stage) {
      /*  java.util.List<String> choices = java.util.Arrays.asList("omar@mail.com", "youssef@mail.com");
        javafx.scene.control.ChoiceDialog<String> dialog = new javafx.scene.control.ChoiceDialog<>("omar@mail.com", choices);
        dialog.setTitle("Connexion rapide");
        dialog.setHeaderText("Sélectionnez votre compte");
        dialog.setContentText("Utilisateur :");

        dialog.showAndWait().ifPresent(email -> {
            String target = email.equals("omar@mail.com") ? "youssef@mail.com" : "omar@mail.com";
            // Les mots de passe correspondent à ceux de la DB pour omar@mail.com et youssef@mail.com
            String password = email.equals("omar@mail.com") ? "hashed_pw_5" : "hashed_pw_1";

            ChatView chatView = new ChatView(email, target, password);
            Scene scene = new Scene(chatView, 820, 600);
            stage.setTitle("Chat — " + email);
            stage.setScene(scene);
            stage.setOnCloseRequest(e -> chatView.disconnect());
            stage.show();
        });*/
        SceneManager.init(stage);
        SceneManager.switchTo("login.fxml");
    }


    public static void main(String[] args) {
        launch();
    }
}