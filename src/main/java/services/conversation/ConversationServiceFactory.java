package services.conversation;

import services.JSONMessageStore;

/**
 * One entry point. The UI never instantiates a concrete
 * ConversationService; it asks the factory and gets back the
 * correct one for the current chat. Adding a new conversation
 * kind later (e.g. broadcast channel) only touches this class.
 */
public class ConversationServiceFactory {

    private final JSONMessageStore store;

    public ConversationServiceFactory(JSONMessageStore store) {
        this.store = store;
    }

    public ConversationService forPrivate(String selfUsername, String peerUsername) {
        return new PrivateConversationService(
                ConversationScope.privateChat(selfUsername, peerUsername), store);
    }

    public ConversationService forGroup(String selfUsername, String groupName) {
        return new GroupConversationService(
                ConversationScope.group(selfUsername, groupName), store);
    }
}
