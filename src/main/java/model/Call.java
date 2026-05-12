package model;

import java.time.LocalDateTime;

public class Call {

    private int           id;
    private int           callerId;
    private int           receiverId;
    private String        type;       // audio / video
    private int           duration;   // en secondes
    private LocalDateTime startedAt;
    private String        status;     // ongoing / ended / missed

    // Constructeur nouveau appel
    public Call(int callerId, int receiverId, String type) {
        this.callerId   = callerId;
        this.receiverId = receiverId;
        this.type       = type;
        this.duration   = 0;
        this.startedAt  = LocalDateTime.now();
        this.status     = "ongoing";
    }

    // Constructeur complet
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

    // Getters
    public int           getId()         { return id; }
    public int           getCallerId()   { return callerId; }
    public int           getReceiverId() { return receiverId; }
    public String        getType()       { return type; }
    public int           getDuration()   { return duration; }
    public LocalDateTime getStartedAt()  { return startedAt; }
    public String        getStatus()     { return status; }

    // Setters
    public void setId(int id)              { this.id       = id; }
    public void setStatus(String status)   { this.status   = status; }
    public void setDuration(int duration)  { this.duration = duration; }

    // Utilitaires
    public String getDurationFormatted() {
        int min = duration / 60;
        int sec = duration % 60;
        return String.format("%02d:%02d", min, sec);
    }
    public boolean isAudio() { return "audio".equals(type); }
    public boolean isVideo() { return "video".equals(type); }

    @Override
    public String toString() {
        return "Call{id=" + id + ", from=" + callerId +
                ", to=" + receiverId + ", type='" + type +
                "', duration=" + getDurationFormatted() +
                ", status='" + status + "'}";
    }
}