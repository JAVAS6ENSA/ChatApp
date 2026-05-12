package model;

public enum StatutAppel {
    LIBRE,
    RINGING,
    IN_CALL,
    REFUSED,
    ENDED;
    public boolean estOccupe() {
        return this == RINGING || this == IN_CALL;
    }
}
