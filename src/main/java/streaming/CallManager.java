package streaming;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

/**
 * Drives a 1:1 audio/video call.
 *
 * Threads:
 *   - videoCaptureLoop : grabs frames, paints them into the local PiP, and
 *                        sends the JPEG over UDP to the remote peer.
 *   - videoReceiveLoop : reads JPEG packets and paints them into the remote view.
 *   - audioSender      : reads from the mic and sends raw PCM over UDP.
 *   - audioReceiver    : reads PCM packets and writes them to the speakers.
 */
public class CallManager {

    private final String remoteIP;
    private final boolean isCaller;
    private final boolean isVideo;
    private volatile String peerName;

    private volatile boolean running = false;
    private volatile boolean micMuted = false;
    private volatile boolean cameraHidden = false;

    private final int videoPortLocal;
    private final int audioPortLocal;
    private final int videoPortRemote;
    private final int audioPortRemote;

    private Stage callStage;
    private ImageView remoteView;
    private ImageView localPreviewView;
    private Label statusLabel;
    private Label durationLabel;
    private Label localPreviewStatus;
    private Button muteBtn;
    private Button hideCamBtn;
    private StackPane localPreviewBox;
    private Runnable onEnd;

    private Timeline durationTimer;
    private long callStartMillis;

    // Pre-rendered solid-black JPEG sent to the peer when the user hides
    // their camera, so the remote side gets an explicit blackout instead of
    // a stale last frame.
    private byte[] blackFrameBytes;

    public CallManager(String remoteIP, boolean isCaller, boolean isVideo) {
        this(remoteIP, isCaller, isVideo, "Peer");
    }

    public CallManager(String remoteIP, boolean isCaller, boolean isVideo, String peerName) {
        this.remoteIP = remoteIP;
        this.isCaller = isCaller;
        this.isVideo = isVideo;
        this.peerName = peerName == null ? "Peer" : peerName;

        if (isCaller) {
            this.videoPortLocal = 5000;
            this.audioPortLocal = 6000;
            this.videoPortRemote = 5001;
            this.audioPortRemote = 6001;
        } else {
            this.videoPortLocal = 5001;
            this.audioPortLocal = 6001;
            this.videoPortRemote = 5000;
            this.audioPortRemote = 6000;
        }
    }

    /** Set a callback fired when the user closes / ends the call window. */
    public void setOnEnd(Runnable onEnd) {
        this.onEnd = onEnd;
    }

    /** Override the displayed peer name (used when the server hands us a real
     *  username after construction, replacing the placeholder). */
    public void setPeerName(String name) {
        if (name == null || name.isBlank()) return;
        this.peerName = name;
        Platform.runLater(() -> {
            if (callStage != null) {
                callStage.setTitle((isVideo ? "Video call" : "Audio call") + " · " + this.peerName);
            }
            if (statusLabel != null) {
                statusLabel.setText(this.peerName + (isVideo ? " · Connected" : ""));
            }
        });
    }

    public void startCall() {
        running = true;
        System.out.println("[STREAM] Starting " + (isVideo ? "video" : "audio")
                + " call with " + peerName + " (" + remoteIP + ")"
                + " role=" + (isCaller ? "caller" : "recipient"));

        Platform.runLater(this::buildCallWindow);

        if (isVideo) {
            blackFrameBytes = createBlackJpeg(320, 240);
            new Thread(this::videoCaptureLoop,  "VideoCapture").start();
            new Thread(this::videoReceiveLoop,  "VideoReceiver").start();
        }
        new Thread(this::audioSender,   "AudioSender").start();
        new Thread(this::audioReceiver, "AudioReceiver").start();
    }

    public void stopCall() {
        if (!running) return;
        running = false;
        Platform.runLater(() -> {
            if (durationTimer != null) {
                durationTimer.stop();
                durationTimer = null;
            }
            if (callStage != null) {
                callStage.close();
                callStage = null;
            }
        });
    }

    // ────────────────────────────────────────────────────────────
    //  UI
    // ────────────────────────────────────────────────────────────

