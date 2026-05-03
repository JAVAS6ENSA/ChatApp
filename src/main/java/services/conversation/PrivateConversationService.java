package services.conversation;

import model.ChatMessage;
import services.JSONMessageStore;

/**
 * Private 1-on-1 conversation. Only the original author is
 * allowed to edit or delete their own messages, and a deleted
 * message stays deleted (no un-delete).
 *
 * The wire format mirrors the existing PRIVATE one:
 *   EDIT|<self>|<peer>|MID:<mid>|<newContent>
 *   DELETE|<self>|<peer>|MID:<mid>
 */
public class PrivateConversationService extends AbstractConversationService {

    public PrivateConversationService(ConversationScope scope, JSONMessageStore store) {
        super(scope, store);
    }

    @Override
    public boolean canEdit(ChatMessage msg, String requester) {
        if (msg == null || requester == null) return false;
        if (msg.getType() != null && msg.getType().equalsIgnoreCase("DELETED")) return false;
        return requester.equalsIgnoreCase(msg.getSender());
    }

    @Override
    public boolean canDelete(ChatMessage msg, String requester) {
        if (msg == null || requester == null) return false;
        return requester.equalsIgnoreCase(msg.getSender());
    }

    @Override
    public String editWireCommand(long clientMid, String newContent) {
        return "EDIT|" + scope.selfUsername() + "|" + scope.peer()
             + "|MID:" + clientMid + "|" + (newContent == null ? "" : newContent);
    }

    @Override
    public String deleteWireCommand(long clientMid) {
        return "DELETE|" + scope.selfUsername() + "|" + scope.peer() + "|MID:" + clientMid;
    }
}
