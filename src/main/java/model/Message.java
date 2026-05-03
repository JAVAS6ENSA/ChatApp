package model;

import java.time.LocalDateTime;

public class Message {

    private int           id;
    private int           senderId;
    private int           receiverId;   // 0 si message de groupe
    private int           groupId;      // 0 si message privé
    private String        content;
    private String        type;         // TEXT / IMAGE / AUDIO / VIDEO / FILE / DELETED
    private String        status;       // sent / delivered / read
    private LocalDateTime dateMsg;
    private long          clientMid;    // identifiant côté client (System.currentTimeMillis)
    private boolean       edited;
    private boolean       deleted;

    // Constructeur pour nouveau message privé
    public Message(int senderId, int receiverId, String content) {
        this.senderId   = senderId;
        this.receiverId = receiverId;
        this.groupId    = 0;
        this.content    = content;
        this.type       = "text";
        this.status     = "sent";
        this.dateMsg    = LocalDateTime.now();
    }

    // Constructeur pour nouveau message de groupe
    public Message(int senderId, int groupId, String content, boolean isGroup) {
        this.senderId   = senderId;
        this.receiverId = 0;
        this.groupId    = groupId;
        this.content    = content;
        this.type       = "text";
        this.status     = "sent";
        this.dateMsg    = LocalDateTime.now();
    }

    // Constructeur complet (lecture BDD)
    public Message(int id, int senderId, int receiverId, int groupId,
                   String content, String type, String status) {
        this.id         = id;
        this.senderId   = senderId;
        this.receiverId = receiverId;
        this.groupId    = groupId;
        this.content    = content;
        this.type       = type;
        this.status     = status;
        this.dateMsg    = LocalDateTime.now();
    }

    // Getters
    public int           getId()          { return id; }
    public int           getSenderId()    { return senderId; }
    public int           getReceiverId()  { return receiverId; }
    public int           getGroupId()     { return groupId; }
    public String        getContent()     { return content; }
    public String        getType()        { return type; }
    public String        getStatus()      { return status; }
    public LocalDateTime getDateMsg()     { return dateMsg; }
    public long          getClientMid()   { return clientMid; }
    public boolean       isEdited()       { return edited; }
    public boolean       isDeleted()      { return deleted; }
    public String        getFormattedTime() {
        if (dateMsg == null) return "";
        return dateMsg.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }

    // Setters
    public void setId(int id)                 { this.id      = id; }
    public void setStatus(String status)      { this.status  = status; }
    public void setDateMsg(LocalDateTime d)   { this.dateMsg = d; }
    public void setContent(String content)    { this.content = content; }
    public void setType(String type)          { this.type    = type; }
    public void setClientMid(long mid)        { this.clientMid = mid; }
    public void setEdited(boolean edited)     { this.edited  = edited; }
    public void setDeleted(boolean deleted)   { this.deleted = deleted; }

    public boolean isPrivate() { return groupId == 0; }
    public boolean isGroup()   { return groupId != 0; }

    @Override
    public String toString() {
        return "Message{id=" + id + ", from=" + senderId +
                ", to=" + (isGroup() ? "group#" + groupId : "user#" + receiverId) +
                ", content='" + content + "', status='" + status + "'}";
    }
}