    private void buildCallWindow() {
        if (callStage != null) return;

        callStage = new Stage();
        callStage.initStyle(StageStyle.DECORATED);
        callStage.setTitle((isVideo ? "Video call" : "Audio call") + " · " + peerName);

        Region root = isVideo ? buildVideoRoot() : buildAudioRoot();

        Scene scene = new Scene(root, isVideo ? 880 : 480, isVideo ? 620 : 520);
        callStage.setScene(scene);

        if (isVideo && remoteView != null) {
            remoteView.fitWidthProperty().bind(scene.widthProperty());
            remoteView.fitHeightProperty().bind(scene.heightProperty());
        }

        startDurationTimer();
        callStage.setOnCloseRequest(e -> requestEnd());
        callStage.show();
    }

    private Region buildAudioRoot() {
        // Top: avatar + name + status.
        Circle avatar = new Circle(56);
        avatar.setFill(Color.web("#25d366"));
        avatar.setStroke(Color.web("#1f1f1f"));
        avatar.setStrokeWidth(3);

        Label initial = new Label(initialOf(peerName));
        initial.setTextFill(Color.WHITE);
        initial.setStyle("-fx-font-size: 42px; -fx-font-weight: bold;");

        StackPane avatarBox = new StackPane(avatar, initial);
        avatarBox.setPrefSize(120, 120);

        Label nameLabel = new Label(peerName);
        nameLabel.setTextFill(Color.WHITE);
        nameLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        statusLabel = new Label(isCaller ? "Calling…" : "Connected");
        statusLabel.setTextFill(Color.web("#cccccc"));
        statusLabel.setStyle("-fx-font-size: 14px;");

        VBox top = new VBox(12, avatarBox, nameLabel, statusLabel);
        top.setAlignment(Pos.CENTER);
        top.setPadding(new Insets(40, 14, 14, 14));

        // Center: duration timer.
        durationLabel = new Label("00:00");
        durationLabel.setTextFill(Color.web("#9aa6ad"));
        durationLabel.setStyle("-fx-font-size: 18px;");

        VBox center = new VBox(durationLabel);
        center.setAlignment(Pos.CENTER);

        // Bottom: pinned controls.
        HBox controls = buildControlsBar();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0b141a;");
        root.setTop(top);
        root.setCenter(center);
        root.setBottom(controls);
        return root;
    }

    private Region buildVideoRoot() {
        // Background: remote video feed.
        StackPane background = new StackPane();
        background.setStyle("-fx-background-color: #0b141a;");

        remoteView = new ImageView();
        remoteView.setPreserveRatio(true);
        remoteView.setSmooth(true);

        Label remotePlaceholder = new Label("Waiting for " + peerName + "'s video…");
        remotePlaceholder.setTextFill(Color.web("#cccccc"));
        remotePlaceholder.setStyle("-fx-font-size: 16px;");
        background.getChildren().addAll(remotePlaceholder, remoteView);

        // Picture-in-picture local preview.
        localPreviewView = new ImageView();
        localPreviewView.setFitWidth(200);
        localPreviewView.setFitHeight(150);
        localPreviewView.setPreserveRatio(true);
        localPreviewView.setSmooth(true);

        Rectangle pipBg = new Rectangle(200, 150);
        pipBg.setArcWidth(16);
        pipBg.setArcHeight(16);
        pipBg.setFill(Color.web("#000000"));

        localPreviewStatus = new Label("Camera…");
        localPreviewStatus.setTextFill(Color.web("#bbbbbb"));
        localPreviewStatus.setStyle("-fx-font-size: 12px;");

        localPreviewBox = new StackPane(pipBg, localPreviewStatus, localPreviewView);
        localPreviewBox.setPrefSize(200, 150);
        localPreviewBox.setMaxSize(200, 150);
        localPreviewBox.setStyle(
                "-fx-background-color: rgba(0,0,0,0.6);" +
                "-fx-border-color: #25d366;" +
                "-fx-border-width: 2;" +
                "-fx-background-radius: 12;" +
                "-fx-border-radius: 12;");
        StackPane.setAlignment(localPreviewBox, Pos.BOTTOM_RIGHT);
        // Bottom margin keeps the PiP above the pinned controls bar.
        StackPane.setMargin(localPreviewBox, new Insets(0, 24, 110, 0));
        makeDraggable(localPreviewBox);

        // Top status overlay (peer name + status + duration).
        statusLabel = new Label(peerName + " · " + (isCaller ? "Calling…" : "Connected"));
        statusLabel.setTextFill(Color.WHITE);
        statusLabel.setStyle(
                "-fx-font-size: 14px;" +
                "-fx-background-color: rgba(0,0,0,0.55);" +
                "-fx-padding: 8 14 8 14;" +
                "-fx-background-radius: 16;");

        durationLabel = new Label("00:00");
        durationLabel.setTextFill(Color.web("#cccccc"));
        durationLabel.setStyle(
                "-fx-font-size: 12px;" +
                "-fx-background-color: rgba(0,0,0,0.55);" +
                "-fx-padding: 6 12 6 12;" +
                "-fx-background-radius: 14;");

        VBox topOverlay = new VBox(6, statusLabel, durationLabel);
        topOverlay.setAlignment(Pos.CENTER);
        // Without USE_PREF_SIZE the VBox would stretch to fill the StackPane
        // and TOP_CENTER alignment would have no visible effect.
        topOverlay.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(topOverlay, Pos.TOP_CENTER);
        StackPane.setMargin(topOverlay, new Insets(20, 0, 0, 0));

        // Bottom pinned controls.
        HBox controls = buildControlsBar();
        // USE_PREF_SIZE prevents the HBox from stretching to fill the StackPane
        // vertically — that was the root cause of the audio-call overlap bug.
        controls.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(controls, Pos.BOTTOM_CENTER);

        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: #0b141a;");
        root.getChildren().addAll(background, localPreviewBox, topOverlay, controls);
        return root;
    }

