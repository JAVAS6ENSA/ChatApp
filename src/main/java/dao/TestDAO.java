package dao;

import dao.*;
import databases.DBConnection;
import model.*;
import java.sql.Connection;
import java.util.List;

public class TestDAO {
    public static void main(String[] args) {

        // Test connexion
        Connection c = DBConnection.getInstance();
        System.out.println("Connexion : " + (c != null ? " OK" : " FAIL"));

        UserDAO    userDAO = new UserDAO();
        MessageDAO msgDAO  = new MessageDAO();
        GroupDAO   grpDAO  = new GroupDAO();
        CallDAO    callDAO = new CallDAO();

        // Test login
        User u = userDAO.login("youssef@mail.com", "hashed_pw_1");
        System.out.println("Login : " + (u != null ? " " + u : " FAIL"));

        // Test users online
        List<User> online = userDAO.getOnlineUsers();
        System.out.println("Online users : " + online.size());

        // Test message privé
        Message msg = new Message(1, 2, "Salam ! Test message");
        System.out.println("Save message : " + (msgDAO.saveMessage(msg) ? "✓" : "✗"));

        // Test conversation
        List<Message> conv = msgDAO.getConversation(1, 2);
        System.out.println("Conversation 1↔2 : " + conv.size() + " message(s)");

        // Test groupe
        int gid = grpDAO.createGroup("Groupe Test", 1);
        grpDAO.addMember(gid, 2);
        grpDAO.addMember(gid, 3);
        System.out.println("Groupe créé : id=" + gid +
                ", membres=" + grpDAO.getMembers(gid));

        // Test appel
        int cid = callDAO.startCall(1, 2, "audio");
        callDAO.endCall(cid, 120);
        System.out.println("Appel créé et terminé : id=" + cid);

        System.out.println("\n✓ Tous les tests terminés !");
    }
}
