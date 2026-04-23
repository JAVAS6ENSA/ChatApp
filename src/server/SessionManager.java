import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
public class SessionManager {
    private final Map<String,ClientHandler> onlineClients = new ConcurrentHashMap<>();

    public void registerClientSession(String username,ClientHandler client)
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

    public ClientHandler getHandler(String username)
    {
        return onlineClients.get(username);
    }

    public Set<String> getOnlineUsernames()
    {
        return Collections.unmodifiableSet(onlineClients.keySet());
    }

    private void broadcastOnlineList(){
        String list = String.join("-",onlineClient.keySet());
        String message = "Currently Online: " + list;
        for(ClientHandler client : onlineClients)
        {
            h.sendToClient(msg);
        }
    }

}
