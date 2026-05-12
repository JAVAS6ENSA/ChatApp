package model;

public class ChatMessage {
    private String sender;
    private String receiver;
    private String type; // TEXT, AUDIO, FILE, IMAGE, VIDEO
    private String content;
    private String time;
    private String status;
    private long timestamp;
    private boolean isEdited;

    public ChatMessage(String sender, String receiver, String type, String content, String time, String status, long timestamp) {
        this.sender = sender;
        this.receiver = receiver;
        this.type = type;
        this.content = content;
        this.time = time;
        this.status = status;
        this.timestamp = timestamp;
        this.isEdited = false;
    }

    public String getSender() { return sender; }
    public String getReceiver() { return receiver; }
    public String getType() { return type; }
    public String getContent() { return content; }
    public String getTime() { return time; }
    public String getStatus() { return status; }
    public long getTimestamp() { return timestamp; }
    public boolean isEdited() { return isEdited; }

    public void setStatus(String status) { this.status = status; }
    public void setContent(String content) { this.content = content; }
    public void setEdited(boolean edited) { this.isEdited = edited; }
    public void setType(String type) { this.type = type; }
}
