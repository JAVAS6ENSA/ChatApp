import java.io.*;
import java.net.Socket;
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
    private void handleLogin(String[] parts) throws IncorrectFormat,Blank,alreadyConnected, invalidCoordinates
    {
        if(parts.length < 3)
        {
            sendToClient("ERREUR: Format incorrecte");
            throw new IncorrectFormat();
        }

        String username = parts[1];
        String password = parts [2];

        if(username.isEmpty() || password.isEmpty())
        {
            sendToClient("ERROR: vous devez entrer le mot de passe et le nom");
            throw new Blank();
        }

        if(sManager.isOnline(username))
        {
            sendToClient("Connexion impossible: Vous etes déja connectés dans un autre appareil");
            throw new alreadyConnected();
        }

        String result = database.loginUser(username,password);
        if(result == null)
        {
            sendToClient("ERROR: invalid usename or password");
            throw new invalidCoordinates();
        }
        else if (result.equals("BLOCKED"))
        {
            sendToClient("ERREUR: Utilisateur bloqué veuillez contacter l'administrateur pour toute reclamation");
        }
    }
    private void handleMessage(String data)
    {
        String[] parts = data.split("\\|", -1); // i seprate my data with | I used \\ to tell it that | is not a tabulation character the -1 take "" as an elements and adds it to the table
        switch(parts[0]) // this is the action of the user for example ("LOGIN", "OMAR", "1234", "EMAIL","")
        {
            case "LOGIN" :
                handleLogin(parts);
                break;
            case "REGISTER":
                handleRegister(parts);
                break;
            case "LOGOUT":
                handleLogout();
                break;
            case "GET_ONLINE":
                handleGetOnline();
                break;
            default:
                sendToClient("ERREUR: Action non reconnue: " + parts[0]);  
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
               handleMessage(line.trim());
            }
            
        } catch (Exception e) 
        {
            e.printStackTrace();
        }
    }
}

