package com.cloud.inventory.api;

import java.time.Instant;

public record StockResponse(
        String skuId,
        int availableQty,
        int reservedQty,
        Instant updatedAt
) {
}
