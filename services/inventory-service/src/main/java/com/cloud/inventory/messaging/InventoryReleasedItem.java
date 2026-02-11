package com.cloud.inventory.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InventoryReleasedItem(
        @JsonProperty("sku_id") String skuId,
        int quantity
) {
}
