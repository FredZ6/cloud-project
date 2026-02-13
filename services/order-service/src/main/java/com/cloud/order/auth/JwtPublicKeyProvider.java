package com.cloud.order.auth;

import java.security.interfaces.RSAPublicKey;

@FunctionalInterface
public interface JwtPublicKeyProvider {
    RSAPublicKey resolve(String keyId);
}
