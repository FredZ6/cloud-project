package com.cloud.order.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record OrderCreatedItem(
        @JsonProperty("sku_id") String skuId,
        int quantity,
        BigDecimal price
) {
}
