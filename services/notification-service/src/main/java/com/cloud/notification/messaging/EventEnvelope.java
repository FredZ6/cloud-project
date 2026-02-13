package com.cloud.notification.messaging;

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
}
