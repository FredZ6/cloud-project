package com.cloud.auth.service;

import com.cloud.auth.api.IssueTokenRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(new ObjectMapper());
        ReflectionTestUtils.setField(tokenService, "tokenSecret", "test-secret");
        ReflectionTestUtils.setField(tokenService, "defaultTtlSeconds", 3600);
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
        String tampered = issued.token().substring(0, issued.token().length() - 1) + "x";

        var introspection = tokenService.introspect(tampered);

        assertFalse(introspection.active());
        assertEquals("TOKEN_SIGNATURE_INVALID", introspection.reason());
    }
}
