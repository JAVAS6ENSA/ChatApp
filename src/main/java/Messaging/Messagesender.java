package Messaging;

import java.io.*;
import java.net.Socket;

public class Messagesender {

    private PrintWriter out;

    public Messagesender(Socket socket) throws IOException {
        this.out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
    }

    public void sendPrivateMessage(int senderId, int receiverId, String content) {
        String msg = "PRIVATE|" + senderId + "|" + receiverId + "|" + content;
        out.println(msg);
    }

    public void sendGroupMessage(int senderId, int groupId, String content) {
        String msg = "GROUP|" + senderId + "|" + groupId + "|" + content;
        out.println(msg);
    }


    public void sendLogin(String email, String password) {
        out.println("LOGIN|" + email + "|" + password);
    }


    public void sendLogout(int userId) {
        out.println("LOGOUT|" + userId);
    }


    public void send(String rawMessage) {
        out.println(rawMessage);
    }

    public void close() {
        if (out != null) out.close();
    }
}