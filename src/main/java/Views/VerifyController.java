package Views;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import server.clientAPP;
import server.session;
import dao.UserDAO;
import model.User;

public class VerifyController {
    @FXML private Label infoLabel;
    @FXML private TextField codeField;
    @FXML private Label errorLabel;
    @FXML private Button verifyButton;
    @FXML private Button resendButton;

    private final String    phone  = SceneManager.getPendingPhone();
    private final clientAPP  client = SceneManager.getPendingClient();

    @FXML
    private void initialize() {
        String devCode = SceneManager.getPendingDevCode();
        if (devCode != null && !devCode.isBlank()) {
            infoLabel.setText("Dev mode (no SMS): your code for " + phone + " is " + devCode);
            codeField.setText(devCode);
        } else {
            infoLabel.setText("We sent a 6-digit code to " + phone + ".");
        }
    }

    @FXML
    private void handleVerify() {
        String code = codeField.getText().trim();
        if (code.isEmpty()) {
            errorLabel.setText("Enter the code from the SMS.");
            return;
        }
        verifyButton.setDisable(true);
        errorLabel.setText("Verifying...");
        Thread t = new Thread(() -> {
            try {
                client.send("VERIFY_OTP|" + phone + "|" + code);
                String response = readResponse();
                Platform.runLater(() -> onVerify(response));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    errorLabel.setText("Server connection failed.");
                    verifyButton.setDisable(false);
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void onVerify(String response) {
        verifyButton.setDisable(false);
        if (response == null) {
            errorLabel.setText("No response from server.");
            return;
        }
        if (response.startsWith("Connexion réussite")) {
            String username = response.split(":", 2)[1].trim();
            applyPendingRegistration();
            session.getInstance().login(username, "USERINSTANCE");
            SceneManager.switchToChat(username, client);
            return;
        }
        errorLabel.setText(response);
    }

    private void applyPendingRegistration() {
        try {
            String  name = SceneManager.getPendingRegName();
            byte[]  pic  = SceneManager.getPendingRegPic();
            if ((name == null || name.isBlank()) && pic == null) return;
            UserDAO userDAO = new UserDAO();
            User me = userDAO.getByPhone(phone);
            if (me != null) {
                if (name != null && !name.isBlank()) userDAO.updateDisplayName(me.getId(), name);
                if (pic != null && pic.length > 0)   userDAO.updateProfilePictureData(me.getId(), pic);
            }
        } catch (Exception e) {
            System.err.println("applyPendingRegistration failed: " + e.getMessage());
        } finally {
            SceneManager.clearPendingRegistration();
        }
    }

    @FXML
    private void handleResend() {
        resendButton.setDisable(true);
        errorLabel.setText("Resending...");
        Thread t = new Thread(() -> {
            try {
                client.send("REQUEST_OTP|" + phone);
                String response = readResponse();
                Platform.runLater(() -> {
                    resendButton.setDisable(false);
                    if (response != null && response.startsWith("OTP_SENT|")) {
                        String[] p = response.split("\\|");
                        if (p.length >= 4 && "DEV".equals(p[2])) {
                            codeField.setText(p[3]);
                            errorLabel.setText("Dev mode: new code is " + p[3]);
                        } else {
                            errorLabel.setText("A new code was sent.");
                        }
                    } else {
                        errorLabel.setText(String.valueOf(response));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    resendButton.setDisable(false);
                    errorLabel.setText("Server connection failed.");
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // Skip async broadcast lines and wait for the real auth/OTP result.
    private String readResponse() {
        for (int i = 0; i < 12; i++) {
            String line = client.read();
            if (line == null) return "Server disconnected.";
            if (line.startsWith("USER_STATUS|")) continue;
            if (line.startsWith("Currently Online:")) continue;
            return line;
        }
        return "Response timeout.";
    }

    @FXML
    private void goBack() {
        SceneManager.switchTo("login.fxml");
    }
}
