package com.cloud.auth.service;

public record TokenIssueResult(
        String token,
        AuthTokenClaims claims
) {
}
