package com.cloud.auth.service;

import com.cloud.auth.api.IssueTokenRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {

    private TokenService tokenService;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        JwtSigningKeyProvider keyProvider = new JwtSigningKeyProvider(keyPair, "test-key");
        tokenService = new TokenService(new ObjectMapper(), keyProvider);
        ReflectionTestUtils.setField(tokenService, "defaultTtlSeconds", 3600);
        ReflectionTestUtils.setField(tokenService, "issuer", "auth-service");
    }

    @Test
    void issueAndIntrospectShouldReturnActiveClaims() {
        var issued = tokenService.issueToken(new IssueTokenRequest("user-1", List.of("buyer"), 600));

        var introspection = tokenService.introspect(issued.token());

        assertTrue(introspection.active());
        assertEquals("user-1", introspection.claims().userId());
        assertEquals(List.of("buyer"), introspection.claims().roles());
    }

    @Test
    void introspectShouldRejectTamperedToken() {
        var issued = tokenService.issueToken(new IssueTokenRequest("user-2", List.of(), 600));
        String[] parts = issued.token().split("\\.");
        String tamperedPayload = parts[1].substring(0, parts[1].length() - 1)
                + (parts[1].endsWith("A") ? "B" : "A");
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        var introspection = tokenService.introspect(tampered);

        assertFalse(introspection.active());
        assertEquals("TOKEN_SIGNATURE_INVALID", introspection.reason());
    }
}
