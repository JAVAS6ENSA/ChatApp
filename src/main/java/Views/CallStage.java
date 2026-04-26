package Views;

import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import model.StatutAppel;

public class CallStage extends Stage {

    private CallView callView;

    public CallStage(String targetName, StatutAppel initialStatus) {
        // Optionnel : rendre la fenêtre sans bordures pour un look plus moderne
        // initStyle(StageStyle.UNDECORATED);
        
        callView = new CallView(targetName, initialStatus);
        Scene scene = new Scene(callView);
        
        setTitle("Appel - " + targetName);
        setScene(scene);
        setResizable(false);
        
        // S'assurer que l'appel se termine si on ferme la fenêtre
        setOnCloseRequest(e -> {
            // Logique de fin d'appel ici
            System.out.println("Fermeture de la fenêtre d'appel");
        });
    }

    public CallView getView() {
        return callView;
    }
}
