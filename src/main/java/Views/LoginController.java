package Views;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import server.clientAPP;
import server.session;

public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;

    private final clientAPP client = new clientAPP();

    @FXML
    private void handleLogin() {
        String user = usernameField.getText().trim();
        String pass = passwordField.getText();

        if (user.isEmpty() || pass.isEmpty()) {
            errorLabel.setText("Please fill in all fields.");
            return;
        }

        loginButton.setDisable(true);
        errorLabel.setText("Signing in...");

        Thread t = new Thread(() -> doLogin(user, pass));
        t.setDaemon(true);
        t.start();
    }

    private void doLogin(String user, String pass) {
        try {
            if (!client.isConnected() && !client.connect()) {
                Platform.runLater(() -> {
                    errorLabel.setText("Server is offline. Start the server first.");
                    loginButton.setDisable(false);
                });
                return;
            }
            client.send("LOGIN|" + user + "|" + pass);
            String response = readAuthResponse();
            Platform.runLater(() -> onLoginResponse(response));
        } catch (Exception e) {
            Platform.runLater(() -> {
                errorLabel.setText("Server connection failed.");
                loginButton.setDisable(false);
            });
        }
    }

    private void onLoginResponse(String response) {
        loginButton.setDisable(false);
        if (response == null) {
            errorLabel.setText("No response from server.");
            return;
        }
        if (response.startsWith("Connexion réussite")) {
            String actualUsername = response.split(":", 2)[1].trim();
            session.getInstance().login(actualUsername, "USERINSTANCE");
            SceneManager.switchToChat(actualUsername, client);
            return;
        }
        errorLabel.setText(response);
    }

    // The server can send broadcast events (like USER_STATUS) just after LOGIN.
    // We skip those and wait for the real authentication result.
    private String readAuthResponse() {
        for (int i = 0; i < 12; i++) {
            String line = client.read();
            if (line == null) return "Server disconnected.";
            if (line.startsWith("USER_STATUS|")) continue;
            if (line.startsWith("Currently Online:")) continue;
            return line;
        }
        return "Login response timeout.";
    }

    @FXML
    private void goToRegister() {
        SceneManager.switchTo("register.fxml");
    }
}
