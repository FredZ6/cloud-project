package com.cloud.inventory.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID reservationId,
        UUID orderId,
        String status,
        String reason,
        Instant createdAt,
        List<ReservationItemResponse> items
) {
    public record ReservationItemResponse(String skuId, int quantity) {
    }
}
