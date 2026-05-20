package server;

import dao.BlockDAO;
import dao.CallDAO;
import dao.GroupDAO;
import dao.MessageDAO;
import dao.UserDAO;
import databases.DBConnection;
import model.Group;
import model.Message;
import model.SessionAppel;
import model.User;
import server.Exceptions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;

public class clientHandler implements Runnable {
    private final Socket socket;
    private final SessionManager sManager;
    private final AppelManager appelManager;
    private final GroupCallManager groupCallManager;
    private PrintWriter going;
    private BufferedReader coming;
    private String username = null;
    private String phone = null;
    private String role = null;
    private long start ;
    private int currentCallRowId = -1;

    public clientHandler(Socket socket,
                         SessionManager sManager,
                         AppelManager appelManager,
                         GroupCallManager groupCallManager) {
        this.socket = socket;
        this.sManager = sManager;
        this.appelManager = appelManager;
        this.groupCallManager = groupCallManager;
    }

    public clientHandler(Socket socket, SessionManager sManager, AppelManager appelManager) {
        this(socket, sManager, appelManager, new GroupCallManager());
    }

    // CALL_REQUEST|TO[|VIDEO] ou video c pq j ai mis [| c est optionel
    private void traiterDemandeAppel(String[] parts) throws Exception, IncorrectFormat {
        if (username == null) { envoyerAuClient("ERROR|Non authentifié"); throw new Exception(); }
        if (parts.length < 2) { envoyerAuClient("ERROR|Format incorrect"); throw new IncorrectFormat(); }

        clientHandler recepteur = SessionManager.getHandler(parts[1]);
        if (recepteur == null) {
            envoyerAuClient("CALL_UNAVAILABLE|" + parts[1]);
            return;
        }
        if (recepteur.username.equals(username)) {
            envoyerAuClient("CALL_ERROR|Impossible d'appeler soi-même");
            return;
        }
        String type = parts.length > 2 && "VIDEO".equalsIgnoreCase(parts[2]) ? "VIDEO" : "AUDIO";

   //     User user1 = new User(0, username, "online", false);
    //    User user2 = new User(1, recepteur.username, "online", false);
      //  try {

//        } catch (AlreadyOngoingCall ex) {
//            envoyerAuClient("CALL_BUSY|" + recepteur.username);
//            return;
//        }


        //TODO REFACTOR THIS PART AS IT IS REDUNDANT

        try {
            UserDAO udao = new UserDAO();
            User me = udao.getByUsername(username);
            User them = udao.getByUsername(recepteur.username);
            if (me != null && them != null) {
                boolean ok = appelManager.demarrerAppel(me, them);
                if (!ok) {
                    envoyerAuClient("CALL_BUSY|" + recepteur.username);
                    return;
                }
                currentCallRowId = new CallDAO().startCall(me.getId(), them.getId(), type.toLowerCase());
            }

        } catch (Exception ignored) {}

        envoyerAuClient("CALL_RINGING|" + recepteur.username + "|" + type);
        recepteur.envoyerAuClient("CALL_REQUEST|" + username + "|" + type);
    }

    // CALL_ACCEPT|port[|lan Ip
    private void traiterAcceptationAppel(String[] parts) throws Exception, UnexpectedBehavior {
        if (username == null) { envoyerAuClient("ERROR|Non authentifié"); throw new Exception(); }
        if (parts.length < 2) { envoyerAuClient("ERROR|Format CALL_ACCEPT|port"); throw new IncorrectFormat(); }
        SessionAppel session = appelManager.getAppelByUser(username);
        if (session == null) { envoyerAuClient("CALL_ERROR|Aucun appel entrant"); return; }

        int portUdp;
        try { portUdp = Integer.parseInt(parts[1].trim()); }
        catch (NumberFormatException e) { envoyerAuClient("ERROR|Port invalide"); throw new IncorrectFormat(); }

        boolean ok = appelManager.accepterAppel(username);
        if (!ok) { envoyerAuClient("CALL_ERROR|Impossible d'accepter"); return; }

        clientHandler starter = SessionManager.getHandler(session.getAppelant().getUsername());

        //bug avec aya j'ai appelé puis elle racroche et j ai enors appel en cours
        if (starter == null) {

            appelManager.nettoyerSession(session);
            envoyerAuClient("CALL_CANCELLED|" + session.getAppelant().getUsername());
            return;
        }

        String recepteurIp = getIpAddress();
        session.setInfosAudioRecepteur(recepteurIp, portUdp);
        if (parts.length >= 3 && parts[2] != null && !parts[2].trim().isEmpty())
            session.setLanIpRecepteur(parts[2].trim());
        envoyerAuClient("WAIT_CALLER_READY");
        starter.envoyerAuClient("CALL_ACCEPTED|" + username + "|" + recepteurIp + "|" + portUdp);
    }

    private void traiterCallerReady(String[] parts) throws Exception {
        if (username == null) { envoyerAuClient("ERROR|Non authentifié"); throw new Exception(); }
        if (parts.length < 2) { envoyerAuClient("ERROR|Format CALL_READY|port"); throw new IncorrectFormat(); }
        SessionAppel session = appelManager.getAppelByUser(username);
        if (session == null) { envoyerAuClient("CALL_ERROR|Aucun appel en attente"); return; }
        if (!session.getAppelant().getUsername().equals(username)) {
            envoyerAuClient("CALL_ERROR|Seul l'appelant peut envoyer CALL_READY");
            return;
        }

        int portUdp;
        try { portUdp = Integer.parseInt(parts[1].trim()); }
        catch (NumberFormatException e) { envoyerAuClient("ERROR|Port invalide"); throw new IncorrectFormat(); }

        clientHandler recepteur = SessionManager.getHandler(session.getRecepteur().getUsername());
        if (recepteur == null) {
            envoyerAuClient("CALL_ERROR|Recepteur hors ligne");
            appelManager.nettoyerSession(session);
            return;
        }
        if (session.getIpRecepteur() == null || session.getPortRecepteur() <= 0) {
            envoyerAuClient("CALL_ERROR|Le recepteur n'a pas encore fourni son port");
            return;
        }

        String ipAppelant = getIpAddress();
        session.setInfosAudioAppelant(ipAppelant, portUdp);
        if (parts.length >= 3 && parts[2] != null && !parts[2].trim().isEmpty())
            session.setLanIpAppelant(parts[2].trim());

        //on a toujours cette condition verifié maintenant car j ai besoin d implementer le turn server


        start = sessionStartMs(session);
        envoyerAuClient("START_AUDIO|" + session.audioIpForAppelant() + "|" + session.getPortRecepteur()
                + "|" + session.getRecepteur().getUsername());
        //ca retourne l'ip de recepteur, username et le port
        recepteur.envoyerAuClient("START_AUDIO|" + session.audioIpForRecepteur() + "|" + session.getPortAppelant()
                + "|" + session.getAppelant().getUsername());

    }


