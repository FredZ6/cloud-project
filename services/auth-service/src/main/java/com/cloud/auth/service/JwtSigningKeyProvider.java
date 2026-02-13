package com.cloud.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JwtSigningKeyProvider {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;

    @Autowired
    public JwtSigningKeyProvider(
            @Value("${app.auth.rsa.private-key-pem:}") String privateKeyPem,
            @Value("${app.auth.rsa.public-key-pem:}") String publicKeyPem,
            @Value("${app.auth.rsa.key-id:}") String configuredKeyId
    ) {
        this(resolveKeyPair(privateKeyPem, publicKeyPem), configuredKeyId);
    }

    JwtSigningKeyProvider(KeyPair keyPair, String configuredKeyId) {
        this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
        this.publicKey = (RSAPublicKey) keyPair.getPublic();
        this.keyId = configuredKeyId == null || configuredKeyId.isBlank()
                ? deriveKeyId(this.publicKey)
                : configuredKeyId.trim();
    }

    public RSAPrivateKey privateKey() {
        return privateKey;
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public String keyId() {
        return keyId;
    }

    public Map<String, Object> toJwk() {
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("kid", keyId);
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("n", toBase64UrlUnsigned(publicKey.getModulus()));
        jwk.put("e", toBase64UrlUnsigned(publicKey.getPublicExponent()));
        return jwk;
    }

    private static KeyPair resolveKeyPair(String privateKeyPem, String publicKeyPem) {
        if (isBlank(privateKeyPem) && isBlank(publicKeyPem)) {
            return generateKeyPair();
        }
        if (isBlank(privateKeyPem) || isBlank(publicKeyPem)) {
            throw new IllegalStateException("Both app.auth.rsa.private-key-pem and app.auth.rsa.public-key-pem must be provided");
        }
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decodePem(privateKeyPem)));
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decodePem(publicKeyPem)));
            return new KeyPair(publicKey, privateKey);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse RSA key pair", exception);
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate RSA key pair", exception);
        }
    }

    private static String deriveKeyId(RSAPublicKey key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getEncoded());
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(Arrays.copyOf(hash, 12));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to derive key id", exception);
        }
    }

    private static byte[] decodePem(String pem) {
        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }

    private static String toBase64UrlUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
