package com.cloud.inventory.api;

import java.time.Instant;
import java.util.UUID;

public record InventoryReleaseEventResponse(
        UUID releaseId,
        UUID orderId,
        UUID reservationId,
        String reason,
        Instant createdAt
) {
}