    private void traiterRefusAppel(String[] parts) throws Exception {
        if (username == null) { envoyerAuClient("ERROR|Non authentifié"); throw new Exception(); }
        SessionAppel session = appelManager.getAppelByUser(username);
        if (session == null) { envoyerAuClient("CALL_ERROR|Aucun appel entrant"); return; }

        boolean ok = appelManager.refuserAppel(username);
        if (!ok) { envoyerAuClient("CALL_ERROR|Impossible de refuser"); return; }

        clientHandler starter = SessionManager.getHandler(session.getAppelant().getUsername());
        if (starter != null) {
            starter.envoyerAuClient("CALL_REFUSED|" + username);
            if (starter.currentCallRowId > 0) {
                new CallDAO().refusedCall(starter.currentCallRowId);
                starter.currentCallRowId = -1;
            }
        }
        envoyerAuClient("CALL_REFUSE_OK|" + session.getAppelant().getUsername());
    }


    private void traiterAnnulerAppel(String[] parts) throws Exception {
        if (username == null) { envoyerAuClient("ERROR|Non authentifié"); throw new Exception(); }
        SessionAppel session = appelManager.annulerAppel(username);
        if (session == null) {
            // soit aucune appel ou deja en appel a cause du status ringing on
            // a retourné NULL donc il faut appeler traiter fin appel pour terminer appel
            traiterFinAppel(parts);
            return;
        }
        clientHandler recepteur = SessionManager.getHandler(session.getRecepteur().getUsername());
        if (recepteur != null) {
            recepteur.envoyerAuClient("CALL_CANCELLED|" + username);
        }
        envoyerAuClient("CALL_CANCEL_OK|" + session.getRecepteur().getUsername());
        if (currentCallRowId > 0) {
            new CallDAO().cancelledCall(currentCallRowId);
            currentCallRowId = -1;
        }
    }

    private void traiterFinAppel(String[] parts) throws Exception {
        if (username == null) { envoyerAuClient("ERROR|Non authentifié"); throw new Exception(); }
        SessionAppel session = appelManager.getAppelByUser(username);
        if (session == null) {
            envoyerAuClient("CALL_END_OK");
            return;
        }
        long durationSecs = Math.max(0,
                (System.currentTimeMillis() - start) / 1000);
        String other = session.getAppelant().getUsername().equals(username)
                ? session.getRecepteur().getUsername()
                : session.getAppelant().getUsername();
        clientHandler otherHandler = SessionManager.getHandler(other);
        boolean wasRinging = session.getStatut() == model.StatutAppel.RINGING;
        boolean ended = appelManager.terminerAppel(username);
        if (otherHandler != null) {
            if (wasRinging && session.getRecepteur().getUsername().equals(other)) {
                otherHandler.envoyerAuClient("CALL_CANCELLED|" + username);
            } else {
                otherHandler.envoyerAuClient("CALL_ENDED|" + username);
            }
        }
        envoyerAuClient("CALL_END_OK|" + other);

        clientHandler callerHandler = SessionManager.getHandler(session.getAppelant().getUsername());
        int rowId = callerHandler != null ? callerHandler.currentCallRowId : -1;
        if (rowId > 0) {
            if (wasRinging) new CallDAO().cancelledCall(rowId);
            else            new CallDAO().endCall(rowId, (int) durationSecs);
            if (callerHandler != null) callerHandler.currentCallRowId = -1;
        }
    }

    private long sessionStartMs(SessionAppel s) {
        return System.currentTimeMillis();
    }

    private static final services.OtpService OTP =
            new services.OtpService(new services.TwilioSmsSender());

    private static String normalizePhone(String raw) {
        return services.PhoneUtil.canonical(raw);
    }
    private static boolean validPhone(String p) {
        return p.matches("\\+?[0-9]{8,15}");
    }

    private void demanderOtp(String[] parts) throws IncorrectFormat {
        if (parts.length < 2) { envoyerAuClient("ERROR|Format REQUEST_OTP|phone"); throw new IncorrectFormat(); }
        String phone = normalizePhone(parts[1].trim());
        if (!validPhone(phone)) { envoyerAuClient("Erreur: numero de telephone invalide"); return; }
        if (!dao.compteDAO.phoneExists(phone)) {
            envoyerAuClient("Erreur: aucun compte pour ce numero"); return;
        }
        envoyerAuClient(otpSentResponse(phone));
    }

    private static String otpSentResponse(String phone) {
        if (!OTP.sendCode(phone)) return "Erreur: impossible d'envoyer le code SMS";
        if (OTP.devMode()) {
            String code = OTP.currentCode(phone);
            if (code != null) return "OTP_SENT|" + phone + "|DEV|" + code;
        }
        return "OTP_SENT|" + phone;
    }

    private void verifierOtp(String[] parts) throws IncorrectFormat {
        if (parts.length < 3) { envoyerAuClient("ERROR|Format VERIFY_OTP|phone|code"); throw new IncorrectFormat(); }
        String phone = normalizePhone(parts[1].trim());
        String code  = parts[2].trim();

        services.OtpService.Result r = OTP.verify(phone, code);
        if (r != services.OtpService.Result.OK) {
            switch (r) {
                case EXPIRED:           envoyerAuClient("Erreur: code expire, redemandez-en un"); break;
                case TOO_MANY_ATTEMPTS: envoyerAuClient("Erreur: trop de tentatives, redemandez un code"); break;
                case NO_CODE:           envoyerAuClient("Erreur: aucun code en attente"); break;
                default:                envoyerAuClient("Erreur: code incorrect"); break;
            }
            return;
        }

        UserDAO userDAO = new UserDAO();
        User result = userDAO.getByPhone(phone);
        if (result == null) { envoyerAuClient("ERROR|Compte introuvable"); return; }
        etablirSession(userDAO, result);
    }

