package com.cloud.order.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        @Schema(description = "Order ID")
        UUID orderId,
        @Schema(description = "User ID", example = "user-1")
        String userId,
        @Schema(description = "Order lifecycle status", example = "NEW")
        String status,
        @Schema(description = "Order total amount", example = "48.30")
        BigDecimal totalAmount,
        @Schema(description = "Order creation time")
        Instant createdAt,
        @Schema(description = "True if idempotency key reused an existing order")
        boolean reused,
        @Schema(description = "Ordered items")
        List<OrderItemResponse> items
) {
    public record OrderItemResponse(
            @Schema(description = "SKU identifier", example = "SKU-001")
            String skuId,
            @Schema(description = "Quantity", example = "2")
            int quantity,
            @Schema(description = "Unit price", example = "19.90")
            BigDecimal price
    ) {
    }
}
