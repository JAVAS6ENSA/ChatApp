package server;

import dao.compteDAO;
import dao.MessageDAO;
import server.Exceptions.*;
import dao.UserDAO;
import model.Message;
import model.User;
import model.SessionAppel;
import databases.DBConnection;
import dao.GroupDAO;
import model.Group;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

        envoyerAuClient("START_AUDIO|" + session.getIpRecepteur() + "|" + session.getPortRecepteur());
        recepteur.envoyerAuClient("START_AUDIO|" + session.getIpAppelant() + "|" + session.getPortAppelant());
    }


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
        User result = userDAO.login(username, password);

        if (result == null) {
            envoyerAuClient("ERROR | Mot de passe ou email incorrecte [ERREUR BASE DE DONNEES]");
            throw new invalidCoordinates();
        }

        this.username = result.getUsername();
        this.email = result.getEmail();
        this.role = result.getRole();


        SessionManager.registerClientSession(this.username, this);

        try { userDAO.updateStatus(result.getId(), "online"); } catch (Exception ignored) {}
        SessionManager.broadcastStatus(this.username, "online");

        envoyerAuClient("Connexion réussite :" + result.getUsername());


        for (String peer : SessionManager.getOnlineUsers()) {
            if (peer == null || peer.equalsIgnoreCase(this.username)) continue;
            envoyerAuClient("USER_STATUS|" + peer + "|online");
        }


        pousserInboxAuLogin(result);
    }

    private void pousserInboxAuLogin(User currentUser) {
        if (currentUser == null) return;
        try {
            MessageDAO messageDAO = new MessageDAO();
            UserDAO userDAO = new UserDAO();

            List<Message> unread = messageDAO.getPendingDelivery(currentUser.getId());
            Set<Integer> sendersToNotify = new HashSet<>();
            for (Message m : unread) {
                User sender = userDAO.getById(m.getSenderId());
                if (sender == null) continue;
                String content = m.getContent() == null ? "" : m.getContent();
                envoyerAuClient("PRIVATE|" + sender.getUsername() + "|" + currentUser.getUsername() + "|" + content);
                messageDAO.updateStatus(m.getId(), "delivered");
                sendersToNotify.add(m.getSenderId());
            }

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



        boolean ok = dao.compteDAO.register(user, password, email);
        envoyerAuClient(ok ? "Compte cree avec succes" : "nom d'utilisateur ou email deja utilise");
    }


    private void terminerAppelInterne(SessionAppel session)
    {

        String otherUser = session.getAppelant().getUsername().equals(username)?  session.getRecepteur().getUsername() : session.getAppelant().getUsername();
        clientHandler otherClient = SessionManager.getHandler(otherUser);
        appelManager.terminerAppel(username);
        if(otherClient != null)
        otherClient.envoyerAuClient("terminé");
    }



    public synchronized void Deconnexion() {

        if (username == null) {
            try { if (socket != null && !socket.isClosed()) socket.close(); } catch (Exception ignored) {}
            return;
        }

        try {
            SessionAppel s = appelManager.getAppelByUser(username);
            if (s != null) terminerAppelInterne(s);
        } catch (Exception ignored) {}

        String me = username;
        String myEmail = email;
        username = null;
        email = null;


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

                String mid = null;
                String content = fourth;
                if (fourth.startsWith("MID:")) {
                    int sep = fourth.indexOf('|');
                    if (sep > 0) {
                        mid = fourth.substring(4, sep);
                        content = fourth.substring(sep + 1);
                    }
                }

                Message savedMessage = null;
                try {
                    UserDAO udao = new UserDAO();
                    User sender = udao.getByUsername(username);
                    User receiver = udao.getByUsername(targetName);
                    if (sender != null && receiver != null) {
                        Message m = new Message(sender.getId(), receiver.getId(), content);
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
                    target.envoyerAuClient("PRIVATE|" + username + "|" + targetName + "|" + content);

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
            String safe = m.getContent() == null ? "" : m.getContent().replace("\n", " ");
            envoyerAuClient("HISTORY|" + otherName + "|" + sender + "|" + safe);
        }
        envoyerAuClient("HISTORY_END|" + otherName);
    }

    private void EnvoyerRequete(String data) throws IncorrectFormat, Blank, alreadyConnected, invalidCoordinates,Exception {
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
            case "GROUP_CREATE": traiterCreerGroupe(parts);    break;
            case "GROUP_MSG":    traiterMessageGroupe(parts);  break;
            case "GROUP_ADD":    traiterAjouterMembre(parts);  break;
            case "GROUP_REMOVE": traiterRetirerMembre(parts);  break;
            case "GROUP_LIST":   traiterListeGroupes();        break;
            case "GROUP_HISTORY": traiterHistoriqueGroupe(parts); break;
            case "GROUP_AUDIO":  traiterAudioGroupe(parts);    break;
            case "GROUP_FILE":   traiterFichierGroupe(parts);  break;
            default:
                envoyerAuClient("ERROR|Action non reconnue: " + parts[0]);
        }
    }





    private void traiterCreerGroupe(String[] parts) throws Exception {
        if (username == null) return;
        if (parts.length < 3) return;
        String name = parts[1];
        String[] membersArr = parts[2].split(",");
        
        UserDAO udao = new UserDAO();
        GroupDAO gdao = new GroupDAO();
        User creator = udao.getByUsername(username);
        if (creator == null) return;

        int groupId = gdao.createGroup(name, creator.getId());
        if (groupId > 0) {
            gdao.addMember(groupId, creator.getId());
            for (String mName : membersArr) {
                User m = udao.getByUsername(mName.trim());
                if (m != null) gdao.addMember(groupId, m.getId());
            }
            envoyerAuClient("GROUP_CREATED|" + groupId + "|" + name);
            SessionManager.broadcastToGroup(groupId, "GROUP_NOTIF|" + groupId + "|New group created: " + name, username);
        }
    }

    private void traiterMessageGroupe(String[] parts) throws Exception {
        if (username == null) return;
        if (parts.length < 3) return;
        int groupId = Integer.parseInt(parts[1]);
        String content = parts[2];

        UserDAO udao = new UserDAO();
        User sender = udao.getByUsername(username);
        if (sender == null) return;

        Message msg = new Message(sender.getId(), 0, content);
        msg.setGroupId(groupId);
        if (new MessageDAO().saveMessage(msg)) {
            SessionManager.broadcastToGroup(groupId, "GROUP_MSG|" + groupId + "|" + username + "|" + content, username);
        }
    }

    private void traiterAjouterMembre(String[] parts) throws Exception {
        if (username == null) return;
        if (parts.length < 3) return;
        int groupId = Integer.parseInt(parts[1]);
        String targetName = parts[2];

        UserDAO udao = new UserDAO();
        GroupDAO gdao = new GroupDAO();
        User target = udao.getByUsername(targetName);
        if (target != null) {
            gdao.addMember(groupId, target.getId());
            SessionManager.broadcastToGroup(groupId, "GROUP_NOTIF|" + groupId + "|" + targetName + " joined the group", username);
        }
    }

    private void traiterRetirerMembre(String[] parts) throws Exception {
        if (username == null) return;
        if (parts.length < 3) return;
        int groupId = Integer.parseInt(parts[1]);
        String targetName = parts[2];

        UserDAO udao = new UserDAO();
        GroupDAO gdao = new GroupDAO();
        User target = udao.getByUsername(targetName);
        if (target != null) {
            gdao.removeMember(groupId, target.getId());
            SessionManager.broadcastToGroup(groupId, "GROUP_NOTIF|" + groupId + "|" + targetName + " left the group", username);
            clientHandler targetHandler = SessionManager.getHandler(targetName);
            if (targetHandler != null) targetHandler.envoyerAuClient("GROUP_REMOVED|" + groupId);
        }
    }

    private void traiterListeGroupes() throws Exception {
        if (username == null) return;
        UserDAO udao = new UserDAO();
        GroupDAO gdao = new GroupDAO();
        User me = udao.getByUsername(username);
        if (me == null) return;

        List<Group> groups = gdao.getUserGroups(me.getId());
        String list = groups.stream()
                .map(g -> g.getId() + ":" + g.getName())
                .collect(Collectors.joining(","));
        envoyerAuClient("GROUP_LIST|" + list);
    }

    private void traiterHistoriqueGroupe(String[] parts) throws Exception {
        if (username == null) return;
        if (parts.length < 2) return;
        int groupId = Integer.parseInt(parts[1]);

        List<Message> msgs = new MessageDAO().getGroupMessages(groupId);
        UserDAO udao = new UserDAO();
        for (Message m : msgs) {
            User s = udao.getById(m.getSenderId());
            String sName = (s != null) ? s.getUsername() : "Unknown";
            envoyerAuClient("GROUP_HISTORY|" + groupId + "|" + sName + "|" + m.getContent());
        }
        envoyerAuClient("GROUP_HISTORY_END|" + groupId);
    }

    private void traiterAudioGroupe(String[] parts) throws Exception {
        if (username == null) return;
        if (parts.length < 3) return;
        int groupId = Integer.parseInt(parts[1]);
        String base64 = parts[2];
        
        SessionManager.broadcastToGroup(groupId, "GROUP_AUDIO|" + groupId + "|" + username + "|" + base64, username);
    }

    private void traiterFichierGroupe(String[] parts) throws Exception {
        if (username == null) return;
        if (parts.length < 4) return;
        int groupId = Integer.parseInt(parts[1]);
        String fileName = parts[2];
        String base64 = parts[3];

        SessionManager.broadcastToGroup(groupId, "GROUP_FILE|" + groupId + "|" + username + "|" + fileName + "|" + base64, username);
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