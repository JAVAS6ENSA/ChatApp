public class ClientAPP{
    //like tp we need to make one and only clientAPP instance
    private static ClientAPP instance;
    public static ClientAPP getInstance()
    {
        if(instance == null ) return new ClientAPP();
        return instance
    }
    packagerivate ClientAPP() {} // i forced the constructor to be private so no one can construct it 
    private Socket socket;
    private printWrite sortant;
    private BufferedReader entrant;

    public static int String PORT = serveur.serverPort; //serverPort
    public static String HOST = "localhost";  // server host localhost

    public void connect()
    {
        socket = new Socket(HOST,PORT); //where i want my socket to take me
        sortant = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()),true); //read
        entrant = new BufferedReader(new InputStreamReader(socket.getInputStream())); //write

        
    }


}