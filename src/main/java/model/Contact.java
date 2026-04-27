package model;

public class Contact {
    public int id;
    public String username;
    public boolean isOnline;
    public boolean isBlocked;
    public boolean isGroup;
    public String dateCreated;
    public long lastMessageTime;
    public String lastMessagePreview;
    public int unreadCount;
    
    public Contact(int id, String u, boolean on, boolean bl, boolean grp, String d) {
        this.id=id; this.username=u; this.isOnline=on; this.isBlocked=bl; this.isGroup=grp; this.dateCreated=d;
        this.lastMessageTime = 0;
        this.lastMessagePreview = "Dernier message ici...";
        this.unreadCount = 0;
    }
}
