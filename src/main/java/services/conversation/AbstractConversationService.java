package services.conversation;

import model.ChatMessage;
import services.JSONMessageStore;

import java.util.List;

/**
 * Shared logic between private and group conversations: storage
 * dispatch + soft-delete bookkeeping. Subclasses only have to
 * provide their permission policy and their wire commands.
 *
 * Why soft-delete? A hard delete looks fine in a single client but
 * desyncs anyone who already loaded the bubble. Flipping the row
 * to type=DELETED keeps the index stable and lets every viewer
 * render the same "this message was deleted" tombstone.
 */
public abstract class AbstractConversationService implements ConversationService {

    protected final ConversationScope scope;
    protected final JSONMessageStore  store;

    protected AbstractConversationService(ConversationScope scope, JSONMessageStore store) {
        this.scope = scope;
        this.store = store;
    }

    @Override public ConversationScope scope() { return scope; }

    @Override
    public void addMessage(ChatMessage msg) {
        store.addMessage(msg, scope.isGroup(), scope.peer());
    }

    @Override
    public List<ChatMessage> getMessages() {
        return store.getConversation(scope.selfUsername(), scope.peer(),
                                     scope.isGroup(), scope.peer());
    }

    @Override
    public boolean editMessage(long clientMid, String newContent, String requester) {
        ChatMessage target = findByMid(clientMid);
        if (target == null) return false;
        if (!canEdit(target, requester)) return false;
        store.editMessage(scope.selfUsername(), scope.peer(),
                          scope.isGroup(), clientMid, newContent);
        return true;
    }

    @Override
    public boolean deleteMessage(long clientMid, String requester) {
        ChatMessage target = findByMid(clientMid);
        if (target == null) return false;
        if (!canDelete(target, requester)) return false;
        store.softDeleteMessage(scope.selfUsername(), scope.peer(),
                                scope.isGroup(), clientMid);
        return true;
    }

    protected ChatMessage findByMid(long mid) {
        for (ChatMessage m : getMessages()) {
            if (m.getTimestamp() == mid) return m;
        }
        return null;
    }
}
