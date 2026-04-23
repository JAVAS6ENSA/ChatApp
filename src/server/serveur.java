import java.io.*; 
// possede le bufferReader qui lit les messages venants des utilisateurs
//printWriter envois des messages vers les utilisateurs
//input outputstreams a travers lesquels on envoi les messages 
import java.net.*; 
//talk to other computers via wifi using Socket
import java.util.*;
//contains List etc
public class serveur {
    private serverSocket socket;
    private int serverPort;
    private List<ClientHandler> activeUsers; 
    // TODO fix in our diagram class we should
    //  add a list of active 
    // users to who we wanna send messages otherwise we already have active clients in our database
    

    public server(int port)
    {
        this.serverPort = port;
        this.activeUsers = new ArrayList<>();
    }

    
}
