package server;

import java.io.*;
import java.net.*;

public class clientAPP {

    public clientAPP() {}

    private Socket socket;
    private PrintWriter sortant;
    private BufferedReader entrant;

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

    public boolean isConnected() {
        return socket != null && socket.isConnected();
    }

    public void deconnecter() {
        try {
            if (socket != null) socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}