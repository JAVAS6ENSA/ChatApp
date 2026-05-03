package services.conversation;

import model.ChatMessage;
import services.JSONMessageStore;

/**
 * Group conversation service. The group UI itself is not wired
 * yet, but having this class in place means:
 *
 *   1. The exact same edit/delete UI (right-click menu in
 *      ChatController) lights up automatically the moment the
 *      group view starts using ConversationServiceFactory.
 *   2. The permission policy lives in ONE place — override here
 *      to let admins delete other members' messages, etc.
 *   3. The wire commands are namespaced (GROUP_EDIT / GROUP_DEL)
 *      so the server can route them without confusing the
 *      private path.
 *
 * When groups land, only the server-side handler for
 * GROUP_EDIT / GROUP_DELETE has to be added; nothing in the UI
 * has to change.
 */
public class GroupConversationService extends AbstractConversationService {

    public GroupConversationService(ConversationScope scope, JSONMessageStore store) {
        super(scope, store);
    }

    @Override
    public boolean canEdit(ChatMessage msg, String requester) {
        // Authors can edit their own non-deleted messages.
        // Admin override lives here when group roles are introduced.
        if (msg == null || requester == null) return false;
        if (msg.getType() != null && msg.getType().equalsIgnoreCase("DELETED")) return false;
        return requester.equalsIgnoreCase(msg.getSender());
    }

    @Override
    public boolean canDelete(ChatMessage msg, String requester) {
        if (msg == null || requester == null) return false;
        // Author can always delete their own message. Admins
        // (group_members.role = 'admin') will be allowed here too
        // once the schema exposes that role to the client.
        return requester.equalsIgnoreCase(msg.getSender());
    }

    @Override
    public String editWireCommand(long clientMid, String newContent) {
        return "GROUP_EDIT|" + scope.selfUsername() + "|" + scope.peer()
             + "|MID:" + clientMid + "|" + (newContent == null ? "" : newContent);
    }

    @Override
    public String deleteWireCommand(long clientMid) {
        return "GROUP_DELETE|" + scope.selfUsername() + "|" + scope.peer() + "|MID:" + clientMid;
    }
}
