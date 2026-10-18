package vn.ptit.soa.chat.security;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.ptit.soa.chat.conversation.ConversationReadService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"secure", "database-learning"})
class SecureConversationHttpTest {
    private static final String ACCOUNT = "233196d7-d149-451d-8006-91e6563be4f0";
    private static final AtomicInteger STATE = new AtomicInteger();
    private static final KeyPair KEY;
    private static final Path KEY_DIR;
    private static final HttpServer USER;

    static {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KEY = generator.generateKeyPair();
            KEY_DIR = Files.createTempDirectory("chat-public-keys-");
            String encoded = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(KEY.getPublic().getEncoded());
            Files.writeString(KEY_DIR.resolve("test-key.pem"), "-----BEGIN PUBLIC KEY-----\n"
                    + encoded + "\n-----END PUBLIC KEY-----\n");
            USER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            USER.createContext("/internal/v1/accounts/" + ACCOUNT + "/access-state", exchange -> {
                String body = STATE.get() == 1
                        ? "{\"accountId\":\"" + ACCOUNT + "\",\"status\":\"LOCKED\",\"role\":\"FARMER\",\"accessAllowed\":false}"
                        : "{\"accountId\":\"" + ACCOUNT + "\",\"status\":\"ACTIVE\",\"role\":\"FARMER\",\"accessAllowed\":true}";
                if (STATE.get() == 2 || !"chat-key".equals(exchange.getRequestHeaders().getFirst("X-Service-Key"))) {
                    exchange.sendResponseHeaders(503, -1);
                } else {
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
                exchange.close();
            });
            USER.start();
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("chat.jwt.public-key-directory", KEY_DIR::toString);
        registry.add("chat.user-service-url", () -> "http://127.0.0.1:" + USER.getAddress().getPort());
        registry.add("chat.internal-service-key", () -> "chat-key");
    }

    @Autowired MockMvc mvc;
    @MockitoBean ConversationReadService reads;

    @Test
    void requestMustHaveValidJwtAndActiveStateBeforeReading() throws Exception {
        when(reads.list(eq(ACCOUNT), eq(20), isNull())).thenReturn(
                new ConversationReadService.ConversationPage(List.of(), null));
        mvc.perform(get("/api/v1/conversations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/conversations").header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
        STATE.set(0);
        mvc.perform(get("/api/v1/conversations").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        STATE.set(1);
        mvc.perform(get("/api/v1/conversations").header("Authorization", "Bearer " + token()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
        STATE.set(2);
        mvc.perform(get("/api/v1/conversations").header("Authorization", "Bearer " + token()))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("ACCESS_STATE_UNAVAILABLE"));
    }

    private String token() throws Exception {
        Instant now = Instant.now();
        SignedJWT token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(),
                new JWTClaimsSet.Builder().issuer("urn:soa:user-service").audience("soa-api")
                        .subject(ACCOUNT).claim("role", "FARMER").issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(900))).jwtID(UUID.randomUUID().toString()).build());
        token.sign(new RSASSASigner(KEY.getPrivate()));
        return token.serialize();
    }
}
