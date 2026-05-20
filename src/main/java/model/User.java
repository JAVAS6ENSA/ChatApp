package model;

public class User {

    private int     id;
    private String  username;
    private String  phone;       // login identity (E.164); replaces email
    private String  status;      // online / offline / away
    private boolean isBlocked;
    private String  role;        // user / admin

    // ── Profile attributes ───────────────────────────────────────────────
    private String  displayName;     // public self-chosen name (set at registration, editable)
    private String  joinDate;        // date when the user registered
    private String  bio;             // short description shown in profile
    private String  profilePicture;  // legacy: file path or URL to the avatar
    private byte[]  profilePictureData; // raw image bytes (portable across machines)

    // Constructeur complet (lecture BDD)
    public User(int id, String username, String phone,
                String status, boolean isBlocked, String role) {
        this.id        = id;
        this.username  = username;
        this.phone     = phone;
        this.status    = status;
        this.isBlocked = isBlocked;
        this.role      = role;
        this.displayName    = "";
        this.joinDate       = "";
        this.bio            = "";
        this.profilePicture = "";
    }

    // Constructeur simplifié (sans téléphone)
    public User(int id, String username, String status, boolean isBlocked) {
        this(id, username, null, status, isBlocked, "user");
    }

    // Getters
    public int     getId()        { return id; }
    public String  getUsername()  { return username; }
    public String  getPhone()     { return phone; }
    public String  getStatus()    { return status; }
    public boolean isBlocked()    { return isBlocked; }
    public String  getRole()      { return role; }

    public String  getDisplayName()        { return displayName; }
    public String  getJoinDate()           { return joinDate; }
    public String  getBio()                { return bio; }
    public String  getProfilePicture()     { return profilePicture; }
    public byte[]  getProfilePictureData() { return profilePictureData; }

    // Setters
    public void setStatus(String status)       { this.status    = status; }
    public void setBlocked(boolean isBlocked)  { this.isBlocked = isBlocked; }
    public void setRole(String role)           { this.role      = role; }

    public void setDisplayName(String displayName)       { this.displayName = displayName == null ? "" : displayName; }
    public void setJoinDate(String joinDate)             { this.joinDate = joinDate; }
    public void setBio(String bio)                       { this.bio = bio; }
    public void setProfilePicture(String profilePicture) { this.profilePicture = profilePicture; }
    public void setProfilePictureData(byte[] data)       { this.profilePictureData = data; }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username +
                "', status='" + status + "', blocked=" + isBlocked + "}";
    }
}
