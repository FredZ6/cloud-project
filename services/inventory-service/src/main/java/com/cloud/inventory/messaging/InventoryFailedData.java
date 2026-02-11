package com.cloud.inventory.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record InventoryFailedData(
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("reservation_id") UUID reservationId,
        String reason
) {
}
