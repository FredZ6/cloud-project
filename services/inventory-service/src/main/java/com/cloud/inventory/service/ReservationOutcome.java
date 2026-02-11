package com.cloud.inventory.service;

import com.cloud.inventory.domain.ReservationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationOutcome(
        UUID reservationId,
        UUID orderId,
        ReservationStatus status,
        String reason,
        Instant createdAt,
        List<ReservedItem> items
) {
    public record ReservedItem(String skuId, int quantity) {
    }
}