    private void etablirSession(UserDAO userDAO, User result) {
        String uname = result.getUsername();
        if (SessionManager.isOnline(uname)) {
            clientHandler oldHandler = SessionManager.getHandler(uname);
            if (oldHandler != null) {
                oldHandler.envoyerAuClient("INFO|Connexion depuis un autre appareil. Déconnexion...");
                oldHandler.Deconnexion();
            }
        }

        this.username = uname;
        this.phone = result.getPhone();
        this.role = result.getRole();

        SessionManager.registerClientSession(this.username, this);
        try { userDAO.updateStatus(result.getId(), "online"); } catch (Exception ignored) {}
        SessionManager.broadcastStatus(this.username, "online");

        envoyerAuClient("Connexion réussite :" + this.username);

        for (String peer : SessionManager.getOnlineUsers()) {
            if (peer == null || peer.equalsIgnoreCase(this.username)) continue;
            envoyerAuClient("USER_STATUS|" + peer + "|online");
        }

        pousserInboxAuLogin(result);
        pousserGroupesAuLogin(result);
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
                String midPart = m.getClientMid() == null ? "" : "MID:" + m.getClientMid() + "|";
                envoyerAuClient("PRIVATE|" + sender.getUsername() + "|" + currentUser.getUsername() + "|" + midPart + content);
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

    private void pousserGroupesAuLogin(User u) {
        try {
            envoyerListeGroupes(u.getId());
        } catch (Exception e) {
            System.err.println("Erreur pousserGroupesAuLogin: " + e.getMessage());
        }
    }

    private void Inscrire(String[] parts) throws IncorrectFormat {
        if (parts.length < 2) { envoyerAuClient("ERROR|Format REGISTER_PHONE|phone"); throw new IncorrectFormat(); }
        String phone = normalizePhone(parts[1].trim());
        if (!validPhone(phone)) { envoyerAuClient("Erreur: numero de telephone invalide"); return; }
        if (dao.compteDAO.phoneExists(phone)) {
            envoyerAuClient("Erreur: ce numero est deja utilise"); return;
        }
        String displayName = parts.length >= 3 ? parts[2].trim() : "";
        if (!dao.compteDAO.registerPhone(phone, displayName)) {
            envoyerAuClient("Erreur: echec de creation du compte"); return;
        }
        envoyerAuClient(otpSentResponse(phone));
    }

    private void renommerContact(String[] parts) throws IncorrectFormat {
        if (parts.length < 2) { envoyerAuClient("ERROR|Format RENAME_CONTACT|contact|alias"); throw new IncorrectFormat(); }
        if (username == null) { envoyerAuClient("ERROR|Non authentifie"); return; }
        String contactUsername = parts[1].trim();
        String alias = parts.length >= 3 ? parts[2].trim() : "";
        UserDAO userDAO = new UserDAO();
        User me   = userDAO.getByUsername(username);
        User peer = userDAO.getByUsername(contactUsername);
        if (me == null || peer == null) { envoyerAuClient("ERROR|Contact introuvable"); return; }
        boolean ok = new dao.ContactDAO().renameContact(me.getId(), peer.getId(), alias);
        envoyerAuClient(ok ? "CONTACT_RENAMED|" + contactUsername + "|" + alias
                           : "ERROR|Echec du renommage");
    }

    public synchronized void Deconnexion() {
        if (username == null) {
            try { if (socket != null && !socket.isClosed()) socket.close(); } catch (Exception ignored) {}
            return;
        }

            try {
                SessionAppel s = appelManager.getAppelByUser(username);
                if (s != null) {
                    String me = username;
                    String other = s.getAppelant().getUsername().equals(me)
                            ? s.getRecepteur().getUsername()
                            : s.getAppelant().getUsername();
                    clientHandler oh = SessionManager.getHandler(other);
                    boolean wasRinging = s.getStatut() == model.StatutAppel.RINGING;
                    appelManager.terminerAppel(me);
                    if (oh != null) {
                        oh.envoyerAuClient(wasRinging ? "CALL_CANCELLED|" + me : "CALL_ENDED|" + me);
                    }
                    if (currentCallRowId > 0) {
                        if (wasRinging) new CallDAO().cancelledCall(currentCallRowId);
                        else            new CallDAO().endCall(currentCallRowId, 0);
                        currentCallRowId = -1;
                    }
                }
            } catch (Exception ignored) {}

        try {
            UserDAO udao = new UserDAO();
            User me = udao.getByUsername(username);
            if (me != null) {
                for (GroupCallManager.Meeting m : new ArrayList<>(groupCallManager.getActiveMeetings())) {
                    if (m.participants.containsKey(username)) {
                        diffuserSortieMeeting(m, username);
                    }
                }
            }
        } catch (Exception ignored) {}

        String me = username;
        username = null;
        boolean stillOwn = SessionManager.getHandler(me) == this;
        //pas le cas si on se connecte d'un autre appareil
        if (stillOwn) {
            SessionManager.removeClientSession(me);
            if (me != null && SessionManager.getHandler(me) == this) {
                SessionManager.removeClientSession(me);
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
        if (username == null) { envoyerAuClient("ERROR|Non authentifié"); return; }
        envoyerAuClient("Currently online: " + String.join(",", SessionManager.getOnlineUsers()));
    }

    private void envoyerMessagePrive(String[] parts) throws IncorrectFormat {
        if (parts.length < 4) { envoyerAuClient("ERROR|Format incorrect"); throw new IncorrectFormat(); }
        if (username == null) return;

        String targetName = parts[2].trim();
        String fourth = parts[3];

        String midRaw = null;
        String content = fourth;
        if (fourth.startsWith("MID:")) {
            int sep = fourth.indexOf('|');
            if (sep > 0) {
                midRaw = fourth.substring(4, sep);
                content = fourth.substring(sep + 1);
            }
        }
        Long midNum = null;
        if (midRaw != null) {
            try { midNum = Long.parseLong(midRaw); } catch (NumberFormatException ignored) {}
        }

        UserDAO udao = new UserDAO();
        User sender = udao.getByUsername(username);
        User receiver = udao.getByUsername(targetName);

        if (sender != null && receiver != null
                && new BlockDAO().isBlocked(receiver.getId(), sender.getId())) {
            if (midRaw != null) envoyerAuClient("BLOCKED|" + targetName + "|" + midRaw);
            else                envoyerAuClient("BLOCKED|" + targetName);
            return;
        }

        Message savedMessage = null;
        try {
            if (sender != null && receiver != null) {
                Message m = new Message(sender.getId(), receiver.getId(), content);
                m.setClientMid(midNum);
                if (new MessageDAO().saveMessage(m)) savedMessage = m;
            }
        } catch (Exception e) {
            System.err.println("Persist message failed: " + e.getMessage());
        }

        String wireContent = midRaw == null ? content : "MID:" + midRaw + "|" + content;

        clientHandler target = SessionManager.getHandler(targetName);
        if (target != null) {
            target.envoyerAuClient("PRIVATE|" + username + "|" + targetName + "|" + wireContent);
            if (savedMessage != null && savedMessage.getId() > 0) {
                new MessageDAO().updateStatus(savedMessage.getId(), "delivered");
            }
            if (midRaw != null) envoyerAuClient("MSG_STATUS|" + targetName + "|" + midRaw + "|delivered");
        } else if (midRaw != null) {
            envoyerAuClient("MSG_STATUS|" + targetName + "|" + midRaw + "|sent");
        }
    }

    private void traiterTypingPrive(String[] parts) {
        if (username == null || parts.length < 2) return;
        String target = parts[1].trim();
        if (target.isEmpty() || target.equalsIgnoreCase(username)) return;
        clientHandler peer = SessionManager.getHandler(target);
        if (peer != null) peer.envoyerAuClient("TYPING|" + username);
    }

    private void traiterHistorique(String[] parts) {
        if (username == null) { envoyerAuClient("ERROR|Non authentifié"); return; }
        if (parts.length < 2) { envoyerAuClient("ERROR|Format HISTORY|user"); return; }
        String otherName = parts[1].trim();
        UserDAO udao = new UserDAO();
        User me = udao.getByUsername(username);
        User other = udao.getByUsername(otherName);
        if (me == null || other == null) { envoyerAuClient("HISTORY_END|" + otherName); return; }
        List<Message> msgs = new MessageDAO().getConversation(me.getId(), other.getId());
        for (Message m : msgs) {
            String sender = (m.getSenderId() == me.getId()) ? username : otherName;
            String safe;
            if (m.isDeleted()) safe = "[message deleted]";
            else               safe = m.getContent() == null ? "" : m.getContent().replace("\n", " ");
            String midPart = m.getClientMid() == null ? "" : "MID:" + m.getClientMid() + "|";
            String editedFlag = m.getEditedAt() != null && !m.isDeleted() ? "EDITED|" : "";
            envoyerAuClient("HISTORY|" + otherName + "|" + sender + "|" + midPart + editedFlag + safe);
        }
        envoyerAuClient("HISTORY_END|" + otherName);
    }

    private void envoyerListeGroupes(int userId) {
        List<Group> groups = new GroupDAO().getUserGroups(userId);
        UserDAO udao = new UserDAO();
        for (Group g : groups) envoyerInfoGroupe(g, udao);
        envoyerAuClient("GROUP_LIST_END");
    }

    private void envoyerInfoGroupe(Group g, UserDAO udao) {
        StringBuilder members = new StringBuilder();
        StringBuilder admins = new StringBuilder();
        for (Integer mid : g.getMemberIds()) {
            User u = udao.getById(mid);
            if (u == null) continue;
            if (members.length() > 0) members.append(",");
            members.append(u.getUsername());
            if (g.isAdmin(mid)) {
                if (admins.length() > 0) admins.append(",");
                admins.append(u.getUsername());
            }
        }
        envoyerAuClient("GROUP_INFO|" + g.getId() + "|" + g.getName() + "|" + members + "|" + admins);
    }

    private void traiterCreationGroupe(String[] parts) {
        if (username == null) { envoyerAuClient("ERROR|Non authentifié"); return; }
        if (parts.length < 3) { envoyerAuClient("ERROR|Format GROUP_CREATE|name|members|admins"); return; }
        String name = parts[1].trim();
        if (name.isEmpty()) { envoyerAuClient("ERROR|Nom de groupe vide"); return; }

        String[] memberArr = parts[2].split(",");
        String[] adminArr = parts.length >= 4 ? parts[3].split(",") : new String[0];

        UserDAO udao = new UserDAO();
        GroupDAO gdao = new GroupDAO();
        User me = udao.getByUsername(username);
        if (me == null) { envoyerAuClient("ERROR|Utilisateur inconnu"); return; }

        int gid = gdao.createGroup(name, me.getId());
        if (gid <= 0) { envoyerAuClient("ERROR|Impossible de créer le groupe"); return; }

        Set<String> adminNames = new HashSet<>();
        for (String a : adminArr) if (!a.isBlank()) adminNames.add(a.trim().toLowerCase());

        adminNames.add(username.toLowerCase());

        Set<String> seen = new HashSet<>();
        seen.add(username.toLowerCase());
        for (String m : memberArr) {
            String mn = m == null ? "" : m.trim();
            if (mn.isEmpty() || !seen.add(mn.toLowerCase())) continue;
            User u = udao.getByUsername(mn);
            if (u == null) continue;
            gdao.addMember(gid, u.getId(), adminNames.contains(mn.toLowerCase()));
        }
        for (String an : adminNames) {
            if (an.equalsIgnoreCase(username)) continue;
            User u = udao.getByUsername(an);
            if (u != null && !gdao.isMember(gid, u.getId())) {
                gdao.addMember(gid, u.getId(), true);
            }
        }

        Group g = gdao.getGroup(gid);
        if (g == null) return;

        for (Integer memberId : g.getMemberIds()) {
            User u = udao.getById(memberId);
            if (u == null) continue;
            clientHandler ch = SessionManager.getHandler(u.getUsername());
            if (ch != null) ch.envoyerInfoGroupe(g, udao);
        }
    }

    private void traiterListeGroupes(String[] parts) {
        if (username == null) { envoyerAuClient("ERROR|Non authentifié"); return; }
        User me = new UserDAO().getByUsername(username);
        if (me == null) return;
        envoyerListeGroupes(me.getId());
    }

    private void traiterHistoriqueGroupe(String[] parts) {
        if (username == null) { envoyerAuClient("ERROR|Non authentifié"); return; }
        if (parts.length < 2) { envoyerAuClient("ERROR|Format GROUP_HISTORY|groupId"); return; }
        int gid;
        try { gid = Integer.parseInt(parts[1].trim()); }
        catch (NumberFormatException e) { envoyerAuClient("ERROR|groupId invalide"); return; }

        User me = new UserDAO().getByUsername(username);
        if (me == null) return;
        if (!new GroupDAO().isMember(gid, me.getId())) {
            envoyerAuClient("ERROR|Non membre du groupe " + gid);
            return;
        }
        UserDAO udao = new UserDAO();
        List<Message> msgs = new MessageDAO().getGroupMessages(gid);
        for (Message m : msgs) {
            User sender = udao.getById(m.getSenderId());
            String senderName = sender == null ? "?" : sender.getUsername();
            String safe;
            String typeOut = m.getType() == null ? "TEXT" : m.getType();
            if (m.isDeleted()) {
                safe = "[message deleted]";
                typeOut = "TEXT";
            } else {
                safe = m.getContent() == null ? "" : m.getContent().replace("\n", " ");
            }
            String midPart = m.getClientMid() == null ? "" : "MID:" + m.getClientMid() + "|";
            String editedFlag = m.getEditedAt() != null && !m.isDeleted() ? "EDITED|" : "";
            envoyerAuClient("GROUP_HISTORY|" + gid + "|" + senderName + "|" + typeOut + "|"
                    + midPart + editedFlag + safe);
        }
        envoyerAuClient("GROUP_HISTORY_END|" + gid);
    }

    private void traiterMessageGroupe(String[] parts, String raw) {
        if (username == null) { envoyerAuClient("ERROR|Non authentifié"); return; }
        if (parts.length < 4) { envoyerAuClient("ERROR|Format GROUP_MSG|gid|MID:n|content"); return; }
        int gid;
        try { gid = Integer.parseInt(parts[1].trim()); }
        catch (NumberFormatException e) { envoyerAuClient("ERROR|groupId invalide"); return; }

        String tail = parts[3];
        String mid = null;
        String content = tail;
        if (tail.startsWith("MID:")) {
            int sep = tail.indexOf('|');
            if (sep > 0) {
                mid = tail.substring(4, sep);
                content = tail.substring(sep + 1);
            }
        }
        String msgType = "TEXT";
        if (content.startsWith("TYPE:")) {
            int sep = content.indexOf('|');
            if (sep > 0) {
                msgType = content.substring(5, sep).toUpperCase();
                content = content.substring(sep + 1);
            }
        }

        UserDAO udao = new UserDAO();
        GroupDAO gdao = new GroupDAO();
        User me = udao.getByUsername(username);
        if (me == null) return;
        if (!gdao.isMember(gid, me.getId())) { envoyerAuClient("ERROR|Non membre du groupe"); return; }

        String storedType = msgType;
        if ("TEXT".equalsIgnoreCase(msgType) && content != null && content.startsWith("MEDIA_MSG|")) {
            String[] f = content.split("\\|", 6);
            if (f.length >= 2) {
                switch (f[1]) {
                    case "IMAGE":     storedType = "IMAGE"; break;
                    case "AUDIO_MSG": storedType = "AUDIO"; break;
                    case "FILE":      storedType = "FILE";  break;
                    default:          storedType = "FILE";  break;
                }
            }
        }
        Long midNum = null;
        if (mid != null) {
            try { midNum = Long.parseLong(mid); } catch (NumberFormatException ignored) {}
        }
        try {
            Message toSave = new Message(0, me.getId(), 0, gid, content, storedType, "delivered");
            toSave.setClientMid(midNum);
            new MessageDAO().saveMessage(toSave);
        } catch (Exception ignored) {}

        String midPart = mid == null ? "" : "MID:" + mid + "|";
        for (Integer memberId : gdao.getMembers(gid)) {
            User u = udao.getById(memberId);
            if (u == null) continue;
            clientHandler ch = SessionManager.getHandler(u.getUsername());
            if (ch == null) continue;
            ch.envoyerAuClient("GROUP_MSG|" + gid + "|" + username + "|" + msgType + "|" + midPart + content);
        }
        if (mid != null) envoyerAuClient("GROUP_MSG_STATUS|" + gid + "|" + mid + "|delivered");
    }

    private void traiterTypingGroupe(String[] parts) {
        if (username == null || parts.length < 2) return;
        int gid;
        try { gid = Integer.parseInt(parts[1].trim()); }
        catch (NumberFormatException e) { return; }
        UserDAO udao = new UserDAO();
        for (Integer mid : new GroupDAO().getMembers(gid)) {
            User u = udao.getById(mid);
            if (u == null || u.getUsername().equalsIgnoreCase(username)) continue;
            clientHandler ch = SessionManager.getHandler(u.getUsername());
            if (ch != null) ch.envoyerAuClient("GROUP_TYPING|" + gid + "|" + username);
        }
    }

    private void traiterMembreGroupe(String action, String[] parts) {
        if (username == null) { envoyerAuClient("ERROR|Non authentifié"); return; }
        if (parts.length < 3) { envoyerAuClient("ERROR|Format incorrect"); return; }
        int gid;
        try { gid = Integer.parseInt(parts[1].trim()); }
        catch (NumberFormatException e) { return; }
        String targetName = parts[2].trim();

        UserDAO udao = new UserDAO();
        GroupDAO gdao = new GroupDAO();
        User me = udao.getByUsername(username);
        User target = udao.getByUsername(targetName);
        if (me == null || target == null) { envoyerAuClient("ERROR|Utilisateur introuvable"); return; }
        if (!gdao.isAdmin(gid, me.getId())) { envoyerAuClient("GROUP_FORBIDDEN|" + gid); return; }

        boolean changed = false;
        switch (action) {
            case "ADD":     changed = gdao.addMember(gid, target.getId(), false); break;
            case "REMOVE":  changed = gdao.removeMember(gid, target.getId());     break;
            case "PROMOTE": changed = gdao.setAdmin(gid, target.getId(), true);   break;
            case "DEMOTE":  changed = gdao.setAdmin(gid, target.getId(), false);  break;
        }
        if (!changed) return;

        Group g = gdao.getGroup(gid);
        if (g == null) return;

        for (Integer memberId : g.getMemberIds()) {
            User u = udao.getById(memberId);
            if (u == null) continue;
            clientHandler ch = SessionManager.getHandler(u.getUsername());
            if (ch != null) ch.envoyerInfoGroupe(g, udao);
        }
        if ("REMOVE".equals(action)) {
            clientHandler ch = SessionManager.getHandler(target.getUsername());
            if (ch != null) ch.envoyerAuClient("GROUP_REMOVED|" + gid);
        }
    }

    private void traiterDemarrageMeeting(String[] parts) {
        if (username == null) { envoyerAuClient("ERROR|Non authentifié"); return; }
        if (parts.length < 4) { envoyerAuClient("ERROR|Format GROUP_CALL_START|gid|TYPE|aPort|vPort"); return; }
        String[] tail = parts[3].split("\\|");
        if (tail.length < 2) { envoyerAuClient("ERROR|Format GROUP_CALL_START|gid|TYPE|aPort|vPort"); return; }
        int gid;
        try { gid = Integer.parseInt(parts[1].trim()); } catch (Exception e) { return; }
        String type = parts[2].equalsIgnoreCase("VIDEO") ? "video" : "audio";
        int audioPort, videoPort;
        try {
            audioPort = Integer.parseInt(tail[0].trim());
            videoPort = Integer.parseInt(tail[1].trim());
        } catch (Exception e) { return; }
        String lanIp = tail.length >= 3 ? tail[2].trim() : null;

        UserDAO udao = new UserDAO();
        GroupDAO gdao = new GroupDAO();
        User me = udao.getByUsername(username);
        if (me == null || !gdao.isMember(gid, me.getId())) { envoyerAuClient("GROUP_FORBIDDEN|" + gid); return; }

        GroupCallManager.Meeting existing = groupCallManager.getMeeting(gid);
        if (existing != null) {
            envoyerAuClient("GROUP_CALL_ACTIVE|" + gid + "|" + existing.type);
            return;
        }
        int rowId = new CallDAO().startGroupCall(me.getId(), gid, type);
        GroupCallManager.Meeting meeting = groupCallManager.createMeeting(gid, type, username, rowId);
        meeting.participants.put(username,
                new GroupCallManager.Participant(username, getIpAddress(), lanIp, audioPort, videoPort));

        for (Integer memberId : gdao.getMembers(gid)) {
            User u = udao.getById(memberId);
            if (u == null || u.getUsername().equalsIgnoreCase(username)) continue;
            clientHandler ch = SessionManager.getHandler(u.getUsername());
            if (ch != null) {
                ch.envoyerAuClient("GROUP_CALL_INVITE|" + gid + "|" + username + "|" + type);
            }
        }
        envoyerAuClient("GROUP_CALL_STARTED|" + gid + "|" + type);
    }

    private void traiterJoinMeeting(String[] parts) {
        if (username == null) return;
        if (parts.length < 4) { envoyerAuClient("ERROR|Format GROUP_CALL_JOIN|gid|aPort|vPort"); return; }
        int gid;
        int audioPort, videoPort;
        String lanIp = null;
        try {
            gid = Integer.parseInt(parts[1].trim());
            audioPort = Integer.parseInt(parts[2].trim());
            String[] vtail = parts[3].split("\\|");
            videoPort = Integer.parseInt(vtail[0].trim());
            if (vtail.length >= 2 && !vtail[1].trim().isEmpty()) lanIp = vtail[1].trim();
        } catch (Exception e) { return; }

        GroupCallManager.Meeting meeting = groupCallManager.getMeeting(gid);
        if (meeting == null) { envoyerAuClient("GROUP_CALL_NOT_FOUND|" + gid); return; }

        String myPublicIp = getIpAddress();
        GroupCallManager.Participant me = new GroupCallManager.Participant(
                username, myPublicIp, lanIp, audioPort, videoPort);
        meeting.participants.put(username, me);

        StringBuilder peers = new StringBuilder();
        for (GroupCallManager.Participant p : meeting.participants.values()) {
            if (p.username.equals(username)) continue;
            if (peers.length() > 0) peers.append(";");
            peers.append(p.username).append(",").append(p.ipFor(myPublicIp)).append(",")
                 .append(p.audioPort).append(",").append(p.videoPort);
        }
        envoyerAuClient("GROUP_CALL_PEERS|" + gid + "|" + meeting.type + "|" + peers);

        for (GroupCallManager.Participant p : meeting.participants.values()) {
            if (p.username.equals(username)) continue;
            clientHandler ch = SessionManager.getHandler(p.username);
            if (ch != null) {
                ch.envoyerAuClient("GROUP_CALL_PEER_JOINED|" + gid + "|" + username + "|"
                        + me.ipFor(p.ip) + "|" + me.audioPort + "|" + me.videoPort);
            }
        }
    }

    private void traiterSortieMeeting(String[] parts) {
        if (username == null || parts.length < 2) return;
        int gid;
        try { gid = Integer.parseInt(parts[1].trim()); } catch (Exception e) { return; }
        GroupCallManager.Meeting meeting = groupCallManager.getMeeting(gid);
        if (meeting == null) return;
        diffuserSortieMeeting(meeting, username);
    }

    private void diffuserSortieMeeting(GroupCallManager.Meeting meeting, String who) {
        meeting.participants.remove(who);
        for (GroupCallManager.Participant p : meeting.participants.values()) {
            clientHandler ch = SessionManager.getHandler(p.username);
            if (ch != null) ch.envoyerAuClient("GROUP_CALL_PEER_LEFT|" + meeting.groupId + "|" + who);
        }
        if (meeting.participants.isEmpty()) {
            long durationSecs = Math.max(0, (System.currentTimeMillis() - meeting.startedAtMs) / 1000);
            if (meeting.callRowId > 0) new CallDAO().endCall(meeting.callRowId, (int) durationSecs);
            groupCallManager.endMeeting(meeting.groupId);
        }
    }

    private void traiterHistoriqueAppels(String[] parts) {
        if (username == null) return;
        User me = new UserDAO().getByUsername(username);
        if (me == null) return;
        UserDAO udao = new UserDAO();
        GroupDAO gdao = new GroupDAO();
        for (model.Call c : new CallDAO().getCallHistory(me.getId())) {
            String callerName = "";
            String otherName = "";
            User caller = udao.getById(c.getCallerId());
            if (caller != null) callerName = caller.getUsername();
            if (c.isGroup()) {
                Group g = gdao.getGroup(c.getGroupId());
                otherName = g == null ? ("Group #" + c.getGroupId()) : g.getName();
            } else {
                User other = udao.getById(c.getReceiverId());
                if (other != null) otherName = other.getUsername();
            }
            envoyerAuClient("CALL_HISTORY|" + c.getId() + "|" + callerName + "|" + otherName + "|"
                    + (c.getType() == null ? "audio" : c.getType()) + "|"
                    + (c.getStatus() == null ? "ended" : c.getStatus()) + "|"
                    + c.getDuration() + "|"
                    + (c.getStartedAt() == null ? "" : c.getStartedAt().toString()) + "|"
                    + (c.isGroup() ? "1" : "0"));
        }
        envoyerAuClient("CALL_HISTORY_END");
    }

    private void traiterEditMessagePrive(String[] parts) {
        if (username == null || parts.length < 4) return;
        String peer = parts[1].trim();
        long mid;
        try { mid = Long.parseLong(parts[2].trim()); }
        catch (NumberFormatException e) { return; }
        String newContent = parts[3];

        User me = new UserDAO().getByUsername(username);
        if (me == null) return;
        if (!new MessageDAO().editMessage(me.getId(), mid, newContent)) {
            envoyerAuClient("MSG_EDIT_FAIL|" + peer + "|" + mid);
            return;
        }
        String wire = "MSG_EDITED_PRIV|" + username + "|" + peer + "|" + mid + "|" + newContent;
        envoyerAuClient(wire);
        clientHandler other = SessionManager.getHandler(peer);
        if (other != null) other.envoyerAuClient(wire);
    }

    private void traiterDeleteMessagePrive(String[] parts) {
        if (username == null || parts.length < 3) return;
        String peer = parts[1].trim();
        long mid;
        try { mid = Long.parseLong(parts[2].trim()); }
        catch (NumberFormatException e) { return; }
        User me = new UserDAO().getByUsername(username);
        if (me == null) return;
        if (!new MessageDAO().deleteMessage(me.getId(), mid)) {
            envoyerAuClient("MSG_DELETE_FAIL|" + peer + "|" + mid);
            return;
        }
        String wire = "MSG_DELETED_PRIV|" + username + "|" + peer + "|" + mid;
        envoyerAuClient(wire);
        clientHandler other = SessionManager.getHandler(peer);
        if (other != null) other.envoyerAuClient(wire);
    }

    private void traiterEditMessageGroupe(String[] parts) {
        if (username == null || parts.length < 4) return;
        int gid;
        try { gid = Integer.parseInt(parts[1].trim()); } catch (Exception e) { return; }
        long mid;
        try { mid = Long.parseLong(parts[2].trim()); } catch (Exception e) { return; }
        String newContent = parts[3];

        User me = new UserDAO().getByUsername(username);
        if (me == null) return;
        if (!new MessageDAO().editMessage(me.getId(), mid, newContent)) {
            envoyerAuClient("MSG_EDIT_FAIL|" + gid + "|" + mid);
            return;
        }
        String wire = "MSG_EDITED_GROUP|" + gid + "|" + username + "|" + mid + "|" + newContent;
        UserDAO udao = new UserDAO();
        for (Integer memberId : new GroupDAO().getMembers(gid)) {
            User u = udao.getById(memberId);
            if (u == null) continue;
            clientHandler ch = SessionManager.getHandler(u.getUsername());
            if (ch != null) ch.envoyerAuClient(wire);
        }
    }

    private void traiterDeleteMessageGroupe(String[] parts) {
        if (username == null || parts.length < 3) return;
        int gid;
        try { gid = Integer.parseInt(parts[1].trim()); } catch (Exception e) { return; }
        long mid;
        try { mid = Long.parseLong(parts[2].trim()); } catch (Exception e) { return; }
        User me = new UserDAO().getByUsername(username);
        if (me == null) return;
        if (!new MessageDAO().deleteMessage(me.getId(), mid)) return;
        String wire = "MSG_DELETED_GROUP|" + gid + "|" + username + "|" + mid;
        UserDAO udao = new UserDAO();
        for (Integer memberId : new GroupDAO().getMembers(gid)) {
            User u = udao.getById(memberId);
            if (u == null) continue;
            clientHandler ch = SessionManager.getHandler(u.getUsername());
            if (ch != null) ch.envoyerAuClient(wire);
        }
    }

    private void traiterBlock(String[] parts, boolean block) {
        if (username == null || parts.length < 2) return;
        String target = parts[1].trim();
        if (target.isEmpty()) return;
        UserDAO udao = new UserDAO();
        User me = udao.getByUsername(username);
        User other = udao.getByUsername(target);
        if (me == null || other == null) { envoyerAuClient("ERROR|Utilisateur introuvable"); return; }
        BlockDAO bdao = new BlockDAO();
        boolean ok = block ? bdao.block(me.getId(), other.getId())
                           : bdao.unblock(me.getId(), other.getId());
        if (!ok && block) { envoyerAuClient("BLOCK_NOOP|" + target); return; }
        envoyerAuClient((block ? "BLOCKED_OK|" : "UNBLOCKED_OK|") + target);
    }

    private void traiterBlockList(String[] parts) {
        if (username == null) return;
        User me = new UserDAO().getByUsername(username);
        if (me == null) return;
        List<String> blocked = new BlockDAO().listBlockedUsernames(me.getId());
        envoyerAuClient("BLOCK_LIST|" + String.join(",", blocked));
    }

    private void traiterQuitterGroupe(String[] parts) {
        if (username == null || parts.length < 2) return;
        int gid;
        try { gid = Integer.parseInt(parts[1].trim()); } catch (Exception e) { return; }
        UserDAO udao = new UserDAO();
        GroupDAO gdao = new GroupDAO();
        User me = udao.getByUsername(username);
        if (me == null) return;
        if (!gdao.isMember(gid, me.getId())) { envoyerAuClient("GROUP_LEFT|" + gid); return; }
        gdao.removeMember(gid, me.getId());

        envoyerAuClient("GROUP_LEFT|" + gid);
        Group g = gdao.getGroup(gid);
        if (g == null) return;
        for (Integer memberId : g.getMemberIds()) {
            User u = udao.getById(memberId);
            if (u == null) continue;
            clientHandler ch = SessionManager.getHandler(u.getUsername());
            if (ch != null) ch.envoyerInfoGroupe(g, udao);
        }
    }

    private void traiterStatutMeeting(String[] parts) {
        if (username == null || parts.length < 2) return;
        int gid;
        try { gid = Integer.parseInt(parts[1].trim()); } catch (Exception e) { return; }
        GroupCallManager.Meeting m = groupCallManager.getMeeting(gid);
        if (m == null) envoyerAuClient("GROUP_CALL_STATUS|" + gid + "|none");
        else envoyerAuClient("GROUP_CALL_STATUS|" + gid + "|active|" + m.type
                + "|" + m.participants.size());
    }

    private void EnvoyerRequete(String data) throws Exception {
        String[] parts = data.split("\\|", 4);
        switch (parts[0].trim()) {
            case "REGISTER_PHONE":   Inscrire(parts);                     break;
            case "REQUEST_OTP":      demanderOtp(parts);                  break;
            case "VERIFY_OTP":       verifierOtp(parts);                  break;
            case "RENAME_CONTACT":   renommerContact(parts);              break;
            case "LOGOUT":           Deconnexion();                       break;
            case "GET_ONLINE":       avoirListeEnLigne();                 break;
            case "PRIVATE":          envoyerMessagePrive(parts);          break;
            case "TYPING":           traiterTypingPrive(parts);           break;
            case "HISTORY":          traiterHistorique(parts);            break;
            case "CALL_REQUEST":     traiterDemandeAppel(parts);          break;
            case "CALL_ACCEPT":      traiterAcceptationAppel(parts);      break;
            case "CALL_READY":       traiterCallerReady(parts);           break;
            case "CALL_REFUSE":      traiterRefusAppel(parts);            break;
            case "CALL_CANCEL":      traiterAnnulerAppel(parts);          break;
            case "CALL_END":         traiterFinAppel(parts);              break;
            case "CALL_HISTORY":     traiterHistoriqueAppels(parts);      break;
            case "GROUP_CREATE":     traiterCreationGroupe(parts);        break;
            case "GROUP_LIST":       traiterListeGroupes(parts);          break;
            case "GROUP_HISTORY":    traiterHistoriqueGroupe(parts);      break;
            case "GROUP_MSG":        traiterMessageGroupe(parts, data);   break;
            case "GROUP_TYPING":     traiterTypingGroupe(parts);          break;
            case "GROUP_ADD":        traiterMembreGroupe("ADD", parts);     break;
            case "GROUP_REMOVE":     traiterMembreGroupe("REMOVE", parts);  break;
            case "GROUP_PROMOTE":    traiterMembreGroupe("PROMOTE", parts); break;
            case "GROUP_DEMOTE":     traiterMembreGroupe("DEMOTE", parts);  break;
            case "GROUP_CALL_START": traiterDemarrageMeeting(parts);      break;
            case "GROUP_CALL_JOIN":  traiterJoinMeeting(parts);           break;
            case "GROUP_CALL_LEAVE": traiterSortieMeeting(parts);         break;
            case "GROUP_CALL_STATUS":traiterStatutMeeting(parts);         break;
            case "GROUP_LEAVE":      traiterQuitterGroupe(parts);         break;
            case "MSG_EDIT_PRIV":    traiterEditMessagePrive(parts);      break;
            case "MSG_DELETE_PRIV":  traiterDeleteMessagePrive(parts);    break;
            case "MSG_EDIT_GROUP":   traiterEditMessageGroupe(parts);     break;
            case "MSG_DELETE_GROUP": traiterDeleteMessageGroupe(parts);   break;
            case "BLOCK_USER":       traiterBlock(parts, true);           break;
            case "UNBLOCK_USER":     traiterBlock(parts, false);          break;
            case "BLOCK_LIST":       traiterBlockList(parts);             break;
            default:
                envoyerAuClient("ERROR|Action non reconnue: " + parts[0]);
        }
    }

    public void envoyerAuClient(String msg) {
        if (going != null) going.println(msg);
    }
    public String getUsername()  { return username; }
    public String getRole()      { return role; }
    public boolean estConnu()    { return username != null; }
    public boolean estAdmin()    { return "ADMIN".equals(role); }
    public String getIpAddress() {
        try { return socket.getInetAddress().getHostAddress(); } //il retourne l'ip comme il le vois dans mon cas
        //elle retourne l'ip de mon routeur ce qui n 'est pas pratique sans turn server
        //donc la troisieme partie est tres importante (lan ip)
        catch (Exception e) { return "127.0.0.1"; }
    }

    public void run() {
        try {
            going = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            coming = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;
            while ((line = coming.readLine()) != null) {
                try { EnvoyerRequete(line.trim()); }
                catch (Exception e) { System.err.println("[clientHandler] " + e.getMessage()); }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Deconnexion();
        }
    }
}
