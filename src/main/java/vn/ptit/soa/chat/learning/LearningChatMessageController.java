package vn.ptit.soa.chat.learning;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.soa.chat.message.ChatMessage;
import vn.ptit.soa.chat.message.ChatMessageService;

@RestController
@Profile("learning & database-learning")
@RequestMapping("/learning/conversations/{conversationId}/messages")
public class LearningChatMessageController {
    private final ChatMessageService messages;

    public LearningChatMessageController(ChatMessageService messages) {
        this.messages = messages;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessage send(@PathVariable String conversationId, @RequestBody SendTextRequest request) {
        if (!"TEXT".equals(request.messageType())) {
            throw new IllegalArgumentException("This learning endpoint accepts TEXT only.");
        }
        return messages.sendText(conversationId, request.senderId(), request.content());
    }

    public record SendTextRequest(String senderId, String messageType, String content) {}

    @GetMapping
    public ChatMessageService.MessagePage history(@PathVariable String conversationId,
            @RequestParam String viewerId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor) {
        return messages.history(conversationId, viewerId, limit, cursor);
    }
}
