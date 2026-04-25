package Server;

import java.net.ServerSocket;
import java.net.Socket;

public class serveur {

    public static final int serverPort = 8080;

    private final SessionManager sessionManager = new SessionManager();
    private final AppelManager   appelManager   = new AppelManager();
    private ServerSocket sSocket;

    // ─── Démarrage ────────────────────────────────────────────────────
    public void startServer() {
        try {
            sSocket = new ServerSocket(serverPort);
            System.out.println("[Serveur] Démarré sur le port " + serverPort);

            while (true) {
                Socket socket = sSocket.accept();
                System.out.println("[Serveur] Nouvelle connexion depuis : "
                        + socket.getInetAddress().getHostAddress()
                        + ":" + socket.getPort());

                clientHandler newClient = new clientHandler(socket, sessionManager, appelManager);

                Thread thread = new Thread(newClient);
                thread.setDaemon(true);
                thread.setName("ClientThread-" + socket.getPort());
                thread.start();
            }

        } catch (Exception e) {
            if (sSocket != null && !sSocket.isClosed()) {
                System.err.println("[Serveur] Erreur inattendue : " + e.getMessage());
                e.printStackTrace();
            } else {
                System.out.println("[Serveur] Arrêté proprement.");
            }
        }
    }

    // ─── Arrêt propre ─────────────────────────────────────────────────
    public void stopServer() {
        try {
            if (sSocket != null && !sSocket.isClosed()) {
                sSocket.close();
                System.out.println("[Serveur] Socket fermé.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─── Arrêt via hook JVM (Ctrl+C / kill) ───────────────────────────
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Serveur] Signal d'arrêt reçu, fermeture...");
            stopServer();
        }));
    }

    // ─── Point d'entrée ───────────────────────────────────────────────
    public static void main(String[] args) {
        serveur s = new serveur();
        s.registerShutdownHook();
        s.startServer();
    }
}