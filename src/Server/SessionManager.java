package Server;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    // ─── Maps thread-safe ─────────────────────────────────────────────
    private static final Map<String, clientHandler> onlineClients = new ConcurrentHashMap<>();
    private static final Map<String, User>          userObjects   = new ConcurrentHashMap<>();

    // ─── Enregistrement ───────────────────────────────────────────────
    public static void registerClientSession(String username, clientHandler client) {
        onlineClients.put(username, client);
        userObjects.put(username, new User(username));
        System.out.println("[SessionManager] " + username + " est maintenant en ligne.");
        System.out.println("[SessionManager] Connectés : " + onlineClients.size());
        broadcastOnlineList();
    }

    // ─── Suppression ──────────────────────────────────────────────────
    public static void removeClientSession(String username) {
        onlineClients.remove(username);
        userObjects.remove(username);
        System.out.println("[SessionManager] " + username + " est maintenant hors ligne.");
        System.out.println("[SessionManager] Connectés : " + onlineClients.size());
        broadcastOnlineList();
    }

    // ─── Getters ──────────────────────────────────────────────────────
    public static boolean isOnline(String username) {
        return onlineClients.containsKey(username);
    }

    public static clientHandler getHandler(String username) {
        return onlineClients.get(username);
    }

    public static User getUser(String username) {
        return userObjects.get(username);
    }

    public static Set<String> getOnlineUsers() {
        return Collections.unmodifiableSet(onlineClients.keySet());
    }

    // ─── Broadcast liste en ligne à tous les clients ──────────────────
    private static void broadcastOnlineList() {
        String list    = String.join(",", onlineClients.keySet());
        String message = "ONLINE_LIST|" + list;
        for (clientHandler client : onlineClients.values()) {
            client.envoyerAuClient(message);
        }
    }
}
