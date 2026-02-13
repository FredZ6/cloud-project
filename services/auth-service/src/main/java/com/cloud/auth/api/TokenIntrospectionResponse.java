package com.cloud.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record TokenIntrospectionResponse(
        @Schema(description = "Whether token is active", example = "true")
        boolean active,
        @Schema(description = "User identifier", example = "user-1")
        String userId,
        @Schema(description = "Roles from token")
        List<String> roles,
        @Schema(description = "Token expiration time")
        Instant expiresAt,
        @Schema(description = "Inactive reason when active=false", example = "TOKEN_EXPIRED")
        String reason
) {
}
