package server;

import dao.UserDAO;
import java.net.ServerSocket;
import java.net.Socket;
public class serveur
{
        private final SessionManager sessionManager = new SessionManager();
        private final AppelManager appelManager = new AppelManager();
        private ServerSocket sSocket;
        public static final int serverPort = 8080;



        public void stopServer()
        {
            try {
                if (sSocket != null) sSocket.close();
            }
            catch(Exception exp)
            {
                exp.printStackTrace();
            }
        }

        public void startServer()
        {
            try
            {

                int reset = new UserDAO().resetAllOffline();
                if (reset > 0) System.out.println("SERVER: reset " + reset + " stale online row(s) to offline");

                sSocket = new ServerSocket(serverPort);
                System.out.println("SERVER: Listening at port" + serverPort + "...");
                while(true)
                {
                    Socket socket = sSocket.accept();
                    System.out.println("SERVER: A new connection has arrived!");
                    clientHandler newClient = new clientHandler(socket, sessionManager, appelManager);
                    Thread thread = new Thread(newClient);
                    thread.setDaemon(true);
                    thread.start();
                }
            }
            catch(Exception e) {
                e.printStackTrace();
                stopServer();
            }
        }

    public static void main(String[] args) {
        new serveur().startServer();
    }
}
