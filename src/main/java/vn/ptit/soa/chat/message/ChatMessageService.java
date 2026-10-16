package vn.ptit.soa.chat.message;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.soa.chat.conversation.Conversation;
import vn.ptit.soa.chat.conversation.ConversationRepository;
import vn.ptit.soa.chat.media.ChatMediaService;
import vn.ptit.soa.chat.media.MediaAssetRepository;
import java.util.Map;

@Service
@Profile("database-learning")
public class ChatMessageService {
    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final MediaAssetRepository media;
    private final ChatOutboxRepository outbox;

    public ChatMessageService(ConversationRepository conversations, ChatMessageRepository messages,
            MediaAssetRepository media, ChatOutboxRepository outbox) {
        this.conversations = conversations;
        this.messages = messages;
        this.media = media;
        this.outbox = outbox;
    }

    // Learning-only path: the production sender must come from a verified JWT.
    @Transactional
    public ChatMessage sendText(String conversationId, String senderId, String content) {
        String roomId = uuid(conversationId);
        String sender = uuid(senderId);
        Conversation room = conversations.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation does not exist."));
        if (!room.participantIds().contains(sender)) {
            throw new IllegalArgumentException("Sender is not a conversation participant.");
        }
        if (!"ACTIVE".equals(room.status())) {
            throw new IllegalArgumentException("Conversation is closed.");
        }
        if (content == null || content.isBlank() || content.codePointCount(0, content.length()) > 2000) {
            throw new IllegalArgumentException("TEXT content must contain 1 to 2000 characters.");
        }
        Instant now = Instant.now();
        ChatMessage message = new ChatMessage(UUID.randomUUID().toString(), roomId, sender,
                "TEXT", content, null, null, false, now, null);
        ChatMessage saved = messages.insert(message);
        conversations.touch(roomId, now);
        writeEvent(room, saved);
        return saved;
    }

    @Transactional
    public ChatMessage sendImage(String conversationId, String senderId, String attachmentUrl) {
        String roomId = uuid(conversationId);
        String sender = uuid(senderId);
        Conversation room = conversations.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation does not exist."));
        if (!room.participantIds().contains(sender))
            throw new IllegalArgumentException("Sender is not a conversation participant.");
        if (!"ACTIVE".equals(room.status())) throw new IllegalArgumentException("Conversation is closed.");
        String prefix = "/learning/conversations/" + roomId + "/media/";
        if (attachmentUrl == null || !attachmentUrl.startsWith(prefix))
            throw new IllegalArgumentException("IMAGE requires a Chat upload URL for this conversation.");
        String mediaId = uuid(attachmentUrl.substring(prefix.length()));
        if (!attachmentUrl.equals(ChatMediaService.url(roomId, mediaId)))
            throw new IllegalArgumentException("Invalid Chat upload URL.");
        Instant now = Instant.now();
        if (!media.attach(mediaId, roomId, sender, now))
            throw new IllegalArgumentException("Image is missing, belongs to someone else, or was already sent.");
        ChatMessage message = new ChatMessage(UUID.randomUUID().toString(), roomId, sender,
                "IMAGE", null, attachmentUrl, null, false, now, null);
        ChatMessage saved = messages.insert(message);
        conversations.touch(roomId, now);
        writeEvent(room, saved);
        return saved;
    }

    private void writeEvent(Conversation room, ChatMessage message) {
        List<String> recipients = room.participantIds().stream()
                .filter(id -> !id.equals(message.senderId())).toList();
        if (recipients.isEmpty()) throw new IllegalStateException("Conversation has no message recipient.");
        outbox.insert(new ChatOutboxEvent(UUID.randomUUID().toString(), "MESSAGE_CREATED", 1,
                "chat-service", message.createdAt(), message.senderId(), recipients,
                "CONVERSATION", message.conversationId(), "soa.chat.message.v1",
                message.conversationId(), Map.of("messageId", message.messageId(),
                    "conversationId", message.conversationId(), "senderId", message.senderId(),
                    "recipientId", recipients.get(0)), null));
    }

    public ReadResult markRead(String conversationId, String readerId, String messageId) {
        String roomId = uuid(conversationId);
        String reader = uuid(readerId);
        String message = uuid(messageId);
        Conversation room = conversations.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation does not exist."));
        if (!room.participantIds().contains(reader)) {
            throw new IllegalArgumentException("Reader is not a conversation participant.");
        }
        if (!"ACTIVE".equals(room.status())) {
            throw new IllegalArgumentException("Conversation is closed.");
        }
        ChatMessage target = messages.findById(message)
                .filter(found -> roomId.equals(found.conversationId()))
                .orElseThrow(() -> new IllegalArgumentException("Message does not exist in this conversation."));
        if (reader.equals(target.senderId())) {
            throw new IllegalArgumentException("A sender cannot mark their own message as read.");
        }
        if (!target.isRead()) {
            target = messages.markReadIfUnread(message, roomId, Instant.now())
                    .orElseGet(() -> messages.findById(message).orElseThrow());
        }
        return new ReadResult(target.messageId(), target.isRead(), target.readAt(),
                messages.countUnreadFromOthers(roomId, reader));
    }

    public record ReadResult(String messageId, boolean isRead, Instant readAt, long unreadCount) {}

    public MessagePage history(String conversationId, String viewerId, int limit, String cursor) {
        String roomId = uuid(conversationId);
        String viewer = uuid(viewerId);
        Conversation room = conversations.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation does not exist."));
        if (!room.participantIds().contains(viewer)) {
            throw new IllegalArgumentException("Viewer is not a conversation participant.");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Limit must be between 1 and 100.");
        }
        MessageCursor before = decodeCursor(cursor);
        List<ChatMessage> found = messages.listNewest(roomId,
                before == null ? null : before.createdAt(),
                before == null ? null : before.messageId(), limit + 1);
        boolean hasMore = found.size() > limit;
        List<ChatMessage> page = hasMore ? found.subList(0, limit) : found;
        String nextCursor = hasMore ? encodeCursor(page.get(page.size() - 1)) : null;
        return new MessagePage(page, nextCursor);
    }

    public record MessagePage(List<ChatMessage> data, String nextCursor) {}

    private record MessageCursor(Instant createdAt, String messageId) {}

    private String encodeCursor(ChatMessage last) {
        String value = last.createdAt().toEpochMilli() + "|" + last.messageId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private MessageCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        if (cursor.length() > 200) throw new IllegalArgumentException("Invalid cursor.");
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            Instant time = Instant.ofEpochMilli(Long.parseLong(parts[0]));
            return new MessageCursor(time, uuid(parts[1]));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid cursor.");
        }
    }

    private String uuid(String value) {
        if (value == null) throw new IllegalArgumentException("ID must be a UUID.");
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return parsed.toString();
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("ID must be a UUID.");
        }
    }
}
