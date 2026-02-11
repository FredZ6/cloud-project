package com.cloud.order.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthTokenVerifierTest {

    private AuthTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new AuthTokenVerifier(new ObjectMapper());
        ReflectionTestUtils.setField(verifier, "tokenSecret", "test-secret");
    }

    @Test
    void shouldReturnEmptyWhenAuthorizationIsMissing() {
        assertTrue(verifier.verifyBearerAuthorization(null).isEmpty());
    }

    @Test
    void shouldVerifyValidBearerToken() {
        Instant expiresAt = Instant.now().plusSeconds(600);
        String token = tokenFor("user-1", List.of("buyer"), expiresAt);

        AuthTokenClaims claims = verifier.verifyBearerAuthorization("Bearer " + token).orElseThrow();

        assertEquals("user-1", claims.userId());
        assertEquals(List.of("buyer"), claims.roles());
    }

    @Test
    void shouldRejectExpiredToken() {
        Instant expiresAt = Instant.now().minusSeconds(10);
        String token = tokenFor("user-2", List.of(), expiresAt);

        assertThrows(ResponseStatusException.class, () -> verifier.verifyBearerAuthorization("Bearer " + token));
    }

    private String tokenFor(String userId, List<String> roles, Instant expiresAt) {
        StringBuilder rolesJson = new StringBuilder("[");
        for (int i = 0; i < roles.size(); i++) {
            if (i > 0) {
                rolesJson.append(',');
            }
            rolesJson.append('"').append(roles.get(i)).append('"');
        }
        rolesJson.append(']');

        String payload = "{\"user_id\":\"" + userId + "\",\"roles\":" + rolesJson + ",\"exp\":" + expiresAt.getEpochSecond() + "}";
        String payloadPart = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signaturePart = sign(payloadPart);
        return payloadPart + "." + signaturePart;
    }

    private String sign(String payloadPart) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(payloadPart.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
