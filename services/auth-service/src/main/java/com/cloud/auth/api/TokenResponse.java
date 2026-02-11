package com.cloud.auth.api;

import java.time.Instant;
import java.util.List;

public record TokenResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        String userId,
        List<String> roles
) {
}
