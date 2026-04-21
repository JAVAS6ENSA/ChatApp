public class connexionBDD {
    static String url = System.getenv("URL");

    static void afficherInfosConnexion()
    {
        System.out.println(url);
    }

    
}
