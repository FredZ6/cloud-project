package com.cloud.order.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InventoryReservedItem(
        @JsonProperty("sku_id") String skuId,
        int quantity
) {
}
