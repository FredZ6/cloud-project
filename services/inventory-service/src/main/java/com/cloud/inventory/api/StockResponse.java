package com.cloud.inventory.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record StockResponse(
        @Schema(description = "SKU identifier", example = "SKU-001")
        String skuId,
        @Schema(description = "Available quantity", example = "98")
        int availableQty,
        @Schema(description = "Reserved quantity", example = "2")
        int reservedQty,
        @Schema(description = "Last update time")
        Instant updatedAt
) {
}
