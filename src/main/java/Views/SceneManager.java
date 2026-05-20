package Views;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;
import server.clientAPP;


public class SceneManager {

    public enum Theme { LIGHT, DARK }

    private static Stage primaryStage;
    private static String currentUsername;
    private static clientAPP currentClient;
    private static Theme currentTheme = Theme.LIGHT;

    // Carried between the phone screen and the OTP screen.
    private static String   pendingPhone;
    private static clientAPP pendingClient;
    private static String   pendingDevCode;   // non-null only in OTP dev mode
    private static String   pendingRegName;   // chosen display name (registration)
    private static byte[]   pendingRegPic;    // chosen avatar bytes (registration)

    public static void init(Stage stage) { primaryStage = stage; }

    public static void switchTo(String fxml) {
        try {
            Parent root = FXMLLoader.load(SceneManager.class.getResource("/" + fxml));
            switchTo(root);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void switchTo(Parent root) {
        Scene scene = new Scene(root);
        applyTheme(scene);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void applyTheme(Scene scene) {
        scene.getStylesheets().clear();
        scene.getStylesheets().add(SceneManager.class.getResource("/app-dark.css").toExternalForm());
        if (currentTheme == Theme.DARK) {
            scene.getStylesheets().add(SceneManager.class.getResource("/app-night.css").toExternalForm());
        }
    }

    public static void applyTheme(DialogPane pane) {
        try {
            pane.getStylesheets().clear();
            pane.getStylesheets().add(SceneManager.class.getResource("/app-dark.css").toExternalForm());
            if (currentTheme == Theme.DARK) {
                pane.getStylesheets().add(SceneManager.class.getResource("/app-night.css").toExternalForm());
            }
        } catch (Exception ignored) {}
    }

    public static Theme getTheme() { return currentTheme; }

    public static void setTheme(Theme theme) {
        if (theme == null || theme == currentTheme) return;
        currentTheme = theme;
        if (primaryStage != null && primaryStage.getScene() != null) {
            applyTheme(primaryStage.getScene());
        }
    }

    public static void toggleTheme() {
        setTheme(currentTheme == Theme.LIGHT ? Theme.DARK : Theme.LIGHT);
    }

    public static void setSession(String username, clientAPP client) {
        currentUsername = username;
        currentClient = client;
    }

    public static String getCurrentUsername() { return currentUsername; }
    public static clientAPP getCurrentClient() { return currentClient; }

    public static void switchToChat(String username, clientAPP client) {
        setSession(username, client);
        switchTo("chat.fxml");
    }

    public static void switchToVerify(String phone, clientAPP client) {
        switchToVerify(phone, client, null, null, null);
    }

    public static void switchToVerify(String phone, clientAPP client,
                                      String devCode, String regName, byte[] regPic) {
        pendingPhone   = phone;
        pendingClient  = client;
        pendingDevCode = devCode;
        pendingRegName = regName;
        pendingRegPic  = regPic;
        switchTo("verify.fxml");
    }

    public static String   getPendingPhone()   { return pendingPhone; }
    public static clientAPP getPendingClient()  { return pendingClient; }
    public static String   getPendingDevCode() { return pendingDevCode; }
    public static String   getPendingRegName() { return pendingRegName; }
    public static byte[]   getPendingRegPic()  { return pendingRegPic; }

    public static void clearPendingRegistration() {
        pendingDevCode = null;
        pendingRegName = null;
        pendingRegPic  = null;
    }
}
