package test.java;

public enum StatutAppel {
    LIBRE,
    RINGING,
    IN_CALL,
    REFUSED,
    ENDED;
//etat d'oocupation
    public boolean estOccupe() {
        return this == RINGING || this == IN_CALL;
    }
}

