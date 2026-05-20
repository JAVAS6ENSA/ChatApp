package model;

import java.time.LocalDateTime;

public class Call {

    private int           id;
    private int           callerId;
    private int           receiverId;   // 0 when it's a group call
    private int           groupId;      // 0 when it's a 1:1 call
    private String        type;         // audio / video
    private int           duration;     // seconds
    private LocalDateTime startedAt;
    private String        status;       // ongoing / ended / missed / refused / cancelled

    public Call(int callerId, int receiverId, String type) {
        this.callerId   = callerId;
        this.receiverId = receiverId;
        this.type       = type;
        this.duration   = 0;
        this.startedAt  = LocalDateTime.now();
        this.status     = "ongoing";
    }

    public Call(int id, int callerId, int receiverId, String type,
                int duration, LocalDateTime startedAt, String status) {
        this.id         = id;
        this.callerId   = callerId;
        this.receiverId = receiverId;
        this.type       = type;
        this.duration   = duration;
        this.startedAt  = startedAt;
        this.status     = status;
    }

    public int           getId()         { return id; }
    public int           getCallerId()   { return callerId; }
    public int           getReceiverId() { return receiverId; }
    public int           getGroupId()    { return groupId; }
    public String        getType()       { return type; }
    public int           getDuration()   { return duration; }
    public LocalDateTime getStartedAt()  { return startedAt; }
    public String        getStatus()     { return status; }

    public void setId(int id)              { this.id       = id; }
    public void setStatus(String status)   { this.status   = status; }
    public void setDuration(int duration)  { this.duration = duration; }
    public void setGroupId(int groupId)    { this.groupId  = groupId; }

    public boolean isGroup() { return groupId > 0; }
    public boolean isAudio() { return "audio".equalsIgnoreCase(type); }
    public boolean isVideo() { return "video".equalsIgnoreCase(type); }

    public String getDurationFormatted() {
        int min = duration / 60;
        int sec = duration % 60;
        if (min >= 60) {
            int h = min / 60;
            min = min % 60;
            return String.format("%dh %02dm %02ds", h, min, sec);
        }
        return String.format("%dm %02ds", min, sec);
    }

    @Override
    public String toString() {
        return "Call{id=" + id + ", from=" + callerId +
                ", to=" + (isGroup() ? "group#" + groupId : "user#" + receiverId) +
                ", type='" + type +
                "', duration=" + getDurationFormatted() +
                ", status='" + status + "'}";
    }
}
