package server;

import dao.compteDAO;
import server.Exceptions.*;
import dao.UserDAO;
import model.User;
import model.SessionAppel;
import databases.DBConnection;

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
        recepteur.envoyerAuClient("CALL_REQUEST|" + username);
    }

    private void traiterAcceptationAppel(String[] parts) throws Exception, UnexpectedBehavior
    {
        if(username == null) {envoyerAuClient("[CLIENT HANDLER ERROR] Veuillez s'authentifier"); throw new Exception(); }
        SessionAppel session = appelManager.getAppelByUser(username);
        if(session == null) {envoyerAuClient("[CLIENT HANDLER] Aucun appel entrant"); throw new Exception();}

        boolean ok = appelManager.accepterAppel(username);

        if(!ok)
        {
            envoyerAuClient("[CLIENT HANDLER] Impossible d'accepter l'appel");
            throw new Exception();
        }

        clientHandler starter = SessionManager.getHandler(session.getAppelant().getUsername());

        String currentIp = getIpAddress();
        String callerIp = starter != null? starter.getIpAddress() : "127.0.0.1";
        if(starter == null)  { envoyerAuClient(" [UNEXPECTED BEHAVIOR] Debut Appel avec vous meme (loopback)  " + username+ "sur " +callerIp); throw new UnexpectedBehavior(); }
        envoyerAuClient("Debut Appel avec " + starter.getUsername() + "Running at " +callerIp);
        // Notifier l'appelant que l'appel a été accepté
        starter.envoyerAuClient("CALL_ACCEPTED|" + username);
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

        SessionManager.registerClientSession(this.username, this); //register him as online
        if (this.email != null) {
            SessionManager.registerClientSession(this.email, this);
        }

        envoyerAuClient("Connexion réussite :" + result.getUsername());
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



    public void Deconnexion() {
        if(appelManager.getAppelByUser(username) != null)
        {
            terminerAppelInterne(appelManager.getAppelByUser(username));
        }
            SessionManager.removeClientSession(username);
            envoyerAuClient("Deconnecté...");
            try
            {
                socket.close();
            } catch (Exception e) {
               e.printStackTrace();
            }

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
                clientHandler target = SessionManager.getHandler(targetName);
                if(target != null)
                {
                    System.out.println("[SERVER DEBUG] Routing PRIVATE from " + username + " to " + targetName);
                    target.envoyerAuClient("PRIVATE|" + username + "|" + targetName + "|" + parts[3]);
                }
                else{
                    System.err.println("[SERVER DEBUG] Target " + targetName + " not found in SessionManager");
                    envoyerAuClient("Utilisateur hors ligne");
                }

            }
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
            case "CALL_REFUSE":  traiterRefusAppel(parts);     break;
           case "CALL_END":     traiterFinAppel(parts);       break;
            default:
                envoyerAuClient("ERROR|Action non reconnue: " + parts[0]);
        }
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