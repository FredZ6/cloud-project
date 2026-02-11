package com.cloud.inventory.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record InventoryReleasedData(
        @JsonProperty("release_id") UUID releaseId,
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("reservation_id") UUID reservationId,
        String reason,
        @JsonProperty("released_items") List<InventoryReleasedItem> releasedItems
) {
}
