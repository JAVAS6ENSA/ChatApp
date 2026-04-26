public class session {
    private static session instance;
    private String username;
    private String role;
    private static session getInstance()
    {
        if(instance == null) return new session();
        return instance;
    }

    private session(){

    }

    public void login(String username, String role)
    {
        this.username = username;
        this.role = role;
    }

    public void lougour()
    {
        this.username = null;
        this.role = null;
    }

    public boolean estEnLigne() 
    { 
        return username!=null; 
    }

    public boolean estAdmin()
    {
        return role.equals("ADMIN");
    }

    public String getUsername()
    {
        return username;
    }

    public String getRole()
    {
        return role;
    }


}
