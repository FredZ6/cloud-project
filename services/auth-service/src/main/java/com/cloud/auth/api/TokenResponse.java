package com.cloud.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record TokenResponse(
        @Schema(description = "JWT access token")
        String accessToken,
        @Schema(description = "Token type", example = "Bearer")
        String tokenType,
        @Schema(description = "Token expiration time")
        Instant expiresAt,
        @Schema(description = "User identifier", example = "user-1")
        String userId,
        @Schema(description = "Roles embedded in token")
        List<String> roles
) {
}
