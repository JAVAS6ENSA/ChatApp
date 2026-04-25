package model;

public class SessionAppel {

    private User appelant;
    private User recepteur;
    private StatutAppel statut;

    public SessionAppel(User appelant, User recepteur) {
        this.appelant = appelant;
        this.recepteur = recepteur;
        this.statut = StatutAppel.RINGING;
    }

    public User getAppelant() {
        return appelant;
    }

    public User getRecepteur() {
        return recepteur;
    }

    public StatutAppel getStatut() {
        return statut;
    }

    public void accepter() {
        statut = StatutAppel.IN_CALL;
    }

    public void refuser() {
        statut = StatutAppel.REFUSED;
    }

    public void terminer() {
        statut = StatutAppel.ENDED;
    }
}
