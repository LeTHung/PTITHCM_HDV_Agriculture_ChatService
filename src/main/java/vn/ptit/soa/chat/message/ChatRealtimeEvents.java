package vn.ptit.soa.chat.message;

public final class ChatRealtimeEvents {
    private ChatRealtimeEvents() {}

    public record MessageSent(ChatMessage message, String recipientId) {}

    public record MessageRead(String conversationId, String readerId,
            ChatMessageService.ReadResult result) {}
}
