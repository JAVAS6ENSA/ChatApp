package test.java;

public class User {

    private String username;
    private StatutAppel etat;

    public User(String username) {
        this.username = username;
        this.etat = StatutAppel.LIBRE;
    }

    public String getUsername() {
        return username;
    }

    public StatutAppel getEtat() {
        return etat;
    }

    public void setEtat(StatutAppel etat) {
        this.etat = etat;
    }
//verifeier si l'utilisateur peut recevoir un appel,cad il est disponible ou non
    public boolean estDisponible() {
        return etat == StatutAppel.LIBRE;
    }
}
