package server;
import java.io.*;
import java.net.Socket;

public class clientHandler implements Runnable{
    private final Socket socket;
    private final SessionManager sessionManager;
    private printWrite out;
    private BufferedWriter in;
    private String username = null,
    private String role = null;

    public clientHandler(Socket socket,SessionManager sessionManager)
    {
        this.socket = socket;
        this.sessionManager = sessionManager;
    }
}

