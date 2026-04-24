import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
public class SessionManager {
    private static final Map<String,clientHandler> onlineClients = new ConcurrentHashMap<>();

    public static void registerClientSession(String username,clientHandler client)
    {
        onlineClients.put(username,client);
        System.out.println("[Session] " + "-> " + username + "is now online");
        System.out.println("Currently online: " + onlineClients.size() +" User");
    }

    public void removeClientSession(String username)
    {
        onlineClients.remove(username);
        System.out.println("[Session] " + "-> " + username + "is now offline");
        System.out.println("Currently online: " + onlineClients.size() +" User");
    }

    public boolean isOnline(String username)
    {
        return onlineClients.containsKey(username);
    }

    public clientHandler getHandler(String username)
    {
        return onlineClients.get(username);
    }

    public Set<String> getOnlineUsernames()
    {
        return Collections.unmodifiableSet(onlineClients.keySet());
    }

    private void broadcastOnlineList(){
        String list = String.join("-", onlineClients.keySet());
        String message = "Currently Online: " + list;
        for(clientHandler client : onlineClients.values())
        {
           // client.sendToClient(message);
        }
    }

}
