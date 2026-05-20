package Views;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class CallController {
    @FXML private Label callTypeLabel;
    @FXML private Label callStateLabel;

    public void setCallType(String type) {
        callTypeLabel.setText(type);
    }

    public void setCallState(String state) {
        callStateLabel.setText(state);
    }
}
