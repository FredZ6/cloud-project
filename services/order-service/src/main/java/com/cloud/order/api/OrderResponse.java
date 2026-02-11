package com.cloud.order.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        String userId,
        String status,
        BigDecimal totalAmount,
        Instant createdAt,
        boolean reused,
        List<OrderItemResponse> items
) {
    public record OrderItemResponse(String skuId, int quantity, BigDecimal price) {
    }
}
