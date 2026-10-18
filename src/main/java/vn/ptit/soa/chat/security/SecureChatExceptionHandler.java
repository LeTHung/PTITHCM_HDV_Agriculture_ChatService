package vn.ptit.soa.chat.security;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.ptit.soa.chat.conversation.ConversationReadService;

@RestControllerAdvice(basePackageClasses = SecureConversationReadController.class)
@Profile("secure")
public class SecureChatExceptionHandler {
    @ExceptionHandler(UserAccessStateClient.AccountForbiddenException.class)
    ResponseEntity<Map<String, Object>> inactive(HttpServletRequest request) {
        return error(403, "ACCOUNT_NOT_ACTIVE", "Account is not active or not allowed for Chat.", request);
    }

    @ExceptionHandler(UserAccessStateClient.AccessStateUnavailableException.class)
    ResponseEntity<Map<String, Object>> unavailable(HttpServletRequest request) {
        return error(503, "ACCESS_STATE_UNAVAILABLE", "Cannot verify account state.", request);
    }

    @ExceptionHandler(ConversationReadService.ConversationAccessDeniedException.class)
    ResponseEntity<Map<String, Object>> forbidden(HttpServletRequest request) {
        return error(403, "FORBIDDEN", "Not a conversation participant.", request);
    }

    @ExceptionHandler(ConversationReadService.ConversationNotFoundException.class)
    ResponseEntity<Map<String, Object>> missing(HttpServletRequest request) {
        return error(404, "NOT_FOUND", "Conversation not found.", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> invalid(IllegalArgumentException exception, HttpServletRequest request) {
        return error(400, "VALIDATION_ERROR", exception.getMessage(), request);
    }

    private ResponseEntity<Map<String, Object>> error(int status, String code, String message,
            HttpServletRequest request) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(Map.of(
                "timestamp", Instant.now().toString(), "status", status, "code", code,
                "message", message, "path", request.getRequestURI()));
    }
}
