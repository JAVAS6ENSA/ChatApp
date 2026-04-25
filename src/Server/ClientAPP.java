package Server;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class ClientAPP {

    // ─── Singleton ────────────────────────────────────────────────────
    private static ClientAPP instance;

    public static ClientAPP getInstance() {
        if (instance == null) {
            instance = new ClientAPP();
        }
        return instance;
    }

    private ClientAPP() {}

    // ─── Attributs réseau ─────────────────────────────────────────────
    private Socket socket;
    private PrintWriter  sortant;
    private BufferedReader entrant;

    public static final int    PORT = serveur.serverPort;
    public static final String HOST = "localhost";

    // ─── Listener de messages entrants ────────────────────────────────
    private Consumer<String> messageListener = null;
    private Thread listenerThread = null;

    // ─── Connexion ────────────────────────────────────────────────────
    public void connect() {
        try {
            socket  = new Socket(HOST, PORT);
            sortant = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            entrant = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println("[ClientAPP] Connecté au serveur " + HOST + ":" + PORT);
        } catch (Exception e) {
            System.err.println("[ClientAPP] Échec de connexion : " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ─── Envoi ────────────────────────────────────────────────────────
    public void send(String message) {
        if (sortant != null) {
            sortant.println(message);
        } else {
            System.err.println("[ClientAPP] Impossible d'envoyer : non connecté.");
        }
    }

    // ─── Lecture bloquante (usage manuel uniquement) ──────────────────
    public String read() {
        try {
            return entrant.readLine();
        } catch (Exception e) {
            System.err.println("[ClientAPP] Erreur de lecture : " + e.getMessage());
            return null;
        }
    }

    // ─── Thread d'écoute automatique ──────────────────────────────────
    // À appeler après connect() pour recevoir les messages en arrière-plan.
    // Le Consumer<String> reçoit chaque ligne envoyée par le serveur.
    // En JavaFX, wrapper avec Platform.runLater() pour toucher l'UI.
    public void startListening(Consumer<String> onMessage) {
        if (listenerThread != null && listenerThread.isAlive()) {
            System.out.println("[ClientAPP] Listener déjà actif.");
            return;
        }

        this.messageListener = onMessage;

        listenerThread = new Thread(() -> {
            try {
                String line;
                while ((line = entrant.readLine()) != null) {
                    final String msg = line;
                    if (messageListener != null) {
                        messageListener.accept(msg);
                    }
                }
            } catch (Exception e) {
                if (!socket.isClosed()) {
                    System.err.println("[ClientAPP] Connexion perdue : " + e.getMessage());
                }
            } finally {
                System.out.println("[ClientAPP] Thread d'écoute terminé.");
            }
        });

        listenerThread.setDaemon(true);
        listenerThread.setName("ClientAPP-Listener");
        listenerThread.start();
        System.out.println("[ClientAPP] Thread d'écoute démarré.");
    }

    // ─── Arrêt du listener ────────────────────────────────────────────
    public void stopListening() {
        messageListener = null;
        if (listenerThread != null) {
            listenerThread.interrupt();
            listenerThread = null;
        }
    }

    // ─── État de connexion ────────────────────────────────────────────
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    // ─── Déconnexion propre ───────────────────────────────────────────
    public void deconnecter() {
        stopListening();
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                System.out.println("[ClientAPP] Déconnecté du serveur.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            sortant  = null;
            entrant  = null;
            socket   = null;
            instance = null;
        }
    }
}