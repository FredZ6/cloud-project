package com.cloud.auth.service;

import java.time.Instant;
import java.util.List;

public record AuthTokenClaims(
        String userId,
        List<String> roles,
        Instant expiresAt
) {
}
