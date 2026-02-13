package com.cloud.auth.service;

import com.cloud.auth.api.IssueTokenRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TokenService {

    private final ObjectMapper objectMapper;
    private final JwtSigningKeyProvider signingKeyProvider;

    @Value("${app.auth.default-ttl-seconds:3600}")
    private int defaultTtlSeconds;

    @Value("${app.auth.issuer:auth-service}")
    private String issuer;

    public TokenService(ObjectMapper objectMapper, JwtSigningKeyProvider signingKeyProvider) {
        this.objectMapper = objectMapper;
        this.signingKeyProvider = signingKeyProvider;
    }

    public TokenIssueResult issueToken(IssueTokenRequest request) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(resolveTtlSeconds(request.ttlSeconds()));
        String userId = normalizeUserId(request.userId());
        List<String> roles = normalizeRoles(request.roles());

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        header.put("kid", signingKeyProvider.keyId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", userId);
        payload.put("user_id", userId);
        payload.put("roles", roles);
        payload.put("iss", issuer);
        payload.put("iat", issuedAt.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        String headerPart = base64UrlEncode(toJsonBytes(header));
        String payloadPart = base64UrlEncode(toJsonBytes(payload));
        String signingInput = headerPart + "." + payloadPart;
        String signaturePart = sign(signingInput);

        String token = signingInput + "." + signaturePart;
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
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new TokenValidationException("TOKEN_MALFORMED");
        }

        JsonNode header = parseJsonPart(parts[0], "TOKEN_HEADER_INVALID");
        if (!"RS256".equals(header.path("alg").asText(""))) {
            throw new TokenValidationException("TOKEN_ALG_INVALID");
        }

        String keyId = header.path("kid").asText("").trim();
        if (!keyId.isEmpty() && !signingKeyProvider.keyId().equals(keyId)) {
            throw new TokenValidationException("TOKEN_KID_UNKNOWN");
        }

        String signingInput = parts[0] + "." + parts[1];
        if (!verifySignature(signingInput, parts[2])) {
            throw new TokenValidationException("TOKEN_SIGNATURE_INVALID");
        }

        JsonNode payload = parseJsonPart(parts[1], "TOKEN_PAYLOAD_INVALID");
        String userId = normalizeUserId(payload.path("sub").asText(payload.path("user_id").asText("")));

        String tokenIssuer = payload.path("iss").asText("").trim();
        if (!issuer.isBlank() && !issuer.equals(tokenIssuer)) {
            throw new TokenValidationException("TOKEN_ISSUER_INVALID");
        }

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

    public Map<String, Object> currentJwk() {
        return signingKeyProvider.toJwk();
    }

    private int resolveTtlSeconds(Integer requestedTtlSeconds) {
        if (requestedTtlSeconds == null) {
            return defaultTtlSeconds;
        }
        return requestedTtlSeconds;
    }

    private JsonNode parseJsonPart(String encodedPart, String reason) {
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(encodedPart);
            return objectMapper.readTree(payloadBytes);
        } catch (IllegalArgumentException | IOException exception) {
            throw new TokenValidationException(reason);
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

    private String sign(String signingInput) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(signingKeyProvider.privateKey());
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return base64UrlEncode(signature.sign());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign token", exception);
        }
    }

    private boolean verifySignature(String signingInput, String signaturePart) {
        try {
            byte[] signatureBytes = Base64.getUrlDecoder().decode(signaturePart);
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(signingKeyProvider.publicKey());
            verifier.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signatureBytes);
        } catch (Exception exception) {
            return false;
        }
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
