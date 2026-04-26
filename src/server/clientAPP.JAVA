public class ClientAPP{
    //like tp we need to make one and only clientAPP instance
    private static ClientAPP instance;
    public static ClientAPP getInstance()
    {
        if(instance == null ) {
            instance = new ClientAPP();
        }
        return instance;
        
    }
    private ClientAPP() {} // i forced the constructor to be private so no one can construct it 
    private Socket socket;
    private PrintWriter sortant;
    private BufferedReader entrant;

    public static int PORT = serveur.serverPort; //serverPort
    public static String HOST = "localhost";  // server host localhost

    public void connect()
    {
        try{
        socket = new Socket(HOST,PORT); //where i want my socket to take me
        sortant = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()),true); //read
        entrant = new BufferedReader(new InputStreamReader(socket.getInputStream())); //write
        System.out.println("[Client app] connected to the server");
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    } 

    public void send(String message)
    {
        if(sortant!= null) sortant.println(message);
    }

    public String read()
    {
        try{
        return entrant.readLine();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }

    public boolean isConnected()
    {
        return socket.isConnected();
    }

    public void deconnecter()
    {
        try
        {
            if( socket != null)socket.close();
        }
        catch(Exception e )
        {
            e.printStackTrace();
        }
        instance = null;
    }


}