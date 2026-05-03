package services.conversation;

/**
 * Identifies one conversation. Carries everything any
 * ConversationService needs to compute its storage key, render
 * a header and authorise actions, regardless of whether the
 * conversation is private or a group.
 *
 * Private  → kind=PRIVATE, peer = the other user's username.
 * Group    → kind=GROUP,   peer = the group name (used today by
 *                         JSONMessageStore as "GROUP_<name>").
 */
public final class ConversationScope {

    public enum Kind { PRIVATE, GROUP }

    private final Kind   kind;
    private final String selfUsername;
    private final String peer;

    private ConversationScope(Kind kind, String selfUsername, String peer) {
        this.kind         = kind;
        this.selfUsername = selfUsername;
        this.peer         = peer;
    }

    public static ConversationScope privateChat(String self, String peer) {
        return new ConversationScope(Kind.PRIVATE, self, peer);
    }

    public static ConversationScope group(String self, String groupName) {
        return new ConversationScope(Kind.GROUP, self, groupName);
    }

    public Kind   kind()         { return kind; }
    public String selfUsername() { return selfUsername; }
    public String peer()         { return peer; }
    public boolean isGroup()     { return kind == Kind.GROUP; }
}
