import java.io.*;
import java.net.Socket;

import Exceptions.Blank;
import Exceptions.IncorrectFormat;
import Exceptions.alreadyConnected;
import Exceptions.banException;
import Exceptions.invalidCoordinates;
import Exceptions.shortt;
import Exceptions.shortt;
import Exceptions.banException;
import dao.UserDAO;
import model.User;

public class clientHandler implements Runnable{
    private final Socket socket;
    private final SessionManager sManager;
    private PrintWriter going;
    private BufferedReader coming;
    private String username = null;
    private String role = null;

    public clientHandler(Socket socket,SessionManager sManager)
    {
        this.socket = socket;
        this.sManager = sManager;
    }



    //partie authentification
    private void seConnecter(String[] parts) throws IncorrectFormat,Blank,alreadyConnected, invalidCoordinates, banException
    {
        if(parts.length < 3)
        {
            envoyerAuClient("ERREUR: Format incorrecte");
            throw new IncorrectFormat();
        }

        String username = parts[1];
        String password = parts [2];

        if(username.isEmpty() || password.isEmpty())
        {
            envoyerAuClient("ERROR: vous devez entrer le mot de passe et le nom");
            throw new Blank();
        }

        if(sManager.isOnline(username))
        {
            envoyerAuClient("Connexion impossible: Vous etes déja connectés dans un autre appareil");
            throw new alreadyConnected();
        }
        UserDAO userDAO = new UserDAO();
        User result = userDAO.login(username, password);
        if (result == null) {
            envoyerAuClient("ERROR: invalid username or password");
            throw new invalidCoordinates();
        } else {
            this.username = result.getUsername();
            this.role = "USER"; // wla zid role f User model
            sManager.registerClientSession(username, this);
            envoyerAuClient("Connecté en tant que: " + username);
        }
        if(result == null)
        {
            envoyerAuClient("ERROR: invalid usename or password");
            throw new invalidCoordinates();
        }
        else if (result.equals("BLOCKED"))
        {
            envoyerAuClient("ERREUR: Utilisateur bloqué veuillez contacter l'administrateur pour toute reclamation");
        }
        else
        {
            this.username = result[0];
            this.role = result[1];
            sManager.registerClientSession(username,this);
            envoyerAuClient("Connecté en tant que: " + username);


        }
    }


    private void Inscrire(String[] parts) throws IncorrectFormat
    {
        if(parts.length < 4)
        {
            envoyerAuClient("ERREUR: vous devez entrer un email, mot de passe et un username ");
            throw new IncorrectFormat();
        }

        String user = parts[1].trim();
        String password = parts[2].trim();
        String email = parts[3].trim();

        if(user.length() < 5)
        {
            envoyerAuClient("Erreur: nom d'utilisateur tres cours (minimum 5)");
        }

        if(!password.matches(".*[@&#~!$%^*]+.*") || password.length() < 8) 
        {
            envoyerAuClient("Erreur: mot de passe est cours ou doit contenir des caractere speciaux parmi [@&#~!$%^*] (min longueur 8) ");
        }

        if(!email.contains("@"))
        {
            envoyerAuClient("Erreur: Email doit contenir un @ exemple: javaapplication@ensa.ma ");
        }

        UserDAO userDAO = new UserDAO();
        boolean verification = userDAO.addUser(new User(0, user, email, password, "", "online"));        envoyerAuClient(verification? "Compte creé avec succés":"nom d'utilisateur ou email déja utilisé ");
    }

    public void Deconnexion()
    {
        SessionManager.removeClientSession(username);
        username = null;
        role = null;
        try {
            socket.close();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        envoyerAuClient("Deconnecté avec succés");
    }


    void avoirListeEnLigne()
    {
        if(username == null)
        {
            envoyerAuClient("ERREUR: utilisateur non authentifié, il ne peut pas consulter la liste des personne en ligne");
            return;
        }
        String list = String.join("," ,SessionManager.getOnlineUsers());

    }


    public void envoyerAuClient(String msg)
    {
        if(going != null) going.println(msg);
    }

    public String getUsername()
    {
        return username;
    }

    public String getRole()
    {
        return role;
    }
    public boolean estConnu()
    {
        return username != null;
    }

    public boolean estAdmin()
    {
        return role.equals("ADMIN");
    }
    private void EnvoyerRequete(String data) throws IncorrectFormat,Blank,alreadyConnected, invalidCoordinates, banException
    {
        String[] parts = data.split("\\|", -1); // i seprate my data with | I used \\ to tell it that | is not a tabulation character the -1 take "" as an elements and adds it to the table
        switch(parts[0]) // this is the action of the user for example ("LOGIN", "OMAR", "1234", "EMAIL","")
        {
            case "LOGIN" :
                seConnecter(parts);
                break;
            case "REGISTER":
                Inscrire(parts);
                break;
            case "LOGOUT":
                Deconnexion();
                break;
            case "GET_ONLINE":
                avoirListeEnLigne();
                break;
            case "PRIVATE":
                // PRIVATE|senderUsername|receiverUsername|content
                if (parts.length >= 4) {
                    String toUser = parts[2];
                    String content = parts[3];
                    clientHandler target = SessionManager.getHandler(toUser);
                    if (target != null) {
                        target.envoyerAuClient("PRIVATE|" + username + "|" + toUser + "|" + content);
                    } else {
                        envoyerAuClient("ERREUR: " + toUser + " n'est pas en ligne");
                    }
                }
                 break;
                //TODO ADD ADMIN ACTIONS
            default:
                envoyerAuClient("ERREUR: Action non reconnue: " + parts[0]);  
        }
    }
    public void run()
    {
        try 
        {
            going = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()) , true);
            coming = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String line;
            while((line = coming.readLine()) != null)
            {
               EnvoyerRequete(line.trim());
            }
            
        } catch (Exception e) 
        {
            e.printStackTrace();
        }
    }
}

