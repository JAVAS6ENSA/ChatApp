package Views;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import server.clientAPP;

public class SceneManager {
    private static Stage primaryStage;
    private static String currentUsername;
    private static clientAPP currentClient;

    public static void init(Stage stage)
    {
        primaryStage = stage;
    }
    public static void switchTo(String fxml)
    {
        try{
        Parent root = FXMLLoader.load(SceneManager.class.getResource("/" + fxml));
        switchTo(root);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

    }

    public static void switchTo(Parent root)
    {
        Scene scene = new Scene(root);
        scene.getStylesheets().add(SceneManager.class.getResource("/app-dark.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void setSession(String username, clientAPP client) {
        currentUsername = username;
        currentClient = client;
    }

    public static String getCurrentUsername() {
        return currentUsername;
    }

    public static clientAPP getCurrentClient() {
        return currentClient;
    }

    public static void switchToChat(String username, clientAPP client) {
        setSession(username, client);
        switchTo("chat.fxml");
    }

}
