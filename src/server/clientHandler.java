package server;
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

    public void run()
    {
        try 
        {
            going = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()) , true);
            coming = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String line;
            while((line = coming.readLine()) != null)
            {
               // handleMessage(line.trim());
            }
            
        } catch (Exception e) 
        {
            e.printStackTrace();
        }
    }
}

