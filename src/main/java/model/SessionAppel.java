package model;

public class SessionAppel {

    private User appelant;
    private User recepteur;
    private StatutAppel statut;
    private String ipAppelant;     // public IP (as the server sees the socket)
    private int portAppelant;
    private String ipRecepteur;    // public IP (as the server sees the socket)
    private int portRecepteur;
    private String lanIpAppelant;  // client-reported LAN IP, for same-NAT calls
    private String lanIpRecepteur;

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

    public String getLanIpAppelant()  { return lanIpAppelant; }
    public String getLanIpRecepteur() { return lanIpRecepteur; }
    public void setLanIpAppelant(String ip)  { this.lanIpAppelant = ip; }
    public void setLanIpRecepteur(String ip) { this.lanIpRecepteur = ip; }

    /**
     * Which IP should the *other* party use to reach this side's audio?
     * When both clients share the same public IP they sit behind the same
     * NAT (same WiFi): direct public→public won't hairpin, so hand over the
     * LAN IP instead. Otherwise (true remote peers) use the public IP.
     */
    public boolean sameNat() {
        return ipAppelant != null && ipAppelant.equals(ipRecepteur);
    }

    /** IP the caller should send audio to (i.e. how to reach the receiver). */
    public String audioIpForAppelant() {
        if (sameNat() && lanIpRecepteur != null && !lanIpRecepteur.isBlank())
            return lanIpRecepteur;
        return ipRecepteur;
    }

    /** IP the receiver should send audio to (i.e. how to reach the caller). */
    public String audioIpForRecepteur() {
        if (sameNat() && lanIpAppelant != null && !lanIpAppelant.isBlank())
            return lanIpAppelant;
        return ipAppelant;
    }
}
