package Views;

import javafx.application.Application;
import javafx.stage.Stage;

public class ChatApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        SceneManager.init(primaryStage);
        SceneManager.switchTo("login.fxml");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
