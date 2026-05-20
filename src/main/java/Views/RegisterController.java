package Views;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import server.clientAPP;

import java.io.File;

public class RegisterController {
    @FXML private TextField nameField;
    @FXML private TextField phoneField;
    @FXML private Label errorLabel;
    @FXML private Label picLabel;
    @FXML private Button registerButton;
    @FXML private Button choosePicButton;

    private final clientAPP client = new clientAPP();
    private byte[] chosenPicture;   // compressed avatar bytes, or null

    @FXML
    private void handleChoosePicture() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose profile picture");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Images", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp"));
        File f = fc.showOpenDialog(choosePicButton.getScene().getWindow());
        if (f == null) return;
        try {
            byte[] compressed = services.ImageUtil.compressAvatar(f);
            if (compressed == null) { picLabel.setText("Could not read that image."); return; }
            chosenPicture = compressed;
            picLabel.setText(f.getName() + " (" + (compressed.length / 1024) + " KB)");
        } catch (Exception ex) {
            picLabel.setText("Failed to load image.");
        }
    }

    @FXML
    private void handleRegister() {
        String name  = nameField.getText().trim();
        String phone = phoneField.getText().trim();
        if (name.isEmpty()) {
            errorLabel.setText("Please enter your name.");
            return;
        }
        if (phone.isEmpty()) {
            errorLabel.setText("Please enter your phone number.");
            return;
        }

        registerButton.setDisable(true);
        errorLabel.setText("Sending code...");

        Thread t = new Thread(() -> doRegister(phone, name));
        t.setDaemon(true);
        t.start();
    }

    private void doRegister(String phone, String name) {
        try {
            if (!client.isConnected() && !client.connect()) {
                Platform.runLater(() -> {
                    errorLabel.setText("Server is offline. Start the server first.");
                    registerButton.setDisable(false);
                });
                return;
            }
            // Name must not contain the protocol delimiter.
            client.send("REGISTER_PHONE|" + phone + "|" + name.replace("|", " "));
            String response = readResponse();
            Platform.runLater(() -> onResponse(phone, name, response));
        } catch (Exception e) {
            Platform.runLater(() -> {
                errorLabel.setText("Server connection failed.");
                registerButton.setDisable(false);
            });
        }
    }

    private void onResponse(String phone, String name, String response) {
        registerButton.setDisable(false);
        if (response == null) {
            errorLabel.setText("No response from server.");
            return;
        }
        if (response.startsWith("OTP_SENT|")) {
            // OTP_SENT|<phone>  or (dev mode) OTP_SENT|<phone>|DEV|<code>
            String[] p = response.split("\\|");
            String devCode = (p.length >= 4 && "DEV".equals(p[2])) ? p[3] : null;
            SceneManager.switchToVerify(phone, client, devCode, name, chosenPicture);
            return;
        }
        errorLabel.setText(response);
    }

    // Skip async broadcast lines and wait for the REGISTER_PHONE result.
    private String readResponse() {
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
