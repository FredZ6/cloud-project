package com.cloud.auth.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record IssueTokenRequest(
        @NotBlank String userId,
        List<@NotBlank String> roles,
        @Min(60) @Max(86400) Integer ttlSeconds
) {
}
