package com.cloud.order.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class JwksPublicKeyProvider implements JwtPublicKeyProvider {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI jwksUri;
    private final Duration cacheTtl;
    private final ReentrantLock refreshLock = new ReentrantLock();

    private volatile CachedKeys cachedKeys = new CachedKeys(Instant.EPOCH, Map.of());

    public JwksPublicKeyProvider(
            ObjectMapper objectMapper,
            @Value("${app.auth.jwks-uri:http://localhost:8084/.well-known/jwks.json}") String jwksUri,
            @Value("${app.auth.jwks-cache-seconds:300}") long jwksCacheSeconds
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        this.jwksUri = URI.create(jwksUri);
        this.cacheTtl = Duration.ofSeconds(Math.max(jwksCacheSeconds, 1));
    }

    @Override
    public RSAPublicKey resolve(String keyId) {
        Map<String, RSAPublicKey> keys = getKeys(false);
        RSAPublicKey resolved = resolveFromMap(keys, keyId);
        if (resolved != null) {
            return resolved;
        }

        keys = getKeys(true);
        resolved = resolveFromMap(keys, keyId);
        if (resolved != null) {
            return resolved;
        }

        throw new IllegalStateException("No matching JWKS key found");
    }

    private Map<String, RSAPublicKey> getKeys(boolean forceRefresh) {
        CachedKeys snapshot = cachedKeys;
        if (!forceRefresh && !isExpired(snapshot)) {
            return snapshot.keys();
        }

        refreshLock.lock();
        try {
            CachedKeys current = cachedKeys;
            if (!forceRefresh && !isExpired(current)) {
                return current.keys();
            }
            Map<String, RSAPublicKey> fetched = fetchKeysFromJwks();
            cachedKeys = new CachedKeys(Instant.now(), Map.copyOf(fetched));
            return cachedKeys.keys();
        } finally {
            refreshLock.unlock();
        }
    }

    private boolean isExpired(CachedKeys snapshot) {
        return snapshot.keys().isEmpty() || Instant.now().isAfter(snapshot.loadedAt().plus(cacheTtl));
    }

    private Map<String, RSAPublicKey> fetchKeysFromJwks() {
        try {
            HttpRequest request = HttpRequest.newBuilder(jwksUri)
                    .timeout(Duration.ofSeconds(3))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Failed to fetch JWKS: HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode keysNode = root.path("keys");
            if (!keysNode.isArray()) {
                throw new IllegalStateException("Invalid JWKS payload");
            }

            Map<String, RSAPublicKey> resolved = new HashMap<>();
            int fallbackIndex = 0;
            for (JsonNode keyNode : keysNode) {
                if (!"RSA".equals(keyNode.path("kty").asText(""))) {
                    continue;
                }
                String modulus = keyNode.path("n").asText("");
                String exponent = keyNode.path("e").asText("");
                if (modulus.isBlank() || exponent.isBlank()) {
                    continue;
                }
                RSAPublicKey publicKey = toPublicKey(modulus, exponent);
                String kid = keyNode.path("kid").asText("").trim();
                if (kid.isBlank()) {
                    kid = "__fallback__" + fallbackIndex++;
                }
                resolved.put(kid, publicKey);
            }

            if (resolved.isEmpty()) {
                throw new IllegalStateException("JWKS contains no usable RSA keys");
            }
            return resolved;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fetch JWKS keys", exception);
        }
    }

    private RSAPublicKey toPublicKey(String modulus, String exponent) throws Exception {
        BigInteger n = new BigInteger(1, java.util.Base64.getUrlDecoder().decode(modulus));
        BigInteger e = new BigInteger(1, java.util.Base64.getUrlDecoder().decode(exponent));
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(new RSAPublicKeySpec(n, e));
    }

    private RSAPublicKey resolveFromMap(Map<String, RSAPublicKey> keys, String keyId) {
        if (keys.isEmpty()) {
            return null;
        }
        if (keyId != null && !keyId.isBlank()) {
            return keys.get(keyId);
        }
        if (keys.size() == 1) {
            return keys.values().iterator().next();
        }
        return null;
    }

    private record CachedKeys(Instant loadedAt, Map<String, RSAPublicKey> keys) {
    }
}
