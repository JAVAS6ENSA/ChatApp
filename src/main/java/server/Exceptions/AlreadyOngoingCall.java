package server.Exceptions;

public class AlreadyOngoingCall extends Exception {
    public AlreadyOngoingCall() {
        super("utilisateur déja en appel en cours");
    }
}
