package test.java;

public class SessionAppel {

    private User appelant;
    private User recepteur;
    private StatutAppel statut;

    public SessionAppel(User appelant, User recepteur) {
        this.appelant = appelant;
        this.recepteur = recepteur;
        this.statut = StatutAppel.RINGING;

        appelant.setEtat(StatutAppel.RINGING);
        recepteur.setEtat(StatutAppel.RINGING);
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
        appelant.setEtat(StatutAppel.IN_CALL);
        recepteur.setEtat(StatutAppel.IN_CALL);
    }

    public void refuser() {
        statut = StatutAppel.REFUSED;
        appelant.setEtat(StatutAppel.LIBRE);
        recepteur.setEtat(StatutAppel.LIBRE);
    }

    public void terminer() {
        statut = StatutAppel.ENDED;
        appelant.setEtat(StatutAppel.LIBRE);
        recepteur.setEtat(StatutAppel.LIBRE);
    }
}
