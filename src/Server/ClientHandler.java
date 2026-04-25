package Server;
import java.io.*;
import java.net.Socket;

public class clientHandler implements Runnable {

    private final Socket socket;
    private final SessionManager sManager;
    private final AppelManager appelManager;   // ← NOUVEAU
    private PrintWriter  going;
    private BufferedReader coming;
    private String username = null;
    private String role     = null;

    // ─── Constructeur ────────────────────────────────────────────────
    public clientHandler(Socket socket, SessionManager sManager, AppelManager appelManager) {
        this.socket       = socket;
        this.sManager     = sManager;
        this.appelManager = appelManager;
    }

    // ─── Auth ─────────────────────────────────────────────────────────
    private void seConnecter(String[] parts) {
        if (parts.length < 3) { envoyerAuClient("ERREUR: format incorrect"); return; }
        String user = parts[1];
        String pass = parts[2];
        if (user.isEmpty() || pass.isEmpty()) { envoyerAuClient("ERREUR: champs vides"); return; }
        if (SessionManager.isOnline(user)) { envoyerAuClient("ERREUR: déjà connecté"); return; }

        String[] result = database.loginUser(user, pass);
        if (result == null) { envoyerAuClient("ERREUR: identifiants invalides"); return; }

        this.username = result[0];
        this.role     = result[1];
        SessionManager.registerClientSession(username, this);
        envoyerAuClient("OK: connecté en tant que " + username);
    }

    public void Deconnexion() {
        if (username != null) {
            SessionAppel session = appelManager.getAppelByUser(username);
            if (session != null) terminerAppelInterne(session);
            SessionManager.removeClientSession(username);
        }
        username = null; role = null;
        try { socket.close(); } catch (Exception e) { e.printStackTrace(); }
    }

    // ─── CALL : demande d'appel ────────────────────────────────────────
    private void traiterDemandeAppel(String[] parts) {
        if (username == null) { envoyerAuClient("ERREUR: non authentifié"); return; }
        if (parts.length < 2)  { envoyerAuClient("ERREUR: destinataire manquant"); return; }

        String targetName = parts[1].trim();

        if (targetName.equals(username)) {
            envoyerAuClient("ERREUR: impossible de s'appeler soi-même");
            return;
        }

        clientHandler target = SessionManager.getHandler(targetName);
        if (target == null) {
            envoyerAuClient("ERREUR: utilisateur hors ligne ou introuvable");
            return;
        }

        User caller   = SessionManager.getUser(username);
        User receiver  = SessionManager.getUser(targetName);

        boolean ok = appelManager.demarrerAppel(caller, receiver);
        if (!ok) {
            envoyerAuClient("ERREUR: appel impossible (vous ou l'autre utilisateur est déjà en appel)");
            return;
        }

        envoyerAuClient("CALL_RINGING|" + targetName);
        target.envoyerAuClient("INCOMING_CALL|" + username);
    }

    // ─── CALL : acceptation ───────────────────────────────────────────
    private void traiterAcceptationAppel(String[] parts) {
        if (username == null) { envoyerAuClient("ERREUR: non authentifié"); return; }

        SessionAppel session = appelManager.getAppelByUser(username);
        if (session == null) { envoyerAuClient("ERREUR: aucun appel en attente"); return; }

        boolean ok = appelManager.accepterAppel(username);
        if (!ok) { envoyerAuClient("ERREUR: impossible d'accepter l'appel"); return; }

        String appelantName = session.getAppelant().getUsername();
        clientHandler appelant = SessionManager.getHandler(appelantName);

        envoyerAuClient("CALL_STARTED|" + appelantName);
        if (appelant != null) appelant.envoyerAuClient("CALL_ACCEPTED|" + username);
    }

    // ─── CALL : refus ────────────────────────────────────────────────
    private void traiterRefusAppel(String[] parts) {
        if (username == null) { envoyerAuClient("ERREUR: non authentifié"); return; }

        SessionAppel session = appelManager.getAppelByUser(username);
        if (session == null) { envoyerAuClient("ERREUR: aucun appel en cours"); return; }

        String appelantName = session.getAppelant().getUsername();
        boolean ok = appelManager.refuserAppel(username);
        if (!ok) { envoyerAuClient("ERREUR: impossible de refuser l'appel"); return; }

        envoyerAuClient("CALL_REFUSED_SENT|" + appelantName);
        clientHandler appelant = SessionManager.getHandler(appelantName);
        if (appelant != null) appelant.envoyerAuClient("CALL_REFUSED|" + username);
    }

    // ─── CALL : fin ──────────────────────────────────────────────────
    private void traiterFinAppel(String[] parts) {
        if (username == null) { envoyerAuClient("ERREUR: non authentifié"); return; }

        SessionAppel session = appelManager.getAppelByUser(username);
        if (session == null) { envoyerAuClient("ERREUR: aucun appel actif"); return; }

        String autreUsername = session.getAppelant().getUsername().equals(username)
                ? session.getRecepteur().getUsername()
                : session.getAppelant().getUsername();

        boolean ok = appelManager.terminerAppel(username);
        if (!ok) { envoyerAuClient("ERREUR: impossible de terminer l'appel"); return; }

        envoyerAuClient("CALL_ENDED|" + autreUsername);
        clientHandler autre = SessionManager.getHandler(autreUsername);
        if (autre != null) autre.envoyerAuClient("CALL_ENDED|" + username);
    }

    // ─── Méthode interne : couper un appel lors de déconnexion ────────
    private void terminerAppelInterne(SessionAppel session) {
        String autreUsername = session.getAppelant().getUsername().equals(username)
                ? session.getRecepteur().getUsername()
                : session.getAppelant().getUsername();
        appelManager.terminerAppel(username);
        clientHandler autre = SessionManager.getHandler(autreUsername);
        if (autre != null) autre.envoyerAuClient("CALL_ENDED|" + username);
    }

    // ─── Dispatcher principal ─────────────────────────────────────────
    private void EnvoyerRequete(String data) {
        String[] parts = data.split("\\|", -1);
        switch (parts[0].toUpperCase()) {
            case "LOGIN":        seConnecter(parts);           break;
            case "LOGOUT":       Deconnexion();                break;
            case "GET_ONLINE":   avoirListeEnLigne();          break;
            case "CALL_REQUEST": traiterDemandeAppel(parts);   break;  // ← NOUVEAU
            case "CALL_ACCEPT":  traiterAcceptationAppel(parts);break;  // ← NOUVEAU
            case "CALL_REFUSE":  traiterRefusAppel(parts);     break;  // ← NOUVEAU
            case "CALL_END":     traiterFinAppel(parts);       break;  // ← NOUVEAU
            default: envoyerAuClient("ERREUR: action inconnue: " + parts[0]);
        }
    }

    void avoirListeEnLigne() {
        if (username == null) { envoyerAuClient("ERREUR: non authentifié"); return; }
        String list = String.join(",", SessionManager.getOnlineUsers());
        envoyerAuClient("ONLINE_LIST|" + list);
    }

    public void envoyerAuClient(String msg) {
        if (going != null) going.println(msg);
    }

    public String getUsername() { return username; }
    public String getRole()     { return role; }
    public boolean estConnu()  { return username != null; }

    @Override
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