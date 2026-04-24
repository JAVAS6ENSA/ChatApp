import java.io.*;
import java.net.Socket;
public class clientHandler implements Runnable{
    private final Socket socket;
    private final SessionManager sessionManager;
    private PrintWriter going;
    private BufferedReader coming;
    private String username = null;
    private String role = null;

    public clientHandler(Socket socket,SessionManager sessionManager)
    {
        this.socket = socket;
        this.sessionManager = sessionManager;
    }


    private void handleLogin(String[] parts)
    {
        if(parts.length < 3)
        {
            sendToClient("ERREUR: Format incorrecte");
            return; // TODO work on exception
        }

        String username = parts[1];
        String password = parts [2];
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

