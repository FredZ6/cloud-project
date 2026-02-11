package com.cloud.auth.api;

import jakarta.validation.constraints.NotBlank;

public record IntrospectTokenRequest(@NotBlank String token) {
}
