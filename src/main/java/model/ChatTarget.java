package model;

import java.util.List;

/**
 * Unifies a 1:1 chat (contact) and a group chat behind a single handle the UI
 * can render and route messages to. Use the factory methods, never the
 * constructor directly.
 */
public class ChatTarget {

    public enum Kind { CONTACT, GROUP }

    public final Kind kind;
    public final Contact contact;
    public final Group group;

    public boolean isOnline;        // valid for CONTACT
    public int unreadCount;
    public String lastMessagePreview;
    public long lastMessageTime;

    private ChatTarget(Kind kind, Contact contact, Group group) {
        this.kind = kind;
        this.contact = contact;
        this.group = group;
        if (contact != null) {
            this.isOnline = contact.isOnline;
            this.unreadCount = contact.unreadCount;
            this.lastMessagePreview = contact.lastMessagePreview;
            this.lastMessageTime = contact.lastMessageTime;
        }
    }

    public static ChatTarget ofContact(Contact c) {
        return new ChatTarget(Kind.CONTACT, c, null);
    }

    public static ChatTarget ofGroup(Group g) {
        return new ChatTarget(Kind.GROUP, null, g);
    }

    public boolean isGroup()   { return kind == Kind.GROUP; }
    public boolean isContact() { return kind == Kind.CONTACT; }

    public String displayName() {
        return kind == Kind.GROUP ? group.getName() : contact.username;
    }

    public int peerId() {
        return kind == Kind.GROUP ? group.getId() : contact.id;
    }

    /** Stable key used to identify the conversation (e.g. for unread maps). */
    public String key() {
        return kind == Kind.GROUP ? "G:" + group.getId()
                                  : "U:" + contact.username.toLowerCase();
    }
}
