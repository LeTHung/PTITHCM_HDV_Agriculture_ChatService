package vn.ptit.soa.chat.security;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.soa.chat.conversation.ConversationReadService;

@RestController
@Profile("secure & database-learning")
@RequestMapping("/api/v1/conversations")
public class SecureConversationReadController {
    private final ConversationReadService reads;
    private final UserAccessStateClient access;

    public SecureConversationReadController(ConversationReadService reads, UserAccessStateClient access) {
        this.reads = reads;
        this.access = access;
    }

    @GetMapping
    public ConversationReadService.ConversationPage list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor) {
        access.requireActiveParticipant(jwt);
        return reads.list(jwt.getSubject(), limit, cursor);
    }

    @GetMapping("/{conversationId}")
    public ConversationReadService.ConversationDetail detail(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String conversationId) {
        access.requireActiveParticipant(jwt);
        return reads.detail(conversationId, jwt.getSubject());
    }
}
