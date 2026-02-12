package com.cloud.notification.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NotificationEvent(
        UUID eventId,
        String eventType,
        UUID orderId,
        UUID paymentId,
        String reason,
        UUID traceId,
        String userId,
        List<String> roles,
        Instant occurredAt,
        Instant receivedAt
) {
}
