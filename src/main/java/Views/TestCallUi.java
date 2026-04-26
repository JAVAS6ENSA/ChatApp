package Views;

import javafx.application.Application;
import javafx.stage.Stage;
import model.StatutAppel;

public class TestCallUi extends Application {

    @Override
    public void start(Stage stage) {

        CallStage callStage = new CallStage("salma", StatutAppel.RINGING);
        callStage.show();

        // test état entrant
        callStage.getView().updateState(StatutAppel.RINGING);

        // test état actif après 3 sec
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                javafx.application.Platform.runLater(() -> {
                    callStage.getView().updateState(StatutAppel.IN_CALL);
                });
            } catch (Exception e) {}
        }).start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
