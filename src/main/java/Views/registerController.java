package Views;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import server.clientAPP;
import server.session;

public class registerController {
    @FXML
    private TextField usernameField;
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmField;
    @FXML
    private Label errorLabel;
    @FXML
    private Button registerButton;

    private final clientAPP client = new clientAPP();

    @FXML
    //its ok if i redo all this because its fast and double protection in case sm1 passed UI
    public void handleRegister() {
        String user = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String pass = passwordField.getText();
        String confirm = confirmField.getText();
        if (user.isEmpty() || email.isEmpty() || pass.isEmpty()) {
            errorLabel.setText("Tous les champs sont requis.");
            return;
        }
        if (user.length() < 5) {
            errorLabel.setText("nom d'utilisateur court (min 5)");
            return;
        }
        if (!pass.matches(".*[@&#!$%^*]+.*") || pass.length() < 8) {
            errorLabel.setText("Mot de passe trop court ou sans caractere special.");
            return;
        }
        if (!pass.equals(confirm)) {
            errorLabel.setText("Les mots de passes ne correspondent pas");
            return;
        }
        if (!email.contains("@")) {
            errorLabel.setText("Email invalide. exemple: applicationjava@ensa.ma");
            return;
        }
        registerButton.setDisable(true);
        errorLabel.setText("Création du compte...");
        Thread thread = new Thread(() -> doRegister(user, pass, email));
        thread.setDaemon(true);
        thread.start();

    }

    private String lastUsername;

    public void doRegister(String user, String pass, String email) {
        this.lastUsername = user;
        String response = "";
        try {
            if (!client.isConnected()) client.connect();
            client.send("REGISTER|" + user + "|" + pass + "|" + email);
            response = client.read();
            final String resp = response;
            Platform.runLater(() -> processRegister(resp));
        } catch (Exception e) {
            Platform.runLater(() -> {
                errorLabel.setText("Erreur EXCEPCTION");
                registerButton.setDisable(false);
            });
        }

    }

    public void processRegister(String response) {

        if (response == null) {
            errorLabel.setText("Connexion perdue.");
        } else if (response.contains("Compte cree avec succes")) {
            session.getInstance().login(lastUsername, "USERINSTANCE");
            SceneManager.switchTo(new MainView(lastUsername, client));
        } else {
            errorLabel.setText(response);
        }
        registerButton.setDisable(false);
    }

    @FXML
    void goToLogin() {
        SceneManager.switchTo("login.fxml");
    }
}