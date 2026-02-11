package com.cloud.order.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Component
public class AuthTokenVerifier {

    private final ObjectMapper objectMapper;

    @Value("${app.auth.token-secret:dev-auth-secret-change-me}")
    private String tokenSecret;

    public AuthTokenVerifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<AuthTokenClaims> verifyBearerAuthorization(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return Optional.empty();
        }

        String value = authorizationHeader.trim();
        if (!value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw unauthorized("Authorization header must use Bearer token");
        }

        String token = value.substring(7).trim();
        if (token.isBlank()) {
            throw unauthorized("Bearer token is empty");
        }

        return Optional.of(verifyToken(token));
    }

    private AuthTokenClaims verifyToken(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw unauthorized("Invalid token format");
        }

        String expectedSignature = sign(parts[0]);
        if (!constantTimeEquals(parts[1], expectedSignature)) {
            throw unauthorized("Invalid token signature");
        }

        JsonNode payload = parsePayload(parts[0]);
        String userId = payload.path("user_id").asText("").trim();
        if (userId.isEmpty()) {
            throw unauthorized("Token subject is missing");
        }

        long expEpochSeconds = payload.path("exp").asLong(0L);
        if (expEpochSeconds <= 0L) {
            throw unauthorized("Token expiration is missing");
        }

        Instant expiresAt = Instant.ofEpochSecond(expEpochSeconds);
        if (!expiresAt.isAfter(Instant.now())) {
            throw unauthorized("Token expired");
        }

        List<String> roles = extractRoles(payload.path("roles"));
        return new AuthTokenClaims(userId, roles, expiresAt);
    }

    private JsonNode parsePayload(String payloadPart) {
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadPart);
            return objectMapper.readTree(payloadBytes);
        } catch (Exception exception) {
            throw unauthorized("Token payload is invalid");
        }
    }

    private String sign(String payloadPart) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(payloadPart.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to verify token", exception);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private List<String> extractRoles(JsonNode rolesNode) {
        if (!rolesNode.isArray()) {
            return List.of();
        }
        List<String> roles = new java.util.ArrayList<>();
        for (JsonNode roleNode : rolesNode) {
            String role = roleNode.asText("").trim();
            if (!role.isEmpty()) {
                roles.add(role);
            }
        }
        return List.copyOf(roles);
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }
}
