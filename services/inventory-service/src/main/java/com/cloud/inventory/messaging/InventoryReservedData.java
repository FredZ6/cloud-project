package com.cloud.inventory.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record InventoryReservedData(
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("reservation_id") UUID reservationId,
        @JsonProperty("reserved_items") List<InventoryReservedItem> reservedItems
) {
}
