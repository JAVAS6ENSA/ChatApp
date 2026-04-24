public class alreadyConnected extends Exception {

    public alreadyConnected()
    {
        super("L'utilisateur est déja connecté dans une autre machine");
    }
    
}
