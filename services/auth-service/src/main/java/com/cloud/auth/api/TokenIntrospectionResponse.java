package com.cloud.auth.api;

import java.time.Instant;
import java.util.List;

public record TokenIntrospectionResponse(
        boolean active,
        String userId,
        List<String> roles,
        Instant expiresAt,
        String reason
) {
}
