package server;

import server.Exceptions.*;
import dao.UserDAO;
import model.User;
import databases.DBConnection;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class clientHandler implements Runnable {
    private final Socket socket;
    private final SessionManager sManager;
    private PrintWriter going;
    private BufferedReader coming;
    private String username = null;
    private String role = null;

    public clientHandler(Socket socket, SessionManager sManager) {
        this.socket = socket;
        this.sManager = sManager;
    }

    // ── LOGIN ─────────────────────────────────────────────────────────────────
    private void seConnecter(String[] parts) throws IncorrectFormat, Blank, alreadyConnected, invalidCoordinates {
        if (parts.length < 3) {
            envoyerAuClient("ERREUR: Format incorrecte");
            throw new IncorrectFormat();
        }

        String username = parts[1];
        String password = parts[2];

        if (username.isEmpty() || password.isEmpty()) {
            envoyerAuClient("ERROR: vous devez entrer le mot de passe et le nom");
            throw new Blank();
        }

        if (sManager.isOnline(username)) {
            envoyerAuClient("Connexion impossible: Vous etes deja connectes dans un autre appareil");
            throw new alreadyConnected();
        }

        UserDAO userDAO = new UserDAO();
        User result = userDAO.login(username, password);

        if (result == null) {
            envoyerAuClient("ERROR: invalid username or password");
            throw new invalidCoordinates();
        }

        this.username = result.getUsername();
        this.role = result.getRole();
        sManager.registerClientSession(this.username, this);
        envoyerAuClient("LOGIN_OK|" + result.getId() + "|" + result.getUsername());
    }

    // ── REGISTER ──────────────────────────────────────────────────────────────
    private void Inscrire(String[] parts) throws IncorrectFormat {
        if (parts.length < 4) {
            envoyerAuClient("ERREUR: vous devez entrer un email, mot de passe et un username");
            throw new IncorrectFormat();
        }

        String user     = parts[1].trim();
        String password = parts[2].trim();
        String email    = parts[3].trim();

        if (user.length() < 5) {
            envoyerAuClient("Erreur: nom d'utilisateur tres cours (minimum 5)");
            return;
        }
        if (!password.matches(".*[@&#~!$%^*]+.*") || password.length() < 8) {
            envoyerAuClient("Erreur: mot de passe court ou sans caracteres speciaux [@&#~!$%^*]");
            return;
        }
        if (!email.contains("@")) {
            envoyerAuClient("Erreur: Email invalide");
            return;
        }

        String sql = "INSERT INTO comptes (username, email, password) VALUES (?, ?, ?)";
        try (PreparedStatement ps = DBConnection.getInstance().prepareStatement(sql)) {
            ps.setString(1, user);
            ps.setString(2, email);
            ps.setString(3, password);
            boolean ok = ps.executeUpdate() > 0;
            envoyerAuClient(ok ? "Compte cree avec succes" : "nom d'utilisateur ou email deja utilise");
        } catch (SQLException e) {
            envoyerAuClient("nom d'utilisateur ou email deja utilise");
        }
    }

    // ── LOGOUT ────────────────────────────────────────────────────────────────
    public void Deconnexion() {
        if (username != null) {
            SessionManager.removeClientSession(username);
            envoyerAuClient("LOGOUT|" + username);
            username = null;
            role = null;
        }
        try { socket.close(); } catch (Exception e) { e.printStackTrace(); }
    }

    // ── ONLINE LIST ───────────────────────────────────────────────────────────
    void avoirListeEnLigne() {
        if (username == null) {
            envoyerAuClient("ERREUR: utilisateur non authentifie");
            return;
        }
        String list = String.join(",", SessionManager.getOnlineUsers());
        envoyerAuClient("ONLINE_LIST|" + list);
    }

    // ── PRIVATE MESSAGE ───────────────────────────────────────────────────────
    private void envoyerMessagePrive(String[] parts) {
        if (username == null) {
            envoyerAuClient("ERREUR: vous devez etre connecte");
            return;
        }
        if (parts.length < 4) {
            envoyerAuClient("ERREUR: Format: PRIVATE|from|to|content");
            return;
        }
        String toUser  = parts[2];
        String content = parts[3];
        clientHandler target = SessionManager.getHandler(toUser);
        if (target != null) {
            target.envoyerAuClient("PRIVATE|" + username + "|" + toUser + "|" + content);
        } else {
            envoyerAuClient("ERREUR: " + toUser + " n'est pas en ligne");
        }
    }

    // ── ROUTER ────────────────────────────────────────────────────────────────
    private void EnvoyerRequete(String data) throws IncorrectFormat, Blank, alreadyConnected, invalidCoordinates {
        String[] parts = data.split("\\|", -1);
        switch (parts[0]) {
            case "LOGIN":      seConnecter(parts);         break;
            case "REGISTER":   Inscrire(parts);            break;
            case "LOGOUT":     Deconnexion();              break;
            case "GET_ONLINE": avoirListeEnLigne();        break;
            case "PRIVATE":    envoyerMessagePrive(parts); break;
            default:
                envoyerAuClient("ERREUR: Action non reconnue: " + parts[0]);
        }
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────
    public void envoyerAuClient(String msg) { if (going != null) going.println(msg); }
    public String getUsername()  { return username; }
    public String getRole()      { return role; }
    public boolean estConnu()    { return username != null; }
    public boolean estAdmin()    { return "ADMIN".equals(role); }

    // ── RUN ───────────────────────────────────────────────────────────────────
    public void run() {
        try {
            going  = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            coming = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;
            while ((line = coming.readLine()) != null) {
                EnvoyerRequete(line.trim());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Deconnexion();
        }
    }
}