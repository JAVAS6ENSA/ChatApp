package model;

public class SessionAppel {

    private User appelant;
    private User recepteur;
    private StatutAppel statut;
    private String ipAppelant;
    private int portAppelant;
    private String ipRecepteur;
    private int portRecepteur;

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

    public String getIpAppelant() {
        return ipAppelant;
    }

    public int getPortAppelant() {
        return portAppelant;
    }

    public String getIpRecepteur() {
        return ipRecepteur;
    }

    public int getPortRecepteur() {
        return portRecepteur;
    }

    public void setInfosAudioAppelant(String ipAppelant, int portAppelant) {
        this.ipAppelant = ipAppelant;
        this.portAppelant = portAppelant;
    }

    public void setInfosAudioRecepteur(String ipRecepteur, int portRecepteur) {
        this.ipRecepteur = ipRecepteur;
        this.portRecepteur = portRecepteur;
    }
}
