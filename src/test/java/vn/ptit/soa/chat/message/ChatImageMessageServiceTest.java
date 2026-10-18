package vn.ptit.soa.chat.message;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import vn.ptit.soa.chat.conversation.Conversation;
import vn.ptit.soa.chat.conversation.ConversationRepository;
import vn.ptit.soa.chat.media.ChatMediaService;
import vn.ptit.soa.chat.media.MediaAssetRepository;

class ChatImageMessageServiceTest {
    private static final String ROOM = "f72a08bd-33d7-4221-9e55-55d5d7f2e2f0";
    private static final String SENDER = "233196d7-d149-451d-8006-91e6563be4f0";
    private static final String RECIPIENT = "e14961fb-7e5a-4613-8837-8cadf6a887de";
    private static final String MEDIA = "9cd756ec-53a1-461c-9960-6c648b157853";
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final MediaAssetRepository media = mock(MediaAssetRepository.class);
    private final ChatOutboxRepository outbox = mock(ChatOutboxRepository.class);
    private ChatMessageService service;

    @BeforeEach
    void setup() {
        service = new ChatMessageService(conversations, messages, media, outbox,
                mock(ApplicationEventPublisher.class));
        when(conversations.findById(ROOM)).thenReturn(Optional.of(new Conversation(ROOM,
                List.of(SENDER, RECIPIENT), null, null, "ACTIVE", Instant.now(), Instant.now())));
        when(messages.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void imageMustBeUnusedAndOwnedBySender() {
        String url = ChatMediaService.url(ROOM, MEDIA);
        assertThrows(IllegalArgumentException.class, () -> service.sendImage(ROOM, SENDER, url));
        verify(messages, never()).insert(any());
        verify(outbox, never()).insert(any());
        when(media.attach(eq(MEDIA), eq(ROOM), eq(SENDER), any())).thenReturn(true);
        ChatMessage sent = service.sendImage(ROOM, SENDER, url);
        assertEquals("IMAGE", sent.messageType());
        assertEquals(url, sent.attachmentUrl());
        ArgumentCaptor<ChatOutboxEvent> event = ArgumentCaptor.forClass(ChatOutboxEvent.class);
        verify(outbox).insert(event.capture());
        assertEquals(List.of(RECIPIENT), event.getValue().recipientIds());
        assertFalse(event.getValue().data().containsKey("attachmentUrl"));
    }

    @Test
    void rejectsExternalAndCrossConversationUrlsBeforeAttaching() {
        assertThrows(IllegalArgumentException.class,
                () -> service.sendImage(ROOM, SENDER, "https://evil.example/image.jpg"));
        assertThrows(IllegalArgumentException.class,
                () -> service.sendImage(ROOM, SENDER, "/learning/conversations/"
                        + RECIPIENT + "/media/" + MEDIA));
        verifyNoInteractions(media);
    }
}
