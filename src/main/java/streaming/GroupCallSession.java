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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
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
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GroupCallSession {

    public static class Peer {
        public final String username;
        public final InetAddress address;
        public final int audioPort;
        public final int videoPort;
        public volatile ImageView videoTile;

        public Peer(String username, InetAddress address, int audioPort, int videoPort) {
            this.username = username;
            this.address = address;
            this.audioPort = audioPort;
            this.videoPort = videoPort;
        }
    }

    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(44100, 16, 1, true, false);

    private final String localUsername;
    private final int groupId;
    private final String groupName;
    private final boolean isVideo;
    private final int audioPortLocal;
    private final int videoPortLocal;

    private final Map<String, Peer> peers = new ConcurrentHashMap<>();
    private volatile boolean running = false;
    private volatile boolean micMuted = false;
    private volatile boolean cameraHidden = false;

    private DatagramSocket audioOut;
    private DatagramSocket videoOut;

    private Stage callStage;
    private Label statusLabel;
    private Label durationLabel;
    private FlowPane participantTiles;
    private Map<String, StackPane> tileByPeer = new HashMap<>();
    private ImageView selfVideoTile;
    private Timeline durationTimer;
    private long callStartMillis;
    private Runnable onEnd;
    private java.util.function.Function<String, String> nameResolver = u -> u;

    public void setNameResolver(java.util.function.Function<String, String> r) {
        if (r != null) this.nameResolver = r;
    }

    private String label(String username) {
        try { String s = nameResolver.apply(username); return s == null ? username : s; }
        catch (Exception e) { return username; }
    }

    public GroupCallSession(String localUsername, int groupId, String groupName,
                            boolean isVideo, int audioPort, int videoPort) {
        this.localUsername = localUsername;
        this.groupId = groupId;
        this.groupName = groupName == null ? "Group meeting" : groupName;
        this.isVideo = isVideo;
        this.audioPortLocal = audioPort;
        this.videoPortLocal = videoPort;
    }

    public int getAudioPortLocal() { return audioPortLocal; }
    public int getVideoPortLocal() { return videoPortLocal; }
    public int getGroupId()        { return groupId; }
    public boolean isRunning()     { return running; }
    public boolean isVideo()       { return isVideo; }

    public void setOnEnd(Runnable r) { this.onEnd = r; }

    public synchronized void addPeer(Peer p) {
        peers.put(p.username, p);
        if (running) Platform.runLater(this::rebuildTiles);
    }

    public synchronized void removePeer(String username) {
        peers.remove(username);
        if (running) Platform.runLater(this::rebuildTiles);
    }

    public void start() {
        if (running) return;
        running = true;

        Platform.runLater(this::buildWindow);

        try { audioOut = new DatagramSocket(); }
        catch (Exception e) { System.err.println("[GROUP CALL] audioOut: " + e.getMessage()); }
        try { videoOut = new DatagramSocket(); }
        catch (Exception e) { System.err.println("[GROUP CALL] videoOut: " + e.getMessage()); }

        new Thread(this::audioSenderLoop,   "GroupAudioSender").start();
        new Thread(this::audioReceiverLoop, "GroupAudioRecv").start();
        if (isVideo) {
            new Thread(this::videoSenderLoop,   "GroupVideoSender").start();
            new Thread(this::videoReceiverLoop, "GroupVideoRecv").start();
        }
    }

    public void leave() {
        if (!running) return;
        running = false;
        try { if (audioOut != null) audioOut.close(); } catch (Exception ignored) {}
        try { if (videoOut != null) videoOut.close(); } catch (Exception ignored) {}
        Platform.runLater(() -> {
            if (durationTimer != null) { durationTimer.stop(); durationTimer = null; }
            if (callStage != null) { callStage.close(); callStage = null; }
        });
        if (onEnd != null) {
            try { onEnd.run(); } catch (Exception ignored) {}
        }
    }

    private void buildWindow() {
        callStage = new Stage();
        callStage.setTitle((isVideo ? "Video meeting" : "Audio meeting") + " · " + groupName);

        statusLabel = new Label("You are connected · " + (isVideo ? "Video" : "Audio"));
        statusLabel.setTextFill(Color.WHITE);
        statusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");

        durationLabel = new Label("00:00");
        durationLabel.setTextFill(Color.web("#cccccc"));
        durationLabel.setStyle("-fx-font-size: 12px;");

        VBox top = new VBox(4, statusLabel, durationLabel);
        top.setAlignment(Pos.CENTER);
        top.setPadding(new Insets(16));
        top.setStyle("-fx-background-color: rgba(0,0,0,0.7);");

        participantTiles = new FlowPane(12, 12);
        participantTiles.setPadding(new Insets(14));
        participantTiles.setAlignment(Pos.CENTER);
        participantTiles.setStyle("-fx-background-color: #0b141a;");

        rebuildTiles();

        Button mute = new Button("Mute");
        mute.setOnAction(e -> {
            micMuted = !micMuted;
            mute.setText(micMuted ? "Unmute" : "Mute");
        });
        styleControl(mute, "#2a3942");

        Button leave = new Button("Leave");
        leave.setOnAction(e -> leave());
        styleControl(leave, "#e53935");

        HBox controls = new HBox(14);
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(new Insets(14));
        controls.setStyle("-fx-background-color: rgba(0,0,0,0.7);");

        if (isVideo) {
            Button camToggle = new Button("Hide Camera");
            styleControl(camToggle, "#2a3942");
            camToggle.setOnAction(e -> {
                cameraHidden = !cameraHidden;
                camToggle.setText(cameraHidden ? "Show Camera" : "Hide Camera");
            });
            controls.getChildren().addAll(mute, camToggle, leave);
        } else {
            controls.getChildren().addAll(mute, leave);
        }

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0b141a;");
        root.setTop(top);
        root.setCenter(participantTiles);
        root.setBottom(controls);

        Scene scene = new Scene(root, isVideo ? 980 : 560, isVideo ? 660 : 420);
        callStage.setScene(scene);
        callStage.setOnCloseRequest(e -> leave());
        callStage.show();

        startDurationTimer();
    }

    private void startDurationTimer() {
        callStartMillis = System.currentTimeMillis();
        durationTimer = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
            long secs = (System.currentTimeMillis() - callStartMillis) / 1000;
            durationLabel.setText(String.format("%02d:%02d", secs / 60, secs % 60));
        }));
        durationTimer.setCycleCount(Timeline.INDEFINITE);
        durationTimer.play();
    }

    private void rebuildTiles() {
        if (participantTiles == null) return;
        participantTiles.getChildren().clear();
        tileByPeer.clear();

        if (isVideo) {
            selfVideoTile = new ImageView();
            selfVideoTile.setFitWidth(220);
            selfVideoTile.setFitHeight(160);
            selfVideoTile.setPreserveRatio(true);
            addTile(label(localUsername) + " (you)", selfVideoTile, true);
        } else {
            selfVideoTile = null;
            addTile(label(localUsername) + " (you)", null, true);
        }

        for (Peer p : peers.values()) {
            ImageView iv = isVideo ? new ImageView() : null;
            if (iv != null) {
                iv.setFitWidth(220);
                iv.setFitHeight(160);
                iv.setPreserveRatio(true);
                p.videoTile = iv;
            }
            StackPane tile = addTile(label(p.username), iv, false);
            tileByPeer.put(p.username, tile);
        }
    }

    private StackPane addTile(String label, ImageView img, boolean self) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(8));
        card.setStyle("-fx-background-color: #1e2a30; -fx-background-radius: 12;");

        javafx.scene.Node content;
        if (img != null) {
            content = img;
        } else {
            Circle dot = new Circle(36);
            dot.setFill(Color.web(self ? "#25d366" : "#34b7f1"));
            Label initial = new Label(label.isEmpty() ? "?" : label.substring(0, 1).toUpperCase());
            initial.setTextFill(Color.WHITE);
            initial.setStyle("-fx-font-size: 24px; -fx-font-weight: 700;");
            StackPane avatar = new StackPane(dot, initial);
            avatar.setPrefSize(120, 120);
            content = avatar;
        }

        Label name = new Label(label);
        name.setTextFill(Color.WHITE);
        name.setStyle("-fx-font-size: 13px;");

        card.getChildren().addAll(content, name);
        StackPane wrapper = new StackPane(card);
        wrapper.setPrefSize(240, 200);
        participantTiles.getChildren().add(wrapper);
        return wrapper;
    }

    private static void styleControl(Button btn, String bg) {
        btn.setStyle(
                "-fx-background-color: " + bg + ";" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 13px;" +
                "-fx-padding: 8 18 8 18;" +
                "-fx-background-radius: 20;" +
                "-fx-cursor: hand;");
    }

    private void audioSenderLoop() {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
        TargetDataLine mic;
        try {
            mic = (TargetDataLine) AudioSystem.getLine(info);
            mic.open(AUDIO_FORMAT);
            mic.start();
        } catch (Exception e) {
            System.err.println("[GROUP CALL] Mic unavailable: " + e.getMessage());
            return;
        }
        byte[] buf = new byte[1024];
        byte[] silence = new byte[1024];
        try {
            while (running) {
                int n = mic.read(buf, 0, buf.length);
                if (n <= 0) continue;
                for (Peer p : peers.values()) {
                    if (audioOut == null || audioOut.isClosed()) break;
                    byte[] payload = micMuted ? silence : buf;
                    audioOut.send(new DatagramPacket(payload, n, p.address, p.audioPort));
                }
            }
        } catch (Exception e) {
            if (running) System.err.println("[GROUP CALL] audioSenderLoop: " + e.getMessage());
        } finally {
            try { mic.stop(); mic.close(); } catch (Exception ignored) {}
        }
    }

    private void audioReceiverLoop() {
        try (DatagramSocket socket = new DatagramSocket(audioPortLocal);
             SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(
                     new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT))) {
            speakers.open(AUDIO_FORMAT);
            speakers.start();
            socket.setSoTimeout(800);
            byte[] buf = new byte[4096];
            while (running) {
                try {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    socket.receive(pkt);
                    speakers.write(pkt.getData(), 0, pkt.getLength());
                } catch (SocketTimeoutException ignored) {}
            }
        } catch (Exception e) {
            if (running) System.err.println("[GROUP CALL] audioReceiverLoop: " + e.getMessage());
        }
    }

    private void videoSenderLoop() {
        FrameGrabber grabber = openCamera();
        if (grabber == null) {
            System.err.println("[GROUP CALL] No camera available.");
            return;
        }
        Java2DFrameConverter conv = new Java2DFrameConverter();
        try {
            while (running) {
                if (cameraHidden) { Thread.sleep(150); continue; }
                Frame frame = grabber.grab();
                if (frame == null) { Thread.sleep(20); continue; }
                BufferedImage bi = conv.getBufferedImage(frame);
                if (bi == null) continue;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                javax.imageio.ImageIO.write(bi, "jpg", baos);
                byte[] data = baos.toByteArray();
                if (data.length >= 60000) continue;

                if (selfVideoTile != null) {
                    Image fxImg = new Image(new ByteArrayInputStream(data));
                    Platform.runLater(() -> selfVideoTile.setImage(fxImg));
                }

                for (Peer p : peers.values()) {
                    if (videoOut == null || videoOut.isClosed()) break;
                    videoOut.send(new DatagramPacket(data, data.length, p.address, p.videoPort));
                }
                Thread.sleep(80);
            }
        } catch (Exception e) {
            if (running) System.err.println("[GROUP CALL] videoSenderLoop: " + e.getMessage());
        } finally {
            try { grabber.stop(); grabber.release(); } catch (Exception ignored) {}
        }
    }

    private void videoReceiverLoop() {
        try (DatagramSocket socket = new DatagramSocket(videoPortLocal)) {
            socket.setSoTimeout(800);
            byte[] buf = new byte[65507];
            while (running) {
                try {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    socket.receive(pkt);
                    String from = peerNameByAddress(pkt.getAddress());
                    if (from == null) continue;
                    Peer p = peers.get(from);
                    if (p == null || p.videoTile == null) continue;
                    Image img = new Image(new ByteArrayInputStream(pkt.getData(), 0, pkt.getLength()));
                    Platform.runLater(() -> p.videoTile.setImage(img));
                } catch (SocketTimeoutException ignored) {}
            }
        } catch (Exception e) {
            if (running) System.err.println("[GROUP CALL] videoReceiverLoop: " + e.getMessage());
        }
    }

    private String peerNameByAddress(InetAddress addr) {
        if (addr == null) return null;
        for (Peer p : peers.values()) {
            if (p.address != null && p.address.equals(addr)) return p.username;
        }
        if (peers.size() == 1) return peers.keySet().iterator().next();
        return null;
    }

    private FrameGrabber openCamera() {
        for (int i = 0; i < 3; i++) {
            try {
                OpenCVFrameGrabber g = new OpenCVFrameGrabber(i);
                g.setImageWidth(320);
                g.setImageHeight(240);
                g.start();
                return g;
            } catch (Exception ignored) {}
        }
        return null;
    }
}
