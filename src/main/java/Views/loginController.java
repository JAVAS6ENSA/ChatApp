package Views;

import server.clientAPP;
import server.session;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class loginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;

    private final clientAPP client= new clientAPP();

    @FXML

    void handleLogin() {
        String user = usernameField.getText().trim();
        //in java concepts : usernameField has method to getText and we trim it and then give it
        //to user for verification

        String pass = passwordField.getText();

        if (user.isEmpty() || pass.isEmpty()) {
            errorLabel.setText("Veuillez remplir tous les champs."); //didnt we do this in the client hanler?
            return;
        }

        loginButton.setDisable(true);
        errorLabel.setText("Connexion...");
        Thread thread = new Thread(() -> doLogin(user, pass)); //it says here is an implematation of run() method that exists in runnable, a thread always needs a runnable as an anrgument

        //this does it in the background without holding the UI
        thread.setDaemon(true); //just like the server when we close the app the thread dies as well
        thread.start();
    }
        private void doLogin (String user, String pass)
        {
            try
            {
                if(!client.isConnected()) client.connect();

                client.send("LOGIN|"+user +"|" + pass);
                String response = client.read();
                Platform.runLater( () -> getResponse(response,user)); //another runnable //potential prof question what is runnable
            }
            catch (Exception e)
            {
                Platform.runLater(() -> {
                    errorLabel.setText("[DEBUG] can't connect to the client handler layer");
                    loginButton.setDisable(false);
                });
            }
        }

        private void getResponse(String response, String user)
        {
            loginButton.setDisable(false);
            if(response == null)
            {
                errorLabel.setText("Erreur de connexion, Verifier votre connexion");
            }
            else if(response.startsWith("Connexion réussite"))
            {
                String actualUsername = response.split(":", 2)[1].trim();
                session.getInstance().login(actualUsername, "USERINSTANCE");
                SceneManager.switchTo(new MainView(actualUsername, client));
            }
            else
            {
                errorLabel.setText(response);
            }
        }
@FXML
    public void goToRegister()
    {
        SceneManager.switchTo("register.fxml");
    }
}
