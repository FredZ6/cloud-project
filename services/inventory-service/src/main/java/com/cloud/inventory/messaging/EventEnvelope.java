package com.cloud.inventory.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") Instant occurredAt,
        @JsonProperty("trace_id") UUID traceId,
        EventIdentity identity,
        T data,
        int version
) {
    public static <T> EventEnvelope<T> of(String eventType, T data) {
        return of(eventType, data, null);
    }

    public static <T> EventEnvelope<T> of(String eventType, T data, EventIdentity identity) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                Instant.now(),
                UUID.randomUUID(),
                identity,
                data,
                1
        );
    }

    public static <T> EventEnvelope<T> of(String eventType, T data, UUID traceId, EventIdentity identity) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                Instant.now(),
                traceId == null ? UUID.randomUUID() : traceId,
                identity,
                data,
                1
        );
    }
}
