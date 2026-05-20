package server;

import model.User;
import model.SessionAppel;
import model.StatutAppel;
import server.Exceptions.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AppelManager {

    private final Map<String, SessionAppel> appels = new ConcurrentHashMap<>();

    public SessionAppel getAppelByUser(String username) {
        return appels.get(username);
    }

    public boolean estDejaEnAppel(String username) {
        return appels.containsKey(username);
    }

    public boolean demarrerAppel(User caller, User reciever) throws AlreadyOngoingCall {
        if (caller.getUsername().equals(reciever.getUsername())) return false;
        SessionAppel session = new SessionAppel(caller, reciever);
        SessionAppel u1 = appels.putIfAbsent(caller.getUsername(), session);
        SessionAppel u2 = appels.putIfAbsent(reciever.getUsername(), session);
        if (u1 != null || u2 != null) {
            // Roll back any partial insertion (only remove the entries we just added).
            if (u1 == null) appels.remove(caller.getUsername(), session);
            if (u2 == null) appels.remove(reciever.getUsername(), session);
            throw new AlreadyOngoingCall();
        }
        return true;
    }

    public boolean accepterAppel(String reciever) {
        SessionAppel session = getAppelByUser(reciever);
        if (session == null) return false;
        synchronized (session) {
            if (!session.getRecepteur().getUsername().equals(reciever)) return false;
            if (session.getStatut() != StatutAppel.RINGING) return false;
            session.accepter();
            return true;
        }
    }

    public boolean refuserAppel(String reciever) {
        SessionAppel session = getAppelByUser(reciever);
        if (session == null) return false;
        synchronized (session) {
            if (!session.getRecepteur().getUsername().equals(reciever)) return false;
            if (session.getStatut() != StatutAppel.RINGING) return false;
            session.refuser();
            nettoyerSession(session);
            return true;
        }
    }

    /**
     * Caller-side cancellation. Only allowed while the call is still ringing
     * (the recipient has not picked up yet). Returns the cleaned-up session so
     * the caller can notify the recipient.
     */
    public SessionAppel annulerAppel(String caller) {
        SessionAppel session = getAppelByUser(caller);
        if (session == null) return null;
        synchronized (session) {
            if (!session.getAppelant().getUsername().equals(caller)) return null;
            if (session.getStatut() != StatutAppel.RINGING) return null;
            session.refuser();
            nettoyerSession(session);
            return session;
        }
    }

    public boolean terminerAppel(String reciever) {
        SessionAppel session = getAppelByUser(reciever);
        if (session == null) return false;
        synchronized (session) {
            // Allow termination both while ringing AND while connected. Without
            // this, the caller cannot tear down a still-ringing call.
            if (session.getStatut() != StatutAppel.IN_CALL
                    && session.getStatut() != StatutAppel.RINGING) return false;
            session.terminer();
            nettoyerSession(session);
            return true;
        }
    }

    public void nettoyerSession(SessionAppel session) {
        if (session == null) return;
        appels.remove(session.getAppelant().getUsername());
        appels.remove(session.getRecepteur().getUsername());
    }
}
