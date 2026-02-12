package com.cloud.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record IssueTokenRequest(
        @Schema(description = "User identifier", example = "user-1")
        @NotBlank String userId,
        @Schema(description = "User roles", example = "[\"buyer\"]")
        List<@NotBlank String> roles,
        @Schema(description = "Token lifetime in seconds (60-86400)", example = "3600")
        @Min(60) @Max(86400) Integer ttlSeconds
) {
}
