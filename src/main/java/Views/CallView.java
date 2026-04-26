package Views;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;
import model.StatutAppel;

public class CallView extends StackPane {

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final String BG_DARK    = "#0f1117";
    private static final String ACCENT     = "#4f8ef7";
    private static final String DANGER     = "#ff4d4d";
    private static final String SUCCESS    = "#22c55e";
    private static final String TEXT_MAIN  = "#e8eaf0";
    private static final String TEXT_MUTED = "#6b7280";

    // ── State ─────────────────────────────────────────────────────────────────
    private final String targetName;
    private StatutAppel currentStatus;
    private int secondsElapsed = 0;
    private Timeline timer;

    // ── UI Components ─────────────────────────────────────────────────────────
    private VBox mainLayout;
    private Label statusLabel;
    private Label timerLabel;
    private HBox controlsBox;
    private StackPane avatarPane;

    public CallView(String targetName, StatutAppel initialStatus) {
        this.targetName = targetName;
        this.currentStatus = initialStatus;

        buildUI();
        updateState(initialStatus);
    }

    private void buildUI() {
        setStyle("-fx-background-color: " + BG_DARK + ";");
        setPrefSize(350, 500);

        mainLayout = new VBox(30);
        mainLayout.setAlignment(Pos.CENTER);
        mainLayout.setPadding(new Insets(40));

        // Avatar
        avatarPane = buildAvatar(targetName);

        // Name & Status
        VBox infoBox = new VBox(10);
        infoBox.setAlignment(Pos.CENTER);

        Label nameLabel = new Label(targetName);
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        nameLabel.setTextFill(Color.web(TEXT_MAIN));

        statusLabel = new Label();
        statusLabel.setFont(Font.font("System", 16));
        statusLabel.setTextFill(Color.web(TEXT_MUTED));

        timerLabel = new Label("00:00");
        timerLabel.setFont(Font.font("System", 14));
        timerLabel.setTextFill(Color.web(ACCENT));
        timerLabel.setVisible(false);

        infoBox.getChildren().addAll(nameLabel, statusLabel, timerLabel);

        // Controls
        controlsBox = new HBox(20);
        controlsBox.setAlignment(Pos.CENTER);

        mainLayout.getChildren().addAll(avatarPane, infoBox, controlsBox);
        getChildren().add(mainLayout);
    }

    public void updateState(StatutAppel status) {
        this.currentStatus = status;
        Platform.runLater(() -> {
            controlsBox.getChildren().clear();
            
            switch (status) {
                case RINGING: // Incoming
                    statusLabel.setText("Appel entrant...");
                    timerLabel.setVisible(false);
                    controlsBox.getChildren().addAll(
                        buildRoundButton("✅", SUCCESS, this::onAccept),
                        buildRoundButton("❌", DANGER, this::onDecline)
                    );
                    break;

                case LIBRE: // Outgoing (Calling someone)
                    statusLabel.setText("Appel en cours...");
                    timerLabel.setVisible(false);
                    controlsBox.getChildren().addAll(
                        buildRoundButton("❌", DANGER, this::onHangUp)
                    );
                    break;

                case IN_CALL:
                    statusLabel.setText("En ligne");
                    timerLabel.setVisible(true);
                    startTimer();
                    controlsBox.getChildren().addAll(
                        buildRoundButton("🎤", "#374151", null), // Mute
                        buildRoundButton("❌", DANGER, this::onHangUp),
                        buildRoundButton("🔊", "#374151", null)  // Speaker
                    );
                    break;

                case REFUSED:
                    statusLabel.setText("Appel refusé");
                    stopTimer();
                    statusLabel.setTextFill(Color.web(DANGER));
                    break;

                case ENDED:
                    statusLabel.setText("Appel terminé");
                    stopTimer();
                    controlsBox.getChildren().clear();
                    break;
            }
        });
    }

    private StackPane buildAvatar(String name) {
        Circle circle = new Circle(60);
        circle.setFill(Color.web(ACCENT));
        // Effet de halo subtil
        circle.setStroke(Color.web(ACCENT, 0.3));
        circle.setStrokeWidth(10);

        Label label = new Label(String.valueOf(name.charAt(0)).toUpperCase());
        label.setFont(Font.font("System", FontWeight.BOLD, 40));
        label.setTextFill(Color.WHITE);

        return new StackPane(circle, label);
    }

    private Button buildRoundButton(String text, String color, Runnable action) {
        Button btn = new Button(text);
        btn.setPrefSize(60, 60);
        String baseStyle = "-fx-background-color: " + color + "; -fx-text-fill: white;" +
                           "-fx-background-radius: 30; -fx-font-size: 20; -fx-cursor: hand;";
        btn.setStyle(baseStyle);
        
        if (action != null) {
            btn.setOnAction(e -> action.run());
        }

        btn.setOnMouseEntered(e -> btn.setOpacity(0.8));
        btn.setOnMouseExited(e -> btn.setOpacity(1.0));

        return btn;
    }

    private void startTimer() {
        if (timer != null) timer.stop();
        secondsElapsed = 0;
        timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            secondsElapsed++;
            int mins = secondsElapsed / 60;
            int secs = secondsElapsed % 60;
            timerLabel.setText(String.format("%02d:%02d", mins, secs));
        }));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
    }

    private void stopTimer() {
        if (timer != null) timer.stop();
    }

    // ── Handlers (To be implemented with logic) ───────────────────────────────

    private Runnable onAcceptCallback;
    private Runnable onDeclineCallback;
    private Runnable onHangUpCallback;

    public void setOnAccept(Runnable callback) { this.onAcceptCallback = callback; }
    public void setOnDecline(Runnable callback) { this.onDeclineCallback = callback; }
    public void setOnHangUp(Runnable callback) { this.onHangUpCallback = callback; }

    private void onAccept() {
        if (onAcceptCallback != null) onAcceptCallback.run();
        updateState(StatutAppel.IN_CALL);
    }

    private void onDecline() {
        if (onDeclineCallback != null) onDeclineCallback.run();
        updateState(StatutAppel.REFUSED);
    }

    private void onHangUp() {
        if (onHangUpCallback != null) onHangUpCallback.run();
        updateState(StatutAppel.ENDED);
    }


}
