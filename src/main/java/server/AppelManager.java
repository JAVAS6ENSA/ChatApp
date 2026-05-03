package server;

import model.User;
import model.SessionAppel;
import model.StatutAppel;
import server.Exceptions.*;

import java.util.Map; //HASHMAP
import java.util.concurrent.ConcurrentHashMap;
public class AppelManager
    {
        //this map contains all current calls
        private final Map<String,SessionAppel> appels = new ConcurrentHashMap<>();

        public SessionAppel getAppelByUser(String username)
        {
            return appels.get(username);
        }

        public boolean estDejaEnAppel(String username)
        {
            return appels.containsKey(username);
        }

        public boolean demarrerAppel(User caller, User reciever) throws AlreadyOngoingCall // i guess it could be easier if we use a client handler for this one
        {
            SessionAppel session = new SessionAppel(caller,reciever);
            if(caller.getUsername().equals(reciever.getUsername())) return false;
            SessionAppel user1 = appels.putIfAbsent(caller.getUsername(),session); //puts each user with his session in the map
            SessionAppel user2 = appels.putIfAbsent(reciever.getUsername(),session);
            //if user in session it returns his session
            //mtn si l un dex deux est deja en appel en cours?
            //ATTENTION : la fonction putIfAbsent returns nulls si ajouté avec succés
            if(user1 != null || user2 != null)
            {
                appels.remove(caller.getUsername(),session);
                appels.remove(reciever.getUsername(),session);
                //ATTENTION cette fonction ne supprime pas l utilsateur de son autre session c juste de la session qu on tante creer
                throw new AlreadyOngoingCall();
            }
            return true;
        }
        //now lets suppose it is rigning
        public boolean accepterAppel(String reciever) //coté accepteur (username de reciever)
        {
            SessionAppel session = getAppelByUser(reciever); //is is calling?
            if(session == null) return false;
            //this synchronized keyword tells me not this session is no accessed by other threads
            synchronized (session)
            {
                if(!session.getRecepteur().getUsername().equals(reciever)) return false;
                if (session.getStatut() != StatutAppel.RINGING) return false;
                session.accepter();
                return true;
            }
            //TODO FACTORIZE THIS BLOCK IT IS REPETITIVE BETWEEN ACCEPTER AND REFUSER
        }


        public boolean refuserAppel(String reciever) //coté accepteur (username de reciever)
        {
            SessionAppel session = getAppelByUser(reciever); //is is calling?
            if(session == null) return false;
            //this synchronized keyword tells me not this session is no accessed by other threads
            synchronized (session)
            {
                if(!session.getRecepteur().getUsername().equals(reciever)) return false;
                if (session.getStatut() != StatutAppel.RINGING) return false;
                session.refuser();
                nettoyerSession(session);
                return true;
            }

            //TODO FACTORIZE THIS BLOCK IT IS REPETITIVE
        }

        public boolean terminerAppel(String reciever)
        {
            SessionAppel session = getAppelByUser(reciever);
            if(session == null) return false;
            synchronized (session)
            {
                if(session.getStatut() != StatutAppel.IN_CALL) return false;
                session.terminer();
                nettoyerSession(session);
                return true;
            }
        }

        public void nettoyerSession(SessionAppel session)
        {
            if(session == null) return;
            appels.remove(session.getAppelant().getUsername());
            appels.remove(session.getRecepteur().getUsername());
        }
    }