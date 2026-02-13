package com.cloud.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record IntrospectTokenRequest(
        @Schema(description = "Token to introspect")
        @NotBlank String token
) {
}
