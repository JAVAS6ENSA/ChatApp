package server;

import java.io.*;
import java.net.*;
import javax.sound.sampled.*;

public class clientAPP {

    public clientAPP() {}

    private Socket socket;
    private PrintWriter sortant;
    private BufferedReader entrant;
    private volatile boolean listening = false;
    private MessageListener messageListener;
    private DatagramSocket udpSocket;
    private volatile boolean enAppel = false;
    private int monPortUDP = 5000;

    public static int PORT = 8080;
    public static String HOST = "localhost";

    public boolean connect() {
        try {
            socket  = new Socket(HOST, PORT);
            sortant = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            entrant = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println("[ClientAPP] Connecté au serveur");
            return true;
        } catch (Exception e) {
            System.err.println("[ClientAPP] Erreur de connexion: " + e.getMessage());
            return false;
        }
    }

    public void send(String message) {
        if (sortant != null) sortant.println(message);
    }

    public String read() {
        try {
            return entrant.readLine();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public interface MessageListener {
        void onMessage(String message);
    }

    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    public void setMonPortUDP(int monPortUDP) {
        this.monPortUDP = monPortUDP;
    }

    public int getMonPortUDP() {
        return monPortUDP;
    }

    public void startListening() {
        if (listening) return;
        listening = true;
        Thread listenerThread = new Thread(() -> {
            try {
                String line;
                while (listening && (line = read()) != null) {
                    if (messageListener != null) {
                        messageListener.onMessage(line);
                    }
                }
            } finally {
                listening = false;
            }
        }, "clientAPP-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void demarrerAudio(String destIp, int destPort) {
        try {
            if (udpSocket != null && !udpSocket.isClosed()) {
                udpSocket.close();
            }
            enAppel = true;
            udpSocket = new DatagramSocket(monPortUDP);
            envoyerAudio(destIp, destPort);
            recevoirAudio();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void arreterAudio() {
        enAppel = false;
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
    }

    private void envoyerAudio(String destIp, int destPort) {
        new Thread(() -> {
            AudioFormat format = new AudioFormat(8000, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            try (TargetDataLine micro = (TargetDataLine) AudioSystem.getLine(info)) {
                micro.open(format);
                micro.start();
                byte[] buffer = new byte[1024];
                InetAddress dest = InetAddress.getByName(destIp);
                while (enAppel && !udpSocket.isClosed()) {
                    int bytes = micro.read(buffer, 0, buffer.length);
                    DatagramPacket packet = new DatagramPacket(buffer, bytes, dest, destPort);
                    udpSocket.send(packet);
                }
            } catch (Exception e) {
                if (enAppel) e.printStackTrace();
            }
        }, "clientAPP-audio-send").start();
    }

    private void recevoirAudio() {
        new Thread(() -> {
            AudioFormat format = new AudioFormat(8000, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            try (SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(info)) {
                speakers.open(format);
                speakers.start();
                byte[] buffer = new byte[1024];
                while (enAppel && !udpSocket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);
                    speakers.write(packet.getData(), 0, packet.getLength());
                }
            } catch (Exception e) {
                if (enAppel) e.printStackTrace();
            }
        }, "clientAPP-audio-recv").start();
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected();
    }

    public void deconnecter() {
        try {
            listening = false;
            arreterAudio();
            if (socket != null) socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}