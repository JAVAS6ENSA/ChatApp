import java.io.*; 
// possede le bufferReader qui lit les messages venants des utilisateurs
//printWriter envois des messages vers les utilisateurs
//input outputstreams a travers lesquels on envoi les messages 
import java.net.*; 
//talk to other computers via wifi using Socket
import java.util.*;

import server.clientHandler;
//contains List etc
public class serveur 
{
        private final SessionManager sessionManager = new SessionManager();
        private ServerSocket sSocket;
        private static final int serverPort = 8080;
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
                
                while(true) //after each threead creation 
                                            // we make a new thread waiting for another acception 
                {
                    Socket socket = sSocket.accept();
                    System.out.println("SERVER: A new connection has arrived!");
                    ClientHandler newClient = new clientHandler(socket,sessionManager);
                    Thread thread = new Thread(clientHandler);
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
