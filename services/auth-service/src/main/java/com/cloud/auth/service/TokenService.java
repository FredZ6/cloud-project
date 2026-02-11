package com.cloud.auth.service;

import com.cloud.auth.api.IssueTokenRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;

@Service
public class TokenService {

    private final ObjectMapper objectMapper;

    @Value("${app.auth.default-ttl-seconds:3600}")
    private int defaultTtlSeconds;

    @Value("${app.auth.token-secret:dev-auth-secret-change-me}")
    private String tokenSecret;

    public TokenService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TokenIssueResult issueToken(IssueTokenRequest request) {
        Instant expiresAt = Instant.now().plusSeconds(resolveTtlSeconds(request.ttlSeconds()));
        String userId = normalizeUserId(request.userId());
        List<String> roles = normalizeRoles(request.roles());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", userId);
        payload.put("roles", roles);
        payload.put("exp", expiresAt.getEpochSecond());

        String payloadPart = base64UrlEncode(toJsonBytes(payload));
        String signaturePart = sign(payloadPart);

        String token = payloadPart + "." + signaturePart;
        AuthTokenClaims claims = new AuthTokenClaims(userId, roles, expiresAt);
        return new TokenIssueResult(token, claims);
    }

    public TokenIntrospectionResult introspect(String token) {
        try {
            AuthTokenClaims claims = verify(token);
            return new TokenIntrospectionResult(true, claims, null);
        } catch (TokenValidationException exception) {
            return new TokenIntrospectionResult(false, null, exception.reason());
        }
    }

    public AuthTokenClaims verify(String token) {
        if (token == null || token.isBlank()) {
            throw new TokenValidationException("TOKEN_MISSING");
        }

        String[] parts = token.trim().split("\\.");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new TokenValidationException("TOKEN_MALFORMED");
        }

        String expectedSignature = sign(parts[0]);
        if (!constantTimeEquals(parts[1], expectedSignature)) {
            throw new TokenValidationException("TOKEN_SIGNATURE_INVALID");
        }

        JsonNode payload = parsePayload(parts[0]);
        String userId = normalizeUserId(payload.path("user_id").asText(""));

        long expEpochSeconds = payload.path("exp").asLong(0L);
        if (expEpochSeconds <= 0L) {
            throw new TokenValidationException("TOKEN_EXP_MISSING");
        }

        Instant expiresAt = Instant.ofEpochSecond(expEpochSeconds);
        if (!expiresAt.isAfter(Instant.now())) {
            throw new TokenValidationException("TOKEN_EXPIRED");
        }

        List<String> roles = normalizeRoles(extractRoles(payload));
        return new AuthTokenClaims(userId, roles, expiresAt);
    }

    private int resolveTtlSeconds(Integer requestedTtlSeconds) {
        if (requestedTtlSeconds == null) {
            return defaultTtlSeconds;
        }
        return requestedTtlSeconds;
    }

    private JsonNode parsePayload(String payloadPart) {
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadPart);
            return objectMapper.readTree(payloadBytes);
        } catch (IllegalArgumentException | IOException exception) {
            throw new TokenValidationException("TOKEN_PAYLOAD_INVALID");
        }
    }

    private List<String> extractRoles(JsonNode payload) {
        JsonNode rolesNode = payload.path("roles");
        if (!rolesNode.isArray()) {
            return List.of();
        }
        return toStringList(rolesNode);
    }

    private List<String> toStringList(JsonNode arrayNode) {
        List<String> values = new java.util.ArrayList<>();
        for (JsonNode roleNode : arrayNode) {
            String value = roleNode.asText("").trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private String normalizeUserId(String value) {
        if (value == null || value.isBlank()) {
            throw new TokenValidationException("TOKEN_USER_ID_MISSING");
        }
        return value.trim();
    }

    private List<String> normalizeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new java.util.ArrayList<>();
        for (String role : roles) {
            if (role == null) {
                continue;
            }
            String trimmed = role.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return List.copyOf(normalized);
    }

    private byte[] toJsonBytes(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize token payload", exception);
        }
    }

    private String base64UrlEncode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String sign(String payloadPart) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return base64UrlEncode(mac.doFinal(payloadPart.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign token", exception);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static final class TokenValidationException extends RuntimeException {
        private final String reason;

        private TokenValidationException(String reason) {
            super(reason);
            this.reason = reason;
        }

        private String reason() {
            return reason;
        }
    }
}
