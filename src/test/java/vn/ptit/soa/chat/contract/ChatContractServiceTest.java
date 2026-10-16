package vn.ptit.soa.chat.contract;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import vn.ptit.soa.chat.conversation.Conversation;
import vn.ptit.soa.chat.conversation.ConversationRepository;
import vn.ptit.soa.chat.message.ChatMessageRepository;

class ChatContractServiceTest {
    private static final String OFFER = "202fe3fd-626a-41c8-80fd-70af82c1f256";
    private static final String CONTRACT = "202fe3fd-626a-41c8-80fd-70af82c1f257";
    private static final String FARMER = "233196d7-d149-451d-8006-91e6563be4f0";
    private static final String TRADER = "e14961fb-7e5a-4613-8837-8cadf6a887de";
    private static final String ROOM = "f72a08bd-33d7-4221-9e55-55d5d7f2e2f0";

    @Test
    void redeliveryDoesNotDuplicateContractCard() {
        ConversationRepository rooms = mock(ConversationRepository.class);
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        ChatContractService service = new ChatContractService(rooms, messages);
        when(rooms.findByOfferId(OFFER)).thenReturn(Optional.of(room(null)), Optional.of(room(CONTRACT)));
        when(rooms.linkContract(eq(ROOM), eq(CONTRACT), any())).thenReturn(true);
        when(messages.findById(any())).thenReturn(Optional.empty(), Optional.of(mock(vn.ptit.soa.chat.message.ChatMessage.class)));

        service.contractCreated(CONTRACT, OFFER, FARMER, TRADER, Instant.now());
        service.contractCreated(CONTRACT, OFFER, FARMER, TRADER, Instant.now());

        verify(rooms, times(1)).linkContract(eq(ROOM), eq(CONTRACT), any());
        verify(messages, times(1)).insert(any());
    }

    @Test
    void wrongParticipantsCannotLinkContract() {
        ConversationRepository rooms = mock(ConversationRepository.class);
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        when(rooms.findByOfferId(OFFER)).thenReturn(Optional.of(room(null)));
        ChatContractService service = new ChatContractService(rooms, messages);

        assertThrows(IllegalStateException.class, () -> service.contractCreated(CONTRACT, OFFER,
                FARMER, "11111111-1111-4111-8111-111111111111", Instant.now()));
        verify(rooms, never()).linkContract(any(), any(), any());
        verify(messages, never()).insert(any());
    }

    private Conversation room(String contractId) {
        return new Conversation(ROOM, List.of(FARMER, TRADER), OFFER, contractId,
                "ACTIVE", Instant.EPOCH, Instant.EPOCH);
    }
}
