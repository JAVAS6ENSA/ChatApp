package services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import model.ChatMessage;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;

public class JSONMessageStore {

    private static final String FILE_PATH = "data_messages.json";
    private Map<String, List<ChatMessage>> database;
    private Set<String> blockedUsers;
    private Set<String> hiddenConversations;
    private Gson gson;

    private static class StorageWrapper {
        Map<String, List<ChatMessage>> messages = new HashMap<>();
        Set<String> blockedUsers = new HashSet<>();
        Set<String> hiddenConversations = new HashSet<>();
    }

    public JSONMessageStore() {
        this.database = new HashMap<>();
        this.blockedUsers = new HashSet<>();
        this.hiddenConversations = new HashSet<>();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        load();
    }

    public void addMessage(ChatMessage msg, boolean isGroup, String groupName) {
        String key = isGroup ? getGroupKey(groupName) : getConversationKey(msg.getSender(), msg.getReceiver());
        database.computeIfAbsent(key, k -> new ArrayList<>()).add(msg);
        
        // Si un message arrive, on dé-cache la conversation
        if (hiddenConversations.contains(key)) {
            hiddenConversations.remove(key);
        }
        save();
    }

    public List<ChatMessage> getConversation(String user1, String user2, boolean isGroup, String groupName) {
        String key = isGroup ? getGroupKey(groupName) : getConversationKey(user1, user2);
        return database.getOrDefault(key, new ArrayList<>());
    }
    
    // --- NOUVELLES OPTIONS --- //
    
    public void editMessage(String user1, String user2, boolean isGroup, long targetTimestamp, String newContent) {
        String key = isGroup ? getGroupKey(user2) : getConversationKey(user1, user2);
        List<ChatMessage> list = database.get(key);
        if (list != null) {
            for (ChatMessage m : list) {
                if (m.getTimestamp() == targetTimestamp) {
                    m.setContent(newContent);
                    m.setEdited(true);
                    save();
                    return;
                }
            }
        }
    }
    
    public void deleteMessage(String user1, String user2, boolean isGroup, long targetTimestamp) {
        String key = isGroup ? getGroupKey(user2) : getConversationKey(user1, user2);
        List<ChatMessage> list = database.get(key);
        if (list != null) {
            list.removeIf(m -> m.getTimestamp() == targetTimestamp);
            save();
        }
    }
    
    public void deleteConversation(String user1, String user2, boolean isGroup) {
        String key = isGroup ? getGroupKey(user2) : getConversationKey(user1, user2);
        hiddenConversations.add(key);
        save();
    }
    
    public boolean isHidden(String user1, String user2, boolean isGroup) {
        String key = isGroup ? getGroupKey(user2) : getConversationKey(user1, user2);
        return hiddenConversations.contains(key);
    }
    
    public void blockUser(String user) {
        blockedUsers.add(user);
        save();
    }
    
    public void unblockUser(String user) {
        blockedUsers.remove(user);
        save();
    }
    
    public boolean isBlocked(String user) {
        return blockedUsers.contains(user);
    }

    // --- INTERNES --- //

    private String getConversationKey(String u1, String u2) {
        String[] users = {u1, u2};
        Arrays.sort(users);
        return users[0] + "_" + users[1];
    }

    private String getGroupKey(String groupName) {
        return "GROUP_" + groupName;
    }

    private void save() {
        try (FileWriter writer = new FileWriter(FILE_PATH)) {
            StorageWrapper wrap = new StorageWrapper();
            wrap.messages = this.database;
            wrap.blockedUsers = this.blockedUsers;
            wrap.hiddenConversations = this.hiddenConversations;
            gson.toJson(wrap, writer);
        } catch (Exception e) {
            System.err.println("Erreur sauvegarde JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void load() {
        File file = new File(FILE_PATH);
        if (!file.exists()) return;
        
        try (FileReader reader = new FileReader(file)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root.isJsonObject() && root.getAsJsonObject().has("messages")) {
                // Nouveau Format
                StorageWrapper wrap = gson.fromJson(root, StorageWrapper.class);
                if (wrap.messages != null) database = wrap.messages;
                if (wrap.blockedUsers != null) this.blockedUsers = wrap.blockedUsers;
                if (wrap.hiddenConversations != null) this.hiddenConversations = wrap.hiddenConversations;
            } else if (root.isJsonObject()) {
                // Ancien format (Migration silencieuse)
                Type mapType = new TypeToken<Map<String, List<ChatMessage>>>() {}.getType();
                Map<String, List<ChatMessage>> oldMap = gson.fromJson(root, mapType);
                if (oldMap != null) database = oldMap;
            }
        } catch (Exception e) {
            System.err.println("Erreur lecture JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
