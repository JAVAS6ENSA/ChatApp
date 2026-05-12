package Views;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import server.clientAPP;
import server.session;

public class RegisterController {
    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmField;
    @FXML private Label errorLabel;
    @FXML private Button registerButton;

    private final clientAPP client = new clientAPP();
    private String lastUsername;

    @FXML
    private void handleRegister() {
        String user = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String pass = passwordField.getText();
        String confirm = confirmField.getText();

        if (user.isEmpty() || email.isEmpty() || pass.isEmpty() || confirm.isEmpty()) {
            errorLabel.setText("All fields are required.");
            return;
        }
        if (!pass.equals(confirm)) {
            errorLabel.setText("Passwords do not match.");
            return;
        }

        registerButton.setDisable(true);
        errorLabel.setText("Creating account...");

        Thread t = new Thread(() -> doRegister(user, pass, email));
        t.setDaemon(true);
        t.start();
    }

    private void doRegister(String user, String pass, String email) {
        lastUsername = user;
        try {
            if (!client.isConnected() && !client.connect()) {
                Platform.runLater(() -> {
                    errorLabel.setText("Server is offline. Start the server first.");
                    registerButton.setDisable(false);
                });
                return;
            }
            client.send("REGISTER|" + user + "|" + pass + "|" + email);
            String response = readRegisterResponse();
            Platform.runLater(() -> onRegisterResponse(response));
        } catch (Exception e) {
            Platform.runLater(() -> {
                errorLabel.setText("Server connection failed.");
                registerButton.setDisable(false);
            });
        }
    }

    private void onRegisterResponse(String response) {
        registerButton.setDisable(false);
        if (response == null) {
            errorLabel.setText("No response from server.");
            return;
        }
        if (response.contains("Compte cree avec succes")) {
            session.getInstance().login(lastUsername, "USERINSTANCE");
            SceneManager.switchToChat(lastUsername, client);
            return;
        }
        errorLabel.setText(response);
    }

    private String readRegisterResponse() {
        for (int i = 0; i < 12; i++) {
            String line = client.read();
            if (line == null) return "Server disconnected.";
            if (line.startsWith("USER_STATUS|")) continue;
            if (line.startsWith("Currently Online:")) continue;
            return line;
        }
        return "Register response timeout.";
    }

    @FXML
    private void goToLogin() {
        SceneManager.switchTo("login.fxml");
    }
}
