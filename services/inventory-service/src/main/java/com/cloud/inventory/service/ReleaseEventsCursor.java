package com.cloud.inventory.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public final class ReleaseEventsCursor {

    private ReleaseEventsCursor() {
    }

    public static String encode(Instant createdAt, UUID id) {
        if (createdAt == null || id == null) {
            throw new IllegalArgumentException("createdAt and id are required");
        }
        String payload = createdAt.toEpochMilli() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static Decoded decode(String after) {
        if (after == null || after.isBlank()) {
            throw badRequest("after must be provided");
        }

        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(after), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw badRequest("invalid cursor");
        }

        String[] parts = payload.split(":", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw badRequest("invalid cursor");
        }

        long millis;
        UUID id;
        try {
            millis = Long.parseLong(parts[0]);
            id = UUID.fromString(parts[1]);
        } catch (RuntimeException e) {
            throw badRequest("invalid cursor");
        }

        return new Decoded(Instant.ofEpochMilli(millis), id);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record Decoded(Instant createdAt, UUID id) {
    }
}

