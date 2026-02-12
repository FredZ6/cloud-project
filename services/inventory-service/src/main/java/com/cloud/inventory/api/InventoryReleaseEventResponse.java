package com.cloud.inventory.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record InventoryReleaseEventResponse(
        @Schema(description = "Release event ID")
        UUID releaseId,
        @Schema(description = "Order ID")
        UUID orderId,
        @Schema(description = "Reservation ID")
        UUID reservationId,
        @Schema(description = "Release reason", example = "PAYMENT_FAILED")
        String reason,
        @Schema(description = "Release event creation time")
        Instant createdAt
) {
}
