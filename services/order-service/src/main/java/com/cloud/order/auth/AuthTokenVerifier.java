package com.cloud.order.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Component
public class AuthTokenVerifier {

    private final ObjectMapper objectMapper;
    private final JwtPublicKeyProvider publicKeyProvider;

    @Value("${app.auth.expected-issuer:auth-service}")
    private String expectedIssuer;

    public AuthTokenVerifier(ObjectMapper objectMapper, JwtPublicKeyProvider publicKeyProvider) {
        this.objectMapper = objectMapper;
        this.publicKeyProvider = publicKeyProvider;
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
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw unauthorized("Invalid token format");
        }

        JsonNode header = parseJsonPart(parts[0], "Token header is invalid");
        String algorithm = header.path("alg").asText("");
        if (!"RS256".equals(algorithm)) {
            throw unauthorized("Unsupported token algorithm");
        }

        String keyId = header.path("kid").asText("").trim();
        RSAPublicKey publicKey;
        try {
            publicKey = publicKeyProvider.resolve(keyId);
        } catch (Exception exception) {
            throw unauthorized("Unable to resolve token signing key");
        }

        String signingInput = parts[0] + "." + parts[1];
        if (!verifySignature(signingInput, parts[2], publicKey)) {
            throw unauthorized("Invalid token signature");
        }

        JsonNode payload = parseJsonPart(parts[1], "Token payload is invalid");

        String userId = payload.path("sub").asText(payload.path("user_id").asText("")).trim();
        if (userId.isEmpty()) {
            throw unauthorized("Token subject is missing");
        }

        String issuer = payload.path("iss").asText("").trim();
        if (!expectedIssuer.isBlank()) {
            if (issuer.isEmpty()) {
                throw unauthorized("Token issuer is missing");
            }
            if (!expectedIssuer.equals(issuer)) {
                throw unauthorized("Invalid token issuer");
            }
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

    private JsonNode parseJsonPart(String part, String errorMessage) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(part);
            return objectMapper.readTree(bytes);
        } catch (Exception exception) {
            throw unauthorized(errorMessage);
        }
    }

    private boolean verifySignature(String signingInput, String signaturePart, RSAPublicKey publicKey) {
        try {
            byte[] signatureBytes = Base64.getUrlDecoder().decode(signaturePart);
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signatureBytes);
        } catch (Exception exception) {
            return false;
        }
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
