package server;

import model.User;
import model.SessionAppel;
import model.StatutAppel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
public class AppelManager {

    // Thread-safe + accès O(1)
    private final Map<String, SessionAppel> appels = new ConcurrentHashMap<>();

    // =========================================================
    // 🔥 O(1) lookup
    // =========================================================
    public SessionAppel getAppelByUser(String username) {
        return appels.get(username);
    }

    public boolean estDejaEnAppel(String username) {
        return appels.containsKey(username);
    }

    // =========================================================
    // 📞 DÉMARRER APPEL (thread-safe)
    // =========================================================
    public boolean demarrerAppel(User caller, User receiver) {

        // éviter auto-appel
        if (caller.getUsername().equals(receiver.getUsername())) {
            return false;
        }

        SessionAppel session = new SessionAppel(caller, receiver);

        // insertion atomique
        SessionAppel c1 = appels.putIfAbsent(caller.getUsername(), session);
        SessionAppel c2 = appels.putIfAbsent(receiver.getUsername(), session);

        if (c1 != null || c2 != null) {
            // rollback sécurisé
            appels.remove(caller.getUsername(), session);
            appels.remove(receiver.getUsername(), session);
            return false;
        }

        return true;
    }

    // =========================================================
    // 📲 ACCEPTER APPEL
    // =========================================================
    public boolean accepterAppel(String username) {

        SessionAppel session = getAppelByUser(username);
        if (session == null) return false;

        synchronized (session) {
            if (!session.getRecepteur().getUsername().equals(username)) {
                return false;
            }

            if (session.getStatut() != StatutAppel.RINGING) {
                return false;
            }

            session.accepter();
            return true;
        }
    }

    // =========================================================
    // ❌ REFUSER APPEL
    // =========================================================
    public boolean refuserAppel(String username) {

        SessionAppel session = getAppelByUser(username);
        if (session == null) return false;

        synchronized (session) {
            if (!session.getRecepteur().getUsername().equals(username)) {
                return false;
            }

            if (session.getStatut() != StatutAppel.RINGING) {
                return false;
            }

            session.refuser();
        }

        nettoyer(session);
        return true;
    }

    // =========================================================
    // 📴 TERMINER APPEL
    // =========================================================
    public boolean terminerAppel(String username) {

        SessionAppel session = getAppelByUser(username);
        if (session == null) return false;

        synchronized (session) {
            if (session.getStatut() != StatutAppel.IN_CALL) {
                return false;
            }

            session.terminer();
        }

        nettoyer(session);
        return true;
    }

    // =========================================================
    // 🧹 Nettoyage sécurisé
    // =========================================================
    private void nettoyer(SessionAppel session) {
        appels.remove(session.getAppelant().getUsername(), session);
        appels.remove(session.getRecepteur().getUsername(), session);
    }
}