    private HBox buildControlsBar() {
        muteBtn = new Button("Mute");
        muteBtn.setOnAction(e -> {
            micMuted = !micMuted;
            muteBtn.setText(micMuted ? "Unmute" : "Mute");
        });
        styleControl(muteBtn, "#2a3942");

        Button endBtn = new Button("End call");
        styleControl(endBtn, "#e53935");
        endBtn.setOnAction(e -> requestEnd());

        HBox controls = new HBox(14);
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(new Insets(16, 24, 24, 24));
        controls.setStyle("-fx-background-color: rgba(0,0,0,0.55);");

        if (isVideo) {
            hideCamBtn = new Button("Hide Camera");
            styleControl(hideCamBtn, "#2a3942");
            hideCamBtn.setOnAction(e -> toggleCameraHidden());
            controls.getChildren().addAll(muteBtn, hideCamBtn, endBtn);
        } else {
            controls.getChildren().addAll(muteBtn, endBtn);
        }
        return controls;
    }

    private void toggleCameraHidden() {
        cameraHidden = !cameraHidden;
        if (hideCamBtn != null) {
            hideCamBtn.setText(cameraHidden ? "Show Camera" : "Hide Camera");
        }
        Platform.runLater(() -> {
            if (cameraHidden) {
                if (localPreviewView != null) localPreviewView.setImage(null);
                if (localPreviewStatus != null) localPreviewStatus.setText("Camera off");
            } else {
                if (localPreviewStatus != null) localPreviewStatus.setText("Camera…");
            }
        });
    }

