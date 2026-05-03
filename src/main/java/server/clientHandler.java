package server;

import dao.compteDAO;
import dao.MessageDAO;
import server.Exceptions.*;
import dao.UserDAO;
import model.Message;
import model.User;
import model.SessionAppel;
import databases.DBConnection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.io.*;
import java.net.Socket;
import java.rmi.UnexpectedException;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class clientHandler implements Runnable {
    private final Socket socket;
    private final SessionManager sManager ;
    private final AppelManager appelManager;
    private PrintWriter going;
    private BufferedReader coming;
    private String username = null;
    private String email = null;
    private String role = null;

    public clientHandler(Socket socket, SessionManager sManager, AppelManager appelManager) {
        this.socket = socket;
        this.sManager = sManager;
        this.appelManager = appelManager;
    }

// CALL|TO
    private void traiterDemandeAppel(String[] parts) throws Exception, IncorrectFormat
    {
        if(username == null) {envoyerAuClient("[CLIENT HANDLER] NON AUTHENTIFIE"); throw new Exception();}
        if(parts.length < 2) {envoyerAuClient("[CLIENT HANDLER] Format Incorrecte"); throw new IncorrectFormat();}

        clientHandler recepteur = SessionManager.getHandler(parts[1]);

        if(recepteur == null)
        {
            envoyerAuClient("Utilisateur hors ligne"); throw new Exception();
        }
        if(recepteur.username.equals(username))
        {
            envoyerAuClient("Impossible!"); throw new Exception();
        }

        //user at a high level //TODO NEEDS FIX IDK WHT DOES USER1 USER 2 means usually we should use client handler at this point
        User user1 = new User(0, username,"Online",false);
        User user2 = new User(1, recepteur.username, "Online",false);
        boolean check = appelManager.demarrerAppel(user1,user2);
        if(!check)
        {
            envoyerAuClient("[CLIENT HANDLER] l'utilisateur que vous essayer d'appeller est déja en appel en cours!");
            throw new Exception();
        }
        envoyerAuClient("Ringing...");
        String videoFlag = parts.length > 2 ? "|" + parts[2] : "";
        recepteur.envoyerAuClient("CALL_REQUEST|" + username + videoFlag);
    }

    private void traiterAcceptationAppel(String[] parts) throws Exception, UnexpectedBehavior
    {
        if(username == null) {envoyerAuClient("[CLIENT HANDLER ERROR] Veuillez s'authentifier"); throw new Exception(); }
        if(parts.length < 2) {envoyerAuClient("[CLIENT HANDLER] Format attendu: CALL_ACCEPT|port"); throw new IncorrectFormat(); }
        SessionAppel session = appelManager.getAppelByUser(username);
        if(session == null) {envoyerAuClient("[CLIENT HANDLER] Aucun appel entrant"); throw new Exception();}

        int portUdp;
        try {
            portUdp = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            envoyerAuClient("[CLIENT HANDLER] Port UDP invalide");
            throw new IncorrectFormat();
        }

        boolean ok = appelManager.accepterAppel(username);

        if(!ok)
        {
            envoyerAuClient("[CLIENT HANDLER] Impossible d'accepter l'appel");
            throw new Exception();
        }

        clientHandler starter = SessionManager.getHandler(session.getAppelant().getUsername());

        String recepteurIp = getIpAddress();
        String callerIp = starter != null? starter.getIpAddress() : "127.0.0.1";
        if(starter == null)  { envoyerAuClient(" [UNEXPECTED BEHAVIOR] Debut Appel avec vous meme (loopback)  " + username+ "sur " +callerIp); throw new UnexpectedBehavior(); }
        session.setInfosAudioRecepteur(recepteurIp, portUdp);
        envoyerAuClient("WAIT_CALLER_READY");
        // Notifier l'appelant que l'appel a été accepté avec ip/port UDP du recepteur
        starter.envoyerAuClient("CALL_ACCEPTED|" + username + "|" + recepteurIp + "|" + portUdp);
    }

    private void traiterCallerReady(String[] parts) throws Exception
    {
        if(username == null) {envoyerAuClient("[CLIENT HANDLER ERROR] Veuillez s'authentifier"); throw new Exception(); }
        if(parts.length < 2) {envoyerAuClient("[CLIENT HANDLER] Format attendu: CALL_READY|port"); throw new IncorrectFormat(); }
        SessionAppel session = appelManager.getAppelByUser(username);
        if(session == null) {envoyerAuClient("[CLIENT HANDLER] Aucun appel en attente"); throw new Exception();}
        if(!session.getAppelant().getUsername().equals(username)) {
            envoyerAuClient("[CLIENT HANDLER] Seul l'appelant peut envoyer CALL_READY");
            throw new Exception();
        }

        int portUdp;
        try {
            portUdp = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            envoyerAuClient("[CLIENT HANDLER] Port UDP invalide");
            throw new IncorrectFormat();
        }

        clientHandler recepteur = SessionManager.getHandler(session.getRecepteur().getUsername());
        if(recepteur == null) {
            envoyerAuClient("[CLIENT HANDLER] Recepteur hors ligne");
            throw new Exception();
        }
        if(session.getIpRecepteur() == null || session.getPortRecepteur() <= 0) {
            envoyerAuClient("[CLIENT HANDLER] Le recepteur n'a pas encore fourni son port UDP");
            throw new Exception();
        }

        String ipAppelant = getIpAddress();
        session.setInfosAudioAppelant(ipAppelant, portUdp);

        // Signal final de démarrage audio vers les deux clients
        envoyerAuClient("START_AUDIO|" + session.getIpRecepteur() + "|" + session.getPortRecepteur());
        recepteur.envoyerAuClient("START_AUDIO|" + session.getIpAppelant() + "|" + session.getPortAppelant());
    }

    //TODO traiter refus- traiter terminer

    private void traiterRefusAppel(String[] parts) throws Exception
    {
        if(username == null) {envoyerAuClient("[CLIENT HANDLER ERROR] Veuillez s'authentifier"); throw new Exception(); }
        SessionAppel session = appelManager.getAppelByUser(username);
        if(session == null) {envoyerAuClient("[CLIENT HANDLER] Aucun appel entrant"); throw new Exception();}

        boolean ok = appelManager.refuserAppel(username);

        if(!ok)
        {
            envoyerAuClient("[CLIENT HANDLER] Impossible d'accepter l'appel");
            throw new Exception();
        }

        envoyerAuClient("Appel refusé avec succés");

        clientHandler starter = SessionManager.getHandler(session.getAppelant().getUsername());

        if(starter != null) starter.envoyerAuClient("CALL_REFUSED|" + username);
    }


    private void traiterFinAppel(String[] parts) throws Exception
    {
        //we dont know who called so we have to figure it out from session
        if(username == null) {envoyerAuClient("[CLIENT HANDLER ERROR] Veuillez s'authentifier"); throw new Exception(); }
        SessionAppel session = appelManager.getAppelByUser(username);
        if(session == null)
        {
            envoyerAuClient("Aucune appel actif");
            throw new Exception();
        }
        String autre = session.getAppelant().getUsername().equals(username )? session.getRecepteur().getUsername(): session.getAppelant().getUsername();
        clientHandler other = SessionManager.getHandler(autre);
        boolean ok = appelManager.terminerAppel(username);
        if(!ok) envoyerAuClient("impossible de terminer");
        if(other != null) other.envoyerAuClient("CALL_ENDED|" + username);
        envoyerAuClient("CALL_ENDED|" + autre);

    }


    private void seConnecter(String[] parts) throws IncorrectFormat, Blank, alreadyConnected, invalidCoordinates {
        if (parts.length < 3) {
            envoyerAuClient("ERROR | Format incorrect");
            throw new IncorrectFormat();
        }

        String username = parts[1];
        String password = parts[2];

        if (username.isEmpty() || password.isEmpty()) {
            envoyerAuClient("ERROR: vous devez entrer le mot de passe et le nom");
            throw new Blank();
        }

        if (SessionManager.isOnline(username)) {
            clientHandler oldHandler = SessionManager.getHandler(username);
            if (oldHandler != null) {
                oldHandler.envoyerAuClient("INFO | Connexion depuis un autre appareil. Déconnexion...");
                oldHandler.Deconnexion();
            }
        }

        UserDAO userDAO = new UserDAO();
        User result = userDAO.login(username, password); //expecting from maryam to give me an object of type user

        if (result == null) {
            envoyerAuClient("ERROR | Mot de passe ou email incorrecte [ERREUR BASE DE DONNEES]");
            throw new invalidCoordinates();
        }

        this.username = result.getUsername(); //else instanciate our client handler
        this.email = result.getEmail();
        this.role = result.getRole();

        // We key SessionManager only by username. The email key was unused for
        // routing and only inflated broadcasts (each client received them twice).
        SessionManager.registerClientSession(this.username, this);

        // Mark user as online in DB and tell everybody else
        try { userDAO.updateStatus(result.getId(), "online"); } catch (Exception ignored) {}
        SessionManager.broadcastStatus(this.username, "online");

        envoyerAuClient("Connexion réussite :" + result.getUsername());

        // Snapshot of who is currently online, sent only to this freshly-logged-in
        // client. Without it the sidebar dots reflect whatever was in the DB at
        // load time, which can be stale (e.g. a peer who crashed before
        // Deconnexion ran). Subsequent USER_STATUS broadcasts keep it fresh.
        for (String peer : SessionManager.getOnlineUsers()) {
            if (peer == null || peer.equalsIgnoreCase(this.username)) continue;
            envoyerAuClient("USER_STATUS|" + peer + "|online");
        }

        // Push pending inbox messages right after login success.
        // This guarantees visibility even if live delivery was missed.
        pousserInboxAuLogin(result);
    }

    private void pousserInboxAuLogin(User currentUser) {
        if (currentUser == null) return;
        try {
            MessageDAO messageDAO = new MessageDAO();
            UserDAO userDAO = new UserDAO();
            // Only deliver messages that haven't been delivered yet. Otherwise
            // every login replays the entire history.
            List<Message> unread = messageDAO.getPendingDelivery(currentUser.getId());
            Set<Integer> sendersToNotify = new HashSet<>();
            for (Message m : unread) {
                User sender = userDAO.getById(m.getSenderId());
                if (sender == null) continue;
                String content = m.getContent() == null ? "" : m.getContent();
                // Replay with the original mid so edit/delete still match
                // after the receiver was offline. If for some reason the row
                // has no mid (legacy data), fall back to the DB id.
                long replayMid = m.getClientMid() > 0 ? m.getClientMid() : m.getId();
                envoyerAuClient("PRIVATE|" + sender.getUsername() + "|" + currentUser.getUsername()
                        + "|MID:" + replayMid + "|" + content);
                // Mark as delivered so we don't replay forever at each login.
                messageDAO.updateStatus(m.getId(), "delivered");
                sendersToNotify.add(m.getSenderId());
            }
            // Tell each currently-connected sender that messages they sent to me
            // (which were only "sent" because I was offline) are now delivered.
            for (Integer sid : sendersToNotify) {
                User sender = userDAO.getById(sid);
                if (sender == null) continue;
                clientHandler senderHandler = SessionManager.getHandler(sender.getUsername());
                if (senderHandler != null) {
                    senderHandler.envoyerAuClient("CONV_DELIVERED|" + currentUser.getUsername());
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur pousserInboxAuLogin: " + e.getMessage());
        }
    }


    private void Inscrire(String[] parts) throws IncorrectFormat {
        if (parts.length < 4) {
            envoyerAuClient("[CLIENT HANDLER] FORMAT INCORRECTE");
            throw new IncorrectFormat();
        }

        String user     = parts[1].trim(); //supprimer les espaces du debut et de la fin
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




        boolean ok = dao.compteDAO.register(user, password, email);
        envoyerAuClient(ok ? "Compte cree avec succes" : "nom d'utilisateur ou email deja utilise");
    }

//terminer appel interne to only tell the current user but not the other user

    private void terminerAppelInterne(SessionAppel session)
    {

        String otherUser = session.getAppelant().getUsername().equals(username)?  session.getRecepteur().getUsername() : session.getAppelant().getUsername();
        clientHandler otherClient = SessionManager.getHandler(otherUser);
        appelManager.terminerAppel(username);
        if(otherClient != null)
        otherClient.envoyerAuClient("terminé");
    }



    public synchronized void Deconnexion() {
        // Idempotent: run() always calls this from its finally{} on socket close,
        // and seConnecter calls it on the previous handler when a same-user login
        // takes over. Without the guard, the second invocation removes the new
        // handler's SessionManager entry and flips the DB row back to offline,
        // breaking live PRIVATE / CALL routing until the user re-logs in.
        if (username == null) {
            try { if (socket != null && !socket.isClosed()) socket.close(); } catch (Exception ignored) {}
            return;
        }

        // End any ongoing call first (uses this.username).
        try {
            SessionAppel s = appelManager.getAppelByUser(username);
            if (s != null) terminerAppelInterne(s);
        } catch (Exception ignored) {}

        String me = username;
        String myEmail = email;
        username = null;
        email = null;

        // Only release ownership if SessionManager still points to *us*.
        // A newer login for the same username may already have replaced our mapping.
        boolean stillOwn = SessionManager.getHandler(me) == this;
        if (stillOwn) {
            SessionManager.removeClientSession(me);
            if (myEmail != null && SessionManager.getHandler(myEmail) == this) {
                SessionManager.removeClientSession(myEmail);
            }
            try {
                User u = new UserDAO().getByUsername(me);
                if (u != null) new UserDAO().updateStatus(u.getId(), "offline");
            } catch (Exception ignored) {}
            SessionManager.broadcastStatus(me, "offline");
        }

        try { envoyerAuClient("Deconnecté..."); } catch (Exception ignored) {}
        try { socket.close(); } catch (Exception ignored) {}
    }


    void avoirListeEnLigne() {
        if (username == null) {
            envoyerAuClient("ERROR | utilisateur non authentifié");
            return;
        }
        String list = String.join(",", SessionManager.getOnlineUsers());
        envoyerAuClient("Currently online: " + list);
    }


    private void envoyerMessagePrive(String[] parts) throws IncorrectFormat
        {
            if(parts.length < 4) { envoyerAuClient("[CLIENT HANDLER] format incorrecte"); throw new IncorrectFormat();}
            if(username != null)
            {

                String targetName = parts[2].trim();
                String fourth = parts[3];

                // Optional MID:<id>| prefix used by the client to track delivery status.
                // Strip it before persisting/forwarding so receivers and DB never see it.
                String mid = null;
                String content = fourth;
                if (fourth.startsWith("MID:")) {
                    int sep = fourth.indexOf('|');
                    if (sep > 0) {
                        mid = fourth.substring(4, sep);
                        content = fourth.substring(sep + 1);
                    }
                }

                // Persist the message in DB so it can be loaded later
                Message savedMessage = null;
                try {
                    UserDAO udao = new UserDAO();
                    User sender = udao.getByUsername(username);
                    User receiver = udao.getByUsername(targetName);
                    if (sender != null && receiver != null) {
                        Message m = new Message(sender.getId(), receiver.getId(), content);
                        // Stamp the client-generated mid so later EDIT/DELETE
                        // requests can locate this row without trusting clients
                        // with a raw DB id.
                        if (mid != null) {
                            try { m.setClientMid(Long.parseLong(mid)); } catch (NumberFormatException ignored) {}
                        }
                        if (new MessageDAO().saveMessage(m)) {
                            savedMessage = m;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Persist message failed: " + e.getMessage());
                }

                clientHandler target = SessionManager.getHandler(targetName);
                if(target != null)
                {
                    System.out.println("[SERVER DEBUG] Routing PRIVATE from " + username + " to " + targetName);
                    // Forward the sender's mid through to the receiver so both
                    // sides can refer to the same message when the sender later
                    // edits or deletes it. Without this the receiver would
                    // store the message under their own clock and MSG_EDITED /
                    // MSG_DELETED would never match.
                    String forwardedTail = (mid != null) ? ("MID:" + mid + "|" + content) : content;
                    target.envoyerAuClient("PRIVATE|" + username + "|" + targetName + "|" + forwardedTail);
                    // Mark the row delivered now that the recipient has it live.
                    // Otherwise pousserInboxAuLogin replays it on their next login.
                    if (savedMessage != null && savedMessage.getId() > 0) {
                        new MessageDAO().updateStatus(savedMessage.getId(), "delivered");
                    }
                    if (mid != null) {
                        envoyerAuClient("MSG_STATUS|" + targetName + "|" + mid + "|delivered");
                    }
                }
                else{
                    System.err.println("[SERVER DEBUG] Target " + targetName + " not online; message stored for later delivery");
                    if (mid != null) {
                        envoyerAuClient("MSG_STATUS|" + targetName + "|" + mid + "|sent");
                    }
                }

            }
        }

    // Return saved history between this user and another user.
    // Format request:  HISTORY|otherUsername
    // Format reply:    HISTORY|otherUsername|sender|content   (one line per message)
    //                  HISTORY_END|otherUsername
    private void traiterHistorique(String[] parts) throws Exception {
        if (username == null) { envoyerAuClient("ERROR|Non authentifié"); return; }
        if (parts.length < 2) { envoyerAuClient("ERROR|Format HISTORY|user"); return; }

        String otherName = parts[1].trim();
        UserDAO udao = new UserDAO();
        User me = udao.getByUsername(username);
        User other = udao.getByUsername(otherName);
        if (me == null || other == null) {
            envoyerAuClient("HISTORY_END|" + otherName);
            return;
        }
        List<Message> msgs = new MessageDAO().getConversation(me.getId(), other.getId());
        for (Message m : msgs) {
            String sender = (m.getSenderId() == me.getId()) ? username : otherName;
            // We replace any embedded '|' to keep the line parser simple
            String safe = m.getContent() == null ? "" : m.getContent().replace("\n", " ");
            envoyerAuClient("HISTORY|" + otherName + "|" + sender + "|" + safe);
        }
        envoyerAuClient("HISTORY_END|" + otherName);
    }

    private void EnvoyerRequete(String data) throws IncorrectFormat, Blank, alreadyConnected, invalidCoordinates,Exception {
        // EDIT / DELETE keep their own splits because their payloads
        // contain the new content and we don't want the limit-4 split
        // above to swallow embedded '|' characters.
        String head = data.split("\\|", 2)[0].trim();
        if ("EDIT".equals(head))   { traiterEdition(data);   return; }
        if ("DELETE".equals(head)) { traiterSuppression(data); return; }

        String[] parts = data.split("\\|", 4);
        switch (parts[0].trim()) {
            case "LOGIN":      seConnecter(parts);         break;
            case "REGISTER":   Inscrire(parts);            break;
            case "LOGOUT":     Deconnexion();              break;
            case "GET_ONLINE": avoirListeEnLigne();        break;
            case "PRIVATE":    envoyerMessagePrive(parts); break;
            case "CALL_REQUEST": traiterDemandeAppel(parts);   break;
           case "CALL_ACCEPT":  traiterAcceptationAppel(parts);break;
            case "CALL_READY":   traiterCallerReady(parts);     break;
            case "CALL_REFUSE":  traiterRefusAppel(parts);     break;
           case "CALL_END":     traiterFinAppel(parts);       break;
            case "HISTORY":      traiterHistorique(parts);     break;
            default:
                envoyerAuClient("ERROR|Action non reconnue: " + parts[0]);
        }
    }

    // ── Edit / soft-delete a previously sent private message ───────
    // Wire format from client:
    //   EDIT|<self>|<peer>|MID:<mid>|<newContent>
    //   DELETE|<self>|<peer>|MID:<mid>
    // Outgoing notification to peer:
    //   MSG_EDITED|<self>|<mid>|<newContent>
    //   MSG_DELETED|<self>|<mid>
    private void traiterEdition(String raw) {
        if (username == null) { envoyerAuClient("ERROR|Non authentifié"); return; }
        String[] p = raw.split("\\|", 5);
        if (p.length < 5)  { envoyerAuClient("ERROR|Format EDIT|self|peer|MID:x|content"); return; }
        String peer = p[2].trim();
        long mid    = parseMid(p[3]);
        if (mid <= 0) { envoyerAuClient("ERROR|MID invalide"); return; }
        String newContent = p[4];

        try {
            UserDAO udao = new UserDAO();
            User me = udao.getByUsername(username);
            if (me == null) return;
            boolean ok = new MessageDAO().editMessage(mid, me.getId(), newContent);
            if (!ok) { envoyerAuClient("ERROR|Edit refusé"); return; }
            clientHandler peerHandler = SessionManager.getHandler(peer);
            if (peerHandler != null) {
                peerHandler.envoyerAuClient("MSG_EDITED|" + username + "|" + mid + "|" + newContent);
            }
        } catch (Exception e) {
            System.err.println("Erreur traiterEdition: " + e.getMessage());
        }
    }

    private void traiterSuppression(String raw) {
        if (username == null) { envoyerAuClient("ERROR|Non authentifié"); return; }
        String[] p = raw.split("\\|", 4);
        if (p.length < 4) { envoyerAuClient("ERROR|Format DELETE|self|peer|MID:x"); return; }
        String peer = p[2].trim();
        long mid    = parseMid(p[3]);
        if (mid <= 0) { envoyerAuClient("ERROR|MID invalide"); return; }

        try {
            UserDAO udao = new UserDAO();
            User me = udao.getByUsername(username);
            if (me == null) return;
            boolean ok = new MessageDAO().softDeleteMessage(mid, me.getId());
            if (!ok) { envoyerAuClient("ERROR|Delete refusé"); return; }
            clientHandler peerHandler = SessionManager.getHandler(peer);
            if (peerHandler != null) {
                peerHandler.envoyerAuClient("MSG_DELETED|" + username + "|" + mid);
            }
        } catch (Exception e) {
            System.err.println("Erreur traiterSuppression: " + e.getMessage());
        }
    }

    private long parseMid(String token) {
        if (token == null) return -1;
        String t = token.trim();
        if (t.startsWith("MID:")) t = t.substring(4);
        try { return Long.parseLong(t); } catch (NumberFormatException e) { return -1; }
    }




    public void envoyerAuClient(String msg) { if (going != null) going.println(msg); }
    public String getUsername()  { return username; }
    public String getRole()      { return role; }
    public boolean estConnu()    { return username != null; }
    public boolean estAdmin()    { return "ADMIN".equals(role); }
    public String getIpAddress() { return socket.getInetAddress().getHostAddress(); }


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