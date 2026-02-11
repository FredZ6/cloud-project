package com.cloud.order.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record InventoryReleaseRequestedData(
        @JsonProperty("order_id") UUID orderId,
        String reason
) {
}
