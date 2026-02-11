package com.cloud.auth.service;

public record TokenIntrospectionResult(
        boolean active,
        AuthTokenClaims claims,
        String reason
) {
}
