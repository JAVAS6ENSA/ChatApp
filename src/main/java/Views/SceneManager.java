package Views;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class SceneManager {
    private static Stage primaryStage;
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
        primaryStage.setScene(scene);
        primaryStage.show();
    }

}
