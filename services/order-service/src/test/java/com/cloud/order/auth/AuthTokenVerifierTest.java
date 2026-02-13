package com.cloud.order.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthTokenVerifierTest {

    private AuthTokenVerifier verifier;
    private RSAPrivateKey privateKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        verifier = new AuthTokenVerifier(new ObjectMapper(), keyId -> publicKey);
        ReflectionTestUtils.setField(verifier, "expectedIssuer", "auth-service");
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

        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"test-key\"}";
        String payload = "{\"sub\":\"" + userId + "\",\"roles\":" + rolesJson + ",\"iss\":\"auth-service\",\"exp\":" + expiresAt.getEpochSecond() + "}";

        String headerPart = Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes(StandardCharsets.UTF_8));
        String payloadPart = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signingInput = headerPart + "." + payloadPart;
        String signaturePart = sign(signingInput);
        return signingInput + "." + signaturePart;
    }

    private String sign(String signingInput) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            byte[] signed = signature.sign();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signed);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
