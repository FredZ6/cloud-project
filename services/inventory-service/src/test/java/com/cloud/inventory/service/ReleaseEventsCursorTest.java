package com.cloud.inventory.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReleaseEventsCursorTest {

    @Test
    void encodeDecodeRoundtrip() {
        Instant createdAt = Instant.parse("2026-02-11T10:00:00Z");
        UUID id = UUID.fromString("a32ee3e5-0f81-4a88-a361-f1f2158dfdbf");

        String cursor = ReleaseEventsCursor.encode(createdAt, id);
        ReleaseEventsCursor.Decoded decoded = ReleaseEventsCursor.decode(cursor);

        assertEquals(createdAt, decoded.createdAt());
        assertEquals(id, decoded.id());
    }

    @Test
    void decodeRejectsInvalidBase64() {
        assertThrows(ResponseStatusException.class, () -> ReleaseEventsCursor.decode("!!!"));
    }

    @Test
    void decodeRejectsInvalidPayloadFormat() {
        String payload = "not-a-cursor";
        String cursor = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        assertThrows(ResponseStatusException.class, () -> ReleaseEventsCursor.decode(cursor));
    }
}

