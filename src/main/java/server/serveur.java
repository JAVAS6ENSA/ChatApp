package server;

import java.net.ServerSocket;
import java.net.Socket;
//contains List etc
public class serveur 
{
        private final SessionManager sessionManager = new SessionManager();
        private final AppelManager appelManager = new AppelManager();
        private ServerSocket sSocket;
        public static final int serverPort = 8080;
        // TODO fix in our diagram class we should
        //  add a list of active 
        // users to who we wanna send messages otherwise we already have active clients in our database

        public void stopServer()
        {
            try{
            sSocket.close();
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
                sSocket = new ServerSocket(serverPort);
                System.out.println("SERVER: Listening at port" + serverPort + "...");
                //the server does not have a remote ip or port since eveytime we make a connection it changes
                while(true) //after each threead creation 
                                            // we make a new thread waiting for another acception 
                {
                    Socket socket = sSocket.accept(); //here is gives to that socket the local port and ip and from which client it the client just connected to it basically gives it everything
                    System.out.println("SERVER: A new connection has arrived!");
                    clientHandler newClient = new clientHandler(socket, sessionManager, appelManager);
                    Thread thread = new Thread(newClient);
                    thread.setDaemon(true); //instant disconnection when turning off a server
                    thread.start();
                }
            }
            catch(Exception e)
            {
                stopServer();
            }
        }
    }
