package services.conversation;

import model.ChatMessage;

import java.util.List;

/**
 * The single contract the UI talks to for any conversation.
 *
 * Two implementations exist:
 *   - PrivateConversationService : 1-on-1 chats (active today).
 *   - GroupConversationService   : group chats (placeholder; will
 *                                  be filled in when groups are
 *                                  wired in the UI).
 *
 * Keeping them behind the same interface means the chat controller
 * can render bubbles, edit them and delete them without ever
 * branching on "is this private or a group?". When groups land,
 * the only thing that changes upstream is the factory call.
 */
public interface ConversationService {

    ConversationScope scope();

    /** Append a freshly-sent or freshly-received message. */
    void addMessage(ChatMessage msg);

    /** Full ordered history, including soft-deleted tombstones. */
    List<ChatMessage> getMessages();

    /**
     * Replace the content of a message identified by its client mid.
     * Returns true on success, false if the policy refused (not the
     * author, message already deleted, etc).
     */
    boolean editMessage(long clientMid, String newContent, String requester);

    /**
     * Soft-delete a message: keeps the row but flips its type to
     * DELETED and clears the content so the UI can show a
     * tombstone consistently for every viewer.
     */
    boolean deleteMessage(long clientMid, String requester);

    /** Wire formatter for the underlying transport (server protocol). */
    String editWireCommand(long clientMid, String newContent);

    /** Wire formatter for the underlying transport (server protocol). */
    String deleteWireCommand(long clientMid);

    /** Permission policy hooks — group impl will tighten/loosen these. */
    boolean canEdit(ChatMessage msg, String requester);
    boolean canDelete(ChatMessage msg, String requester);
}
