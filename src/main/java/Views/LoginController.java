package Views;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import server.clientAPP;

public class LoginController {
    @FXML private TextField phoneField;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;

    private final clientAPP client = new clientAPP();

    @FXML
    private void handleLogin() {
        String phone = phoneField.getText().trim();
        if (phone.isEmpty()) {
            errorLabel.setText("Please enter your phone number.");
            return;
        }

        loginButton.setDisable(true);
        errorLabel.setText("Sending code...");

        Thread t = new Thread(() -> doLogin(phone));
        t.setDaemon(true);
        t.start();
    }

    private void doLogin(String phone) {
        try {
            if (!client.isConnected() && !client.connect()) {
                Platform.runLater(() -> {
                    errorLabel.setText("Server is offline. Start the server first.");
                    loginButton.setDisable(false);
                });
                return;
            }
            client.send("REQUEST_OTP|" + phone);
            String response = readResponse();
            Platform.runLater(() -> onResponse(phone, response));
        } catch (Exception e) {
            Platform.runLater(() -> {
                errorLabel.setText("Server connection failed.");
                loginButton.setDisable(false);
            });
        }
    }

    private void onResponse(String phone, String response) {
        loginButton.setDisable(false);
        if (response == null) {
            errorLabel.setText("No response from server.");
            return;
        }
        if (response.startsWith("OTP_SENT|")) {
            SceneManager.switchToVerify(phone, client);
            return;
        }
        errorLabel.setText(response);
    }

    // The server can send broadcast events just after the request.
    // Skip those and wait for the real OTP result.
    private String readResponse() {
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
