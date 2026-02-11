package com.cloud.inventory.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderCreatedData(
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("user_id") String userId,
        List<OrderCreatedItem> items,
        @JsonProperty("total_amount") BigDecimal totalAmount
) {
}
