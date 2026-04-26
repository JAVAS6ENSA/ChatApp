package model;

public class User {

    private int     id;
    private String  username;
    private String  email;
    private String  status;      // online / offline / away
    private boolean isBlocked;
    private String  role;        // user / admin

    // Constructeur complet (lecture BDD)
    public User(int id, String username, String email,
                String status, boolean isBlocked, String role) {
        this.id        = id;
        this.username  = username;
        this.email     = email;
        this.status    = status;
        this.isBlocked = isBlocked;
        this.role      = role;
    }

    // Constructeur simplifié (sans email)
    public User(int id, String username, String status, boolean isBlocked) {
        this(id, username, null, status, isBlocked, "user");
    }

    // Getters
    public int     getId()        { return id; }
    public String  getUsername()  { return username; }
    public String  getEmail()     { return email; }
    public String  getStatus()    { return status; }
    public boolean isBlocked()    { return isBlocked; }
    public String  getRole()      { return role; }

    // Setters
    public void setStatus(String status)       { this.status    = status; }
    public void setBlocked(boolean isBlocked)  { this.isBlocked = isBlocked; }
    public void setRole(String role)           { this.role      = role; }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username +
                "', status='" + status + "', blocked=" + isBlocked + "}";
    }
}