    private void startDurationTimer() {
        callStartMillis = System.currentTimeMillis();
        if (durationLabel == null) return;
        durationTimer = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
            long secs = (System.currentTimeMillis() - callStartMillis) / 1000;
            durationLabel.setText(String.format("%02d:%02d", secs / 60, secs % 60));
        }));
        durationTimer.setCycleCount(Timeline.INDEFINITE);
        durationTimer.play();
    }

    private static String initialOf(String name) {
        if (name == null || name.isEmpty()) return "?";
        return name.substring(0, 1).toUpperCase();
    }

    private static void styleControl(Button btn, String bg) {
        btn.setStyle(
                "-fx-background-color: " + bg + ";" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 14px;" +
                "-fx-padding: 10 22 10 22;" +
                "-fx-background-radius: 24;" +
                "-fx-cursor: hand;");
    }

    /** Lets the user drag the PiP tile around inside the stage. */
    private void makeDraggable(StackPane node) {
        final double[] start = new double[2];
        final double[] origin = new double[2];
        node.setOnMousePressed(ev -> {
            start[0] = ev.getSceneX();
            start[1] = ev.getSceneY();
            origin[0] = node.getTranslateX();
            origin[1] = node.getTranslateY();
        });
        node.setOnMouseDragged(ev -> {
            node.setTranslateX(origin[0] + ev.getSceneX() - start[0]);
            node.setTranslateY(origin[1] + ev.getSceneY() - start[1]);
        });
    }

    private void requestEnd() {
        boolean wasRunning = running;
        stopCall();
        if (wasRunning && onEnd != null) {
            try { onEnd.run(); } catch (Exception ignored) {}
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Networking helpers
    // ────────────────────────────────────────────────────────────

    private InetAddress resolveTargetAddress() throws Exception {
        String ip = remoteIP;
        if (ip == null || ip.isBlank() || ip.equals("0.0.0.0")) ip = "127.0.0.1";
        return InetAddress.getByName(ip);
    }

    // ────────────────────────────────────────────────────────────
    //  Video
    // ────────────────────────────────────────────────────────────

    private void videoCaptureLoop() {
        FrameGrabber grabber = openCamera();
        if (grabber == null) {
            System.err.println("[STREAM] No camera available — local preview disabled.");
            Platform.runLater(() -> {
                if (localPreviewStatus != null) localPreviewStatus.setText("No camera");
                if (hideCamBtn != null) hideCamBtn.setDisable(true);
            });
            return;
        }

        Java2DFrameConverter converter = new Java2DFrameConverter();
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress dest = resolveTargetAddress();
            while (running) {
                if (cameraHidden) {
                    // Tell the peer the camera is off by sending a black frame
                    // at a slower cadence — the receive loop will paint it.
                    if (blackFrameBytes != null && blackFrameBytes.length > 0
                            && blackFrameBytes.length < 60000) {
                        socket.send(new DatagramPacket(
                                blackFrameBytes, blackFrameBytes.length, dest, videoPortRemote));
                    }
                    Thread.sleep(120);
                    continue;
                }

                Frame frame = grabber.grab();
                if (frame == null) { Thread.sleep(20); continue; }
                BufferedImage bi = converter.getBufferedImage(frame);
                if (bi == null) continue;

                // Encode once as JPEG and reuse the bytes for both the local
                // preview (decoded by JavaFX Image) and the UDP packet — this
                // avoids pulling in javafx-swing just for SwingFXUtils.
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                javax.imageio.ImageIO.write(bi, "jpg", baos);
                byte[] data = baos.toByteArray();

                Image fxImg = new Image(new ByteArrayInputStream(data));
                Platform.runLater(() -> {
                    if (localPreviewView != null && !cameraHidden) {
                        localPreviewView.setImage(fxImg);
                        if (localPreviewStatus != null) localPreviewStatus.setText("");
                    }
                });

                if (data.length < 60000) {
                    socket.send(new DatagramPacket(data, data.length, dest, videoPortRemote));
                }
                Thread.sleep(60);
            }
        } catch (Exception e) {
            if (running) System.err.println("[STREAM] VideoCapture: " + e.getMessage());
        } finally {
            try { grabber.stop(); } catch (Exception ignored) {}
            try { grabber.release(); } catch (Exception ignored) {}
        }
    }

    private FrameGrabber openCamera() {
        // Prefer OpenCVFrameGrabber explicitly: on Linux/macOS the platform
        // default sometimes resolves to FFmpegFrameGrabber, which can't open
        // a V4L webcam and silently fails. Errors are now logged so a future
        // mis-configuration is visible instead of hidden.
        int[] indices = isCaller ? new int[]{0, 1, 2} : new int[]{1, 0, 2};
        for (int i : indices) {
            FrameGrabber g = tryOpenOpenCV(i);
            if (g == null) g = tryOpenDefault(i);
            if (g != null) {
                System.out.println("[STREAM] Camera index " + i + " opened ("
                        + g.getClass().getSimpleName() + ").");
                return g;
            }
        }
        return null;
    }

    private FrameGrabber tryOpenOpenCV(int index) {
        try {
            OpenCVFrameGrabber g = new OpenCVFrameGrabber(index);
            g.setImageWidth(320);
            g.setImageHeight(240);
            g.start();
            return g;
        } catch (Exception e) {
            System.err.println("[STREAM] OpenCV camera index " + index + " failed: " + e.getMessage());
            return null;
        }
    }

    private FrameGrabber tryOpenDefault(int index) {
        try {
            FrameGrabber g = FrameGrabber.createDefault(index);
            g.setImageWidth(320);
            g.setImageHeight(240);
            g.start();
            return g;
        } catch (Exception e) {
            System.err.println("[STREAM] Default camera index " + index + " failed: " + e.getMessage());
            return null;
        }
    }

    private void videoReceiveLoop() {
        try (DatagramSocket socket = new DatagramSocket(videoPortLocal)) {
            socket.setSoTimeout(1000);
            byte[] buffer = new byte[65507];
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    Image img = new Image(new ByteArrayInputStream(
                            packet.getData(), 0, packet.getLength()));
                    Platform.runLater(() -> {
                        if (remoteView != null) remoteView.setImage(img);
                    });
                } catch (SocketTimeoutException ignored) {
                } catch (Exception e) {
                    if (running) System.err.println("[STREAM] VideoRecv: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[STREAM] VideoReceiver bind failed (port "
                    + videoPortLocal + "): " + e.getMessage());
        }
    }

    private static byte[] createBlackJpeg(int w, int h) {
        try {
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = bi.createGraphics();
            g.setColor(java.awt.Color.BLACK);
            g.fillRect(0, 0, w, h);
            g.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(bi, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Audio
    // ────────────────────────────────────────────────────────────

    private static final AudioFormat AUDIO_FORMAT =
            new AudioFormat(44100, 16, 1, true, false);

    private void audioSender() {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
        TargetDataLine mic = null;
        try {
            mic = (TargetDataLine) AudioSystem.getLine(info);
            mic.open(AUDIO_FORMAT);
            mic.start();
        } catch (Exception e) {
            System.err.println("[STREAM] Microphone unavailable (probably in use by the other client): "
                    + e.getMessage());
            return;
        }

        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress dest = resolveTargetAddress();
            byte[] buffer = new byte[1024];
            byte[] silence = new byte[1024]; // sent while muted to keep the path warm
            while (running) {
                int read = mic.read(buffer, 0, buffer.length);
                if (read <= 0) continue;
                if (micMuted) {
                    socket.send(new DatagramPacket(silence, read, dest, audioPortRemote));
                } else {
                    socket.send(new DatagramPacket(buffer, read, dest, audioPortRemote));
                }
            }
        } catch (Exception e) {
            if (running) System.err.println("[STREAM] AudioSender: " + e.getMessage());
        } finally {
            try { mic.stop(); mic.close(); } catch (Exception ignored) {}
        }
    }

    private void audioReceiver() {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
        SourceDataLine speakers = null;
        try {
            speakers = (SourceDataLine) AudioSystem.getLine(info);
            speakers.open(AUDIO_FORMAT);
            speakers.start();
        } catch (Exception e) {
            System.err.println("[STREAM] Speakers unavailable: " + e.getMessage());
            return;
        }

        try (DatagramSocket socket = new DatagramSocket(audioPortLocal)) {
            socket.setSoTimeout(1000);
            byte[] buffer = new byte[4096];
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    speakers.write(packet.getData(), 0, packet.getLength());
                } catch (SocketTimeoutException ignored) {
                } catch (Exception e) {
                    if (running) System.err.println("[STREAM] AudioRecv: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[STREAM] AudioReceiver bind failed (port "
                    + audioPortLocal + "): " + e.getMessage());
        } finally {
            try { speakers.stop(); speakers.close(); } catch (Exception ignored) {}
        }
    }
}
