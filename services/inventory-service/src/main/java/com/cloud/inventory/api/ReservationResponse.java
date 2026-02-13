package com.cloud.inventory.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        @Schema(description = "Reservation ID")
        UUID reservationId,
        @Schema(description = "Order ID")
        UUID orderId,
        @Schema(description = "Reservation status", example = "RESERVED")
        String status,
        @Schema(description = "Failure reason if reservation failed", example = "INSUFFICIENT_STOCK:SKU-001")
        String reason,
        @Schema(description = "Reservation creation time")
        Instant createdAt,
        @Schema(description = "Reserved items")
        List<ReservationItemResponse> items
) {
    public record ReservationItemResponse(
            @Schema(description = "SKU identifier", example = "SKU-001")
            String skuId,
            @Schema(description = "Reserved quantity", example = "1")
            int quantity
    ) {
    }
}
