package streaming;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.Frame;

import javax.sound.sampled.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.Arrays;

public class CallManager {

    private String remoteIP;
    private boolean isCaller;
    private volatile boolean running = false;

    private int videoPortLocal;
    private int audioPortLocal;
    private int videoPortRemote;
    private int audioPortRemote;

    private Stage videoStage;
    private ImageView videoView;

    public CallManager(String remoteIP, boolean isCaller) {
        this.remoteIP = remoteIP;
        this.isCaller = isCaller;

        // Force localhost si on est sur la même machine pour éviter les soucis de pare-feu réseau
        if (remoteIP.equals("0.0.0.0") || remoteIP.startsWith("192.168")) {
            // Optionnel : on pourrait garder l'IP, mais localhost est plus sûr pour le test local
        }

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

    public void startCall() {
        running = true;
        System.out.println("[STREAM] Appel vers : " + remoteIP);

        Platform.runLater(() -> {
            videoStage = new Stage();
            videoStage.setTitle(isCaller ? "Appel Sortant" : "Appel Entrant");
            videoView = new ImageView();
            videoView.setFitWidth(640);
            videoView.setFitHeight(480);
            videoView.setPreserveRatio(true);

            StackPane root = new StackPane(videoView);
            root.setStyle("-fx-background-color: #000;");
            videoStage.setScene(new Scene(root, 640, 480));
            videoStage.setOnCloseRequest(e -> stopCall());
            videoStage.show();
        });

        new Thread(this::videoSender, "VideoSender").start();
        new Thread(this::videoReceiver, "VideoReceiver").start();
        new Thread(this::audioSender, "AudioSender").start();
        new Thread(this::audioReceiver, "AudioReceiver").start();
    }

    public void stopCall() {
        running = false;
        Platform.runLater(() -> {
            if (videoStage != null) videoStage.close();
        });
    }

    private void showError(String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("Erreur Matériel");
            alert.setContentText(msg);
            alert.show();
        });
    }

    // ==========================================
    // VIDÉO (UDP - Résolution réduite pour fluidité)
    // ==========================================

    private void videoSender() {
        FrameGrabber grabber = null;
        try (DatagramSocket socket = new DatagramSocket()) {
            // Détection si on est en local pour forcer localhost
            String targetIP = remoteIP;
            if (remoteIP.startsWith("192.168") || remoteIP.equals("0.0.0.0")) {
                targetIP = "127.0.0.1";
            }
            InetAddress address = InetAddress.getByName(targetIP);
            
            // On essaie plusieurs indices pour que 2 instances puissent avoir 2 caméras différentes
            int[] indices = isCaller ? new int[]{0, 1, 2} : new int[]{1, 0, 2};
            for (int i : indices) {
                try {
                    grabber = FrameGrabber.createDefault(i);
                    grabber.setImageWidth(320);
                    grabber.setImageHeight(240);
                    grabber.start();
                    System.out.println("[STREAM] Caméra " + i + " démarrée.");
                    break;
                } catch (Exception e) { grabber = null; }
            }

            if (grabber == null) {
                System.err.println("[STREAM] Aucune caméra disponible ou déjà utilisée.");
                return;
            }

            Java2DFrameConverter converter = new Java2DFrameConverter();
            while (running) {
                Frame frame = grabber.grab();
                if (frame == null) continue;

                BufferedImage bi = converter.getBufferedImage(frame);
                if (bi != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    javax.imageio.ImageIO.write(bi, "jpg", baos);
                    byte[] data = baos.toByteArray();
                    if (data.length < 60000) {
                        socket.send(new DatagramPacket(data, data.length, address, videoPortRemote));
                    }
                }
                Thread.sleep(70); 
            }
        } catch (Exception e) {
            System.err.println("VideoSender: " + e.getMessage());
        } finally {
            try { if (grabber != null) grabber.stop(); } catch (Exception ignored) {}
        }
    }

    private void videoReceiver() {
        try (DatagramSocket socket = new DatagramSocket(videoPortLocal)) {
            socket.setSoTimeout(1000);
            byte[] buffer = new byte[65507];

            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    Image img = new Image(new ByteArrayInputStream(packet.getData(), 0, packet.getLength()));
                    Platform.runLater(() -> {
                        if (videoView != null) videoView.setImage(img);
                    });
                } catch (SocketTimeoutException ignored) {}
                catch (Exception e) { System.err.println("VideoRecv Error: " + e.getMessage()); }
            }
        } catch (Exception e) {
            System.err.println("VideoReceiver Port occupé: " + e.getMessage());
        }
    }

    // ==========================================
    // AUDIO (UDP - 44.1kHz)
    // ==========================================

    private void audioSender() {
        AudioFormat format = new AudioFormat(44100, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        try (DatagramSocket socket = new DatagramSocket();
             TargetDataLine mic = (TargetDataLine) AudioSystem.getLine(info)) {
            
            String targetIP = remoteIP;
            if (remoteIP.startsWith("192.168") || remoteIP.equals("0.0.0.0")) {
                targetIP = "127.0.0.1";
            }
            InetAddress address = InetAddress.getByName(targetIP);
            
            mic.open(format);
            mic.start();

            byte[] buffer = new byte[1024];
            while (running) {
                int read = mic.read(buffer, 0, buffer.length);
                if (read > 0) {
                    socket.send(new DatagramPacket(buffer, read, address, audioPortRemote));
                }
            }
        } catch (Exception e) {
            System.err.println("[STREAM] Erreur Micro (probablement déjà utilisé) : " + e.getMessage());
        }
    }


    private void audioReceiver() {
        AudioFormat format = new AudioFormat(44100, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        try (DatagramSocket socket = new DatagramSocket(audioPortLocal);
             SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(info)) {
            
            socket.setSoTimeout(1000);
            speakers.open(format);
            speakers.start();

            byte[] buffer = new byte[4096];
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    speakers.write(packet.getData(), 0, packet.getLength());
                } catch (SocketTimeoutException ignored) {}
            }
        } catch (Exception e) {
            System.err.println("AudioReceiver Error: " + e.getMessage());
        }
    }
}

