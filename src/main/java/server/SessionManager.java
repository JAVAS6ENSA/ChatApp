package server;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
public class SessionManager {
    private static final Map<String, clientHandler> onlineClients = new ConcurrentHashMap<>();

    public static void
    registerClientSession(String username, clientHandler client)
    {
        onlineClients.put(username,client);
        System.out.println("[Session] " + "-> " + username + "is now online"); //for server debug
        System.out.println("Currently online: " + onlineClients.size() +" User"); //for server debug
    }

    public static void removeClientSession(String username)
    {
        onlineClients.remove(username);
        System.out.println("[Session] " + "-> " + username + "is now offline");
        System.out.println("Currently online: " + onlineClients.size() +" User");
    }

    public static boolean isOnline(String username)
    {
        return onlineClients.containsKey(username);
    }

    public static clientHandler getHandler(String username)
    {
        return onlineClients.get(username);
    }

    public static Set<String> getOnlineUsers()
    {
        return Collections.unmodifiableSet(onlineClients.keySet());
    }

    public static void broadcastOnlineList(){
        String list = String.join("-", onlineClients.keySet());
        String message = "Currently Online: " + list;
        for(clientHandler client : onlineClients.values())
        {
           client.envoyerAuClient(message);
        }
    }


}
