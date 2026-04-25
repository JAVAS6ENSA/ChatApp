package test.java;

import java.io.Serializable;

public class ClassMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    // Types de messages (actions)
    public static final String CALL_REQUEST = "CALL_REQUEST";
    public static final String CALL_ACCEPT  = "CALL_ACCEPT";
    public static final String CALL_REFUSE  = "CALL_REFUSE";
    public static final String CALL_END     = "CALL_END";

    private String type;
    private String from;
    private String to;
    private String callId; // optionnel mais recommandé

    // 🔹 Constructeur principal
    public CallMessage(String type, String from, String to) {
        this.type = type;
        this.from = from;
        this.to = to;
    }

    // 🔹 Constructeur avec callId (version améliorée)
    public CallMessage(String type, String from, String to, String callId) {
        this.type = type;
        this.from = from;
        this.to = to;
        this.callId = callId;
    }

    // 🔹 Getters
    public String getType() {
        return type;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public String getCallId() {
        return callId;
    }

    // 🔹 Setters (optionnel mais utile)
    public void setType(String type) {
        this.type = type;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    // 🔹 toString (debug très utile)
    @Override
    public String toString() {
        return "CallMessage{" +
                "type='" + type + '\'' +
                ", from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", callId='" + callId + '\'' +
                '}';
    }
}