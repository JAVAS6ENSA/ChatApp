package Server;

public class Session {

    // ─── Singleton ────────────────────────────────────────────────────
    private static Session instance;

    private Session() {}

    public static Session getInstance() {
        if (instance == null) {
            instance = new Session();
        }
        return instance;
    }

    // ─── Attributs ────────────────────────────────────────────────────
    private String username = null;
    private String role     = null;

    // ─── Login / Logout ───────────────────────────────────────────────
    public void login(String username, String role) {
        this.username = username;
        this.role     = role;
        System.out.println("[Session] Connecté en tant que : " + username + " (" + role + ")");
    }

    public void logout() {
        System.out.println("[Session] Déconnexion de : " + username);
        this.username = null;
        this.role     = null;
        instance      = null; // reset singleton pour permettre reconnexion
    }

    // ─── Getters ──────────────────────────────────────────────────────
    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    // ─── Vérifications ────────────────────────────────────────────────
    public boolean estEnLigne() {
        return username != null;
    }

    public boolean estAdmin() {
        return role != null && role.equals("ADMIN");
    }
}
